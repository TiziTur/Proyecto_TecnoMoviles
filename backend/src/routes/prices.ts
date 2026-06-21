// prices.ts — Comparativa de precios SEPA con paginación, búsqueda normalizada y filtros.
// GET /prices/compare?query=&category=&brand=&minPrice=&maxPrice=&offset=0
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { computeCheapestSummary } from '../lib/cheapestSummary';

const router = Router();
router.use(authMiddleware);

const PAGE_SIZE = 50;

// Limpia nombres de producto: quita abreviaturas corporativas (S.A., S.R.L., S.A.F.R.T., etc.),
// aplica título case y normaliza el formato/tamaño final (ej. "X1.5LT" -> "1.5L").
function cleanProductName(raw: string): string {
  const cleaned = raw
    .replace(/\s+[A-Z](\.[A-Z])+\.?/g, ' ')   // quita S.A., S.A.F.R.T., S.R.L., S.A.I.C., etc.
    .replace(/\s{2,}/g, ' ')
    .trim();
  if (!cleaned) return raw;
  const titled = cleaned.toLowerCase()
    .replace(/(^|[\s\-\/])(\S)/g, (_, sep, char) => sep + char.toUpperCase());
  return normalizeFormat(titled);
}

// Normaliza el formato/tamaño al final del nombre para que se lea como en un local real
// en vez del formato crudo de SEPA (ej. "X1.5LT" -> "1.5L", "X500GR" -> "500g").
function normalizeFormat(name: string): string {
  return name
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*LT(?:\.(?=\s|$)|\b)/gi, '$1L')
    .replace(/\bX(\d+)\s*X\s*(\d+(?:[.,]\d+)?)\s*ML(?:\.(?=\s|$)|\b)/gi, '$1x$2ml')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*ML(?:\.(?=\s|$)|\b)/gi, '$1ml')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*KG(?:\.(?=\s|$)|\b)/gi, '$1kg')
    .replace(/\bX(\d+(?:[.,]\d+)?)\s*GR?S?(?:\.(?=\s|$)|\b)/gi, '$1g')
    .replace(/\bX(\d+)\s*UN(?:\.(?=\s|$)|\b)/gi, 'x$1')
    // Variantes sin prefijo "X" observadas en datos reales de SEPA, con o sin punto final
    // (ej. "1.5lt.", "750cc.", "500 Ml", "X800g.", "112G", "1.5 Litro").
    // El punto final solo se consume si va seguido de espacio o fin de string, para no
    // pegar la unidad con la palabra siguiente (ej. "1.250 LT.RETORNAB" no debe perder el espacio).
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Cc(?:\.(?=\s|$)|\b)/gi, '$1ml')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Lt(?:\.(?=\s|$)|\b)/gi, '$1L')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Litro[s]?(?:\.(?=\s|$)|\b)/gi, '$1L')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Ml(?:\.(?=\s|$)|\b)/gi, '$1ml')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Kg(?:\.(?=\s|$)|\b)/gi, '$1kg')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*Gr[s]?(?:\.(?=\s|$)|\b)/gi, '$1g')
    .replace(/\b(\d+(?:[.,]\d+)?)\s*G(?:\.(?=\s|$)|\b)/gi, '$1g')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

function normalize(name: string): string {
  return name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9\s]/g, '').trim();
}

