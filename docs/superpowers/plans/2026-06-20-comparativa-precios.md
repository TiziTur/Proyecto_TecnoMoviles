# Comparativa de precios — Ticket→Seed matching y pulido de listado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vincular los productos detectados al escanear un ticket con el catálogo de precios de referencia (seed/SEPA), permitiendo que el usuario confirme/corrija ese vínculo manualmente, y pulir el listado de comparativa de precios (nombres legibles + iconos por categoría). La pantalla de "comparar esta compra contra otros supermercados" ya existe y no requiere cambios de UI (ver nota en la spec); solo se beneficia indirectamente de un matching más preciso en el backend.

**Architecture:** Backend: nueva columna `products.seed_product_name`, una librería compartida de matching por tokens (`backend/src/lib/seedMatching.ts`) usada tanto por el endpoint nuevo `/products/match-seed` como por el endpoint existente `purchases/:id/compare` (que pasa a preferir el vínculo exacto cuando existe). Android: el flujo de escaneo de ticket pasa por un nuevo paso de matching antes de mostrar la confirmación, que ahora es una pantalla completa (`TicketConfirmScreen`) en vez de un `AlertDialog`, con búsqueda manual de vínculo. El vínculo viaja end-to-end hasta Room/Postgres junto con el resto del producto.

**Tech Stack:** Backend Node.js/Express/TypeScript + PostgreSQL (sin framework de tests — se verifica con `curl` manual, siguiendo la convención existente del proyecto). Android: Kotlin + Jetpack Compose + Hilt + Room + Retrofit.

---

## Task 1: Columna `seed_product_name` en `products`

**Files:**
- Modify: `backend/schema.sql`

- [ ] **Step 1: Agregar la columna de forma idempotente**

En `backend/schema.sql`, después del bloque de la tabla `products` (después de la línea `created_at  TIMESTAMP     DEFAULT NOW()` y antes de los índices), agregar:

```sql
-- Vínculo opcional al catálogo de precios de referencia (reference_prices.product_name exacto).
-- NULL = no vinculado (no comparable contra otros supermercados todavía).
ALTER TABLE products ADD COLUMN IF NOT EXISTS seed_product_name TEXT;
```

- [ ] **Step 2: Aplicar el cambio en la base de desarrollo/Railway**

Run (con `DATABASE_URL` apuntando a la base correspondiente):
```bash
cd backend && psql "$DATABASE_URL" -f schema.sql
```
Expected: sin errores (los `CREATE TABLE IF NOT EXISTS` y el nuevo `ADD COLUMN IF NOT EXISTS` son no-ops si ya existen).

- [ ] **Step 3: Commit**

```bash
git add backend/schema.sql
git commit -m "feat(db): agregar columna seed_product_name a products"
```

---

## Task 2: Librería compartida de matching por tokens

**Files:**
- Create: `backend/src/lib/seedMatching.ts`
- Modify: `backend/src/routes/purchaseComparison.ts`

- [ ] **Step 1: Crear `backend/src/lib/seedMatching.ts`**

Extrae `tokenize`/`scoreMatch` (hoy definidas localmente en `purchaseComparison.ts`) a un módulo compartido, y agrega `matchNameToSeed` para matching de un solo nombre con umbral de confianza:

```typescript
// seedMatching.ts — utilidades de matching por tokens entre nombres de ticket y reference_prices.
// Compartido por purchaseComparison.ts (comparar una compra completa) y productMatch.ts
// (vincular un producto escaneado al catálogo apenas se confirma el ticket).
import { Pool } from 'pg';

export function tokenize(name: string): string[] {
  return name
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(t => t.length >= 3);
}

export function scoreMatch(ticketTokens: string[], sepaName: string): number {
  const sepaLow = sepaName.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  return ticketTokens.filter(t => sepaLow.includes(t)).length;
}

export interface SeedMatchResult {
  seedMatch: string | null;
  candidates: string[];
}

// Matchea un nombre de ticket contra reference_prices.product_name.
// Confianza alta (cubre >=70% de los tokens y al menos 2 en común) => auto-match (seedMatch no nulo).
// Si no, se devuelven hasta 5 candidatos para que el usuario elija manualmente.
export async function matchNameToSeed(pool: Pool, ticketName: string): Promise<SeedMatchResult> {
  const tokens = tokenize(ticketName);
  if (tokens.length === 0) return { seedMatch: null, candidates: [] };

  const topTokens = tokens.slice(0, 3);
  const conditions = topTokens.map((_, i) => `LOWER(product_name) LIKE $${i + 1}`).join(' AND ');
  const params = topTokens.map(t => `%${t}%`);

  const res = await pool.query(
    `SELECT DISTINCT product_name FROM reference_prices WHERE ${conditions} LIMIT 50`,
    params
  );

  let candidateRows = res.rows as Array<{ product_name: string }>;
  if (candidateRows.length === 0) {
    const orRes = await pool.query(
      `SELECT DISTINCT product_name FROM reference_prices WHERE LOWER(product_name) LIKE $1 LIMIT 30`,
      [`%${tokens[0]}%`]
    );
    candidateRows = orRes.rows;
  }

  const scored = candidateRows
    .map(c => ({ name: c.product_name, score: scoreMatch(tokens, c.product_name) }))
    .filter(c => c.score > 0)
    .sort((a, b) => b.score - a.score);

  if (scored.length === 0) return { seedMatch: null, candidates: [] };

  const best = scored[0];
  const isConfident = best.score >= 2 && best.score / tokens.length >= 0.7;

  return {
    seedMatch: isConfident ? best.name : null,
    candidates: scored.slice(0, 5).map(c => c.name)
  };
}
```

- [ ] **Step 2: Refactorizar `purchaseComparison.ts` para usar la librería compartida**

Reemplazar el contenido completo de `backend/src/routes/purchaseComparison.ts` por:

