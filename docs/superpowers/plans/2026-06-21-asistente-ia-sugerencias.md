# Asistente IA: sugerencias de compra más barata — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar al asistente IA (chat) y a la pantalla Home la capacidad de sugerir dónde
conviene comprar, basándose en la comparativa de precios SEPA ya existente.

**Architecture:** Un helper de backend (`computeCheapestSummary`) calcula de forma
determinística qué supermercado tiene los precios más bajos en general; lo consumen dos
rutas: un endpoint nuevo (`GET /prices/cheapest-summary`, usado por una tarjeta en Home)
y el endpoint de chat existente (que lo inyecta como contexto adicional). En ambos casos
los números son siempre calculados en SQL/JS — Gemini solo redacta texto a partir de
ellos, nunca los inventa.

**Tech Stack:** Backend Node/Express/TypeScript + `pg` (Postgres) + Gemini
`gemini-2.5-flash-lite` via `fetch`. Android Kotlin/Jetpack Compose/Hilt/Retrofit+Gson.

**Spec:** `docs/superpowers/specs/2026-06-21-asistente-ia-sugerencias-design.md`

**Convención de testing de este repo:** no hay framework de tests automatizados (ni
Jest/Mocha en el backend, ni tests de UI en Android). La verificación es
`npx tsc --noEmit` + `curl` manual en el backend, y `./gradlew :app:compileDebugKotlin`
+ prueba manual en el emulador en Android. Todos los pasos de "test" de este plan siguen
esa convención existente — no se introduce un framework nuevo.

---

### Task 1: Helper de cálculo agregado (`backend/src/lib/cheapestSummary.ts`)

**Files:**
- Create: `backend/src/lib/cheapestSummary.ts`

- [ ] **Step 1: Crear el helper**

```ts
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
```

- [ ] **Step 2: Verificar que compila**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores (el archivo no se usa todavía en ningún lado, pero debe compilar
de forma aislada — sin imports rotos, sin tipos inconsistentes).

- [ ] **Step 3: Commit**

```bash
git add backend/src/lib/cheapestSummary.ts
git commit -m "feat: helper para calcular el supermercado mas barato en general"
```

---

### Task 2: Endpoint `GET /prices/cheapest-summary`

**Files:**
- Modify: `backend/src/routes/prices.ts`

- [ ] **Step 1: Agregar el import y la ruta**

Al principio de `backend/src/routes/prices.ts`, junto a los imports existentes (línea 3-5):

```ts
import { computeCheapestSummary } from '../lib/cheapestSummary';
```

Al final del archivo, antes de `export default router;` (después del cierre de la ruta
`/compare` existente, que termina en `});` seguido de una línea vacía):

```ts
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
```

- [ ] **Step 2: Verificar que compila**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 3: Probar manualmente con curl**

Con el servidor corriendo localmente (`npm run dev` en `backend/`) y un token JWT válido
de un usuario ya logueado:

```bash
curl -s http://localhost:3000/prices/cheapest-summary -H "Authorization: Bearer <TOKEN>"
```

Expected: JSON con `cheapestSupermarket`, `productsCompared`, `productsWon`,
`totalSavings`, `avgSavingsPct`, `isEmpty: false`, `headline` (una o dos frases en
español). Si `reference_prices` está vacía o tiene un solo supermercado, debe responder
`{"isEmpty":true}` sin tirar error 500.

- [ ] **Step 4: Commit**

```bash
git add backend/src/routes/prices.ts
git commit -m "feat: endpoint cheapest-summary para sugerir donde conviene comprar"
```

---

### Task 3: El chat gana contexto de precios

**Files:**
- Modify: `backend/src/routes/chat.ts`

- [ ] **Step 1: Agregar el import**

Junto a los imports existentes de `chat.ts` (línea 5-7):

```ts
import { computeCheapestSummary } from '../lib/cheapestSummary';
```

- [ ] **Step 2: Calcular el contexto de precios antes de armar el `systemPrompt`**

Justo después del bloque que calcula `contextSummary` (después de la línea que termina
con `}).join('\n\n');`, antes de `const systemPrompt = ...`), agregar:

```ts
    // Contexto de precios agregado — si falla, el chat sigue funcionando sin él
    // (no debe romper la respuesta del chat por un problema en este cálculo aparte).
    let priceContext = '';
    try {
      const cheapestStats = await computeCheapestSummary(pool);
      if (cheapestStats) {
        priceContext = `\n\nDATOS GENERALES DE PRECIOS (SEPA, comparativa entre supermercados):
