# Etapa Final — SUPER AHORRO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar comparativa de precios con datos reales SEPA, exportación CSV, filtros avanzados en historial y toggle de WorkManager en Settings.

**Architecture:** Backend: nueva tabla `reference_prices` alimentada por seed script que parsea ZIP diario de datos.produccion.gob.ar/sepa-precios. El endpoint `/prices/compare` consulta esa tabla en lugar de datos hardcodeados. Android: filtros en memoria sobre datos de Room; exportación con FileProvider + Intent ACTION_SEND; WorkManager configurable desde ThemeDataStore.

**Tech Stack:** TypeScript/Node.js + pg (backend), Kotlin/Jetpack Compose, Room, DataStore, WorkManager, FileProvider, unzipper, csv-parse.

---

## File Map

| Archivo | Operación | Tarea |
|---------|-----------|-------|
| `backend/schema.sql` | Modify | 1 |
| `backend/package.json` | Modify | 1 |
| `backend/src/seeds/sepaImport.ts` | Create | 2 |
| `backend/src/routes/prices.ts` | Modify | 3 |
| `app/.../data/network/dto/AiDtos.kt` | Modify | 4 |
| `app/.../data/network/ApiService.kt` | Modify | 4 |
| `app/.../ui/screens/prices/PriceComparisonViewModel.kt` | Modify | 4 |
| `app/.../ui/screens/prices/PriceComparisonScreen.kt` | Modify | 4 |
| `app/src/main/res/xml/file_paths.xml` | Create | 5 |
| `app/src/main/AndroidManifest.xml` | Modify | 5 |
| `app/.../data/repository/ProductRepository.kt` | Modify | 5 |
| `app/.../ui/screens/history/HistoryViewModel.kt` | Modify | 5, 6 |
| `app/.../ui/screens/history/HistoryScreen.kt` | Modify | 5, 6 |
| `app/.../data/local/ThemeDataStore.kt` | Modify | 7 |
| `app/.../ui/screens/settings/SettingsViewModel.kt` | Modify | 7 |
| `app/.../SuperAhorroApp.kt` | Modify | 7 |
| `app/.../ui/screens/settings/SettingsScreen.kt` | Modify | 7 |

---

## Task 1: Backend — Tabla reference_prices + dependencias

**Files:**
- Modify: `backend/schema.sql`
- Modify: `backend/package.json`

- [ ] **Paso 1: Agregar tabla al schema.sql**

Agregar al final de `backend/schema.sql`:

```sql
-- Precios de referencia cargados desde SEPA (datos.produccion.gob.ar)
CREATE TABLE IF NOT EXISTS reference_prices (
  id           SERIAL PRIMARY KEY,
  product_name TEXT NOT NULL,
  brand        TEXT DEFAULT '',
  supermarket  TEXT NOT NULL,
  price        NUMERIC(10,2) NOT NULL,
  province     TEXT DEFAULT '',
  updated_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ref_prices_name ON reference_prices (LOWER(product_name));
```

- [ ] **Paso 2: Ejecutar en Railway**

En la consola PostgreSQL de Railway (Settings → Connect → psql):
```sql
CREATE TABLE IF NOT EXISTS reference_prices (
  id           SERIAL PRIMARY KEY,
  product_name TEXT NOT NULL,
  brand        TEXT DEFAULT '',
  supermarket  TEXT NOT NULL,
  price        NUMERIC(10,2) NOT NULL,
  province     TEXT DEFAULT '',
  updated_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ref_prices_name ON reference_prices (LOWER(product_name));
```
Expected: `CREATE TABLE` + `CREATE INDEX`

- [ ] **Paso 3: Instalar dependencias**

```bash
cd backend
npm install unzipper csv-parse
npm install --save-dev @types/unzipper
```
Expected: `added N packages` sin errores.

- [ ] **Paso 4: Agregar script en package.json**

En `backend/package.json`, en `"scripts"`, agregar:
```json
"seed:sepa": "ts-node src/seeds/sepaImport.ts"
```

- [ ] **Commit**

```bash
git add backend/schema.sql backend/package.json backend/package-lock.json
git commit -m "feat(backend): add reference_prices table and seed dependencies"
```