```typescript
// purchaseComparison.ts — Compara una compra completa contra precios SEPA.
// Para productos vinculados a la seed (seed_product_name), hace un match exacto.
// Para los no vinculados, cae al matching difuso por tokens (tokenize/scoreMatch).
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { tokenize, scoreMatch } from '../lib/seedMatching';

const router = Router({ mergeParams: true });
router.use(authMiddleware);

interface SepaMatch { product_name: string; supermarket: string; price: number }

router.get('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  if (isNaN(purchaseId)) { res.status(400).json({ error: 'purchaseId inválido' }); return; }

  try {
    // 1. Verificar ownership
    const pRes = await pool.query(
      'SELECT id, supermarket, total FROM purchases WHERE id = $1 AND user_id = $2',
      [purchaseId, req.userId]
    );
    if (pRes.rows.length === 0) { res.status(404).json({ error: 'Compra no encontrada' }); return; }
    const purchase = pRes.rows[0];

    // 2. Obtener productos de la compra (incluyendo el vínculo a la seed si existe)
    const prodRes = await pool.query(
      'SELECT id, name, price, quantity, category, seed_product_name FROM products WHERE purchase_id = $1 ORDER BY id',
      [purchaseId]
    );
    const products = prodRes.rows;
    if (products.length === 0) {
      res.json({ purchaseId, userTotal: 0, supermarket: purchase.supermarket, comparisons: [], unmatchedProducts: [] });
      return;
    }

    // 3. Para cada producto: match exacto si está vinculado, si no matching difuso por tokens.
    const matchedByProduct: Array<{
      ticketName: string;
      ticketPrice: number;
      ticketQty: number;
      category: string;
      sepaMatches: SepaMatch[];
    }> = [];

    for (const prod of products) {
      if (prod.seed_product_name) {
        const exactRes = await pool.query(
          `SELECT product_name, supermarket, price FROM reference_prices WHERE product_name = $1`,
          [prod.seed_product_name]
        );
        const bestBySupermarket = new Map<string, SepaMatch>();
        for (const row of exactRes.rows as SepaMatch[]) {
          if (!bestBySupermarket.has(row.supermarket)) bestBySupermarket.set(row.supermarket, row);
        }
        matchedByProduct.push({
          ticketName: prod.name,
          ticketPrice: parseFloat(prod.price),
          ticketQty: prod.quantity,
          category: prod.category ?? '',
          sepaMatches: Array.from(bestBySupermarket.values()).map(c => ({
            product_name: c.product_name,
            supermarket: c.supermarket,
            price: parseFloat(String(c.price))
          }))
        });
        continue;
      }

      const tokens = tokenize(prod.name);
      if (tokens.length === 0) {
        matchedByProduct.push({ ticketName: prod.name, ticketPrice: prod.price, ticketQty: prod.quantity, category: prod.category ?? '', sepaMatches: [] });
        continue;
      }

      const topTokens = tokens.slice(0, 3);
      const conditions = topTokens.map((t, i) => `LOWER(product_name) LIKE $${i + 1}`).join(' AND ');
      const params = topTokens.map(t => `%${t}%`);

      const sepaRes = await pool.query(
        `SELECT product_name, supermarket, price FROM reference_prices WHERE ${conditions} LIMIT 50`,
        params
      );

      let candidates = sepaRes.rows;
      if (candidates.length === 0 && tokens.length > 0) {
        const orRes = await pool.query(
          `SELECT product_name, supermarket, price FROM reference_prices WHERE LOWER(product_name) LIKE $1 LIMIT 30`,
          [`%${tokens[0]}%`]
        );
        candidates = orRes.rows;
      }

      const scored = candidates
        .map((c: any) => ({ ...c, score: scoreMatch(tokens, c.product_name) }))
        .filter((c: any) => c.score > 0)
        .sort((a: any, b: any) => b.score - a.score);

      const bestBySupermarket = new Map<string, typeof scored[0]>();
      for (const c of scored) {
        if (!bestBySupermarket.has(c.supermarket)) bestBySupermarket.set(c.supermarket, c);
      }

      matchedByProduct.push({
        ticketName: prod.name,
        ticketPrice: parseFloat(prod.price),
        ticketQty: prod.quantity,
        category: prod.category ?? '',
        sepaMatches: Array.from(bestBySupermarket.values()).map(c => ({
          product_name: c.product_name,
          supermarket: c.supermarket,
          price: parseFloat(c.price)
        }))
      });
    }

    // 4. Calcular total por supermercado
    const allSupermarkets = new Set<string>();
    for (const mp of matchedByProduct) {
      for (const m of mp.sepaMatches) allSupermarkets.add(m.supermarket);
    }

    const userTotal = products.reduce((s: number, p: any) => s + parseFloat(p.price) * p.quantity, 0);

    const comparisons: Array<{
      supermarket: string;
      total: number;
      matchedCount: number;
      savings: number;
      savingsPct: number;
      products: Array<{ ticketName: string; matchedName: string; sepaPrice: number; ticketPrice: number; category: string }>;
    }> = [];

    for (const supermarket of allSupermarkets) {
      let total = 0;
      let matchedCount = 0;
      const productDetails = [];

      for (const mp of matchedByProduct) {
        const match = mp.sepaMatches.find(m => m.supermarket === supermarket);
        if (match) {
          total += match.price * mp.ticketQty;
          matchedCount++;
          productDetails.push({
            ticketName: mp.ticketName,
            matchedName: match.product_name,
            sepaPrice: match.price,
            ticketPrice: mp.ticketPrice,
            category: mp.category
          });
        } else {
          total += mp.ticketPrice * mp.ticketQty;
          productDetails.push({
            ticketName: mp.ticketName,
            matchedName: '',
            sepaPrice: mp.ticketPrice,
            ticketPrice: mp.ticketPrice,
            category: mp.category
          });
        }
      }

      if (matchedCount === 0) continue;

      const savings = userTotal - total;
      const savingsPct = userTotal > 0 ? Math.round((savings / userTotal) * 100) : 0;

      comparisons.push({ supermarket, total, matchedCount, savings, savingsPct, products: productDetails });
    }

    comparisons.sort((a, b) => a.total - b.total);

    const unmatchedProducts = matchedByProduct
      .filter(mp => mp.sepaMatches.length === 0)
      .map(mp => mp.ticketName);

    res.json({
      purchaseId,
      userTotal,
      supermarket: purchase.supermarket,
      comparisons,
      unmatchedProducts
    });

  } catch (err: any) {
    console.error('Error en compare-purchase:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
```

