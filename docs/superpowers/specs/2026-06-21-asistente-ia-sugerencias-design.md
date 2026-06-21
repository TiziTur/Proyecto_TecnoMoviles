# Asistente IA: sugerencias de compra más barata

## Contexto

El chat IA (`POST /chat`, `ChatScreen`/`ChatViewModel`) ya existe y funciona, pero hoy
solo tiene contexto del historial de compras propio del usuario — no conoce los datos
de comparación de precios SEPA (`reference_prices`), por lo que no puede responder con
fundamento a preguntas tipo "¿dónde conviene comprar?". Tampoco existe ningún resumen
proactivo en Home sobre dónde conviene comprar en general.

Esta spec cubre cerrar esa brecha: dar al chat contexto de precios agregados, y agregar
una tarjeta en Home con un resumen directo (no conversacional) de qué supermercado
conviene en general este mes.

## Decisiones de alcance (confirmadas con el usuario)

- El chat gana contexto de precios **y además** hay una tarjeta dedicada en Home (ambas
  cosas, no una sola).
- La tarjeta de Home muestra un **resumen directo en la propia pantalla** (no navega al
  chat ni envía un mensaje automático).
- Los números de ahorro se calculan **de forma determinística** en el backend; Gemini
  solo redacta el texto final a partir de esos números ya calculados — nunca inventa
  cifras.
- El cálculo se basa en **todo el catálogo SEPA general** (no en los productos que
  compra habitualmente el usuario) — funciona para cualquier usuario, tenga o no
  historial.
- La tarjeta de Home **carga el resumen automáticamente** al abrir Home (igual que el
  resto de los datos de Home), no requiere que el usuario la toque para calcularlo.
- Al tocar la tarjeta, **navega a `PriceComparisonScreen`** (no es solo informativa).
- Fuera de alcance: lookup de precio por producto específico dentro del chat (requeriría
  function-calling de Gemini); cacheo del cálculo agregado; comparar contra el historial
  personal del usuario.

## Backend

### 1. Helper compartido: `backend/src/lib/cheapestSummary.ts`

`computeCheapestSummary(pool): Promise<CheapestSummaryStats>`

Lógica:
1. `SELECT product_name, supermarket, MIN(price) AS price FROM reference_prices GROUP BY product_name, supermarket`.
2. Se descartan productos que aparecen en un solo supermercado (no son comparables).
3. Para cada producto comparable, se determina el supermercado más barato y el más caro
   entre los que lo tienen (mismo criterio que ya usa `prices.ts` por producto:
   `cheapestAt`/`maxSavings`/`savingsPct`).
4. Se cuentan victorias (veces que cada supermercado fue el más barato) →
   `cheapestSupermarket` = el supermercado con más victorias.
5. Sobre los productos ganados por `cheapestSupermarket`: `totalSavings` = suma de
   (precio más caro − precio más barato) en esos productos; `avgSavingsPct` = promedio
   del ahorro porcentual en esos mismos productos.
6. `productsCompared` = total de productos comparables (todo el catálogo); `productsWon`
   = cantidad de productos donde `cheapestSupermarket` fue el más barato.

```ts
interface CheapestSummaryStats {
  cheapestSupermarket: string;
  productsCompared: number;
  productsWon: number;
  totalSavings: number;
  avgSavingsPct: number;
}
```

Si no hay productos comparables (catálogo vacío o con un solo supermercado), el helper
devuelve `null` y ambos consumidores lo manejan sin romper (endpoint devuelve estado
vacío, chat omite el párrafo de precios).

### 2. Nuevo endpoint: `GET /prices/cheapest-summary` (en `prices.ts`)

- Llama a `computeCheapestSummary`.
- Si es `null`, responde `{ isEmpty: true }`.
- Si no, le pasa los números ya calculados a Gemini (`gemini-2.5-flash-lite`,
  `temperature: 0.4`, `maxOutputTokens: 150`) pidiendo 1-2 frases en español, amigables,
  que mencionen el supermercado y el ahorro aproximado — sin inventar ni modificar los
  números recibidos.
