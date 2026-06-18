// sepaImport.ts — Descarga el ZIP diario de SEPA desde datos.produccion.gob.ar,
// parsea los ZIPs internos (uno por comercio) y llena la tabla reference_prices.
// Estructura real: outer.zip → sepa_N_comercio-X.zip → comercio.csv + productos.csv (delimitador |)
// Uso: npm run seed:sepa
import * as https from 'https';
import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import unzipper from 'unzipper';
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

function download(url: string, dest: string, redirects = 0): Promise<void> {
  if (redirects > 5) return Promise.reject(new Error('Too many redirects'));
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    const file = fs.createWriteStream(dest);
    file.on('error', err => { fs.unlink(dest, () => {}); reject(err); });
    const req = proto.get(url, { headers: { 'User-Agent': 'SuperAhorro/1.0' } }, res => {
      if (res.statusCode === 301 || res.statusCode === 302) {
        file.close();
        fs.unlink(dest, () => {});
        if (!res.headers.location) { reject(new Error('Redirect without Location header')); return; }
        download(res.headers.location, dest, redirects + 1).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error(`HTTP ${res.statusCode} al descargar ${url}`));
        return;
      }
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve()));
    });
    req.on('error', err => { fs.unlink(dest, () => {}); reject(err); });
  });
}

async function getLatestZipUrl(): Promise<string> {
  const data = await new Promise<string>((resolve, reject) => {
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

function bufferStream(stream: NodeJS.ReadableStream): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    stream.on('data', (chunk: Buffer) => chunks.push(chunk));
    stream.on('end', () => resolve(Buffer.concat(chunks)));
    stream.on('error', reject);
  });
}

// Parsea un ZIP interno (un comercio): lee comercio.csv para el nombre de bandera,
// luego productos.csv para los precios, haciendo join por id_bandera.
async function processInnerZip(innerBuf: Buffer, zipName: string): Promise<SepaRow[]> {
  const dir = await unzipper.Open.buffer(innerBuf);

  // 1. comercio.csv → Map<id_bandera, bandera_nombre>
  const banderas = new Map<string, string>();
  const comercioFile = dir.files.find(f => f.path === 'comercio.csv');
  if (comercioFile) {
    const text = (await comercioFile.buffer()).toString('utf8').replace(/^﻿/, '');
    const lines = text.split('\n');
    const headers = lines[0].split('|');
    const idBanderaIdx = headers.indexOf('id_bandera');
    const nombreIdx    = headers.indexOf('comercio_bandera_nombre');
    for (let i = 1; i < lines.length; i++) {
      const cols = lines[i].split('|');
      const id     = cols[idBanderaIdx]?.trim();
      const nombre = cols[nombreIdx]?.trim();
      if (id && nombre) banderas.set(id, nombre);
    }
  }

  // 2. productos.csv → SepaRow[]
  const rows: SepaRow[] = [];
  const productosFile = dir.files.find(f => f.path === 'productos.csv');
  if (!productosFile) return rows;

  const text = (await productosFile.buffer()).toString('utf8').replace(/^﻿/, '');
  const lines = text.split('\n');
  if (lines.length < 2) return rows;

  const headers    = lines[0].split('|');
  const idBanderaIdx = headers.indexOf('id_bandera');
  const descIdx      = headers.indexOf('productos_descripcion');
  const marcaIdx     = headers.indexOf('productos_marca');
  const precioIdx    = headers.indexOf('productos_precio_lista');

  if (descIdx < 0 || precioIdx < 0) {
    console.log(`   → Headers no reconocidos en ${path.basename(zipName)}: ${headers.join(', ')}`);
    return rows;
  }

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    const cols   = line.split('|');
    const name   = cols[descIdx]?.trim();
    const idBan  = idBanderaIdx >= 0 ? cols[idBanderaIdx]?.trim() : '';
    const market = banderas.get(idBan) || idBan;
    const brand  = marcaIdx >= 0 ? (cols[marcaIdx]?.trim() || '') : '';
    const price  = parseFloat((cols[precioIdx] ?? '').replace(',', '.').trim());
    if (name && market && !isNaN(price) && price > 0) {
      rows.push({ product_name: name, brand, supermarket: market, price, province: '' });
    }
  }
  return rows;
}

async function insertRows(rows: SepaRow[]): Promise<void> {
  if (rows.length === 0) { console.log('Sin filas para insertar.'); return; }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('DELETE FROM reference_prices');
    const BATCH = 500;
    let inserted = 0;
    for (let i = 0; i < rows.length; i += BATCH) {
      const batch  = rows.slice(i, i + BATCH);
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
    console.log('3. Procesando ZIPs internos...');
    const allRows: SepaRow[] = [];

    // El outer ZIP contiene ZIPs internos (uno por comercio), no CSVs directamente
    await new Promise<void>((resolve, reject) => {
      const promises: Promise<void>[] = [];
      fs.createReadStream(zipPath!)
        .pipe(unzipper.Parse())
        .on('entry', (entry: any) => {
          const fileName: string = entry.path;
          if (fileName.toLowerCase().endsWith('.zip')) {
            const p = bufferStream(entry).then(async buf => {
              try {
                const rows = await processInnerZip(buf, fileName);
                if (rows.length > 0) {
                  process.stdout.write(`\r   ${rows.length} productos de ${path.basename(fileName).substring(0, 40)}...`);
                  // Evitar stack overflow: no usar spread con arrays de cientos de miles de elementos
                  for (const r of rows) allRows.push(r);
                }
              } catch (e) {
                console.warn(`\n   ⚠ Error en ${path.basename(fileName)}:`, e);
              }
            });
            promises.push(p);
          } else {
            entry.autodrain();
          }
        })
        .on('close', () => Promise.all(promises).then(() => resolve()))
        .on('error', reject);
    });

    console.log(`\n   Total filas crudas: ${allRows.length}`);
    if (allRows.length === 0) throw new Error('No se encontraron filas válidas.');

    // Deduplicar: promedio de precio por (nombre, supermercado)
    const map = new Map<string, { sum: number; count: number; brand: string; origName: string; origMarket: string }>();
    for (const r of allRows) {
      const key = `${r.product_name.toLowerCase()}\x00${r.supermarket.toLowerCase()}`;
      const ex = map.get(key);
      if (ex) { ex.sum += r.price; ex.count++; }
      else map.set(key, { sum: r.price, count: 1, brand: r.brand, origName: r.product_name, origMarket: r.supermarket });
    }
    const deduped: SepaRow[] = [];
    for (const val of map.values()) {
      deduped.push({
        product_name: val.origName, brand: val.brand, supermarket: val.origMarket,
        price: Math.round((val.sum / val.count) * 100) / 100, province: ''
      });
    }
    console.log(`   Registros únicos: ${deduped.length}`);
    console.log('4. Insertando en PostgreSQL...');
    await insertRows(deduped);
    console.log('✓ Seed completado.');
  } catch (err) {
    console.error('✗ Error:', err);
    process.exit(1);
  } finally {
    if (zipPath && fs.existsSync(zipPath)) fs.unlinkSync(zipPath);
    await pool.end();
  }
}

main();