- [ ] **Step 3: Compilar TypeScript para verificar que no hay errores**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores

- [ ] **Step 4: Commit**

```bash
git add backend/src/lib/seedMatching.ts backend/src/routes/purchaseComparison.ts
git commit -m "refactor: extraer matching por tokens a lib compartida y usar vinculo exacto cuando existe"
```

---

## Task 3: Endpoint de matching y búsqueda en el catálogo

**Files:**
- Create: `backend/src/routes/productMatch.ts`
- Modify: `backend/src/index.ts`

- [ ] **Step 1: Crear `backend/src/routes/productMatch.ts`**

```typescript
// productMatch.ts — Vincula productos escaneados de un ticket con el catálogo de precios
// de referencia (seed), antes de que el usuario confirme qué guardar en la compra.
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { matchNameToSeed } from '../lib/seedMatching';

const router = Router();
router.use(authMiddleware);

// POST /products/match-seed
// Body: { products: [{ name: string }] }
// Response: { matches: [{ seedMatch: string|null, candidates: string[] }] } (mismo orden que el body)
router.post('/match-seed', async (req: AuthRequest, res: Response): Promise<void> => {
  const products = (req.body.products ?? []) as Array<{ name: string }>;
  try {
    const matches = await Promise.all(products.map(p => matchNameToSeed(pool, p.name ?? '')));
    res.json({ matches });
  } catch (err: any) {
    console.error('Error en match-seed:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

// GET /products/seed-search?query=...
// Búsqueda libre en el catálogo, para que el usuario vincule manualmente un producto.
router.get('/seed-search', async (req: AuthRequest, res: Response): Promise<void> => {
  const query = ((req.query.query as string) ?? '').trim();
  if (query.length < 2) { res.json([]); return; }
  try {
    const result = await pool.query(
      `SELECT DISTINCT product_name, brand FROM reference_prices
       WHERE product_name ILIKE '%' || $1 || '%'
       ORDER BY product_name LIMIT 20`,
      [query]
    );
    res.json(result.rows.map(r => ({ productName: r.product_name, brand: r.brand ?? '' })));
  } catch (err: any) {
    console.error('Error en seed-search:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
```

- [ ] **Step 2: Montar la ruta en `backend/src/index.ts`**

Agregar el import junto a los demás (después de `import purchaseComparisonRoutes from './routes/purchaseComparison';`):

```typescript
import productMatchRoutes from './routes/productMatch';
```

Y agregar el mount junto a las demás rutas (después de `app.use('/purchases/:purchaseId/compare', purchaseComparisonRoutes);`):

```typescript
app.use('/products', productMatchRoutes);
```

- [ ] **Step 3: Compilar**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores

- [ ] **Step 4: Verificación manual con el server corriendo localmente**

Run: `cd backend && npm run dev` (en una terminal aparte, con `.env` configurado con `DATABASE_URL` y `JWT_SECRET`)

Con un token válido (`TOKEN`, obtenido de `POST /auth/login`):
```bash
curl -s -X POST http://localhost:3000/products/match-seed \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"products":[{"name":"Coca Cola 1.5L"},{"name":"Producto Que No Existe Jamas"}]}'
```
Expected: JSON con `matches` de longitud 2 — el primero probablemente con `seedMatch` no nulo (si hay datos SEPA de gaseosas cargados), el segundo con `seedMatch: null` y `candidates: []`.

```bash
curl -s "http://localhost:3000/products/seed-search?query=coca" -H "Authorization: Bearer $TOKEN"
```
Expected: array de objetos `{productName, brand}` con "coca" en el nombre.

- [ ] **Step 5: Commit**

```bash
git add backend/src/routes/productMatch.ts backend/src/index.ts
git commit -m "feat: endpoint de matching ticket-seed y busqueda manual en el catalogo"
```

---

## Task 4: Pasar `seed_product_name` por el CRUD de productos

**Files:**
- Modify: `backend/src/routes/products.ts`

- [ ] **Step 1: Reemplazar el contenido completo de `backend/src/routes/products.ts`**

```typescript
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router({ mergeParams: true }); // mergeParams para acceder a :purchaseId
router.use(authMiddleware);

// Verifica que la compra pertenece al usuario antes de operar sobre sus productos.
async function verifyOwnership(purchaseId: number, userId: number): Promise<boolean> {
  const pid = parseInt(String(purchaseId));
  const uid = parseInt(String(userId));
  const result = await pool.query('SELECT id, user_id FROM purchases WHERE id = $1', [pid]);
  if (result.rows.length === 0) return false;
  return parseInt(String(result.rows[0].user_id)) === uid;
}

function toProductJson(pr: any) {
  return {
    id: pr.id,
    purchase_id: pr.purchase_id,
    code: pr.code ?? '',
    name: pr.name,
    description: pr.description ?? '',
    price: parseFloat(pr.price),
    quantity: pr.quantity,
    category: pr.category ?? '',
    seed_product_name: pr.seed_product_name ?? null
  };
}

// GET /purchases/:purchaseId/products
router.get('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  try {
    if (!(await verifyOwnership(purchaseId, req.userId!))) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      'SELECT * FROM products WHERE purchase_id = $1 ORDER BY id',
      [purchaseId]
    );
    res.json(result.rows.map(toProductJson));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// POST /purchases/:purchaseId/products
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  const { code, name, description, price, quantity, category, seed_product_name } = req.body;
  if (!name || price === undefined) {
    res.status(400).json({ error: 'Nombre y precio son obligatorios' });
    return;
  }
  try {
    const owns = await verifyOwnership(purchaseId, req.userId!);
    if (!owns) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      `INSERT INTO products (purchase_id, code, name, description, price, quantity, category, seed_product_name)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING *`,
      [purchaseId, code ?? '', name, description ?? '', price, quantity ?? 1, category ?? '', seed_product_name ?? null]
    );
    const pr = result.rows[0];

    await pool.query(
      `UPDATE purchases
       SET total = (SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE purchase_id = $1)
       WHERE id = $1`,
      [purchaseId]
    );

    res.status(201).json(toProductJson(pr));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// PUT /purchases/:purchaseId/products/:productId
router.put('/:productId', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  const productId = parseInt(req.params.productId);
  const { code, name, description, price, quantity, category, seed_product_name } = req.body;
  try {
    if (!(await verifyOwnership(purchaseId, req.userId!))) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      `UPDATE products
       SET code               = COALESCE($1, code),
           name               = COALESCE($2, name),
           description        = COALESCE($3, description),
           price              = COALESCE($4, price),
           quantity           = COALESCE($5, quantity),
           category           = COALESCE($6, category),
           seed_product_name  = COALESCE($7, seed_product_name)
       WHERE id = $8 AND purchase_id = $9
       RETURNING *`,
      [code, name, description, price, quantity, category, seed_product_name, productId, purchaseId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Producto no encontrado' });
      return;
    }

    await pool.query(
      `UPDATE purchases
       SET total = (SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE purchase_id = $1)
       WHERE id = $1`,
      [purchaseId]
    );

    res.json(toProductJson(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// DELETE /purchases/:purchaseId/products/:productId
router.delete('/:productId', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  const productId = parseInt(req.params.productId);
  try {
    if (!(await verifyOwnership(purchaseId, req.userId!))) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      'DELETE FROM products WHERE id = $1 AND purchase_id = $2 RETURNING id',
      [productId, purchaseId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Producto no encontrado' });
      return;
    }

    await pool.query(
      `UPDATE purchases
       SET total = (SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE purchase_id = $1)
       WHERE id = $1`,
      [purchaseId]
    );

    res.status(204).send();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

export default router;
```

