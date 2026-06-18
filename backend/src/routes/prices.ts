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

      if (priceList.length === 0) return null;

      const cheapest = priceList[0];
      const priciest = priceList[priceList.length - 1];
      const savings  = priceList.length > 1 ? priciest.price - cheapest.price : 0;

      return {
        productName,
        prices: priceList,
        cheapestAt:    cheapest.supermarket,
        cheapestPrice: cheapest.price,
        maxSavings:    savings,
        savingsPct:    priceList.length > 1 ? Math.round((savings / priciest.price) * 100) : 0,
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
