// ticket.ts — Endpoint para escanear un ticket de supermercado con Grok Vision (xAI).
// Recibe una o más imágenes en base64 (fragmentos consecutivos de un mismo ticket largo),
// las envía juntas a Grok con un prompt estructurado y devuelve los productos parseados
// listos para guardar.
// Montado en: POST /purchases/:id/scan-ticket
import { Router, Response } from 'express';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router({ mergeParams: true });
router.use(authMiddleware);

interface ParsedProduct {
  name: string;
  price: number;
  quantity: number;
  code?: string;
  description?: string;
  category?: string;
}

// Un ticket nunca tiene el mismo producto en dos entradas separadas — si aparece más de una vez
// (normalmente porque el ticket vino en 2+ fotos y Gemini no dedupe perfecto el solapamiento entre
// fotos, a pesar de que el prompt se lo pide explícitamente), lo correcto es una sola entrada con
// la cantidad sumada, nunca dos líneas del mismo producto. No confiamos esto solo al prompt: se
// fuerza acá de forma determinística.
function mergeDuplicateProducts(products: ParsedProduct[]): ParsedProduct[] {
  const merged = new Map<string, ParsedProduct>();
  for (const p of products) {
    const key = p.code && p.code.trim() !== ''
      ? `code:${p.code.trim()}`
      : `name:${p.name.trim().toLowerCase()}`;
    const existing = merged.get(key);
    if (existing) {
      existing.quantity += p.quantity;
    } else {
      merged.set(key, { ...p });
    }
  }
  return Array.from(merged.values());
}