---

## Task 2: Backend — Seed script sepaImport.ts

**Files:**
- Create: `backend/src/seeds/sepaImport.ts`

- [ ] **Paso 1: Crear directorio**

```bash
mkdir backend/src/seeds
```

- [ ] **Paso 2: Crear el archivo**

Crear `backend/src/seeds/sepaImport.ts` con el siguiente contenido completo:

```typescript
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

function download(url: string, dest: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    const file = fs.createWriteStream(dest);
    proto.get(url, { headers: { 'User-Agent': 'SuperAhorro/1.0' } }, res => {
      if (res.statusCode === 301 || res.statusCode === 302) {
        file.close();
        fs.unlink(dest, () => {});
        download(res.headers.location!, dest).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
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
    https.get(
      'https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios',
      { headers: { 'User-Agent': 'SuperAhorro/1.0' } },
      res => {
        let body = '';
        res.on('data', c => body += c);
        res.on('end', () => resolve(body));
        res.on('error', reject);
      }
    );
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
        if (name && market && !isNaN(price) && price > 0 && rows.length < 10000) {
          rows.push({ product_name: name, brand, supermarket: market, price, province });
        }
      }
    });
    parser.on('error', reject);
    parser.on('end', () => resolve(rows));
    stream.pipe(parser);
  });
}

async function insertRows(rows: SepaRow[]): Promise<void> {
  if (rows.length === 0) { console.log('Sin filas para insertar.'); return; }
  await pool.query('DELETE FROM reference_prices');
  const BATCH = 500;
  let inserted = 0;
  for (let i = 0; i < rows.length; i += BATCH) {
    const batch = rows.slice(i, i + BATCH);
    const values = batch.map((_, j) => {
      const b = j * 5;
      return `($${b+1}, $${b+2}, $${b+3}, $${b+4}, $${b+5})`;
    }).join(', ');
    const params = batch.flatMap(r => [r.product_name, r.brand, r.supermarket, r.price, r.province]);
    await pool.query(
      `INSERT INTO reference_prices (product_name, brand, supermarket, price, province) VALUES ${values}`,
      params
    );
    inserted += batch.length;
    process.stdout.write(`\r  Insertados: ${inserted}/${rows.length}`);
  }
  console.log(`\n✓ ${inserted} registros importados.`);
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

    // Deduplicar: promedio por (nombre, supermercado)
    const map = new Map<string, { sum: number; count: number; brand: string; province: string }>();
    for (const r of allRows) {
      const key = `${r.product_name.toLowerCase()}|${r.supermarket.toLowerCase()}`;
      const existing = map.get(key);
      if (existing) { existing.sum += r.price; existing.count++; }
      else map.set(key, { sum: r.price, count: 1, brand: r.brand, province: r.province });
    }
    const deduped: SepaRow[] = [];
    for (const [key, val] of map.entries()) {
      const [name, market] = key.split('|');
      deduped.push({ product_name: name, brand: val.brand, supermarket: market,
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
```

- [ ] **Paso 3: Ejecutar el seed** (requiere `DATABASE_URL` en `.env`)

```bash
cd backend
npm run seed:sepa
```

Expected:
```
=== Seed SEPA — Precios Claros ===
1. Obteniendo URL del ZIP...
  Recurso: Miércoles
2. Descargando ZIP...
3. Extrayendo CSVs...
   Leyendo: sepa_precios_20260617.csv
   → 8000 filas en sepa_precios_20260617.csv
   Registros únicos: 4230
4. Insertando en PostgreSQL...
  Insertados: 4230/4230
✓ Seed completado.
```

Si imprime "Sin columnas reconocibles", el ZIP de SEPA tiene headers distintos. Revisar qué headers tiene descargando el ZIP y ajustando la función `detectColumns()` con los nombres reales.

- [ ] **Commit**

```bash
git add backend/src/seeds/sepaImport.ts
git commit -m "feat(backend): SEPA seed script for reference_prices"
```

---

## Task 3: Backend — Actualizar prices.ts para usar reference_prices

**Files:**
- Modify: `backend/src/routes/prices.ts`

- [ ] **Paso 1: Reemplazar prices.ts completo**