- De ${cheapestStats.productsCompared} productos comparados, ${cheapestStats.cheapestSupermarket} tiene el precio más bajo en ${cheapestStats.productsWon} de ellos.
- Ahorro promedio estimado eligiendo ${cheapestStats.cheapestSupermarket} en esos productos: ${cheapestStats.avgSavingsPct}%.
- Podés usar este dato general para recomendar dónde conviene comprar, además del historial del usuario.`;
      }
    } catch (priceErr) {
      console.error('Error calculando contexto de precios para el chat:', priceErr);
      // priceContext queda en '' — el chat sigue funcionando solo con el historial.
    }
```

- [ ] **Step 3: Insertar `priceContext` en el `systemPrompt`**

En el template literal de `systemPrompt` (línea 57-73), agregar `${priceContext}`
inmediatamente después del bloque `HISTORIAL RECIENTE` y antes de `INSTRUCCIONES:`:

```ts
    const systemPrompt = `Sos un asistente financiero personal integrado en la app Klarity, que ayuda a los usuarios a entender y optimizar sus gastos de supermercado.

DATOS DEL USUARIO:
- Total de compras registradas: ${purchases.length}
- Gasto total histórico: $${totalGastado.toFixed(2)}
- Supermercados visitados: ${supermercados.join(', ') || 'ninguno aún'}

HISTORIAL RECIENTE (últimas 20 compras):
${contextSummary || 'El usuario aún no tiene compras registradas.'}
${priceContext}

INSTRUCCIONES:
- Respondé en español, de manera amigable y concisa
- Cuando menciones montos, usá el formato $ con número (ej: $15.000)
- Si el usuario pregunta algo que no podés responder con los datos, decíselo claramente
- No inventes datos que no estén en el historial
- Podés hacer cálculos, comparar gastos, identificar patrones, sugerir ahorros
- Máximo 200 palabras en la respuesta`;
```

(Solo cambia el bloque entre `HISTORIAL RECIENTE` e `INSTRUCCIONES:` — el resto del
prompt queda igual.)

- [ ] **Step 4: Verificar que compila**

Run: `cd backend && npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 5: Probar manualmente con curl**

```bash
curl -s http://localhost:3000/chat -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"message": "¿Dónde conviene comprar este mes?"}'
```

Expected: `{"reply": "..."}` donde la respuesta menciona un supermercado concreto y datos
de ahorro (no solo "no tengo esa información"). Repetir con `reference_prices` vacía (o
sin `GEMINI_API_KEY`) para confirmar que el chat sigue respondiendo (sin el contexto de
precios, pero sin romperse).

- [ ] **Step 6: Commit**

```bash
git add backend/src/routes/chat.ts
git commit -m "feat: el chat usa contexto de precios SEPA para sugerir donde comprar"
```

---

### Task 4: DTO Android (`CheapestSummaryResponse`)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt`

- [ ] **Step 1: Agregar el DTO**

Al final de `AiDtos.kt` (después de `SeedSearchResultDto`, línea 125-128):

```kotlin

// ── Sugerencia: donde conviene comprar en general ────────────────

data class CheapestSummaryResponse(
    @SerializedName("isEmpty")             val isEmpty: Boolean = false,
    @SerializedName("cheapestSupermarket") val cheapestSupermarket: String = "",
    @SerializedName("productsCompared")    val productsCompared: Int = 0,
    @SerializedName("productsWon")         val productsWon: Int = 0,
    @SerializedName("totalSavings")        val totalSavings: Double = 0.0,
    @SerializedName("avgSavingsPct")       val avgSavingsPct: Int = 0,
    @SerializedName("headline")            val headline: String = ""
)
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/dto/AiDtos.kt
git commit -m "feat: DTO CheapestSummaryResponse para la sugerencia de compra"
```

---

### Task 5: Endpoint Retrofit (`ApiService.kt`)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt`

- [ ] **Step 1: Agregar el endpoint**

Justo después de `getPriceComparisons` (línea 108-118), antes del comentario
`// ── Comparativa de compra completa contra SEPA ────────────`:

```kotlin
    @GET("prices/cheapest-summary")
    suspend fun getCheapestSummary(
        @Header("Authorization") token: String
    ): Response<CheapestSummaryResponse>

```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/network/ApiService.kt
git commit -m "feat: endpoint Retrofit getCheapestSummary"
```

---

### Task 6: `HomeUiState` gana los campos del resumen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/state/HomeUiState.kt`

- [ ] **Step 1: Agregar los campos**

```kotlin
package com.undef.superahorroturina.ui.state

import com.undef.superahorroturina.data.network.dto.CheapestSummaryResponse
import com.undef.superahorroturina.model.Purchase

// Estado de UI para la pantalla Home.
// Separado del ViewModel para seguir el principio de separación de responsabilidades:
// el ViewModel maneja lógica, el UiState solo transporta datos hacia la UI.
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val userName: String = "",
    val totalThisMonth: Double = 0.0,
    val monthlyLimit: Float = 50_000f,
    val recentPurchases: List<Purchase> = emptyList(),
    val purchaseCount: Int = 0,
    val supermarketCount: Int = 0,
    val cheapestSummary: CheapestSummaryResponse? = null,
    val cheapestSummaryLoading: Boolean = true,
    val cheapestSummaryError: Boolean = false
)
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/state/HomeUiState.kt
git commit -m "feat: HomeUiState incluye el resumen de donde conviene comprar"
```

