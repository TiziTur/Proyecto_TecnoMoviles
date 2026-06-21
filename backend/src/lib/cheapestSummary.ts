// cheapestSummary.ts — Calcula, de forma determinística, qué supermercado tiene los
// precios más bajos en general sobre todo el catálogo SEPA (reference_prices).
// Solo se consideran "comparables" los productos que existen en 2+ supermercados.
import { Pool } from 'pg';

export interface CheapestSummaryStats {
  cheapestSupermarket: string;
  productsCompared: number;
  productsWon: number;
  totalSavings: number;
  avgSavingsPct: number;
}

export async function computeCheapestSummary(pool: Pool): Promise<CheapestSummaryStats | null> {
  const result = await pool.query(`
    WITH per_product_supermarket AS (
      SELECT product_name, supermarket, MIN(price) AS price
      FROM reference_prices
      GROUP BY product_name, supermarket
    )
    SELECT product_name, supermarket, price
    FROM per_product_supermarket
    WHERE product_name IN (
      SELECT product_name FROM per_product_supermarket
      GROUP BY product_name
      HAVING COUNT(DISTINCT supermarket) > 1
    )
    ORDER BY product_name
  `);

  if (result.rows.length === 0) return null;

  // Agrupar filas por producto
  const byProduct = new Map<string, { supermarket: string; price: number }[]>();
  for (const row of result.rows) {
    const price = parseFloat(row.price);
    const list = byProduct.get(row.product_name) ?? [];
    list.push({ supermarket: row.supermarket, price });
    byProduct.set(row.product_name, list);
  }

  // Por cada producto: el más barato "gana" ese producto, contra el más caro disponible
  const wins: Record<string, number> = {};
  const winnerAcc: Record<string, { totalSavings: number; pctSum: number; count: number }> = {};

  for (const entries of byProduct.values()) {
    const sorted = [...entries].sort((a, b) => a.price - b.price);
    const cheapest = sorted[0];
    const priciest = sorted[sorted.length - 1];
    const savings  = priciest.price - cheapest.price;
    const pct      = priciest.price > 0 ? (savings / priciest.price) * 100 : 0;

    wins[cheapest.supermarket] = (wins[cheapest.supermarket] ?? 0) + 1;

    const acc = winnerAcc[cheapest.supermarket] ?? { totalSavings: 0, pctSum: 0, count: 0 };
    acc.totalSavings += savings;
    acc.pctSum       += pct;
    acc.count        += 1;
    winnerAcc[cheapest.supermarket] = acc;
  }

  const [cheapestSupermarket] = Object.entries(wins).sort((a, b) => b[1] - a[1])[0];
  const acc = winnerAcc[cheapestSupermarket];

  return {
    cheapestSupermarket,
    productsCompared: byProduct.size,
    productsWon:      acc.count,
    totalSavings:     Math.round(acc.totalSavings * 100) / 100,
    avgSavingsPct:    Math.round(acc.pctSum / acc.count)
  };
}