Reemplazar todo el contenido de `backend/src/routes/prices.ts` con:

```typescript
// prices.ts — Comparativa de precios usando datos reales de SEPA (reference_prices)
// combinada con precios del historial del usuario.
// GET /prices/compare?query=<nombre_opcional>
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

function normalize(name: string): string {
  return name.toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9\s]/g, '').trim();
}

router.get('/compare', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const query = ((req.query.query as string) ?? '').trim();

    // 1. Precios del usuario (historial)
    const userResult = await pool.query(
      `SELECT DISTINCT ON (pr.name, p.supermarket)
              pr.name AS product_name, p.supermarket,
              pr.price AS user_price, p.purchase_date
       FROM products pr
       JOIN purchases p ON p.id = pr.purchase_id
       WHERE p.user_id = $1
         AND ($2 = '' OR pr.name ILIKE '%' || $2 || '%')
       ORDER BY pr.name, p.supermarket, p.purchase_date DESC`,
      [req.userId, query]
    );

    // 2. Precios de referencia SEPA
    const refResult = await pool.query(
      `SELECT product_name, supermarket, price, brand, updated_at
       FROM reference_prices
       WHERE ($1 = '' OR product_name ILIKE '%' || $1 || '%')
       ORDER BY product_name LIMIT 200`,
      [query]
    );

    // 3. Metadata
    const metaResult = await pool.query(`SELECT MAX(updated_at) AS lu FROM reference_prices`);
    const lastUpdated: string | null = metaResult.rows[0]?.lu ?? null;
    const hasRefData = refResult.rows.length > 0;

    // 4. Agrupar por nombre normalizado
    const userMap: Record<string, Record<string, number>> = {};
    for (const row of userResult.rows) {
      const norm = normalize(row.product_name);
      if (!userMap[norm]) userMap[norm] = {};
      userMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.user_price);
    }

    const refMap: Record<string, Record<string, number>> = {};
    for (const row of refResult.rows) {
      const norm = normalize(row.product_name);
      if (!refMap[norm]) refMap[norm] = {};
      refMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.price);
    }

    // 5. Combinar y construir comparativas
    const allNorms = new Set([...Object.keys(userMap), ...Object.keys(refMap)]);

    const comparisons = Array.from(allNorms).map(norm => {
      const userRow = userResult.rows.find(r => normalize(r.product_name) === norm);
      const refRow  = refResult.rows.find(r => normalize(r.product_name) === norm);
      const productName = userRow?.product_name ?? refRow?.product_name ?? norm;

      const allPrices: Record<string, { price: number; isUserData: boolean }> = {};
      for (const [s, p] of Object.entries(userMap[norm] ?? {}))
        allPrices[s] = { price: p, isUserData: true };
      for (const [s, p] of Object.entries(refMap[norm] ?? {}))
        if (!allPrices[s]) allPrices[s] = { price: p, isUserData: false };

      const priceList = Object.entries(allPrices)
        .map(([supermarket, d]) => ({ supermarket, price: d.price, isUserData: d.isUserData }))
        .sort((a, b) => a.price - b.price);

      if (priceList.length < 2) return null;

      const cheapest = priceList[0];
      const priciest = priceList[priceList.length - 1];
      const savings  = priciest.price - cheapest.price;

      return {
        productName,
        prices: priceList,
        cheapestAt:    cheapest.supermarket,
        cheapestPrice: cheapest.price,
        maxSavings:    savings,
        savingsPct:    Math.round((savings / priciest.price) * 100),
      };
    }).filter(Boolean);

    comparisons.sort((a: any, b: any) => b.maxSavings - a.maxSavings);

    res.json({
      comparisons,
      source:      hasRefData ? 'SEPA - preciosclaros.gob.ar' : 'historial_usuario',
      lastUpdated: lastUpdated ?? null,
      isEmpty:     !hasRefData && userResult.rows.length === 0,
    });
  } catch (err: any) {
    console.error('Error en /prices/compare:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
```

- [ ] **Paso 2: Verificar build**

```bash
cd backend && npm run build
```
Expected: `0 errors` en `dist/`.

- [ ] **Commit**

```bash
git add backend/src/routes/prices.ts
git commit -m "feat(backend): prices/compare queries reference_prices DB + SEPA attribution"
```

