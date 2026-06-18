// sepaImport.ts — Descarga el ZIP diario de SEPA desde datos.produccion.gob.ar,
// parsea el CSV y llena la tabla reference_prices en PostgreSQL.
// Uso: npm run seed:sepa
import * as https from 'https';
import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import unzipper from 'unzipper';
import { parse } from 'csv-parse';
import pool from '../db';
import dotenv from 'dotenv';

dotenv.config();

interface SepaRow {
  product_name: string;
  brand: string;
  supermarket: string;
  price: number;
  province: string;
}

// Detecta columnas por nombre (SEPA cambia los headers entre versiones)
function detectColumns(headers: string[]): {
  nameCol?: string; brandCol?: string; marketCol?: string;
  priceCol?: string; provinceCol?: string;
} {
  const h = headers.map(x => x.toLowerCase().trim().replace(/['"]/g, ''));
  const find = (...candidates: string[]) => {
    for (const c of candidates) {
      const idx = h.indexOf(c);
      if (idx >= 0) return headers[idx];
    }
    return undefined;
  };
  return {
    nameCol:     find('nombre', 'nombre_producto', 'product_name', 'descripcion', 'nombre_completo'),
    brandCol:    find('marca', 'brand', 'marca_nombre'),
    marketCol:   find('bandera_nombre', 'nombre_bandera', 'comercio_bandera', 'comercio_nombre', 'sucursal_nombre'),
    priceCol:    find('precio', 'productos_precio_lista', 'precio_lista', 'price', 'productos_precio_referencia'),
    provinceCol: find('provincia', 'province'),
  };
}

// C2: redirects parameter added to guard against infinite redirect recursion
function download(url: string, dest: string, redirects = 0): Promise<void> {
  if (redirects > 5) return Promise.reject(new Error('Too many redirects'));
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    const file = fs.createWriteStream(dest);
    // m2: WriteStream error handler
    file.on('error', err => { fs.unlink(dest, () => {}); reject(err); });
    proto.get(url, { headers: { 'User-Agent': 'SuperAhorro/1.0' } }, res => {
      if (res.statusCode === 301 || res.statusCode === 302) {
        file.close();
        fs.unlink(dest, () => {});
        // C2: guard against missing Location header; pass redirects counter
        if (!res.headers.location) { reject(new Error('Redirect without Location header')); return; }
        download(res.headers.location, dest, redirects + 1).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        // m3: drain the response body before rejecting
        res.resume();
        reject(new Error(`HTTP ${res.statusCode} al descargar ${url}`));
        return;
      }
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve()));
    }).on('error', err => { fs.unlink(dest, () => {}); reject(err); });
  });
}

async function getLatestZipUrl(): Promise<string> {
  const data = await new Promise<string>((resolve, reject) => {
    // C3: add req.on('error') and status code check
    const req = https.get(
      'https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios',
      { headers: { 'User-Agent': 'SuperAhorro/1.0' } },
      res => {
        if (res.statusCode !== 200) {
          res.resume();
          reject(new Error(`CKAN API returned HTTP ${res.statusCode}`));
          return;
        }
        let body = '';
        res.on('data', c => body += c);
        res.on('end', () => resolve(body));
        res.on('error', reject);
      }
    );
    req.on('error', reject);
  });
  const pkg = JSON.parse(data);
  const resources: any[] = pkg?.result?.resources ?? [];
  const zips = resources.filter((r: any) => (r.format ?? '').toUpperCase() === 'ZIP' && r.url);
  if (zips.length === 0) throw new Error('No se encontraron recursos ZIP en el dataset SEPA');
  zips.sort((a: any, b: any) =>
    new Date(b.last_modified ?? 0).getTime() - new Date(a.last_modified ?? 0).getTime()
  );
  console.log(`  Recurso: ${zips[0].name ?? zips[0].url}`);
  return zips[0].url as string;
}

function parseCsvStream(stream: NodeJS.ReadableStream): Promise<SepaRow[]> {
  return new Promise((resolve, reject) => {
    const rows: SepaRow[] = [];
    let cols: ReturnType<typeof detectColumns> | null = null;

    const parser = parse({ delimiter: ';', relax_column_count: true, skip_empty_lines: true, columns: true, bom: true });

    parser.on('readable', () => {
      let record: Record<string, string>;
      while ((record = parser.read()) !== null) {
        if (!cols) {
          cols = detectColumns(Object.keys(record));
          if (!cols.nameCol || !cols.marketCol || !cols.priceCol) {
            parser.destroy();
            // C1: drain the entry so unzipper can advance
            stream.resume();
            resolve([]);
            return;
          }
        }
        const name     = (record[cols.nameCol!] ?? '').trim();
        const market   = (record[cols.marketCol!] ?? '').trim();
        const priceStr = (record[cols.priceCol!] ?? '').replace(',', '.').trim();
        const price    = parseFloat(priceStr);
        const brand    = cols.brandCol    ? (record[cols.brandCol]    ?? '').trim() : '';
        const province = cols.provinceCol ? (record[cols.provinceCol] ?? '').trim() : '';
        // I1: removed hard cap of 10000 rows
        if (name && market && !isNaN(price) && price > 0) {
          rows.push({ product_name: name, brand, supermarket: market, price, province });
        }
      }
    });
    parser.on('error', reject);
    parser.on('end', () => resolve(rows));
    stream.pipe(parser);
  });
}