- Responde `{ ...stats, headline }`.
- Mismo patrón de auth (`authMiddleware`, ya aplicado al router de `prices.ts`) y de
  manejo de errores de Gemini que `ticket.ts`/`chat.ts` (502 si Gemini falla, no rompe
  con un 500 genérico).

### 3. `chat.ts` gana contexto de precios

- Llama a `computeCheapestSummary` (mismo helper, sin llamada extra a Gemini).
- Si no es `null`, se agrega un párrafo al `systemPrompt` existente, por ejemplo:
  > Datos generales de precios (SEPA, comparativa entre supermercados): de
  > {productsCompared} productos comparados, {cheapestSupermarket} tiene el precio más
  > bajo en {productsWon} ({avgSavingsPct}% de ahorro promedio en esos casos).
- Se suma al mismo prompt/llamada que ya existe — no agrega un round-trip extra a
  Gemini por mensaje de chat.
- Si el helper devuelve `null`, se omite el párrafo sin romper el resto del prompt.

## Android

### 4. DTOs (`AiDtos.kt`)

```kotlin
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

### 5. `ApiService.kt`

```kotlin
@GET("prices/cheapest-summary")
suspend fun getCheapestSummary(@Header("Authorization") token: String): Response<CheapestSummaryResponse>
```

### 6. `HomeViewModel` + `HomeUiState`

- `HomeViewModel` pasa a inyectar también `ApiService` (mismo patrón de
  `session.bearerToken.first()` + llamada a `api.*` ya usado en
  `PurchaseDetailViewModel`).
- `HomeUiState` gana:
  ```kotlin
  val cheapestSummary: CheapestSummaryResponse? = null,
  val cheapestSummaryLoading: Boolean = true,
  val cheapestSummaryError: Boolean = false
  ```
- En `init()`, un `viewModelScope.launch` independiente (con su propio `try/catch`) llama
  a `getCheapestSummary` y actualiza esos tres campos. Un fallo ahí no debe afectar el
  resto de `HomeUiState` (los datos de compras/total del mes siguen cargando igual).

### 7. `HomeScreen.kt`: nueva tarjeta

- Se agrega junto al `AiFeatureCard` de "Asistente IA" ya existente, bajo la sección
  "Herramientas IA".
- Título: "¿Dónde conviene comprar este mes?".
- Estados:
  - `cheapestSummaryLoading` → `CircularProgressIndicator` chico.
  - `cheapestSummaryError` o `cheapestSummary?.isEmpty == true` → texto discreto
    ("No se pudo calcular" / "Aún no hay suficientes datos"), sin bloquear el resto de
    Home.
  - Caso éxito → muestra `cheapestSummary.headline`.
- `onClick` → navega a `PriceComparisonScreen`, reusando el callback `onNavigateToPriceComparison`
  que `HomeScreen` ya recibe y tiene cableado en `NavGraph.kt:122` (no se agrega ningún
  callback nuevo).

## Testing / verificación

- Backend: sin framework de tests (convención ya establecida en este repo) — verificar
  con `npx tsc --noEmit` y `curl` manual a `/prices/cheapest-summary` y `/chat` con datos
  reales en la base.
- Caso borde a probar manualmente: catálogo de `reference_prices` vacío o con un solo
  supermercado (el helper debe devolver `null` sin tirar excepción).
- Android: build de Kotlin (`./gradlew :app:compileDebugKotlin`) y prueba manual en el
  emulador/dispositivo: abrir Home y confirmar que la tarjeta carga el resumen y navega
  a `PriceComparisonScreen` al tocarla; abrir el chat y preguntar "¿dónde conviene
  comprar?" para confirmar que la respuesta usa los datos agregados.