---

## Task 4: Android — DTOs, ApiService, PriceComparisonViewModel y Screen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonViewModel.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonScreen.kt`

- [ ] **Paso 1: Actualizar PriceComparisonResponse en AiDtos.kt**

Reemplazar la data class `PriceComparisonResponse` (últimas 3 líneas del archivo) con:

```kotlin
data class PriceComparisonResponse(
    @SerializedName("comparisons")  val comparisons: List<PriceComparisonItemDto>,
    @SerializedName("source")       val source: String = "",
    @SerializedName("lastUpdated")  val lastUpdated: String? = null,
    @SerializedName("isEmpty")      val isEmpty: Boolean = false
)
```

- [ ] **Paso 2: Agregar query param en ApiService.kt**

Reemplazar la función `getPriceComparisons` con:

```kotlin
    @GET("prices/compare")
    suspend fun getPriceComparisons(
        @Header("Authorization") token: String,
        @Query("query") query: String? = null
    ): Response<PriceComparisonResponse>
```

- [ ] **Paso 3: Reemplazar PriceComparisonViewModel.kt**

```kotlin
package com.undef.superahorroturina.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceComparisonUiState(
    val isLoading: Boolean = true,
    val comparisons: List<PriceComparisonItemDto> = emptyList(),
    val source: String = "",
    val lastUpdated: String? = null,
    val isEmpty: Boolean = false,
    val error: String = ""
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PriceComparisonViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceComparisonUiState())
    val uiState: StateFlow<PriceComparisonUiState> = _uiState.asStateFlow()

    val searchQuery = MutableStateFlow("")

    init {
        loadComparisons()
        viewModelScope.launch {
            searchQuery.debounce(500).distinctUntilChanged()
                .collect { query -> loadComparisons(query) }
        }
    }

    fun loadComparisons(query: String = searchQuery.value) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val token    = session.bearerToken.first()
                val response = api.getPriceComparisons(token, query.ifBlank { null })
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _uiState.value = PriceComparisonUiState(
                        isLoading   = false,
                        comparisons = body.comparisons,
                        source      = body.source,
                        lastUpdated = body.lastUpdated,
                        isEmpty     = body.isEmpty
                    )
                } else {
                    _uiState.value = PriceComparisonUiState(
                        isLoading = false, error = "Error ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PriceComparisonUiState(
                    isLoading = false, error = e.message ?: "Error de conexión"
                )
            }
        }
    }
}
```

- [ ] **Paso 4: Agregar buscador y footer en PriceComparisonScreen.kt**

En `PriceComparisonScreen`, agregar las siguientes colecciones de estado al inicio del composable:

```kotlin
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
```

Agregar como primer `item {}` en el `LazyColumn` (antes del header de ahorro):

```kotlin
                        item {
                            OutlinedTextField(
                                value         = searchQuery,
                                onValueChange = { viewModel.searchQuery.value = it },
                                label         = { Text("Buscar producto") },
                                leadingIcon   = { Icon(Icons.Default.Search, null) },
                                trailingIcon  = {
                                    if (searchQuery.isNotBlank())
                                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                },
                                modifier   = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape      = MaterialTheme.shapes.large
                            )
                        }
```

Agregar como último `item {}` antes del `Spacer` final:

```kotlin
                        if (uiState.source.isNotBlank()) {
                            item {
                                Text(
                                    text  = "Fuente: ${uiState.source}" +
                                            if (uiState.lastUpdated != null)
                                                " · Act: ${uiState.lastUpdated!!.take(10)}"
                                            else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
```

Reemplazar el bloque `uiState.comparisons.isEmpty() && !uiState.isLoading` con:

```kotlin
                uiState.isEmpty || (uiState.comparisons.isEmpty() && !uiState.isLoading) -> {
                    EmptyState(
                        icon    = Icons.Default.CompareArrows,
                        message = if (uiState.source.isEmpty())
                            "Ejecutá 'npm run seed:sepa' en el backend o registrá compras en varios supermercados"
                        else
                            "No se encontraron productos con ese nombre",
                        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp)
                    )
                }
```

- [ ] **Paso 5: Compilar**

