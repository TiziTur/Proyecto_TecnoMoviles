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

  // Bebidas (antes que Lácteo para evitar conflictos con leche chocolatada en bebida)
  if (/gaseosa|jugo|agua min|agua soda|vino|cerveza|sidra|bebida|sprite|fanta|cola|pepsi|manaos|soda|isoton|tonica|energiz|te helado|agua saboriz|jugo.*polvo|tang |clight /.test(n)) return 'Bebida';

  // Lácteos
  if (/leche|yogur|queso|manteca|crema de leche|ricota|dulce.*leche|mozzarella|cheddar|untable|infantil.*leche|postre.*leche|flan|brie|gouda/.test(n)) return 'Lácteo';

  // Mascotas
  if (/pedigree|purina|whiskas|wiskas|gatarina|canigou|royal canin|dog chow|cat chow|croqueta.*perro|croqueta.*gato|alimento.*perro|alimento.*gato|snack.*perro|snack.*gato|arena.*gato|hueso.*perro|premio.*mascota/.test(n)) return 'Mascotas';

  // Bebé
  if (/pampers|huggies|pañal|maternizada|leche.*formula|formula.*lacte|papilla|comida.*bebe|crema.*bebe|talco.*bebe|johnsons.*bebe|toallita.*bebe|toalla.*humeda|bebe.*higien/.test(n)) return 'Bebé';

  // Papel e Higiene (antes que Limpieza ya que servilleta/papel cocina no es limpieza)
  if (/papel.*higien|papel.*toilet|rollo.*papel|servilleta|papel.*cocina|toalla.*papel|tissue|pañuelo.*descart|higiene.*intim|protector.*diario|cotton.*hidrof/.test(n)) return 'Papel';

  // Limpieza del hogar
  if (/detergente|lavandina|limpiador|desengrasante|suavizante|desinfectante|ariel|skip|cif|\bala\b|lysoform|pinesol|bolsa.*basura|cloro|esponja|trapo.*cocina|quitamanchas|limpiapisos|ajax|vim |lustramuebles|limpiavidrios/.test(n)) return 'Limpieza';

  // Perfumería e Higiene Personal
  if (/shampoo|acondicionador|jabon.*tocador|desodorante|crema facial|gel.*ducha|pasta.*dental|dentifric|cepillo.*dental|toalla.*femen|afeitad|perfume|colonia|protector solar|enjuague.*bucal|hilo.*dental|gel.*cabello|tinte.*cabello|crema.*depil/.test(n)) return 'Perfumería';

  // Carne y Fiambres
  if (/pollo|carne.*vacuna|carne.*cerdo|peceto|milanesa|chorizo|salchicha|jamon|fiambre|mortadela|paleta|nalga|cuadril|costilla|bife|lomo vac|asado vac|hamburguesa|picada|cerdo|pescado.*fresc|salmon|merluza|atun.*fresc/.test(n)) return 'Carne y Fiambre';

  // Panadería y Repostería
  if (/\bpan\b|facturas|medialuna|bizcocho|budin|tostada|pan rallado|lactal|mignon|galletita.*salada|pan.*integr|pan.*molde|preparado.*torta|mezcla.*bizcocho/.test(n)) return 'Panadería';

  // Golosinas (dulces, chocolates — antes que Snack)
  if (/chocolate|alfajor|caramelo|chicle|golosina|oblea|wafer|turron|tableta.*choco|nutella|dulce.*choco|chupete|bombon|mashmellow|marshmellow|gomita|jellybeans/.test(n)) return 'Golosinas';

  // Snack (salados y galletitas)
  if (/papa.*frita|chizito|doritos|pringles|oreo|galletita|mani |pochoclo|tortita|palito|cheetos|ruffles|\blays\b|crackers|palitos.*salad|bizcocho.*salad/.test(n)) return 'Snack';

  // Aceites (separado de condimentos)
  if (/aceite.*girasol|aceite.*oliva|aceite.*maiz|aceite.*canola|aceite.*soja|aceite.*mezcla|\baceite\b/.test(n)) return 'Aceite';

  // Condimentos y Salsas
  if (/sal fina|sal entrefina|azucar|pimienta|vinagre|mayonesa|ketchup|mostaza|salsa.*tomate|aderezo|oregano|curry|especias|caldo.*cubo|caldito|sopa.*sobre|sazonador|sopas/.test(n)) return 'Condimento';

  // Enlatados y Conservas
  if (/atun|sardina|tomate.*triturado|pure.*tomate|conserva|choclo.*lata|arveja|lenteja.*lata|poroto.*lata|champiñon.*lata|palmito|aceitunas|alcaparra/.test(n)) return 'Enlatado';

  // Congelados
  if (/helado|pizza.*congel|empanada.*congel|nugget|bastones.*papa|vegetal.*congel|espinaca.*congel|papa.*pre.*frita|croqueta.*congel/.test(n)) return 'Congelado';

  // Cereales y Desayuno
  if (/granola|muesli|corn flakes|zucaritas|froot loop|coco pop|kellogg|nesquik|trix |copos.*maiz|copos.*trigo|copos.*arroz|cereal|avena|copos.*avena|arroz.*inflado|musli/.test(n)) return 'Cereales';

  // Almacén (secos básicos — después de cereales)
  if (/arroz|fideos|tallarines|ñoquis|polenta|harina|legumbre|lenteja|garbanzo|poroto|soja.*grano|maiz.*seco|fecula|almidón|yerba|te |mate/.test(n)) return 'Almacén';

  return 'Alimento';
}

