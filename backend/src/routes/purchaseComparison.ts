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