Build → Make Project. Expected: 0 errores.

- [ ] **Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt \
        app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonViewModel.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonScreen.kt
git commit -m "feat(android): price comparison with search + SEPA source footer"
```

---

## Task 5: Android — Exportación CSV (FileProvider + HistoryViewModel + HistoryScreen)

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/.../data/repository/ProductRepository.kt`
- Modify: `app/.../ui/screens/history/HistoryViewModel.kt`
- Modify: `app/.../ui/screens/history/HistoryScreen.kt`

- [ ] **Paso 1: Crear file_paths.xml**

Crear `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="exports" path="exports/" />
</paths>
```

- [ ] **Paso 2: Agregar FileProvider en AndroidManifest.xml**

Dentro de `<application>` antes de `</application>`, agregar:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Paso 3: Agregar getLocalProductsForPurchase en ProductRepository.kt**

Agregar al final de la clase `ProductRepository`, antes del `}` de cierre:

```kotlin
    suspend fun getLocalProductsForPurchase(purchaseId: Int): List<Product> =
        productDao.getByPurchaseId(purchaseId).first().map { e ->
            Product(e.id, e.code, e.name, e.description, e.price, e.quantity)
        }
```

- [ ] **Paso 4: Agregar ProductRepository + exportToCsv en HistoryViewModel.kt**

Agregar `ProductRepository` al constructor de `HistoryViewModel`:

```kotlin
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository
) : ViewModel() {
```

Agregar estos imports (junto a los existentes):

```kotlin
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.undef.superahorroturina.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
```

Agregar el método `exportToCsv` al final de la clase, antes del `}` de cierre:

```kotlin
    fun exportToCsv(context: Context, onReady: (Uri) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val purchases = _uiState.value.purchases
                val sb = StringBuilder()
                sb.appendLine("\"Fecha\",\"Hora\",\"Supermercado\",\"Total\"," +
                              "\"Cod Producto\",\"Nombre\",\"Descripcion\",\"Precio\",\"Cantidad\"")

                for (purchase in purchases) {
                    val products = productRepository.getLocalProductsForPurchase(purchase.id)
                    if (products.isEmpty()) {
                        sb.appendLine("\"${purchase.date}\",\"${purchase.time}\"," +
                                      "\"${purchase.supermarket}\",\"${purchase.total}\"," +
                                      "\"\",\"\",\"\",\"\",\"\"")
                    } else {
                        for (p in products) {
                            sb.appendLine("\"${purchase.date}\",\"${purchase.time}\"," +
                                          "\"${purchase.supermarket}\",\"${purchase.total}\"," +
                                          "\"${p.code}\",\"${p.name.replace("\"","\"\"")}\",\"${p.description.replace("\"","\"\"")}\",\"${p.price}\",\"${p.quantity}\"")
                        }
                    }
                }

                val dir  = File(context.filesDir, "exports").also { it.mkdirs() }
                val file = File(dir, "compras_${System.currentTimeMillis()}.csv")
                file.writeText(sb.toString(), Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) { onReady(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Error al exportar") }
            }
        }
    }
```

- [ ] **Paso 5: Agregar botón exportar en HistoryScreen.kt**

Agregar al inicio del composable `HistoryScreen`, antes del `Scaffold`:

```kotlin
    val context = LocalContext.current
```

Agregar import:

```kotlin
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
```

Reemplazar el `AppTopBar` existente con `TopAppBar` que incluye la acción de exportar:

```kotlin
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.exportToCsv(
                            context = context,
                            onReady = { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Historial de compras — Super Ahorro")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Exportar compras"))
                            },
                            onError = { /* no-op en demo */ }
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
```

Agregar imports necesarios:

```kotlin
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
```

- [ ] **Paso 6: Compilar**

Build → Make Project. Expected: 0 errores.

- [ ] **Commit**

```bash
git add app/src/main/res/xml/file_paths.xml \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/undef/superahorroturina/data/repository/ProductRepository.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryViewModel.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryScreen.kt
git commit -m "feat(android): CSV export via FileProvider + Intent ACTION_SEND from HistoryScreen"
```

---

## Task 6: Android — Filtros avanzados en HistoryViewModel + HistoryScreen