Nota: se quitaron los `console.log` de debug que tenía el archivo original (`[verifyOwnership]`, `[POST product]`) — eran ruido de una sesión de debugging anterior, no se pierde funcionalidad.

- [ ] **Step 2: Compilar**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores

- [ ] **Step 3: Verificación manual**

Con el server corriendo y un `purchaseId` real del usuario autenticado:
```bash
curl -s -X POST http://localhost:3000/purchases/$PURCHASE_ID/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Test Producto","price":100,"quantity":1,"seed_product_name":"COCA COLA X1.5LT"}'
```
Expected: respuesta 201 con `"seed_product_name":"COCA COLA X1.5LT"` en el JSON.

- [ ] **Step 4: Commit**

```bash
git add backend/src/routes/products.ts
git commit -m "feat: incluir seed_product_name en el CRUD de productos"
```

---

## Task 5: Nombres más descriptivos en la comparativa (formato/tamaño legible)

**Files:**
- Modify: `backend/src/routes/prices.ts`

- [ ] **Step 1: Extender `cleanProductName` con normalización de formato**

En `backend/src/routes/prices.ts`, reemplazar la función `cleanProductName` (líneas 14-23) por:

```typescript
// Limpia nombres de producto: quita abreviaturas corporativas (S.A., S.R.L., S.A.F.R.T., etc.),
// aplica título case y normaliza el formato/tamaño final (ej. "X1.5LT" -> "1.5L").
function cleanProductName(raw: string): string {
  const cleaned = raw
    .replace(/\s+[A-Z](\.[A-Z])+\.?/g, ' ')   // quita S.A., S.A.F.R.T., S.R.L., S.A.I.C., etc.
    .replace(/\s{2,}/g, ' ')
    .trim();
  if (!cleaned) return raw;
  const titled = cleaned.toLowerCase()
    .replace(/(^|[\s\-\/])(\S)/g, (_, sep, char) => sep + char.toUpperCase());
  return normalizeFormat(titled);
}

// Normaliza el formato/tamaño al final del nombre para que se lea como en un local real
// en vez del formato crudo de SEPA (ej. "X1.5LT" -> "1.5L", "X500GR" -> "500g").
function normalizeFormat(name: string): string {
  return name
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*LT\b/gi, '$1L')
    .replace(/\bX(\d+)\s*X\s*(\d+(?:[.,]\d+)?)\s*ML\b/gi, '$1x$2ml')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*ML\b/gi, '$1ml')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*KG\b/gi, '$1kg')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*GR?\b/gi, '$1g')
    .replace(/\bX(\d+)\s*UN\b/gi, 'x$1')
    .replace(/\s{2,}/g, ' ')
    .trim();
}
```

- [ ] **Step 2: Compilar**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores

- [ ] **Step 3: Verificación manual contra datos reales**

Run: `cd backend && npm run dev`, luego:
```bash
curl -s "http://localhost:3000/prices/compare?query=coca&offset=0" -H "Authorization: Bearer $TOKEN" | head -c 2000
```
Expected: revisar el campo `productName` de los resultados — los tamaños/formatos al final deben verse legibles (ej. "1.5L", "500ml") en vez del formato crudo SEPA. Si algún patrón real de la base no fue cubierto por las regex, ajustar `normalizeFormat` con el caso encontrado.

- [ ] **Step 4: Commit**

```bash
git add backend/src/routes/prices.ts
git commit -m "feat: normalizar formato/tamano en nombres de productos de la comparativa"
```

---

## Task 6: DTOs Android para matching y búsqueda en el catálogo

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt`

- [ ] **Step 1: Agregar los DTOs nuevos**

Al final de `AiDtos.kt`, agregar:

```kotlin

// ── Matching ticket → seed ───────────────────────────────────────

data class MatchSeedItemDto(
    @SerializedName("name") val name: String
)

data class MatchSeedRequest(
    @SerializedName("products") val products: List<MatchSeedItemDto>
)

data class SeedMatchResultDto(
    @SerializedName("seedMatch")  val seedMatch: String?,
    @SerializedName("candidates") val candidates: List<String> = emptyList()
)

data class MatchSeedResponse(
    @SerializedName("matches") val matches: List<SeedMatchResultDto>
)

