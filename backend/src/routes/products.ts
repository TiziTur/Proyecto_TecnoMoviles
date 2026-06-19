import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router({ mergeParams: true }); // mergeParams para acceder a :purchaseId
router.use(authMiddleware);

// Verifica que la compra pertenece al usuario antes de operar sobre sus productos.
async function verifyOwnership(purchaseId: number, userId: number): Promise<boolean> {
  const pid = parseInt(String(purchaseId));
  const uid = parseInt(String(userId));
  console.log(`[verifyOwnership] checking purchaseId=${pid} (${typeof pid}) userId=${uid} (${typeof uid})`);
  const result = await pool.query(
    'SELECT id, user_id FROM purchases WHERE id = $1',
    [pid]
  );
  console.log(`[verifyOwnership] found rows: ${result.rows.length}, row:`, result.rows[0]);
  if (result.rows.length === 0) return false;
  return parseInt(String(result.rows[0].user_id)) === uid;
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
    res.json(result.rows.map(pr => ({
      id: pr.id,
      purchase_id: pr.purchase_id,
      code: pr.code ?? '',
      name: pr.name,
      description: pr.description ?? '',
      price: parseFloat(pr.price),
      quantity: pr.quantity,
      category: pr.category ?? ''
    })));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// POST /purchases/:purchaseId/products
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  const { code, name, description, price, quantity, category } = req.body;
  console.log(`[POST product] purchaseId=${purchaseId} userId=${req.userId} name=${name} price=${price}`);
  if (!name || price === undefined) {
    res.status(400).json({ error: 'Nombre y precio son obligatorios' });
    return;
  }
  try {
    const owns = await verifyOwnership(purchaseId, req.userId!);
    console.log(`[POST product] verifyOwnership(${purchaseId}, ${req.userId}) = ${owns}`);
    if (!owns) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      `INSERT INTO products (purchase_id, code, name, description, price, quantity, category)
       VALUES ($1, $2, $3, $4, $5, $6, $7)
       RETURNING *`,
      [purchaseId, code ?? '', name, description ?? '', price, quantity ?? 1, category ?? '']
    );
    const pr = result.rows[0];

    // Recalcular y actualizar el total de la compra
    await pool.query(
      `UPDATE purchases
       SET total = (SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE purchase_id = $1)
       WHERE id = $1`,
      [purchaseId]
    );

    res.status(201).json({
      id: pr.id,
      purchase_id: pr.purchase_id,
      code: pr.code ?? '',
      name: pr.name,
      description: pr.description ?? '',
      price: parseFloat(pr.price),
      quantity: pr.quantity,
      category: pr.category ?? ''
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// PUT /purchases/:purchaseId/products/:productId
router.put('/:productId', async (req: AuthRequest, res: Response): Promise<void> => {
  const purchaseId = parseInt(req.params.purchaseId);
  const productId = parseInt(req.params.productId);
  const { code, name, description, price, quantity, category } = req.body;
  try {
    if (!(await verifyOwnership(purchaseId, req.userId!))) {
      res.status(404).json({ error: 'Compra no encontrada' });
      return;
    }
    const result = await pool.query(
      `UPDATE products
       SET code        = COALESCE($1, code),
           name        = COALESCE($2, name),
           description = COALESCE($3, description),
           price       = COALESCE($4, price),
           quantity    = COALESCE($5, quantity),
           category    = COALESCE($6, category)
       WHERE id = $7 AND purchase_id = $8
       RETURNING *`,
      [code, name, description, price, quantity, category, productId, purchaseId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Producto no encontrado' });
      return;
    }

    // Recalcular total de la compra
    await pool.query(
      `UPDATE purchases
       SET total = (SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE purchase_id = $1)
       WHERE id = $1`,
      [purchaseId]
    );

    const pr = result.rows[0];
    res.json({
      id: pr.id,
      purchase_id: pr.purchase_id,
      code: pr.code ?? '',
      name: pr.name,
      description: pr.description ?? '',
      price: parseFloat(pr.price),
      quantity: pr.quantity,
      category: pr.category ?? ''
    });
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

    // Recalcular total de la compra
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