---

### Task 7: `HomeViewModel` carga el resumen

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: Agregar imports y la dependencia de `ApiService`**

Reemplazar los imports (líneas 1-15) por:

```kotlin
package com.undef.superahorroturina.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
```

Y el constructor (línea 17-22):

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val sessionDataStore: SessionDataStore,
    private val themeDataStore: ThemeDataStore,
    private val api: ApiService
) : ViewModel() {
```

- [ ] **Step 2: Disparar la carga del resumen en `init()`**

Dentro del bloque `init { ... }` existente (línea 27-54), después del segundo
`viewModelScope.launch` (el que observa `themeDataStore.monthlyLimit`) y antes de la
llamada a `loadData()`, agregar un tercer `viewModelScope.launch` independiente:

```kotlin
        viewModelScope.launch {
            try {
                val token = sessionDataStore.bearerToken.first()
                val response = api.getCheapestSummary(token)
                if (response.isSuccessful) {
                    val body = response.body()
                    _uiState.value = _uiState.value.copy(
                        cheapestSummary        = body,
                        cheapestSummaryLoading = false,
                        cheapestSummaryError   = body == null || body.isEmpty
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        cheapestSummaryLoading = false,
                        cheapestSummaryError   = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cheapestSummaryLoading = false,
                    cheapestSummaryError   = true
                )
            }
        }
        loadData()
    }
```

(Este bloque reemplaza la línea final `loadData()` del `init` — queda como el último
`launch` antes de cerrar el `init`, seguido de la llamada a `loadData()` igual que antes.)

- [ ] **Step 3: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeViewModel.kt
git commit -m "feat: HomeViewModel carga el resumen de donde conviene comprar"
```

---

### Task 8: Tarjeta en `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: Agregar la tarjeta después de la fila de `AiFeatureCard`**

Después del bloque `item { Row(...) { AiFeatureCard(...) AiFeatureCard(...) } }` (que
termina en la línea 377 con `}`), y antes de `// Recent purchases section` (línea 379),
insertar:

```kotlin
                        item {
                            CheapestSummaryCard(
                                loading  = uiState.cheapestSummaryLoading,
                                error    = uiState.cheapestSummaryError,
                                headline = uiState.cheapestSummary?.headline,
                                onClick  = onNavigateToPriceComparison
                            )
                        }
```

- [ ] **Step 2: Agregar el composable `CheapestSummaryCard`**

Justo antes de `// ── AiFeatureCard ─────...` (línea 517), agregar:

```kotlin
// ── CheapestSummaryCard ──────────────────────────────────────

@Composable
private fun CheapestSummaryCard(
    loading: Boolean,
    error: Boolean,
    headline: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = "¿Dónde conviene comprar este mes?",
                    style = MaterialTheme.typography.titleSmall
                )
                when {
                    loading -> Text(
                        text  = "Calculando…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    error || headline.isNullOrBlank() -> Text(
                        text  = "Aún no hay suficientes datos para calcularlo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        text     = headline,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}
```

`Icons.Default.TipsAndUpdates` ya está disponible vía el import existente
`androidx.compose.material.icons.filled.*` (línea 18 del archivo) — no requiere un
import nuevo.

- [ ] **Step 3: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Probar manualmente**

Con el backend corriendo y accesible desde el emulador/dispositivo: abrir Home y
confirmar que la tarjeta "¿Dónde conviene comprar este mes?" aparece, muestra
"Calculando…" brevemente y después el `headline` real. Tocarla debe navegar a
`PriceComparisonScreen`. Apagar el backend o vaciar `reference_prices` y confirmar que
muestra el estado "Aún no hay suficientes datos" sin romper el resto de Home.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/home/HomeScreen.kt
git commit -m "feat: tarjeta en Home con sugerencia de donde conviene comprar"
```

---

## Verificación final

- [ ] `cd backend && npx tsc --noEmit` sin errores.
- [ ] `./gradlew :app:compileDebugKotlin` sin errores.
- [ ] Probar en el emulador: Home muestra la tarjeta y navega a `PriceComparisonScreen`
      al tocarla; el chat responde con datos concretos a "¿dónde conviene comprar?".
- [ ] Caso borde: `reference_prices` vacía o con un solo supermercado — ni el endpoint ni
      el chat deben romperse (responden vacío/sin el contexto extra, no 500).