function detectCategory(name: string): string {
  const n = name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  if (/gaseosa|jugo|agua min|agua soda|vino|cerveza|sidra|bebida|sprite|fanta|cola|pepsi|manaos|soda|isoton|tonica|energiz|te helado|agua saboriz|tang |clight /.test(n)) return 'Bebida';
  if (/leche|yogur|queso|manteca|crema de leche|ricota|dulce.*leche|mozzarella|cheddar|untable|infantil.*leche|postre.*leche|flan|brie|gouda/.test(n)) return 'Lácteo';
  if (/pedigree|purina|whiskas|wiskas|gatarina|canigou|royal canin|dog chow|cat chow|croqueta.*perro|croqueta.*gato|alimento.*perro|alimento.*gato|arena.*gato|hueso.*perro/.test(n)) return 'Mascotas';
  if (/pampers|huggies|pañal|maternizada|leche.*formula|formula.*lacte|papilla|comida.*bebe|crema.*bebe|johnsons.*bebe|toallita.*bebe|toalla.*humeda/.test(n)) return 'Bebé';
  if (/papel.*higien|papel.*toilet|rollo.*papel|servilleta|papel.*cocina|toalla.*papel|tissue|pañuelo.*descart|higiene.*intim|protector.*diario|cotton.*hidrof/.test(n)) return 'Papel';
  if (/detergente|lavandina|limpiador|desengrasante|suavizante|desinfectante|ariel|skip|cif|\bala\b|lysoform|pinesol|bolsa.*basura|cloro|esponja|trapo.*cocina|quitamanchas|limpiapisos|ajax|vim |lustramuebles/.test(n)) return 'Limpieza';
  if (/shampoo|acondicionador|jabon.*tocador|desodorante|crema facial|gel.*ducha|pasta.*dental|dentifric|cepillo.*dental|toalla.*femen|afeitad|perfume|colonia|protector solar|enjuague.*bucal|hilo.*dental|gel.*cabello|tinte.*cabello/.test(n)) return 'Perfumería';
  if (/pollo|carne.*vacuna|carne.*cerdo|peceto|milanesa|chorizo|salchicha|jamon|fiambre|mortadela|paleta|nalga|cuadril|costilla|bife|lomo vac|asado vac|hamburguesa|picada|cerdo|pescado.*fresc|salmon|merluza|atun.*fresc/.test(n)) return 'Carne y Fiambre';
  if (/\bpan\b|facturas|medialuna|bizcocho|budin|tostada|pan rallado|lactal|mignon|galletita.*salada|pan.*integr|pan.*molde|preparado.*torta/.test(n)) return 'Panadería';
  if (/chocolate|alfajor|caramelo|chicle|golosina|oblea|wafer|turron|tableta.*choco|nutella|dulce.*choco|chupete|bombon|mashmellow|marshmellow|gomita/.test(n)) return 'Golosinas';
  if (/papa.*frita|chizito|doritos|pringles|oreo|galletita|mani |pochoclo|tortita|palito|cheetos|ruffles|\blays\b|crackers|palitos.*salad/.test(n)) return 'Snack';
  if (/aceite.*girasol|aceite.*oliva|aceite.*maiz|aceite.*canola|aceite.*soja|aceite.*mezcla|\baceite\b/.test(n)) return 'Aceite';
  if (/sal fina|sal entrefina|azucar|pimienta|vinagre|mayonesa|ketchup|mostaza|salsa.*tomate|aderezo|oregano|curry|especias|caldo.*cubo|caldito|sopa.*sobre|sazonador|sopas/.test(n)) return 'Condimento';
  if (/atun|sardina|tomate.*triturado|pure.*tomate|conserva|choclo.*lata|arveja|lenteja.*lata|poroto.*lata|champiñon.*lata|palmito|aceitunas|alcaparra/.test(n)) return 'Enlatado';
  if (/helado|pizza.*congel|empanada.*congel|nugget|bastones.*papa|vegetal.*congel|espinaca.*congel|papa.*pre.*frita|croqueta.*congel/.test(n)) return 'Congelado';
  if (/granola|muesli|corn flakes|zucaritas|froot loop|coco pop|kellogg|nesquik|trix |copos.*maiz|copos.*trigo|copos.*arroz|cereal|avena|copos.*avena|arroz.*inflado|musli/.test(n)) return 'Cereales';
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

// Normaliza el texto quitando guiones, espacios y puntos — para hacer match "cocacola" = "coca-cola"
function normQuery(q: string): string {
  return q.replace(/[-\s.]/g, '').toLowerCase();
}

router.get('/compare', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const query    = ((req.query.query    as string) ?? '').trim();
    const category = ((req.query.category as string) ?? '').trim();
    const brand    = ((req.query.brand    as string) ?? '').trim().toLowerCase();
    const minPrice = parseFloat((req.query.minPrice as string) ?? '0') || 0;
    const maxPrice = parseFloat((req.query.maxPrice as string) ?? '0') || 0;
    const offset   = Math.max(0, parseInt((req.query.offset as string) ?? '0') || 0);
    const sortRaw  = ((req.query.sort as string) ?? '').trim().toLowerCase();
    // Safe: orderBy never interpolates user input directly
    const orderBy  = sortRaw === 'price_asc'  ? 'min_price ASC'
                   : sortRaw === 'price_desc' ? 'min_price DESC'
                   : 'product_name ASC';

    const qNorm = normQuery(query); // "coca-cola" → "cocacola"

    const catCondition = (category && CATEGORY_KEYWORDS[category])
      ? `AND LOWER(product_name) ~* '${CATEGORY_KEYWORDS[category].replace(/'/g, "''")}'`
      : '';

    // Parámetros fijos para las queries de datos
    // $1=query, $2=qNorm, $3=brand, $4=minPrice, $5=maxPrice
    const sqlParams = [query, qNorm, brand, minPrice, maxPrice];

    // WHERE compartido (búsqueda normalizada: "cocacola" matchea "coca-cola")
    const searchWhere = `
      ($1 = '' OR product_name ILIKE '%' || $1 || '%'
       OR REPLACE(REPLACE(REPLACE(LOWER(product_name), '-', ''), ' ', ''), '.', '')
          ILIKE '%' || $2 || '%')
      ${catCondition}
      AND ($3 = '' OR LOWER(brand) ILIKE '%' || $3 || '%')
    `;

    // HAVING para filtro de precio (sobre el mínimo precio del producto)
    const priceHaving = `
      ($4::numeric = 0 OR MIN(price) >= $4::numeric)
      AND ($5::numeric = 0 OR MIN(price) <= $5::numeric)
    `;

    // 1. Query principal (paginada) + count total + metadata + marcas top
    const [userResult, refResult, countResult, metaResult, brandsResult, catSampleResult] =
      await Promise.all([
        // Precios del usuario (sin paginación — pocos registros)
        pool.query(
          `SELECT DISTINCT ON (pr.name, p.supermarket)
                  pr.name AS product_name, p.supermarket,
                  pr.price AS user_price, p.purchase_date
           FROM products pr JOIN purchases p ON p.id = pr.purchase_id
           WHERE p.user_id = $1
             AND ($2 = '' OR pr.name ILIKE '%' || $2 || '%'
                  OR REPLACE(REPLACE(REPLACE(LOWER(pr.name), '-', ''), ' ', ''), '.', '')
                     ILIKE '%' || $3 || '%')
           ORDER BY pr.name, p.supermarket, p.purchase_date DESC`,
          [req.userId, query, qNorm]
        ),

        // SEPA paginado: agrupamos por producto, filtramos por precio en HAVING
        pool.query(
          `WITH filtered AS (
             SELECT product_name, MIN(price) AS min_price FROM reference_prices
             WHERE ${searchWhere}
             GROUP BY product_name
             HAVING ${priceHaving}
           ),
           paged AS (
             SELECT product_name FROM filtered
             ORDER BY ${orderBy}
             LIMIT ${PAGE_SIZE} OFFSET ${offset}
           )
           SELECT rp.product_name, rp.supermarket, rp.price, rp.brand
           FROM reference_prices rp
           JOIN paged ON paged.product_name = rp.product_name
           ORDER BY rp.product_name`,
          sqlParams
        ),

        // Total de productos distintos con los mismos filtros
        pool.query(
          `SELECT COUNT(*) as total FROM (
             SELECT product_name FROM reference_prices
             WHERE ${searchWhere}
             GROUP BY product_name
             HAVING ${priceHaving}
           ) sub`,
          sqlParams
        ),

        pool.query(`SELECT MAX(updated_at) AS lu FROM reference_prices`),

        // Top marcas (solo en primera página)
        offset === 0 ? pool.query(
          `SELECT brand, COUNT(DISTINCT product_name) as cnt
           FROM reference_prices
           WHERE ($1 = '' OR product_name ILIKE '%' || $1 || '%'
                  OR REPLACE(REPLACE(REPLACE(LOWER(product_name), '-', ''), ' ', ''), '.', '')
                     ILIKE '%' || $2 || '%')
           ${catCondition}
           AND ($3 = '' OR LOWER(brand) ILIKE '%' || $3 || '%')
           AND brand IS NOT NULL AND brand != ''
           GROUP BY brand ORDER BY cnt DESC LIMIT 20`,
          [query, qNorm, brand]
        ) : Promise.resolve({ rows: [] as any[] }),

        // Muestra para conteo por categoría (solo en primera página sin filtro de categoría)
        offset === 0 && !category ? pool.query(
          `SELECT DISTINCT product_name FROM reference_prices
           WHERE ($1 = '' OR product_name ILIKE '%' || $1 || '%'
                  OR REPLACE(REPLACE(REPLACE(LOWER(product_name), '-', ''), ' ', ''), '.', '')
                     ILIKE '%' || $2 || '%')
           AND ($3 = '' OR LOWER(brand) ILIKE '%' || $3 || '%')
           ORDER BY product_name LIMIT 4000`,
          [query, qNorm, brand]
        ) : Promise.resolve({ rows: [] as any[] }),
      ]);

    const total       = parseInt(countResult.rows[0]?.total ?? '0', 10);
    const hasMore     = offset + PAGE_SIZE < total;
    const lastUpdated = metaResult.rows[0]?.lu ?? null;
    const hasRefData  = refResult.rows.length > 0;

    // 2. Construir mapas
    const userMap: Record<string, Record<string, number>> = {};
    for (const row of userResult.rows) {
      const norm = normalize(row.product_name);
      if (!userMap[norm]) userMap[norm] = {};
      userMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.user_price);
    }

    const refMap: Record<string, Record<string, number>> = {};
    const refNameMap: Record<string, string> = {};
    const refBrandMap: Record<string, string> = {};
    for (const row of refResult.rows) {
      const norm = normalize(row.product_name);
      if (!refMap[norm]) {
        refMap[norm] = {};
        refNameMap[norm] = row.product_name;
        refBrandMap[norm] = row.brand ?? '';
      }
      refMap[norm][row.supermarket.toLowerCase()] = parseFloat(row.price);
    }

    // 3. Combinar — historial del usuario solo en primera página
    const sepaKeys = new Set(Object.keys(refMap));
    const allNorms = offset === 0
      ? new Set([...Object.keys(userMap), ...sepaKeys])
      : sepaKeys;

    const comparisons = Array.from(allNorms).map(norm => {
      const userRow    = userResult.rows.find(r => normalize(r.product_name) === norm);
      const rawName    = userRow?.product_name ?? refNameMap[norm] ?? norm;
      const productName = cleanProductName(rawName); // ← nombre limpio
      const cat        = detectCategory(rawName);

      if (category && cat !== category) return null;

      const prices: Record<string, { price: number; isUserData: boolean }> = {};
      for (const [s, p] of Object.entries(userMap[norm] ?? {}))
        prices[s] = { price: p, isUserData: true };
      for (const [s, p] of Object.entries(refMap[norm] ?? {}))
        if (!prices[s]) prices[s] = { price: p, isUserData: false };

      const priceList = Object.entries(prices)
        .map(([supermarket, d]) => ({ supermarket, price: d.price, isUserData: d.isUserData }))
        .sort((a, b) => a.price - b.price);

      if (priceList.length === 0) return null;

      const cheapest = priceList[0];
      const priciest = priceList[priceList.length - 1];
      const savings  = priceList.length > 1 ? priciest.price - cheapest.price : 0;

      return {
        productName,
        brand:         refBrandMap[norm] ?? '',
        category:      cat,
        prices:        priceList,
        cheapestAt:    cheapest.supermarket,
        cheapestPrice: cheapest.price,
        maxSavings:    savings,
        savingsPct:    priceList.length > 1 ? Math.round((savings / priciest.price) * 100) : 0,
      };
    }).filter(Boolean);

    if (sortRaw === 'price_asc') {
      comparisons.sort((a: any, b: any) => a.cheapestPrice - b.cheapestPrice);
    } else if (sortRaw === 'price_desc') {
      comparisons.sort((a: any, b: any) => b.cheapestPrice - a.cheapestPrice);
    } else {
      comparisons.sort((a: any, b: any) => {
        const diff = (b.prices.length > 1 ? 1 : 0) - (a.prices.length > 1 ? 1 : 0);
        return diff !== 0 ? diff : b.maxSavings - a.maxSavings;
      });
    }

    // 4. Conteos por categoría (desde muestra)
    let categoryCounts: Record<string, number> = {};
    for (const row of catSampleResult.rows) {
      const cat = detectCategory(row.product_name);
      categoryCounts[cat] = (categoryCounts[cat] ?? 0) + 1;
    }

    // 5. Lista de marcas top
    const brands = brandsResult.rows.map((r: any) => r.brand as string).filter(Boolean);

    res.json({
      comparisons,
      total,
      hasMore,
      offset,
      brands,
      source:         hasRefData ? 'SEPA - preciosclaros.gob.ar' : 'historial_usuario',
      lastUpdated:    lastUpdated ?? null,
      isEmpty:        !hasRefData && userResult.rows.length === 0,
      categoryCounts: offset === 0 && !category ? categoryCounts : {},
    });
  } catch (err: any) {
    console.error('Error en /prices/compare:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

// GET /prices/cheapest-summary — resumen de qué supermercado conviene en general,
// calculado de forma determinística; Gemini solo redacta el texto final.
router.get('/cheapest-summary', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const stats = await computeCheapestSummary(pool);
    if (!stats) {
      res.json({ isEmpty: true });
      return;
    }

    // Texto de respaldo (siempre válido) en caso de que Gemini no esté disponible o falle.
    let headline = `${stats.cheapestSupermarket} tiene los precios más bajos en ${stats.productsWon} de ${stats.productsCompared} productos comparados. Podrías ahorrar hasta $${stats.totalSavings.toFixed(2)} (${stats.avgSavingsPct}% en promedio).`;

    const apiKey = process.env.GEMINI_API_KEY;
    if (apiKey) {
      try {
        const prompt = `Redactá 1 o 2 frases cortas y amigables en español sobre estos datos de precios de supermercado. NO inventes ni modifiques los números, usalos tal cual:
- Supermercado más barato en general: ${stats.cheapestSupermarket}
- Gana en ${stats.productsWon} de ${stats.productsCompared} productos comparados
- Ahorro total estimado: $${stats.totalSavings.toFixed(2)}
- Ahorro promedio: ${stats.avgSavingsPct}%
Devolvé únicamente el texto, sin markdown ni comillas.`;

        const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=${apiKey}`;
        const response = await fetch(geminiUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [{ parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.4, maxOutputTokens: 150 }
          })
        });

        if (response.ok) {
          const data = await response.json() as any;
          const text = data?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
          if (text) headline = text;
        } else {
          console.error('Gemini cheapest-summary error:', await response.text());
        }
      } catch (geminiErr) {
        console.error('Error generando headline con Gemini:', geminiErr);
        // Sigue con el headline de respaldo armado arriba — no rompe la respuesta.
      }
    }

    res.json({ ...stats, isEmpty: false, headline });
  } catch (err: any) {
    console.error('Error en /prices/cheapest-summary:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
