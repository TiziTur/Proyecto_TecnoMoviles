// prices.ts — Comparativa de precios entre supermercados.
// Combina los precios reales del usuario (de su historial) con precios de referencia
// de supermercados argentinos. Los precios de referencia son actualizables sin re-deploy.
// GET /prices/compare — compara los productos del usuario entre supermercados
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

// Precios de referencia por nombre de producto normalizado y supermercado.
// Estructura: Map<nombreNormalizado, Map<supermercado, precio>>
// Esto simula datos reales de supermercados argentinos con precios representativos.
// En producción real se actualizaría desde scraping o Precios Claros API.
const REFERENCE_PRICES: Record<string, Record<string, number>> = {
  'leche': {
    'carrefour': 1850, 'dia': 1720, 'coto': 1890, 'jumbo': 1950, 'walmart': 1780
  },
  'leche entera': {
    'carrefour': 1850, 'dia': 1720, 'coto': 1890, 'jumbo': 1950, 'walmart': 1780
  },
  'pan': {
    'carrefour': 1200, 'dia': 1100, 'coto': 1250, 'jumbo': 1350, 'walmart': 1150
  },
  'pan lactal': {
    'carrefour': 1400, 'dia': 1280, 'coto': 1350, 'jumbo': 1500, 'walmart': 1300
  },
  'coca cola': {
    'carrefour': 2800, 'dia': 2650, 'coto': 2900, 'jumbo': 3100, 'walmart': 2700
  },
  'gaseosa': {
    'carrefour': 2200, 'dia': 2050, 'coto': 2300, 'jumbo': 2500, 'walmart': 2100
  },
  'aceite': {
    'carrefour': 3200, 'dia': 2950, 'coto': 3400, 'jumbo': 3600, 'walmart': 3100
  },
  'aceite girasol': {
    'carrefour': 3200, 'dia': 2950, 'coto': 3400, 'jumbo': 3600, 'walmart': 3100
  },
  'arroz': {
    'carrefour': 1600, 'dia': 1450, 'coto': 1700, 'jumbo': 1850, 'walmart': 1550
  },
  'fideos': {
    'carrefour': 900,  'dia': 820,  'coto': 950,  'jumbo': 1050, 'walmart': 860
  },
  'harina': {
    'carrefour': 1100, 'dia': 980,  'coto': 1150, 'jumbo': 1280, 'walmart': 1020
  },
  'azucar': {
    'carrefour': 1300, 'dia': 1180, 'coto': 1350, 'jumbo': 1450, 'walmart': 1220
  },
  'azúcar': {
    'carrefour': 1300, 'dia': 1180, 'coto': 1350, 'jumbo': 1450, 'walmart': 1220
  },
  'yerba': {
    'carrefour': 2400, 'dia': 2200, 'coto': 2500, 'jumbo': 2700, 'walmart': 2300
  },
  'te': {
    'carrefour': 1800, 'dia': 1650, 'coto': 1900, 'jumbo': 2100, 'walmart': 1700
  },
  'té': {
    'carrefour': 1800, 'dia': 1650, 'coto': 1900, 'jumbo': 2100, 'walmart': 1700
  },
  'cafe': {
    'carrefour': 2900, 'dia': 2700, 'coto': 3100, 'jumbo': 3400, 'walmart': 2800
  },
  'café': {
    'carrefour': 2900, 'dia': 2700, 'coto': 3100, 'jumbo': 3400, 'walmart': 2800
  },
  'manteca': {
    'carrefour': 2200, 'dia': 2050, 'coto': 2350, 'jumbo': 2500, 'walmart': 2100
  },
  'queso': {
    'carrefour': 4500, 'dia': 4200, 'coto': 4700, 'jumbo': 5100, 'walmart': 4300
  },
  'yogur': {
    'carrefour': 1400, 'dia': 1280, 'coto': 1500, 'jumbo': 1650, 'walmart': 1320
  },
  'shampoo': {
    'carrefour': 2800, 'dia': 2600, 'coto': 2950, 'jumbo': 3200, 'walmart': 2700
  },
  'jabón': {
    'carrefour': 850,  'dia': 780,  'coto': 900,  'jumbo': 980,  'walmart': 800
  },
  'jabon': {
    'carrefour': 850,  'dia': 780,  'coto': 900,  'jumbo': 980,  'walmart': 800
  },
};

function normalizeProductName(name: string): string {
  return name.toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')  // quitar tildes
    .replace(/[^a-z0-9\s]/g, '')
    .trim();
}

// GET /prices/compare
// Devuelve, para cada producto único del historial del usuario que tiene precios
// de referencia, cuánto costó en cada supermercado y dónde es más barato.
router.get('/compare', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    // Obtener todos los productos del usuario con su supermercado y precio
    const result = await pool.query(
      `SELECT DISTINCT ON (pr.name, p.supermarket)
              pr.name,
              p.supermarket,
              pr.price as user_price,
              p.purchase_date
       FROM products pr
       JOIN purchases p ON p.id = pr.purchase_id
       WHERE p.user_id = $1
       ORDER BY pr.name, p.supermarket, p.purchase_date DESC`,
      [req.userId]
    );

    // Agrupar por producto
    const productMap: Record<string, Record<string, number>> = {};
    for (const row of result.rows) {
      const norm = normalizeProductName(row.name);
      if (!productMap[row.name]) productMap[row.name] = {};
      productMap[row.name][row.supermarket.toLowerCase()] = parseFloat(row.user_price);
    }

    const comparisons = Object.entries(productMap).map(([productName, userPrices]) => {
      const norm = normalizeProductName(productName);
      const refPrices = REFERENCE_PRICES[norm] ?? {};

      // Combinar precios del usuario con precios de referencia
      const allPrices: Record<string, { price: number; isUserData: boolean }> = {};

      // Primero los del usuario (más confiables)
      for (const [supermarket, price] of Object.entries(userPrices)) {
        allPrices[supermarket] = { price, isUserData: true };
      }

      // Agregar referencia solo para supermercados que el usuario no visitó
      for (const [supermarket, price] of Object.entries(refPrices)) {
        if (!allPrices[supermarket]) {
          allPrices[supermarket] = { price, isUserData: false };
        }
      }

      const priceList = Object.entries(allPrices).map(([supermarket, data]) => ({
        supermarket,
        price: data.price,
        isUserData: data.isUserData
      })).sort((a, b) => a.price - b.price);

      if (priceList.length < 2) return null;

      const cheapest    = priceList[0];
      const mostExpensive = priceList[priceList.length - 1];
      const savings     = mostExpensive.price - cheapest.price;

      return {
        productName,
        prices: priceList,
        cheapestAt: cheapest.supermarket,
        cheapestPrice: cheapest.price,
        maxSavings: savings,
        savingsPct: Math.round((savings / mostExpensive.price) * 100)
      };
    }).filter(Boolean);

    // Ordenar por mayor ahorro potencial
    comparisons.sort((a: any, b: any) => b.maxSavings - a.maxSavings);

    res.json({ comparisons });
  } catch (err: any) {
    console.error('Error en /prices/compare:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