**Files:**
- Modify: `app/.../ui/screens/history/HistoryViewModel.kt`
- Modify: `app/.../ui/screens/history/HistoryScreen.kt`

- [ ] **Paso 1: Agregar PurchaseFilters y actualizar HistoryUiState**

Agregar `PurchaseFilters` antes de `HistoryUiState` en `HistoryViewModel.kt`:

```kotlin
data class PurchaseFilters(
    val supermarket: String = "",
    val dateFrom: java.time.LocalDate? = null,
    val dateTo: java.time.LocalDate? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    val activeCount: Int get() = listOf(
        supermarket.isNotBlank(), dateFrom != null, dateTo != null,
        minAmount != null, maxAmount != null
    ).count { it }
}
```

Actualizar `HistoryUiState` agregando los dos campos nuevos:

```kotlin
data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val purchases: List<Purchase> = emptyList(),
    val filteredPurchases: List<Purchase> = emptyList(),
    val error: String = "",
    val filters: PurchaseFilters = PurchaseFilters(),
    val showFilters: Boolean = false
)
```

- [ ] **Paso 2: Agregar StateFlow de filtros y actualizar el bloque init**

Agregar debajo de `val selectedFilter` en `HistoryViewModel`:

```kotlin
    private val _filters = MutableStateFlow(PurchaseFilters())
```

Reemplazar el bloque `combine(...)` dentro de `init` (el que produce `filteredPurchases`) con:

```kotlin
        combine(
            _uiState.map { it.purchases },
            searchQuery.debounce(300),
            selectedFilter,
            _filters
        ) { purchases, query, chip, adv ->
            purchases.filter { p ->
                val matchSearch  = query.isBlank() || p.supermarket.contains(query, ignoreCase = true)
                val matchChip    = chip == "Todos" || p.supermarket == chip
                val matchSuper   = adv.supermarket.isBlank() || p.supermarket.contains(adv.supermarket, ignoreCase = true)
                val matchFrom    = adv.dateFrom == null || !p.date.isBefore(adv.dateFrom)
                val matchTo      = adv.dateTo   == null || !p.date.isAfter(adv.dateTo)
                val matchMin     = adv.minAmount == null || p.total >= adv.minAmount
                val matchMax     = adv.maxAmount == null || p.total <= adv.maxAmount
                matchSearch && matchChip && matchSuper && matchFrom && matchTo && matchMin && matchMax
            }
        }.onEach { filtered ->
            _uiState.value = _uiState.value.copy(filteredPurchases = filtered, filters = _filters.value)
        }.launchIn(viewModelScope)
```

- [ ] **Paso 3: Agregar métodos de control de filtros**

Agregar al final de la clase `HistoryViewModel`:

```kotlin
    fun updateFilters(f: PurchaseFilters) { _filters.value = f }
    fun clearFilters() { _filters.value = PurchaseFilters() }
    fun toggleShowFilters() {
        _uiState.value = _uiState.value.copy(showFilters = !_uiState.value.showFilters)
    }
```

- [ ] **Paso 4: Agregar FilterPanel composable al final de HistoryScreen.kt**

Agregar los imports al inicio de `HistoryScreen.kt`:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.ZoneId
```

Agregar al final del archivo, fuera de `HistoryScreen`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    filters: PurchaseFilters,
    onFiltersChange: (PurchaseFilters) -> Unit,
    onClear: () -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Filtros avanzados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (filters.activeCount > 0)
                    TextButton(onClick = onClear) { Text("Limpiar") }
            }

            OutlinedTextField(
                value         = filters.supermarket,
                onValueChange = { onFiltersChange(filters.copy(supermarket = it)) },
                label         = { Text("Supermercado") },
                leadingIcon   = { Icon(Icons.Default.Store, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = MaterialTheme.shapes.medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val disabledColors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor      = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = filters.dateFrom?.toString() ?: "", onValueChange = {},
                    label = { Text("Desde") }, leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                    readOnly = true, enabled = false, colors = disabledColors,
                    modifier = Modifier.weight(1f).clickable { showFromPicker = true },
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = filters.dateTo?.toString() ?: "", onValueChange = {},
                    label = { Text("Hasta") }, leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                    readOnly = true, enabled = false, colors = disabledColors,
                    modifier = Modifier.weight(1f).clickable { showToPicker = true },
                    shape = MaterialTheme.shapes.medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.minAmount?.toInt()?.toString() ?: "",
                    onValueChange = { onFiltersChange(filters.copy(minAmount = it.toDoubleOrNull())) },
                    label = { Text("Monto mín.") }, leadingIcon = { Icon(Icons.Default.MonetizationOn, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = filters.maxAmount?.toInt()?.toString() ?: "",
                    onValueChange = { onFiltersChange(filters.copy(maxAmount = it.toDoubleOrNull())) },
                    label = { Text("Monto máx.") }, leadingIcon = { Icon(Icons.Default.MonetizationOn, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium
                )
            }
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filters.dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onFiltersChange(filters.copy(dateFrom = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filters.dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onFiltersChange(filters.copy(dateTo = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }
}
```

