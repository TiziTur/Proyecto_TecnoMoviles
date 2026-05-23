import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();

// GET /users/me — perfil del usuario autenticado
router.get('/me', authMiddleware, async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const result = await pool.query(
      'SELECT id, first_name, last_name, email, phone FROM users WHERE id = $1',
      [req.userId]
    );
    if (result.rows.length === 0) {
      res.status(404).json({ error: 'Usuario no encontrado' });
      return;
    }
    const u = result.rows[0];
    res.json({
      id: u.id,
      firstName: u.first_name,
      lastName: u.last_name,
      email: u.email,
      phone: u.phone
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

// PUT /users/me — actualizar perfil
router.put('/me', authMiddleware, async (req: AuthRequest, res: Response): Promise<void> => {
  const firstName = req.body.firstName || req.body.first_name;
  const lastName  = req.body.lastName  || req.body.last_name;
  const { email, phone } = req.body;
  try {
    const result = await pool.query(
      `UPDATE users
       SET first_name = COALESCE($1, first_name),
           last_name  = COALESCE($2, last_name),
           email      = COALESCE($3, email),
           phone      = COALESCE($4, phone)
       WHERE id = $5
       RETURNING id, first_name, last_name, email, phone`,
      [firstName, lastName, email, phone, req.userId]
    );
    const u = result.rows[0];
    res.json({
      id: u.id,
      firstName: u.first_name,
      lastName: u.last_name,
      email: u.email,
      phone: u.phone
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error interno del servidor' });
  }
});

export default router;