data class SeedSearchResultDto(
    @SerializedName("productName") val productName: String,
    @SerializedName("brand")       val brand: String = ""
)
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt
git commit -m "feat: DTOs para matching de productos contra la seed"
```

---

## Task 7: Campo `seedProductName` en los DTOs de producto

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/dto/PurchaseDtos.kt`

- [ ] **Step 1: Agregar el campo a `ProductDto`, `CreateProductRequest` y `UpdateProductRequest`**

Reemplazar el bloque `// ── Product DTOs ──────────────────────────────────────────────` completo (líneas 31-60) por:

```kotlin
// ── Product DTOs ──────────────────────────────────────────────

data class ProductDto(
    val id: Int,
    @SerializedName("purchase_id") val purchaseId: Int,
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)

data class CreateProductRequest(
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)

data class UpdateProductRequest(
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (puede haber errores en `ProductRepository.kt`/`ApiService.kt` por las llamadas existentes a estos constructores sin el nuevo parámetro — se resuelven en las próximas tasks ya que el campo tiene default `null`/`""`, así que en realidad debería compilar sin tocar nada más)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/dto/PurchaseDtos.kt
git commit -m "feat: agregar seedProductName a los DTOs de producto"
```

---

## Task 8: Nuevos endpoints en `ApiService`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt`

- [ ] **Step 1: Agregar los endpoints de matching/búsqueda**

Al final de la interfaz `ApiService`, antes del `}` de cierre (después de `comparePurchase`), agregar:

```kotlin

    // ── Matching ticket → seed ─────────────────────────────────
    @POST("products/match-seed")
    suspend fun matchSeedProducts(
        @Header("Authorization") token: String,
        @Body body: MatchSeedRequest
    ): Response<MatchSeedResponse>

    @GET("products/seed-search")
    suspend fun searchSeedProducts(
        @Header("Authorization") token: String,
        @Query("query") query: String
    ): Response<List<SeedSearchResultDto>>
```

(El `import com.undef.superahorroturina.data.network.dto.*` ya existente cubre los nuevos tipos, no hace falta agregar imports.)

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt
git commit -m "feat: endpoints Retrofit para matching y busqueda en la seed"
```

---

## Task 9: Migración de Room para `seedProductName`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/local/db/ProductEntity.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt`

- [ ] **Step 1: Agregar el campo a `ProductEntity`**

En `ProductEntity.kt`, reemplazar:
```kotlin
    val category: String = ""
)
```
por:
```kotlin
    val category: String = "",
    val seedProductName: String? = null
)
```

- [ ] **Step 2: Agregar `MIGRATION_2_3` y subir la versión de la base**

Reemplazar el contenido completo de `AppDatabase.kt` por:

```kotlin
package com.undef.superahorroturina.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PurchaseEntity::class, ProductEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productDao(): ProductDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN seedProductName TEXT")
            }
        }
    }
}
```

- [ ] **Step 3: Registrar la migración nueva en `DatabaseModule.kt`**

En `DatabaseModule.kt`, reemplazar:
```kotlin
            .addMigrations(AppDatabase.MIGRATION_1_2)
```
por:
```kotlin
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
```

- [ ] **Step 4: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/db/ProductEntity.kt app/src/main/java/com/undef/superahorroturina/data/local/db/AppDatabase.kt app/src/main/java/com/undef/superahorroturina/di/DatabaseModule.kt
git commit -m "feat: migracion Room para seedProductName en products"
```

---

## Task 10: `ProductRepository` — propagar `seedProductName` y arreglar mapeos incompletos

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/repository/ProductRepository.kt`

- [ ] **Step 1: Reemplazar el contenido completo de `ProductRepository.kt`**

```kotlin
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreateProductRequest
import com.undef.superahorroturina.data.network.dto.MatchSeedItemDto
import com.undef.superahorroturina.data.network.dto.MatchSeedRequest
import com.undef.superahorroturina.data.network.dto.SeedMatchResultDto
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.network.dto.UpdateProductRequest
import com.undef.superahorroturina.model.Product
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val productDao: ProductDao
) {
    suspend fun getProducts(purchaseId: Int): ApiResult<List<Product>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getProducts(token, purchaseId)
        if (response.isSuccessful) {
            val dtos = response.body()!!
            val products = dtos.map {
                Product(it.id, it.code, it.name, it.description, it.price, it.quantity)
            }
            productDao.upsertAll(dtos.map { p ->
                ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName)
            })
            ApiResult.Success(products)
        } else {
            ApiResult.Error("Error al cargar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createProduct(
        purchaseId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int,
        category: String = "", seedProductName: String? = null
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createProduct(
            token, purchaseId,
            CreateProductRequest(code, name, description, price, quantity, category, seedProductName)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al crear producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updateProduct(
        purchaseId: Int, productId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updateProduct(
            token, purchaseId, productId,
            UpdateProductRequest(code, name, description, price, quantity)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al actualizar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deleteProduct(purchaseId: Int, productId: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deleteProduct(token, purchaseId, productId)
        if (response.isSuccessful) {
            productDao.delete(productId)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun getLocalProductsForPurchase(purchaseId: Int): List<Product> =
        productDao.getByPurchaseId(purchaseId).first().map { e ->
            Product(e.id, e.code, e.name, e.description, e.price, e.quantity)
        }

    // ── Matching contra la seed ──────────────────────────────────
    suspend fun matchSeed(names: List<String>): ApiResult<List<SeedMatchResultDto>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.matchSeedProducts(token, MatchSeedRequest(names.map { MatchSeedItemDto(it) }))
        if (response.isSuccessful) {
            ApiResult.Success(response.body()?.matches ?: emptyList())
        } else {
            ApiResult.Error("Error al vincular productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun searchSeedProducts(query: String): ApiResult<List<SeedSearchResultDto>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.searchSeedProducts(token, query)
        if (response.isSuccessful) {
            ApiResult.Success(response.body() ?: emptyList())
        } else {
            ApiResult.Error("Error al buscar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }
}
```