- [ ] **Paso 5: Integrar FilterPanel en el LazyColumn de HistoryScreen**

Agregar dos items después del `LazyRow` de chips (después del item que contiene `filterOptions`):

```kotlin
                            // Botón toggle filtros avanzados
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { viewModel.toggleShowFilters() }) {
                                        Icon(
                                            if (uiState.showFilters) Icons.Default.FilterListOff
                                            else Icons.Default.FilterList,
                                            null, modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (uiState.filters.activeCount > 0)
                                                "Filtros (${uiState.filters.activeCount})"
                                            else "Filtros avanzados"
                                        )
                                    }
                                    if (uiState.filters.activeCount > 0) {
                                        TextButton(onClick = { viewModel.clearFilters() }) {
                                            Text("Limpiar", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }

                            // Panel expandible
                            item {
                                AnimatedVisibility(
                                    visible = uiState.showFilters,
                                    enter   = expandVertically(),
                                    exit    = shrinkVertically()
                                ) {
                                    FilterPanel(
                                        filters         = uiState.filters,
                                        onFiltersChange = { viewModel.updateFilters(it) },
                                        onClear         = { viewModel.clearFilters() }
                                    )
                                }
                            }
```

Si `Icons.Default.FilterListOff` no compila (disponible desde Material Icons Extended), reemplazar con `Icons.Default.FilterList` y cambiar el texto a "Ocultar filtros".

- [ ] **Paso 6: Compilar**

Build → Make Project. Expected: 0 errores.

- [ ] **Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryViewModel.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/history/HistoryScreen.kt
git commit -m "feat(android): advanced filters in HistoryScreen (date range, amount, supermarket)"
```

---

## Task 7: Android — WorkManager toggle en Settings

**Files:**
- Modify: `app/.../data/local/ThemeDataStore.kt`
- Modify: `app/.../ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/.../SuperAhorroApp.kt`
- Modify: `app/.../ui/screens/settings/SettingsScreen.kt`

- [ ] **Paso 1: Agregar clave en ThemeDataStore.kt**

Agregar dentro de la clase, después de la declaración de `MONTHLY_LIMIT`:

```kotlin
    private val PRICE_ALERTS = booleanPreferencesKey("price_alerts_enabled")

    val priceAlertsEnabled: Flow<Boolean> = context.themeDataStore.data
        .map { prefs -> prefs[PRICE_ALERTS] ?: true }

    suspend fun setPriceAlertsEnabled(enabled: Boolean) {
        context.themeDataStore.edit { it[PRICE_ALERTS] = enabled }
    }
