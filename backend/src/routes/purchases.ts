import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

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
    res.json(result.rows.map(row => ({
      id: row.id,
      date: row.purchase_date,
      time: row.purchase_time,
      supermarket: row.supermarket,
      total: parseFloat(row.total),
      ticketImageUri: row.ticket_image_uri,
      productCount: row.product_count
    })));
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

    const p = purchaseResult.rows[0];
    res.json({
      id: p.id,
      date: p.purchase_date,
      time: p.purchase_time,
      supermarket: p.supermarket,
      total: parseFloat(p.total),
      ticketImageUri: p.ticket_image_uri,
      products: productsResult.rows.map(pr => ({
        id: pr.id,
        code: pr.code,
        name: pr.name,
        description: pr.description,
        price: parseFloat(pr.price),
        quantity: pr.quantity
      }))
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// POST /purchases — crear nueva compra
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const { date, time, supermarket, total, ticketImageUri } = req.body;
  if (!date || !supermarket) {
    res.status(400).json({ error: 'Fecha y supermercado son obligatorios' });
    return;
  }
  try {
    const result = await pool.query(
      `INSERT INTO purchases (user_id, purchase_date, purchase_time, supermarket, total, ticket_image_uri)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [req.userId, date, time ?? '00:00', supermarket, total ?? 0, ticketImageUri ?? null]
    );
    const p = result.rows[0];
    res.status(201).json({
      id: p.id,
      date: p.purchase_date,
      time: p.purchase_time,
      supermarket: p.supermarket,
      total: parseFloat(p.total),
      ticketImageUri: p.ticket_image_uri,
      products: []
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// PUT /purchases/:id — editar compra
router.put('/:id', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.id);
  const { date, time, supermarket, total, ticketImageUri } = req.body;
  try {
    const result = await pool.query(
      `UPDATE purchases
       SET purchase_date     = COALESCE($1, purchase_date),
           purchase_time     = COALESCE($2, purchase_time),
           supermarket       = COALESCE($3, supermarket),
           total             = COALESCE($4, total),
           ticket_image_uri  = COALESCE($5, ticket_image_uri)
       WHERE id = $6 AND user_id = $7
       RETURNING *`,
      [date, time, supermarket, total, ticketImageUri, purchaseId, req.userId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const p = result.rows[0];
    res.json({
      id: p.id,
      date: p.purchase_date,
      time: p.purchase_time,
      supermarket: p.supermarket,
      total: parseFloat(p.total),
      ticketImageUri: p.ticket_image_uri
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// DELETE /purchases/:id — eliminar compra (también elimina sus productos por CASCADE)
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