Nota: se corrigió de paso un bug existente en `updateProduct` — no incluía `category` (ni ahora `seedProductName`) al reconstruir el `ProductEntity` desde la respuesta, perdiendo esos campos en Room cada vez que se editaba un producto manualmente. También se corrigió `getProducts`, que construía el `ProductEntity` a partir del modelo `Product` (que nunca tuvo `category`) en vez de a partir del DTO crudo — perdía la categoría en cada refresh completo de la lista.

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/ProductRepository.kt
git commit -m "feat: propagar seedProductName en ProductRepository y corregir mapeos de category"
```

---

## Task 11: `PurchaseDetailViewModel` — matching automático al confirmar el ticket

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt`

- [ ] **Step 1: Reemplazar el contenido completo de `PurchaseDetailViewModel.kt`**

```kotlin
// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
// También maneja el flujo de OCR: escanear ticket → matchear contra la seed → confirmar inserción.
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
import com.undef.superahorroturina.data.network.dto.ScanTicketRequest
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val purchase: Purchase? = null,
    val error: String = ""
)

// Un producto detectado en el ticket junto con su estado de vínculo a la seed.
// seedMatch = nombre exacto de reference_prices.product_name, o null si no está vinculado.
data class ScannedProductUi(
    val product: ScannedProductDto,
    val seedMatch: String? = null,
    val seedCandidates: List<String> = emptyList()
)

// Estado del flujo de escaneo de ticket
sealed class TicketScanState {
    object Idle : TicketScanState()
    object Scanning : TicketScanState()
    data class Confirm(val items: List<ScannedProductUi>, val supermarket: String?) : TicketScanState()
    object Inserting : TicketScanState()
    data class Error(val message: String) : TicketScanState()
    object Done : TicketScanState()
}

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository,
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    private val _ticketScanState = MutableStateFlow<TicketScanState>(TicketScanState.Idle)
    val ticketScanState: StateFlow<TicketScanState> = _ticketScanState.asStateFlow()

    fun resetTicketScan() { _ticketScanState.value = TicketScanState.Idle }

    fun loadPurchase(purchaseId: Int) {
        viewModelScope.launch {
            _uiState.value = PurchaseDetailUiState(isLoading = true)
            when (val result = purchaseRepository.getPurchase(purchaseId)) {
                is ApiResult.Success -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    purchase  = result.data
                )
                is ApiResult.Error   -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }

    fun deletePurchase(onSuccess: () -> Unit) {
        val id = _uiState.value.purchase?.id ?: return
        viewModelScope.launch {
            purchaseRepository.deletePurchase(id)
            onSuccess()
        }
    }

    fun deleteProduct(purchaseId: Int, productId: Int) {
        viewModelScope.launch {
            productRepository.deleteProduct(purchaseId, productId)
            // Recargar la compra para reflejar el nuevo total y lista de productos
            loadPurchase(purchaseId)
        }
    }

    // ── Ticket OCR ────────────────────────────────────────────────
    // Intenta con Gemini Vision; si falla, usa ML Kit como fallback.
    fun scanTicketFromUri(context: Context, imageUri: Uri, purchaseId: Int) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Scanning
            try {
                val bytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
                    ?: run {
                        _ticketScanState.value = TicketScanState.Error("No se pudo leer la imagen")
                        return@launch
                    }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"

                val token = session.bearerToken.first()
                val response = api.scanTicket(token, purchaseId, ScanTicketRequest(base64, mimeType))

                if (response.isSuccessful) {
                    val body = response.body()
                    val products = body?.products ?: emptyList()
                    if (products.isNotEmpty()) {
                        _ticketScanState.value = buildConfirmState(products, body?.supermarket)
                        return@launch
                    }
                }

                // Fallback: ML Kit OCR (sin parseo de productos — extrae texto crudo)
                mlKitFallback(context, imageUri, purchaseId)

            } catch (e: Exception) {
                try { mlKitFallback(context, imageUri, purchaseId) }
                catch (ex: Exception) {
                    _ticketScanState.value = TicketScanState.Error("Error al escanear: ${ex.message}")
                }
            }
        }
    }

    // ML Kit: reconoce el texto del ticket y arma una lista de producto genérico
    // con el texto completo para que el usuario lo vea y ajuste manualmente.
    private suspend fun mlKitFallback(context: Context, imageUri: Uri, purchaseId: Int) {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(image).await()
        val rawText = visionText.text.trim()

        if (rawText.isBlank()) {
            _ticketScanState.value = TicketScanState.Error("No se pudo extraer texto del ticket")
            return
        }

        val parsed = mutableListOf<ScannedProductDto>()
        val priceRegex = Regex("""(\d{1,6}[.,]\d{2})""")
        rawText.lines().forEach { line ->
            val match = priceRegex.find(line)
            if (match != null) {
                val price = match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
                val name  = line.substring(0, match.range.first).trim()
                    .takeIf { it.length >= 2 } ?: "Producto"
                if (price > 0) parsed.add(ScannedProductDto(name = name, price = price))
            }
        }

        _ticketScanState.value = if (parsed.isNotEmpty()) {
            buildConfirmState(parsed, null)
        } else {
            buildConfirmState(
                listOf(ScannedProductDto(name = "Ticket escaneado", price = 0.0, description = rawText.take(200))),
                null
            )
        }
    }

    // Llama a /products/match-seed para los productos detectados y arma el estado de confirmación
    // con el resultado del matching (auto-vinculado o candidatos para elegir manualmente).
    private suspend fun buildConfirmState(products: List<ScannedProductDto>, supermarket: String?): TicketScanState {
        val matchResult = productRepository.matchSeed(products.map { it.name })
        val matches = (matchResult as? ApiResult.Success)?.data
        val items = products.mapIndexed { index, p ->
            val match = matches?.getOrNull(index)
            ScannedProductUi(
                product        = p,
                seedMatch      = match?.seedMatch,
                seedCandidates = match?.candidates ?: emptyList()
            )
        }
        return TicketScanState.Confirm(items, supermarket)
    }

    // Cambia o quita el vínculo de un producto a la seed (elegido a mano por el usuario).
    fun updateSeedLink(index: Int, seedProductName: String?) {
        val current = _ticketScanState.value
        if (current is TicketScanState.Confirm) {
            val updated = current.items.toMutableList()
            updated[index] = updated[index].copy(seedMatch = seedProductName)
            _ticketScanState.value = current.copy(items = updated)
        }
    }

    // Búsqueda libre en el catálogo para el buscador manual de vínculo.
    suspend fun searchSeedProducts(query: String): List<SeedSearchResultDto> {
        val result = productRepository.searchSeedProducts(query)
        return (result as? ApiResult.Success)?.data ?: emptyList()
    }

    // Confirmar e insertar los productos detectados en la compra, con su vínculo a la seed (si lo hay).
    fun confirmScannedProducts(purchaseId: Int, items: List<ScannedProductUi>) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Inserting
            try {
                items.forEach { item ->
                    val p = item.product
                    productRepository.createProduct(
                        purchaseId      = purchaseId,
                        code            = p.code,
                        name            = p.name,
                        description     = p.description,
                        price           = p.price,
                        quantity        = p.quantity,
                        category        = p.category,
                        seedProductName = item.seedMatch
                    )
                }
                _ticketScanState.value = TicketScanState.Done
                loadPurchase(purchaseId)
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("Error al guardar productos: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FALLA (esperado) — `PurchaseDetailScreen.kt` todavía usa `state.products` (campo viejo) y la función `TicketScanConfirmDialog`. Se corrige en la próxima task.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailViewModel.kt
git commit -m "feat: matching automatico contra la seed al confirmar el ticket escaneado"
```

