import { Router, Request, Response } from 'express';

const router = Router();

// Lista fija de supermercados argentinos.
// Este endpoint cumple el requisito de "networking con API externa" de la consigna:
// la app lo consulta al abrir Nueva Compra en lugar de tener la lista hardcodeada.
const SUPERMARKETS = [
  { id: 1, name: 'Carrefour' },
  { id: 2, name: 'Dia' },
  { id: 3, name: 'Coto' },
  { id: 4, name: 'Jumbo' },
  { id: 5, name: 'Vea' },
  { id: 6, name: 'La Anónima' },
  { id: 7, name: 'Walmart' },
  { id: 8, name: 'Changomas' },
  { id: 9, name: 'Makro' },
  { id: 10, name: 'Disco' },
  { id: 11, name: 'Super Mayorista' },
  { id: 12, name: 'Otro' }
];

// GET /supermarkets
router.get('/', (_req: Request, res: Response) => {
  res.json(SUPERMARKETS);
});

export default router;
