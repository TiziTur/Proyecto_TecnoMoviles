// seedMatching.ts — utilidades de matching por tokens entre nombres de ticket y reference_prices.
// Compartido por purchaseComparison.ts (comparar una compra completa) y productMatch.ts
// (vincular un producto escaneado al catálogo apenas se confirma el ticket).
import { Pool } from 'pg';

export function tokenize(name: string): string[] {
  return name
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(t => t.length >= 3);
}

export function scoreMatch(ticketTokens: string[], sepaName: string): number {
  const sepaLow = sepaName.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  return ticketTokens.filter(t => sepaLow.includes(t)).length;
}

export interface SeedMatchResult {
  seedMatch: string | null;
  candidates: string[];
}

// Matchea un nombre de ticket contra reference_prices.product_name.
// Confianza alta (cubre >=70% de los tokens y al menos 2 en común) => auto-match (seedMatch no nulo).
// Si no, se devuelven hasta 5 candidatos para que el usuario elija manualmente.
export async function matchNameToSeed(pool: Pool, ticketName: string): Promise<SeedMatchResult> {
  const tokens = tokenize(ticketName);
  if (tokens.length === 0) return { seedMatch: null, candidates: [] };

  const topTokens = tokens.slice(0, 3);
  const conditions = topTokens.map((_, i) => `LOWER(product_name) LIKE $${i + 1}`).join(' AND ');
  const params = topTokens.map(t => `%${t}%`);

  const res = await pool.query(
    `SELECT DISTINCT product_name FROM reference_prices WHERE ${conditions} LIMIT 50`,
    params
  );

  let candidateRows = res.rows as Array<{ product_name: string }>;
  if (candidateRows.length === 0) {
    const orRes = await pool.query(
      `SELECT DISTINCT product_name FROM reference_prices WHERE LOWER(product_name) LIKE $1 LIMIT 30`,
      [`%${tokens[0]}%`]
    );
    candidateRows = orRes.rows;
  }

  const scored = candidateRows
    .map(c => ({ name: c.product_name, score: scoreMatch(tokens, c.product_name) }))
    .filter(c => c.score > 0)
    .sort((a, b) => b.score - a.score);

  if (scored.length === 0) return { seedMatch: null, candidates: [] };

  const best = scored[0];
  const isConfident = best.score >= 2 && best.score / tokens.length >= 0.7;

  return {
    seedMatch: isConfident ? best.name : null,
    candidates: scored.slice(0, 5).map(c => c.name)
  };
}
