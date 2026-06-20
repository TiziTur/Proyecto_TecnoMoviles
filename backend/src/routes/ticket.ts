// ticket.ts — Endpoint para escanear un ticket de supermercado con Gemini Vision.
// Recibe una o más imágenes en base64 (fragmentos consecutivos de un mismo ticket largo),
// las envía juntas a Gemini con un prompt estructurado y devuelve los productos parseados
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

// POST /purchases/:purchaseId/scan-ticket
// Body: { images: Array<{ imageBase64: string, mimeType?: "image/jpeg" | "image/png" }> }
router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const { images } = req.body;

  if (!Array.isArray(images) || images.length === 0) {
    res.status(400).json({ error: 'Se requiere images como un array no vacío' });
    return;
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    res.status(503).json({ error: 'Gemini API key no configurada en el servidor' });
    return;
  }

  const prompt = `Analizá este ticket de supermercado y extraé la lista de productos.
A continuación se incluyen una o más fotos. Si hay más de una imagen, son fragmentos consecutivos de UN MISMO ticket de supermercado muy largo que no entraba en una sola foto — analizalas todas juntas como si fueran un solo documento continuo, en el orden en que se presentan. Si dos fotos se solapan levemente y un mismo producto aparece visible en ambas, contalo UNA SOLA VEZ en la lista final.
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
- price debe ser el precio UNITARIO (no total de linea)
- Si el ticket muestra "2x $500" el price es 500 y quantity es 2
- category: Bebida para gaseosas/jugos/aguas/cervezas; Lácteo para leche/yogur/queso; Limpieza para detergentes/lavandina; Perfumería para higiene personal; Snack para golosinas/papas fritas; Alimento para el resto de comestibles
- Si no podés leer bien un producto, omitilo
- Solo incluí items que son productos comprados, no descuentos ni subtotales`;

  try {
    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=${apiKey}`;

    const geminiBody = {
      contents: [{
        parts: [
          { text: prompt },
          ...images.map((img: { imageBase64: string; mimeType?: string }) => ({
            inline_data: {
              mime_type: img.mimeType ?? 'image/jpeg',
              data: img.imageBase64
            }
          }))
        ]
      }],
      generationConfig: {
        temperature: 0.1,
        maxOutputTokens: 4096
      }
    };

    const response = await fetch(geminiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(geminiBody)
    });

    if (!response.ok) {
      const errText = await response.text();
      console.error('Gemini error:', errText);
      res.status(502).json({ error: 'Error al contactar Gemini Vision' });
      return;
    }

    const geminiData = await response.json() as any;
    const rawText: string = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';

    // Limpiar markdown si Gemini lo agregó (```json ... ```)
    const cleanText = rawText
      .replace(/```json\n?/g, '')
      .replace(/```\n?/g, '')
      .trim();

    let parsed: { supermarket?: string; date?: string; products: ParsedProduct[] };
    try {
      parsed = JSON.parse(cleanText);
    } catch {
      console.error('Gemini devolvió texto no parseable:', cleanText);
      res.status(422).json({
        error: 'No se pudo interpretar la respuesta de Gemini',
        rawText
      });
      return;
    }

    const products: ParsedProduct[] = (parsed.products ?? []).filter(
      p => p.name && typeof p.price === 'number' && p.price > 0
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
