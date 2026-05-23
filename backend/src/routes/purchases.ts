import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

// Convierte un row de PostgreSQL a JSON alineado con los DTOs de Android.
// purchase_date y purchase_time llegan como objetos Date desde pg — los convertimos a string.
// products es opcional: cuando no se pasa (listado), se usa p.product_count del SQL (COUNT agregado).
function purchaseToJson(p: any, products?: any[]) {
  // purchase_date: pg devuelve un objeto Date → "yyyy-MM-dd"
  const dateObj = p.purchase_date instanceof Date ? p.purchase_date : new Date(p.purchase_date);
  const dateStr = dateObj.toISOString().split('T')[0];

  // purchase_time: pg devuelve string "HH:mm:ss" o "HH:mm"
  const timeStr = typeof p.purchase_time === 'string'
    ? p.purchase_time.substring(0, 5)   // "HH:mm"
    : '00:00';

  // Si se pasaron productos explícitamente (detalle), usamos su longitud.
  // Si no (listado), usamos el product_count que viene del COUNT() del SQL.
  const resolvedProducts = products ?? [];
  const resolvedCount    = products !== undefined
    ? resolvedProducts.length
    : (parseInt(p.product_count) || 0);

  return {
    id:            p.id,
    purchase_date: dateStr,
    purchase_time: timeStr,
    supermarket:   p.supermarket,
    total:         parseFloat(p.total) || 0,
    ticket_image_uri: p.ticket_image_uri ?? null,
    product_count: resolvedCount,
    products:      resolvedProducts.map((pr: any) => ({
      id:          pr.id,
      purchase_id: pr.purchase_id,
      code:        pr.code ?? '',
      name:        pr.name,
      description: pr.description ?? '',
      price:       parseFloat(pr.price),
      quantity:    pr.quantity
    }))
  };
}

// GET /purchases — todas las compras del usuario autenticado
router.get('/', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const result = await pool.query(
      `SELECT p.id, p.purchase_date, p.purchase_time, p.supermarket, p.total, p.ticket_image_uri,
              COUNT(pr.id)::int AS product_count
       FROM purchases p
       LEFT JOIN products pr ON pr.purchase_id = p.id
       WHERE p.user_id = $1
       GROUP BY p.id
       ORDER BY p.purchase_date DESC, p.purchase_time DESC`,
      [req.userId]
    );
    res.json(result.rows.map(row => purchaseToJson(row)));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// GET /purchases/:id — detalle de una compra con sus productos
router.get('/:id', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.id);
  try {
    const purchaseResult = await pool.query(
      'SELECT * FROM purchases WHERE id = $1 AND user_id = $2',
      [purchaseId, req.userId]
    );
    if (purchaseResult.rows.length === 0) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }

    const productsResult = await pool.query(
      'SELECT * FROM products WHERE purchase_id = $1 ORDER BY id',
      [purchaseId]
    );

    res.json(purchaseToJson(purchaseResult.rows[0], productsResult.rows));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// POST /purchases — crear nueva compra
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  // Acepta purchase_date (Android @SerializedName) o date como fallback
  const date        = req.body.purchase_date || req.body.purchaseDate || req.body.date;
  const time        = req.body.purchase_time || req.body.purchaseTime || req.body.time;
  const supermarket = req.body.supermarket;

  if (!date || !supermarket) {
    res.status(400).json({ error: 'Fecha y supermercado son obligatorios' });
    return;
  }

  try {
    const result = await pool.query(
      `INSERT INTO purchases (user_id, purchase_date, purchase_time, supermarket, total)
       VALUES ($1, $2, $3, $4, 0)
       RETURNING *`,
      [req.userId, date, time ?? '00:00', supermarket]
    );
    res.status(201).json(purchaseToJson(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// PUT /purchases/:id — editar compra
router.put('/:id', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.id);
  const date        = req.body.purchase_date || req.body.purchaseDate || req.body.date;
  const time        = req.body.purchase_time || req.body.purchaseTime || req.body.time;
  const supermarket = req.body.supermarket;

  try {
    const result = await pool.query(
      `UPDATE purchases
       SET purchase_date = COALESCE($1, purchase_date),
           purchase_time = COALESCE($2, purchase_time),
           supermarket   = COALESCE($3, supermarket)
       WHERE id = $4 AND user_id = $5
       RETURNING *`,
      [date, time, supermarket, purchaseId, req.userId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    res.json(purchaseToJson(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// DELETE /purchases/:id — elimina compra y sus productos por CASCADE
router.delete('/:id', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.id);
  try {
    const result = await pool.query(
      'DELETE FROM purchases WHERE id = $1 AND user_id = $2 RETURNING id',
      [purchaseId, req.userId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    res.status(204).send();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

export default router;