---

## Task 12: Pantalla completa de confirmación de ticket (`TicketConfirmScreen`)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketConfirmScreen.kt`

- [ ] **Step 1: Crear `TicketConfirmScreen.kt`**

```kotlin
// TicketConfirmScreen.kt — pantalla completa para revisar los productos detectados en un ticket
// antes de guardarlos, mostrando si cada uno matchea con el catálogo de precios de referencia (seed)
// y permitiendo vincular manualmente los que no matchearon o corregir un match equivocado.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import kotlinx.coroutines.delay
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketConfirmScreen(
    products: List<ScannedProductUi>,
    supermarket: String?,
    moneyFormat: NumberFormat,
    onSearchSeed: suspend (String) -> List<SeedSearchResultDto>,
    onLinkChange: (Int, String?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var pickerIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar productos") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Confirmar y guardar (${products.size})")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!supermarket.isNullOrBlank()) {
                Text(
                    text     = "Supermercado: ${supermarket.replaceFirstChar { it.uppercaseChar() }}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(products) { index, item ->
                    ScannedProductRow(
                        item        = item,
                        moneyFormat = moneyFormat,
                        onLinkClick = { pickerIndex = index }
                    )
                }
            }
        }
    }

    val openIndex = pickerIndex
    if (openIndex != null) {
        SeedLinkPickerSheet(
            initialCandidates = products[openIndex].seedCandidates,
            onSearch          = onSearchSeed,
            onPick            = { name -> onLinkChange(openIndex, name); pickerIndex = null },
            onUnlink          = { onLinkChange(openIndex, null); pickerIndex = null },
            onDismiss         = { pickerIndex = null }
        )
    }
}

@Composable
private fun ScannedProductRow(
    item: ScannedProductUi,
    moneyFormat: NumberFormat,
    onLinkClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Text(
                    text       = "${item.product.name} x${item.product.quantity}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    text       = if (item.product.price > 0) "$ ${moneyFormat.format(item.product.price)}" else "–",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }

            val seedMatch = item.seedMatch
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (seedMatch != null) Color(0xFF059669).copy(alpha = 0.12f)
                        else Color(0xFFF59E0B).copy(alpha = 0.12f)
                    )
                    .clickable(onClick = onLinkClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = if (seedMatch != null) Icons.Default.CheckCircle else Icons.Default.Search,
                    contentDescription = null,
                    tint     = if (seedMatch != null) Color(0xFF059669) else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text     = seedMatch?.let { "Vinculado a $it" } ?: "Sin coincidencia — tocar para buscar",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (seedMatch != null) Color(0xFF059669) else Color(0xFFF59E0B),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedLinkPickerSheet(
    initialCandidates: List<String>,
    onSearch: suspend (String) -> List<SeedSearchResultDto>,
    onPick: (String) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(initialCandidates) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = initialCandidates
        } else {
            delay(400)
            results = onSearch(query).map { it.productName }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier            = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Vincular con el catálogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                label         = { Text("Buscar producto…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            TextButton(onClick = onUnlink) { Text("No vincular este producto") }
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(results) { candidate ->
                    Text(
                        text     = candidate,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            "Sin resultados",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/TicketConfirmScreen.kt
git commit -m "feat: pantalla completa de confirmacion de ticket con vinculo a la seed"
```

---

## Task 13: Wirear `TicketConfirmScreen` en `PurchaseDetailScreen` (reemplaza el diálogo viejo)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt`

- [ ] **Step 1: Quitar el import de `ScannedProductDto` (ya no se usa directamente en este archivo)**

Eliminar la línea:
```kotlin
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
```

- [ ] **Step 2: Agregar el early-return a pantalla completa cuando el estado es `Confirm`**

Inmediatamente después de la línea `val moneyFormat   = remember { NumberFormat.getNumberInstance(Locale("es", "AR")) }` (antes de `var showDeleteDialog by remember { mutableStateOf(false) }`), agregar:

```kotlin

    // Pantalla completa de confirmación (reemplaza el flujo normal mientras hay productos para revisar)
    if (ticketState is TicketScanState.Confirm) {
        val confirmState = ticketState as TicketScanState.Confirm
        TicketConfirmScreen(
            products     = confirmState.items,
            supermarket  = confirmState.supermarket,
            moneyFormat  = moneyFormat,
            onSearchSeed = { query -> viewModel.searchSeedProducts(query) },
            onLinkChange = { index, name -> viewModel.updateSeedLink(index, name) },
            onConfirm    = { viewModel.confirmScannedProducts(purchaseId, confirmState.items) },
            onCancel     = { viewModel.resetTicketScan() }
        )
        return
    }
```

- [ ] **Step 3: Quitar la rama `Confirm` del `when` existente y borrar el diálogo viejo**

