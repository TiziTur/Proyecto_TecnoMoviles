import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';

// Extiendo Request para poder adjuntar el userId decodificado del token.
export interface AuthRequest extends Request {
  userId?: number;
}

export function authMiddleware(req: AuthRequest, res: Response, next: NextFunction): void {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Token requerido' });
    return;
  }

  const token = header.substring(7);
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET!) as { userId: number };
    // Forzar parseInt por si el JWT guardó el id como string
    req.userId = parseInt(String(payload.userId), 10);
    console.log(`[auth] userId decodificado: ${req.userId} (tipo: ${typeof req.userId})`);
    next();
  } catch {
    res.status(401).json({ error: 'Token inválido o expirado' });
  }
}
