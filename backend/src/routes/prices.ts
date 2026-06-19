// prices.ts — Comparativa de precios SEPA con filtro por categoría.
// GET /prices/compare?query=&category=
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

function normalize(name: string): string {
  return name.toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9\s]/g, '').trim();
}

function detectCategory(name: string): string {
  const n = name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  if (/gaseosa|jugo|agua min|agua soda|vino|cerveza|sidra|bebida|sprite|fanta|cola|pepsi|manaos|soda|isoton|tonica|energiz|limon|naranja.*bebida/.test(n)) return 'Bebida';
  if (/leche|yogur|queso|manteca|crema de leche|ricota|dulce.*leche|mozzarella|cheddar|untable|infantil.*leche/.test(n)) return 'Lácteo';
  if (/detergente|lavandina|limpiador|desengrasante|suavizante|desinfectante|ariel|skip|cif|ala |lysoform|pinesol|bolsa.*basura|papel higien|servilleta|papel.*cocina|esponja|cloro/.test(n)) return 'Limpieza';
  if (/shampoo|acondicionador|jabon.*tocador|desodorante|crema facial|gel.*ducha|pasta.*dental|dentifric|cepillo.*dental|toalla.*femen|afeitad|perfume|colonia|protector solar/.test(n)) return 'Perfumería';
  if (/galletita|papa.*frita|chizito|doritos|pringles|oreo|alfajor|caramelo|chicle|mani |pochoclo|tortita|golosina|oblea|wafer|chocolate/.test(n)) return 'Snack';
  if (/\bpan\b|facturas|medialuna|bizcocho|budin|tostada|pan rallado|lactal|mignon/.test(n)) return 'Panadería';
  if (/pollo|carne.*vacuna|carne.*cerdo|peceto|milanesa|chorizo|salchicha|jamon|fiambre|mortadela|paleta|nalga|cuadril/.test(n)) return 'Carne';
  if (/helado|pizza.*congel|empanada.*congel|nugget|bastones.*papa/.test(n)) return 'Congelado';
  if (/aceite.*girasol|aceite.*oliva|aceite.*maiz|sal fina|sal entrefina|azucar|pimienta|vinagre|mayonesa|ketchup|mostaza|salsa.*tomate|aderezo/.test(n)) return 'Condimento';
  if (/atun|sardina|tomate.*triturado|pure.*tomate|conserva|choclo.*lata|arveja|lenteja|poroto/.test(n)) return 'Enlatado';
  if (/arroz|fideos|tallarines|ñoquis|polenta|harina|copos|avena|legumbre/.test(n)) return 'Almacén';
  return 'Alimento';
}

const CATEGORY_KEYWORDS: Record<string, string> = {
  'Bebida':      'gaseosa|jugo|agua min|agua soda|vino|cerveza|sidra|bebida|sprite|fanta|cola|pepsi|manaos|soda|isoton|tonica',
  'Lácteo':      'leche|yogur|queso|manteca|crema de leche|ricota|dulce.*leche|mozzarella|cheddar|untable',
  'Limpieza':    'detergente|lavandina|limpiador|desengrasante|suavizante|desinfectante|ariel|skip|cif|ala |lysoform|bolsa.*basura|papel higien|servilleta|papel.*cocina|esponja',
  'Perfumería':  'shampoo|acondicionador|jabon.*tocador|desodorante|crema facial|gel.*ducha|pasta.*dental|dentifric|cepillo.*dental|toalla.*femen|afeitad',
  'Snack':       'galletita|papa.*frita|chizito|doritos|pringles|oreo|alfajor|caramelo|chicle|mani |pochoclo|chocolate|golosina',
  'Panadería':   '\\bpan\\b|facturas|medialuna|bizcocho|budin|tostada|pan rallado|lactal',
  'Carne':       'pollo|carne.*vacuna|carne.*cerdo|peceto|milanesa|chorizo|salchicha|jamon|fiambre',
  'Congelado':   'helado|pizza.*congel|empanada.*congel|nugget',
  'Condimento':  'aceite.*girasol|aceite.*oliva|sal fina|sal entrefina|azucar|pimienta|vinagre|mayonesa|ketchup|mostaza|salsa.*tomate',
  'Enlatado':    'atun|sardina|tomate.*triturado|pure.*tomate|choclo.*lata|arveja|lenteja|poroto',
  'Almacén':     'arroz|fideos|tallarines|ñoquis|polenta|harina|copos|avena',
};

