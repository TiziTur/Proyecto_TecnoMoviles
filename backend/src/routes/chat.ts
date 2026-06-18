// chat.ts — Chat IA sobre el historial de compras del usuario usando Gemini.
// El backend construye un contexto rico con todos los datos del usuario y lo manda a Gemini.
// Gemini actúa como asistente financiero personal que conoce el historial completo.
// POST /chat  — Body: { message: string, history?: { role, text }[] }
import { Router, Response } from 'express';
import pool from '../db';
import { authMiddleware, AuthRequest } from '../middleware/auth';

const router = Router();
router.use(authMiddleware);

router.post('/', async (req: AuthRequest, res: Response): Promise<void> => {
  const { message, history = [] } = req.body;

  if (!message?.trim()) {
    res.status(400).json({ error: 'Se requiere un mensaje' });
    return;
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    res.status(503).json({ error: 'Gemini API key no configurada' });
    return;
  }

  try {
    // Cargar el historial completo del usuario desde la DB para darle contexto a Gemini
    const purchasesResult = await pool.query(
      `SELECT p.id, p.purchase_date, p.purchase_time, p.supermarket, p.total,
              json_agg(json_build_object(
                'name', pr.name,
                'price', pr.price,
                'quantity', pr.quantity,
                'code', pr.code
              ) ORDER BY pr.id) FILTER (WHERE pr.id IS NOT NULL) as products
       FROM purchases p
       LEFT JOIN products pr ON pr.purchase_id = p.id
       WHERE p.user_id = $1
       GROUP BY p.id
       ORDER BY p.purchase_date DESC, p.purchase_time DESC
       LIMIT 100`,
      [req.userId]
    );

    const purchases = purchasesResult.rows;
    const totalGastado = purchases.reduce((s: number, p: any) => s + parseFloat(p.total || 0), 0);
    const supermercados = [...new Set(purchases.map((p: any) => p.supermarket))];

    // Armar el contexto para Gemini
    const contextSummary = purchases.slice(0, 20).map((p: any) => {
      const prods = (p.products || [])
        .map((pr: any) => `  - ${pr.name} x${pr.quantity} @ $${pr.price}`)
        .join('\n');
      return `Compra #${p.id} en ${p.supermarket} el ${p.purchase_date}: $${parseFloat(p.total).toFixed(2)}\n${prods || '  (sin productos detallados)'}`;
    }).join('\n\n');

    const systemPrompt = `Sos un asistente financiero personal integrado en la app Klarity, que ayuda a los usuarios a entender y optimizar sus gastos de supermercado.

DATOS DEL USUARIO:
- Total de compras registradas: ${purchases.length}
- Gasto total histórico: $${totalGastado.toFixed(2)}
- Supermercados visitados: ${supermercados.join(', ') || 'ninguno aún'}

HISTORIAL RECIENTE (últimas 20 compras):
${contextSummary || 'El usuario aún no tiene compras registradas.'}

INSTRUCCIONES:
- Respondé en español, de manera amigable y concisa
- Cuando menciones montos, usá el formato $ con número (ej: $15.000)
- Si el usuario pregunta algo que no podés responder con los datos, decíselo claramente
- No inventes datos que no estén en el historial
- Podés hacer cálculos, comparar gastos, identificar patrones, sugerir ahorros
- Máximo 200 palabras en la respuesta`;

    // Construir el array de contenidos con historial de conversación
    const contents = [
      { role: 'user', parts: [{ text: systemPrompt }] },
      { role: 'model', parts: [{ text: '¡Hola! Soy tu asistente financiero de Klarity. Ya cargué tu historial de compras y estoy listo para ayudarte a entender y mejorar tus gastos. ¿Qué querés saber?' }] },
      ...history.map((h: { role: string; text: string }) => ({
        role: h.role,
        parts: [{ text: h.text }]
      })),
      { role: 'user', parts: [{ text: message }] }
    ];

    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=${apiKey}`;

    const response = await fetch(geminiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents,
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 512
        }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      console.error('Gemini chat error:', errText);
      res.status(502).json({ error: 'Error al contactar Gemini' });
      return;
    }

    const data = await response.json() as any;
    const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? 'No pude generar una respuesta.';

    res.json({ reply });

  } catch (err: any) {
    console.error('Error en chat:', err);
    res.status(500).json({ error: err.message ?? 'Error interno' });
  }
});

export default router;