// I3: DELETE + INSERT wrapped in a transaction
async function insertRows(rows: SepaRow[]): Promise<void> {
  if (rows.length === 0) { console.log('Sin filas para insertar.'); return; }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('DELETE FROM reference_prices');
    const BATCH = 500;
    let inserted = 0;
    for (let i = 0; i < rows.length; i += BATCH) {
      const batch = rows.slice(i, i + BATCH);
      const values = batch.map((_, j) => {
        const b = j * 5;
        return `($${b+1}, $${b+2}, $${b+3}, $${b+4}, $${b+5})`;
      }).join(', ');
      const params = batch.flatMap(r => [r.product_name, r.brand, r.supermarket, r.price, r.province]);
      await client.query(
        `INSERT INTO reference_prices (product_name, brand, supermarket, price, province) VALUES ${values}`,
        params
      );
      inserted += batch.length;
      process.stdout.write(`\r  Insertados: ${inserted}/${rows.length}`);
    }
    await client.query('COMMIT');
    console.log(`\n✓ ${inserted} registros importados.`);
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

async function main() {
  console.log('=== Seed SEPA — Precios Claros ===');
  let zipPath: string | null = null;
  try {
    console.log('1. Obteniendo URL del ZIP...');
    const zipUrl = await getLatestZipUrl();
    zipPath = path.join(os.tmpdir(), `sepa_${Date.now()}.zip`);
    console.log('2. Descargando ZIP...');
    await download(zipUrl, zipPath);
    console.log('3. Extrayendo CSVs...');
    const allRows: SepaRow[] = [];

    await new Promise<void>((resolve, reject) => {
      const promises: Promise<void>[] = [];
      fs.createReadStream(zipPath!)
        .pipe(unzipper.Parse())
        .on('entry', (entry: any) => {
          const fileName: string = entry.path;
          if (fileName.toLowerCase().endsWith('.csv')) {
            console.log(`   Leyendo: ${fileName}`);
            const p = parseCsvStream(entry).then(rows => {
              if (rows.length > 0) {
                console.log(`   → ${rows.length} filas en ${fileName}`);
                allRows.push(...rows);
              } else {
                console.log(`   → Sin columnas reconocibles en ${fileName}`);
              }
            }).catch(e => { console.warn(`   ⚠ Error en ${fileName}:`, e); });
            promises.push(p);
          } else {
            entry.autodrain();
          }
        })
        .on('close', () => Promise.all(promises).then(() => resolve()))
        .on('error', reject);
    });

    if (allRows.length === 0) throw new Error('No se encontraron filas válidas. Verificar formato SEPA.');

    // I2: Deduplicar: promedio por (nombre, supermercado)
    // Uses null-byte separator to avoid key collisions; stores originals to avoid lowercase reconstruction
    const map = new Map<string, { sum: number; count: number; brand: string; province: string; origName: string; origMarket: string }>();
    for (const r of allRows) {
      const key = `${r.product_name.toLowerCase()}\x00${r.supermarket.toLowerCase()}`;
      const existing = map.get(key);
      if (existing) { existing.sum += r.price; existing.count++; }
      else map.set(key, { sum: r.price, count: 1, brand: r.brand, province: r.province, origName: r.product_name, origMarket: r.supermarket });
    }
    const deduped: SepaRow[] = [];
    for (const val of map.values()) {
      deduped.push({ product_name: val.origName, brand: val.brand, supermarket: val.origMarket,
        price: Math.round((val.sum / val.count) * 100) / 100, province: val.province });
    }
    console.log(`   Registros únicos: ${deduped.length}`);
    console.log('4. Insertando en PostgreSQL...');
    await insertRows(deduped);
    console.log('✓ Seed completado.');
  } catch (err) {
    console.error('✗ Error:', err);
    process.exit(1);
  } finally {
    if (zipPath && fs.existsSync(zipPath)) { fs.unlinkSync(zipPath); }
    await pool.end();
  }
}

main();