router.get('/compare', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const query    = ((req.query.query    as string) ?? '').trim();
    const category = ((req.query.category as string) ?? '').trim();

    // Construir cláusula SQL para filtro de categoría (regex case-insensitive, sin extensiones)
    let catCondition = '';
    if (category && CATEGORY_KEYWORDS[category]) {
      catCondition = `AND LOWER(product_name) ~* '${CATEGORY_KEYWORDS[category].replace(/'/g, "''")}'`;
    }

    // 1. Precios del usuario (historial)
    const userResult = await pool.query(
      `SELECT DISTINCT ON (pr.name, p.supermarket)
              pr.name AS product_name, p.supermarket,
              pr.price AS user_price, p.purchase_date
       FROM products pr
       JOIN purchases p ON p.id = pr.purchase_id
       WHERE p.user_id = $1
         AND ($2 = '' OR pr.name ILIKE '%' || $2 || '%')
       ORDER BY pr.name, p.supermarket, p.purchase_date DESC`,
      [req.userId, query]
    );

    // 2. Precios SEPA — con filtro de categoría y límite inteligente
    const limit = query ? 150 : 80;
    const refResult = await pool.query(
      `SELECT product_name, supermarket, price, brand, updated_at
       FROM reference_prices
       WHERE ($1 = '' OR product_name ILIKE '%' || $1 || '%')
       ${catCondition}
       ORDER BY product_name
       LIMIT ${limit}`,
      [query]
    );

    // 3. Metadata
    const metaResult = await pool.query(`SELECT MAX(updated_at) AS lu FROM reference_prices`);
    const lastUpdated: string | null = metaResult.rows[0]?.lu ?? null;
    const hasRefData = refResult.rows.length > 0;

    // 4. Construir mapas normalizados
    const userMap: Record<string, Record<string, number>> = {};
    for (const row of userResult.rows) {
      const norm = normalize(row.product_name);
      if (!userMap[norm]) userMap[norm] = {};
      userMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.user_price);
    }

    const refMap: Record<string, Record<string, number>> = {};
    const refNameMap: Record<string, string> = {}; // norm -> product_name original
    for (const row of refResult.rows) {
      const norm = normalize(row.product_name);
      if (!refMap[norm]) { refMap[norm] = {}; refNameMap[norm] = row.product_name; }
      refMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.price);
    }

    // 5. Combinar
    const allNorms = new Set([...Object.keys(userMap), ...Object.keys(refMap)]);

    const comparisons = Array.from(allNorms).map(norm => {
      const userRow    = userResult.rows.find(r => normalize(r.product_name) === norm);
      const productName = userRow?.product_name ?? refNameMap[norm] ?? norm;
      const detectedCategory = detectCategory(productName);

      // Aplicar filtro de categoría del lado JS también (para los del historial sin filtro SQL)
      if (category && detectedCategory !== category) return null;

      const allPrices: Record<string, { price: number; isUserData: boolean }> = {};
      for (const [s, p] of Object.entries(userMap[norm] ?? {}))
        allPrices[s] = { price: p, isUserData: true };
      for (const [s, p] of Object.entries(refMap[norm] ?? {}))
        if (!allPrices[s]) allPrices[s] = { price: p, isUserData: false };

      const priceList = Object.entries(allPrices)
        .map(([supermarket, d]) => ({ supermarket, price: d.price, isUserData: d.isUserData }))
        .sort((a, b) => a.price - b.price);

      if (priceList.length === 0) return null;

      const cheapest = priceList[0];
      const priciest = priceList[priceList.length - 1];
      const savings  = priceList.length > 1 ? priciest.price - cheapest.price : 0;

      return {
        productName,
        category:      detectedCategory,
        prices:        priceList,
        cheapestAt:    cheapest.supermarket,
        cheapestPrice: cheapest.price,
        maxSavings:    savings,
        savingsPct:    priceList.length > 1 ? Math.round((savings / priciest.price) * 100) : 0,
      };
    }).filter(Boolean);

    // Ordenar: primero los que tienen 2+ precios (comparables), luego por ahorro
    comparisons.sort((a: any, b: any) => {
      const aMulti = a.prices.length > 1 ? 1 : 0;
      const bMulti = b.prices.length > 1 ? 1 : 0;
      if (bMulti !== aMulti) return bMulti - aMulti;
      return b.maxSavings - a.maxSavings;
    });

    // Contar por categoría para los chips
    const categoryCounts: Record<string, number> = {};
    for (const c of comparisons as any[]) {
      if (c) categoryCounts[c.category] = (categoryCounts[c.category] ?? 0) + 1;
    }

    res.json({
      comparisons:     comparisons.slice(0, 60), // máx 60 al cliente
      source:          hasRefData ? 'SEPA - preciosclaros.gob.ar' : 'historial_usuario',
      lastUpdated:     lastUpdated ?? null,
      isEmpty:         !hasRefData && userResult.rows.length === 0,
      categoryCounts,
    });
  } catch (err: any) {
    console.error('Error en /prices/compare:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