// POST /purchases/:purchaseId/scan-ticket
// Body: { images: Array<{ imageBase64: string, mimeType?: "image/jpeg" | "image/png" }> }
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const { images } = req.body;

  if (!Array.isArray(images) || images.length === 0) {
    res.status(400).json({ error: 'Se requiere images como un array no vacío' });
    return;
  }

  const apiKey = process.env.XAI_API_KEY;
  if (!apiKey) {
    res.status(503).json({ error: 'xAI API key no configurada en el servidor' });
    return;
  }

  const prompt = `Analizá este ticket de supermercado y extraé la lista de productos.
A continuación se incluyen una o más fotos. Si hay más de una imagen, son fragmentos consecutivos de UN MISMO ticket de supermercado muy largo que no entraba en una sola foto — analizalas todas juntas como si fueran un solo documento continuo, en el orden en que se presentan. Si dos fotos se solapan levemente y un mismo producto aparece visible en ambas, contalo UNA SOLA VEZ en la lista final.

IMPORTANTE — cómo está estructurado cada renglón de producto en este tipo de ticket (tickets argentinos de supermercado, formato AFIP):
Cada producto comprado ocupa VARIAS líneas consecutivas, no una sola. Un bloque típico de un producto se ve así:
  NOMBRE DEL PRODUCTO
  CODIGO_DE_BARRAS (XX.XX%)              PRECIO_TOTAL_DE_LINEA
  CANTIDAD x PRECIO_UNITARIO
  [línea opcional de descuento — cualquier línea de texto que termine en un monto negativo "$-NNNN.NN"]
Todas esas líneas (nombre, código+IVA, cantidad x precio, y el descuento si existe) son UN SOLO producto — agrupalas en una sola entrada del JSON, nunca generes una entrada separada por cada línea.
La línea "CANTIDAD x PRECIO_UNITARIO" a veces aparece ANTES del nombre del producto en vez de después; sigue perteneciendo al mismo bloque/producto, no es un producto aparte.

SOBRE EL (XX.XX%) JUNTO AL CÓDIGO DE BARRAS — ES IVA, NO ES UN DESCUENTO:
El número entre paréntesis con % que aparece junto al código de barras de CADA producto (ej: "(21.00%)", "(10.50%)", "(0.00%)") es la TASA DE IVA de ese producto, ya incluida en el precio que se muestra. Es un dato informativo fiscal, igual para casi todos los productos del ticket. NUNCA lo restes, lo sumes, ni lo uses para calcular nada — ignoralo por completo al calcular price. Solo existe UN tipo de número-con-% que SÍ hay que restar: el de una línea de DESCUENTO real, que es una línea APARTE (no la del código de barras) y que siempre termina en un monto negativo en pesos ("$-NNNN.NN"), no solo un porcentaje suelto.

SOBRE LAS LÍNEAS DE DESCUENTO:
Una línea de descuento puede tener cualquier texto (promociones bancarias, "2x1 Marca", "X% OFF ...", "2DA UNIDAD AL 50%", "50% NOMBRE_DE_CATEGORIA", códigos de promoción, etc.) — el texto exacto varía y NO hay que memorizar frases fijas. La señal inequívoca de que una línea es un descuento (y no un producto ni la línea de IVA) es que termina en un monto en pesos con signo negativo, con el patrón "$-" seguido de números, normalmente ubicada inmediatamente debajo de la línea de código de barras + IVA de un producto, o debajo de su línea de cantidad x precio unitario. Esa línea SIEMPRE se resta del producto inmediatamente anterior (nunca del siguiente), sin importar qué dice el texto de la promoción. Si ves una línea así, el price final de ese producto es (precio_total_de_linea + monto_negativo_de_la_linea_de_descuento) / cantidad. Revisá CADA producto del ticket buscando si tiene una línea de descuento debajo — es un error común pasarla por alto en productos caros como indumentaria, no solo en alimentos. Una línea de descuento puede empezar con un porcentaje (ej: "50% INDUMENTARIA -$20599.50") y aun así NO ser la línea de IVA — la diferencia es que la línea de IVA está pegada al código de barras y termina en el precio TOTAL (positivo) de la línea, mientras que la línea de descuento es una línea separada que termina en un monto NEGATIVO ("$-...").
Ejemplo genérico para no confundir ambos casos:
  REMERA TALLE M
  1234567890123 (21.00%)                9000.00     → (21.00%) es IVA, se ignora; price provisorio = 9000.00
  50% INDUMENTARIA -$4500.00                          → esto SÍ es un descuento (termina en $-monto): price final = 9000.00 - 4500.00 = 4500.00

OJO: algunas líneas de descuento (promociones bancarias/bancos, tarjetas, programas de puntos) mencionan DOS montos en pesos en la misma línea, por ejemplo "$1299 PROGRAMA X $-1600.00" — un monto de referencia/umbral del programa (sin signo negativo, en este ejemplo "$1299") y el descuento real al final (siempre con signo negativo, en este ejemplo "$-1600.00"). En esos casos usá SIEMPRE el último monto de la línea, el que tiene el signo negativo "$-", e ignorá cualquier otro monto en pesos que aparezca antes en esa misma línea — no es parte del cálculo.

Ignorá por completo líneas que son código de barras suelto, porcentaje de IVA suelto, subtotal, total, "Régimen de Transparencia Fiscal", impuestos nacionales, vuelto, CAE, QR, o cualquier línea sin un nombre de producto real asociado. Nunca inventes un producto genérico como "Producto" para una línea que no puedas identificar — si no podés leer bien un producto, omitilo en vez de inventar un nombre o precio placeholder.
Si el nombre impreso de un producto es parcialmente ilegible (letras borrosas, cortadas, etc.) pero podés leer su código de barras y su precio con claridad, NUNCA reemplaces el nombre por el de otro producto de una categoría distinta que "suene parecido" o que te parezca plausible — eso es peor que no saberlo. En ese caso usá como name el código de barras tal como aparece impreso (ej: "Producto 7799120000993"), manteniendo price, quantity y code correctos. Solo escribí un nombre de producto real cuando puedas leerlo con razonable certeza letra por letra.

SOBRE FOTOS QUE CONTINÚAN UNA A LA OTRA:
Cuando el ticket viene en varias fotos, es común que la última foto termine a mitad de un producto y la siguiente foto empiece repitiendo ese mismo producto desde el principio (para no perder esa línea). Antes de armar la lista final, compará los últimos 2-3 productos de una foto contra los primeros 2-3 productos de la foto siguiente: si coinciden en nombre y/o código de barras (aunque el recorte de la imagen sea distinto), es el MISMO producto fotografiado dos veces — incluilo una sola vez, usando la versión más completa (la que tenga más líneas legibles).

SOBRE PRODUCTOS PESADOS (carne, fiambre, verdura, etc., vendidos por kilo):
En estos productos la línea "CANTIDAD x PRECIO_UNITARIO" muestra un peso con decimales (ej: "1.270 x 16199.0000", "0.218 x 19999.0000"), no una cantidad de unidades. El campo quantity de la respuesta SOLO puede ser un número entero de unidades compradas — NUNCA escribas ahí un peso en kilos. Para estos productos pesados, siempre poné quantity = 1 y price = el PRECIO_TOTAL_DE_LINEA tal como figura impreso (el número grande a la derecha del código de barras, ya con descuento si tiene), sin dividir ni multiplicar por el peso. No intentes calcular un "precio por unidad" para estos productos: no existe, se pagó por peso.

ANTES DE RESPONDER — revisión de cordura:
Repasá la lista completa de precios que vas a devolver. Si alguno te quedó con un dígito de más o de menos respecto al resto (por ejemplo 140649.00 en una lista donde los demás precios están entre 100 y 50000, probablemente el punto decimal está mal puesto y es 14064.90), corregilo. Ningún producto de supermercado (que no sea indumentaria o electrodomésticos) debería superar los $100000.

Devolvé ÚNICAMENTE un JSON válido con este formato exacto, sin texto adicional ni markdown:
{
  "supermarket": "nombre del supermercado si es visible, sino null",
  "date": "fecha en formato YYYY-MM-DD si es visible, sino null",
  "products": [
    {
      "name": "descripción EXACTA del producto tal como aparece en el ticket (ej: Coca-Cola 500ml, Leche La Serenísima Entera 1L)",
      "price": precio_unitario_como_numero,
      "quantity": cantidad_como_entero,
      "code": "codigo_de_barras_si_visible_sino_string_vacio",
      "description": "descripcion_adicional_sino_string_vacio",
      "category": "una de: Alimento, Bebida, Lácteo, Carne, Limpieza, Perfumería, Snack, Congelado, Panadería, Otro"
    }
  ]
}
Reglas:
- name debe ser la descripción EXACTA del producto como figura en el ticket, incluyendo marca, tamaño y variedad
- price debe ser el precio UNITARIO ya con cualquier descuento de esa línea aplicado (no el total de línea, no el precio sin descuento)
- el porcentaje entre paréntesis junto al código de barras es IVA, no se resta nunca; solo se resta una línea de descuento aparte que termine en "$-monto"
- Si el ticket muestra "2x $500" el price es 500 y quantity es 2
- category: Bebida para gaseosas/jugos/aguas/cervezas; Lácteo para leche/yogur/queso; Limpieza para detergentes/lavandina; Perfumería para higiene personal; Snack para golosinas/papas fritas; Alimento para el resto de comestibles
- Si no podés leer bien un producto, omitilo
- Una entrada del JSON por producto comprado, nunca una entrada por línea de texto. No incluyas descuentos, subtotales, impuestos ni líneas sin nombre de producto como entradas propias`;

  try {
    // Grok 2 Vision (xAI) — API compatible con OpenAI. Free tier sin el límite de 20 req/día
    // de Gemini flash. grok-2-vision-1212 es el modelo con mejor capacidad OCR de imágenes.
    const grokUrl = 'https://api.x.ai/v1/chat/completions';

    const messageContent: any[] = [
      { type: 'text', text: prompt },
      ...images.map((img: { imageBase64: string; mimeType?: string }) => ({
        type: 'image_url',
        image_url: {
          url: `data:${img.mimeType ?? 'image/jpeg'};base64,${img.imageBase64}`
        }
      }))
    ];

    const grokBody = {
      model: 'grok-2-vision',
      messages: [{ role: 'user', content: messageContent }],
      temperature: 0,
      max_tokens: 16000
    };

    const fetchGrok = () => fetch(grokUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`
      },
      body: JSON.stringify(grokBody)
    });

    let grokResponse = await fetchGrok();
    let attempt = 1;
    const maxAttempts = 3;
    while (!grokResponse.ok && (grokResponse.status === 503 || grokResponse.status === 429) && attempt < maxAttempts) {
      console.warn(`Grok ${grokResponse.status}, reintentando (${attempt}/${maxAttempts})...`);
      await new Promise(r => setTimeout(r, attempt * 1500));
      attempt++;
      grokResponse = await fetchGrok();
    }

    if (!grokResponse.ok) {
      const errText = await grokResponse.text();
      console.error('Grok error:', errText);
      res.status(502).json({ error: 'Error al contactar Grok Vision' });
      return;
    }
    const grokData = await grokResponse.json() as any;
    const rawText: string = grokData?.choices?.[0]?.message?.content ?? '';

    // Limpiar markdown si Grok lo agregó (```json ... ```)
    const cleanText = rawText
      .replace(/```json\n?/g, '')
      .replace(/```\n?/g, '')
      .trim();

    let parsed: { supermarket?: string; date?: string; products: ParsedProduct[] };
    try {
      parsed = JSON.parse(cleanText);
    } catch {
      console.error('Grok devolvió texto no parseable:', cleanText);
      res.status(422).json({
        error: 'No se pudo interpretar la respuesta de Grok',
        rawText
      });
      return;
    }

    const products: ParsedProduct[] = mergeDuplicateProducts(
      (parsed.products ?? []).filter(
        p => p.name && typeof p.price === 'number' && p.price > 0
      )
    );

    res.json({
      supermarket: parsed.supermarket ?? null,
      date:        parsed.date ?? null,
      products
    });

  } catch (err: any) {
    console.error('Error en scan-ticket:', err);
    res.status(500).json({ error: err.message ?? 'Error interno del servidor' });
  }
});

export default router;