Reemplazar:
```kotlin
    // Diálogo de confirmación de productos escaneados
    when (val state = ticketState) {
        is TicketScanState.Confirm -> {
            TicketScanConfirmDialog(
                products    = state.products,
                supermarket = state.supermarket,
                moneyFormat = moneyFormat,
                onConfirm   = { viewModel.confirmScannedProducts(purchaseId, state.products) },
                onDismiss   = { viewModel.resetTicketScan() }
            )
        }
        is TicketScanState.Error -> {
```
por:
```kotlin
    // Diálogo de error de escaneo / auto-reset al confirmar
    when (val state = ticketState) {
        is TicketScanState.Error -> {
```

- [ ] **Step 4: Borrar la función `TicketScanConfirmDialog` completa**

Eliminar todo el bloque desde el comentario `// ── Diálogo de confirmación de productos escaneados ───────────` hasta el final del archivo (la función `private fun TicketScanConfirmDialog(...)` completa, líneas 372-447 del archivo original).

- [ ] **Step 5: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/purchase/PurchaseDetailScreen.kt
git commit -m "feat: reemplazar el dialogo de confirmacion de ticket por TicketConfirmScreen"
```

---

## Task 14: Iconos por categoría en la comparativa de precios

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonScreen.kt`

- [ ] **Step 1: Agregar la función `categoryIcon`**

Inmediatamente después de la función `categoryColor` (después de su `}` de cierre, línea 61), agregar:

```kotlin

private fun categoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    "Bebida"          -> Icons.Default.LocalDrink
    "Lácteo"          -> Icons.Default.Icecream
    "Carne y Fiambre" -> Icons.Default.LunchDining
    "Panadería"       -> Icons.Default.BakeryDining
    "Almacén"         -> Icons.Default.ShoppingBasket
    "Cereales"        -> Icons.Default.BreakfastDining
    "Aceite"          -> Icons.Default.WaterDrop
    "Condimento"      -> Icons.Default.Restaurant
    "Enlatado"        -> Icons.Default.Inventory
    "Congelado"       -> Icons.Default.AcUnit
    "Golosinas"       -> Icons.Default.Cookie
    "Snack"           -> Icons.Default.Fastfood
    "Limpieza"        -> Icons.Default.CleaningServices
    "Papel"           -> Icons.Default.Inventory2
    "Perfumería"      -> Icons.Default.Spa
    "Bebé"            -> Icons.Default.ChildCare
    "Mascotas"        -> Icons.Default.Pets
    else              -> Icons.Default.LocalGroceryStore
}
```

- [ ] **Step 2: Usar el ícono en los chips de categoría**

Reemplazar:
```kotlin
                        items(visibleCats) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick  = {
                                    viewModel.selectedCategory.value =
                                        if (selectedCategory == cat) "" else cat
                                },
                                label = { Text("$cat (${uiState.categoryCounts[cat] ?: 0})") }
                            )
                        }
```
por:
```kotlin
                        items(visibleCats) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick  = {
                                    viewModel.selectedCategory.value =
                                        if (selectedCategory == cat) "" else cat
                                },
                                label       = { Text("$cat (${uiState.categoryCounts[cat] ?: 0})") },
                                leadingIcon = { Icon(categoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
```

- [ ] **Step 3: Usar el ícono en el badge de categoría de `CompactPriceCard`**

Reemplazar:
```kotlin
                        if (item.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(catColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text       = item.category,
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = catColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
```
por:
```kotlin
                        if (item.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(catColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector        = categoryIcon(item.category),
                                        contentDescription = null,
                                        tint               = catColor,
                                        modifier           = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text       = item.category,
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = catColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
```

- [ ] **Step 4: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Si algún nombre de ícono no existe en la versión de Material Icons Extended del proyecto, el compilador señala exactamente cuál — reemplazar ese ícono puntual por un equivalente cercano (ej. `Icons.Default.Liquor` en vez de alguno no disponible) y volver a compilar.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/prices/PriceComparisonScreen.kt
git commit -m "feat: iconos por categoria en la comparativa de precios"
```

---

## Task 15: Verificación manual end-to-end

**Files:** (ninguno — solo verificación)

- [ ] **Step 1: Compilar y instalar la app completa**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, app instalada

- [ ] **Step 2: Probar el flujo completo de ticket → matching → confirmación**

1. Abrir una compra existente (o crear una nueva) y tocar "Adjuntar ticket".
2. Sacar/elegir una foto de un ticket real de supermercado.
3. Verificar que aparece la nueva pantalla completa "Confirmar productos" (no el diálogo viejo).
4. Verificar que al menos algunos productos muestran el chip verde "Vinculado a …" (si el ticket tiene productos típicos como gaseosas/lácteos que existan en la seed).
5. Tocar un chip ámbar "Sin coincidencia" y verificar que se abre el buscador, que escribir filtra resultados, y que se puede elegir un candidato o "No vincular este producto".
6. Confirmar y guardar; verificar que los productos aparecen en el detalle de la compra con sus precios correctos.

- [ ] **Step 3: Probar la comparación de esa misma compra**

1. Desde el detalle de la compra, tocar el ícono "Comparar precios" (CompareArrows).
2. Verificar que la pantalla `PurchaseComparisonScreen` muestra el ranking de supermercados y que, para los productos que se vincularon manualmente en el paso anterior, el detalle expandido muestra un `matchedName` (en vez de aparecer como "sin dato").

- [ ] **Step 4: Probar el listado general de comparativa de precios**

1. Ir a Comparativa de Precios desde Home.
2. Verificar que los chips de categoría y las tarjetas de producto muestran íconos (no solo color).
3. Verificar que los nombres de producto se ven más legibles en el formato/tamaño (ej. "1.5L" en vez de "X1.5LT") para productos que tengan ese patrón en el nombre crudo.

- [ ] **Step 5: Si todo funciona, no se requiere commit adicional (este task es solo de verificación)**

Si se encuentra algún problema durante la verificación manual, volver a la task correspondiente, corregir, compilar, commitear el fix por separado, y repetir la verificación.