```

- [ ] **Paso 2: Reemplazar SettingsViewModel.kt completo**

```kotlin
package com.undef.superahorroturina.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.state.SettingsUiState
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { themeDataStore.isDarkMode.collect { _uiState.value = _uiState.value.copy(darkMode = it) } }
        viewModelScope.launch { themeDataStore.monthlyLimit.collect { _uiState.value = _uiState.value.copy(monthlyLimit = it) } }
        viewModelScope.launch { themeDataStore.priceAlertsEnabled.collect { _uiState.value = _uiState.value.copy(priceAlerts = it) } }
    }

    fun onDarkModeChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = enabled)
        viewModelScope.launch { themeDataStore.setDarkMode(enabled) }
    }

    fun onNotificationsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifications = enabled)
    }

    fun onPriceAlertsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(priceAlerts = enabled)
        viewModelScope.launch {
            themeDataStore.setPriceAlertsEnabled(enabled)
            if (enabled) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PriceAlertWorker.WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
                        .addTag(PriceAlertWorker.WORK_TAG)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(PriceAlertWorker.WORK_TAG)
            }
        }
    }

    fun onLanguageChange(language: String) { _uiState.value = _uiState.value.copy(language = language, languageExpanded = false) }
    fun onLanguageExpandedChange(expanded: Boolean) { _uiState.value = _uiState.value.copy(languageExpanded = expanded) }
    fun onSortChange(sort: String) { _uiState.value = _uiState.value.copy(selectedSort = sort) }
    fun onMonthlyLimitChange(limit: Float) {
        _uiState.value = _uiState.value.copy(monthlyLimit = limit)
        viewModelScope.launch { themeDataStore.setMonthlyLimit(limit) }
    }
}
```

- [ ] **Paso 3: Reemplazar SuperAhorroApp.kt completo**

```kotlin
package com.undef.superahorroturina

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SuperAhorroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var themeDataStore: ThemeDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val enabled = themeDataStore.priceAlertsEnabled.first()
            if (enabled) {
                WorkManager.getInstance(this@SuperAhorroApp).enqueueUniquePeriodicWork(
                    PriceAlertWorker.WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
                        .addTag(PriceAlertWorker.WORK_TAG)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            }
        }
    }
}
```

- [ ] **Paso 4: Actualizar SettingsScreen.kt — Checkbox → Switch**

Reemplazar el bloque `Row` con `Checkbox` para price alerts (el bloque que empieza con `Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)` dentro de la `SettingsCard` de notificaciones) con:

```kotlin
                    SettingsToggleItem(
                        icon            = Icons.Default.NotificationsActive,
                        title           = stringResource(R.string.settings_price_alerts),
                        subtitle        = stringResource(R.string.settings_price_alerts_desc),
                        checked         = uiState.priceAlerts,
                        onCheckedChange = { viewModel.onPriceAlertsChange(it) }
                    )
```

- [ ] **Paso 5: Compilar**

Build → Make Project. Expected: 0 errores.

- [ ] **Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/ThemeDataStore.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsViewModel.kt \
        app/src/main/java/com/undef/superahorroturina/SuperAhorroApp.kt \
        app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt
git commit -m "feat(android): WorkManager price alerts toggle in Settings, persisted via DataStore"
```

---

## Self-Review

**Cobertura del spec:**
- ✅ Comparativa precios SEPA (Tasks 1-4): tabla + seed + endpoint + Android
- ✅ Exportación CSV (Task 5): FileProvider + ProductRepository + HistoryViewModel + HistoryScreen
- ✅ Filtros avanzados (Task 6): PurchaseFilters + FilterPanel + AnimatedVisibility
- ✅ WorkManager toggle (Task 7): ThemeDataStore + SettingsViewModel + SuperAhorroApp + SettingsScreen

**Consistencia de tipos:**
- `PurchaseFilters.activeCount` usado en ViewModel y Screen ✓
- `_filters: MutableStateFlow<PurchaseFilters>` incluido en `combine(...)` con 4 args ✓
- `themeDataStore.priceAlertsEnabled` / `setPriceAlertsEnabled` definidos en Task 7 Paso 1 ✓
- `productRepository.getLocalProductsForPurchase` definido en Task 5 Paso 3, usado en Paso 4 ✓
- `uiState.showFilters` y `uiState.filters` agregados a `HistoryUiState` en Task 6 Paso 1 ✓

**Notas de implementación:**
- Si `Icons.Default.FilterListOff` no está disponible, usar `Icons.Default.FilterList` con label "Ocultar filtros".
- El seed SEPA puede fallar si los headers del CSV tienen nombres distintos; en ese caso ajustar `detectColumns()` con los headers reales del ZIP descargado.
- El `combine` de `HistoryViewModel` pasa de 3 a 4 flows; la lambda toma 4 parámetros — verificar que no haya error de lambda de Kotlin en la firma.
