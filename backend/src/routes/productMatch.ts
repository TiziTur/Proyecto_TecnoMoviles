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
  const products = req.body.products;
  if (!Array.isArray(products)) {
    res.status(400).json({ error: 'products debe ser un array' });
    return;
  }
  try {
    const matches = await Promise.all((products as Array<{ name: string }>).map(p => matchNameToSeed(pool, p.name ?? '')));
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