const CATEGORY_KEYWORDS: Record<string, string> = {
  'Bebida':          'gaseosa|jugo|agua min|agua soda|vino|cerveza|sidra|bebida|sprite|fanta|cola|pepsi|manaos|soda|isoton|tonica|energiz',
  'Lácteo':          'leche|yogur|queso|manteca|crema de leche|ricota|dulce.*leche|mozzarella|cheddar|untable',
  'Mascotas':        'pedigree|purina|whiskas|gatarina|canigou|royal canin|dog chow|cat chow|alimento.*perro|alimento.*gato',
  'Bebé':            'pampers|huggies|pañal|maternizada|papilla|comida.*bebe|toallita.*bebe',
  'Papel':           'papel.*higien|papel.*toilet|rollo.*papel|servilleta|papel.*cocina|toalla.*papel|tissue',
  'Limpieza':        'detergente|lavandina|limpiador|desengrasante|suavizante|desinfectante|ariel|skip|cif|lysoform|bolsa.*basura|cloro|esponja',
  'Perfumería':      'shampoo|acondicionador|jabon.*tocador|desodorante|crema facial|gel.*ducha|pasta.*dental|dentifric|cepillo.*dental|afeitad|enjuague.*bucal',
  'Carne y Fiambre': 'pollo|carne.*vacuna|carne.*cerdo|peceto|milanesa|chorizo|salchicha|jamon|fiambre|mortadela|hamburguesa',
  'Panadería':       'pan rallado|lactal|medialuna|facturas|bizcocho|budin|tostada|mignon',
  'Golosinas':       'chocolate|alfajor|caramelo|chicle|golosina|oblea|wafer|turron|nutella|bombon|gomita',
  'Snack':           'papa.*frita|chizito|doritos|pringles|oreo|galletita|mani |pochoclo|cheetos|ruffles|crackers',
  'Aceite':          'aceite.*girasol|aceite.*oliva|aceite.*maiz|aceite.*canola|aceite.*soja|aceite.*mezcla',
  'Condimento':      'sal fina|sal entrefina|azucar|pimienta|vinagre|mayonesa|ketchup|mostaza|salsa.*tomate|aderezo|caldo.*cubo|sopas',
  'Enlatado':        'atun|sardina|tomate.*triturado|pure.*tomate|choclo.*lata|arveja|lenteja.*lata|poroto.*lata|aceitunas',
  'Congelado':       'helado|pizza.*congel|empanada.*congel|nugget|bastones.*papa|croqueta.*congel',
  'Cereales':        'granola|muesli|corn flakes|zucaritas|kellogg|nesquik|copos.*maiz|copos.*trigo|cereal|avena',
  'Almacén':         'arroz|fideos|tallarines|ñoquis|polenta|harina|lenteja|garbanzo|poroto|soja|yerba|mate',
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
