# Rediseño de la sección Estadísticas — Design Spec

## Contexto y objetivo

La pantalla de Estadísticas (`StatsScreen.kt`) ya muestra: total gastado, promedio por compra, evolución mensual (gráfico de barras), gasto por supermercado y top productos. El usuario quiere:

1. **Arreglar la barra de progreso de "Gasto por supermercado"**: el `LinearProgressIndicator` de Material3 1.3+ dibuja un hueco + "stop indicator" entre el tramo coloreado y el track vacío, dando la sensación de dos figuras separadas en vez de una barra homogénea.
2. **Modernizar el gráfico de evolución mensual**: actualmente no muestra ninguna escala de referencia, por lo que no se puede saber cuánto representa cada barra sin más contexto.
3. **Agregar nuevas estadísticas útiles para el control de finanzas de un hogar**, dado que ese es el objetivo central de la app.

## Alcance

- Solo afecta `StatsScreen.kt` y `StatsViewModel.kt` (y su `StatsUiState`).
- No requiere cambios en el esquema de Room (`PurchaseEntity`, `ProductEntity`) — todas las estadísticas nuevas se calculan en memoria a partir de `List<Purchase>` ya disponible.
- Se reutiliza `ThemeDataStore.monthlyLimit` (ya usado en Home) para las estadísticas de presupuesto.
- Se mantiene el estilo visual glassmorphism / `dotPatternBackground` existente en toda la app.

## Arquitectura — Navegación con pestañas

`StatsScreen.kt` se reestructura con un `TabRow` (Material3) fijo debajo del `AppTopBar`, con 4 pestañas. Cada pestaña es un contenido scrolleable independiente:

### Tab 1 — General
- Total gastado (card existente)
- Promedio por compra (card existente)
- Gráfico de evolución mensual (modernizado, ver abajo)
- Comparación con el mes anterior (% variación)

### Tab 2 — Presupuesto
- Presupuesto del mes vs. gasto real, con proyección de fin de mes
- Gasto por día de la semana

### Tab 3 — Supermercados
- Gasto por supermercado (con barra de progreso custom, ver abajo)
- Ticket promedio por supermercado

### Tab 4 — Productos
- Top productos (sin cambios respecto a la versión actual)
- Productos con mayor aumento de precio
- Frecuencia de compras y tamaño de canasta promedio

## Fix 1 — Barra de progreso custom (componente `SegmentBar`)

Se reemplaza el `LinearProgressIndicator` de Material3 (que introduce gap + stop indicator en M3 1.3+) por un composable propio, una sola pieza visual:

```kotlin
@Composable
fun SegmentBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f))))
        )
    }
}
```

Track y relleno son un único `Box` con esquinas redondeadas y un `Box` superpuesto recortado con la misma forma — sin huecos ni puntos.

**Usos:**
- "Gasto por supermercado" (Tab 3): `progress` = porcentaje del supermercado sobre el total, `color` = color asignado al supermercado (igual que hoy).
- "Presupuesto del mes" (Tab 2): `progress` = `currentMonthSpent / monthlyLimit`, `color` dinámico según el porcentaje:
  - `< 0.8` → verde (`#10B981`)
  - `0.8 – 1.0` → amarillo (`#F59E0B`)
  - `> 1.0` → rojo (`#EF4444`), y `progress` se clampea a `1f` visualmente pero el texto muestra el % real (puede ser >100%)

## Fix 2 — Gráfico de evolución mensual modernizado

Se mantiene el `Canvas` actual pero se agregan:

- **Eje Y** a la izquierda con 4 etiquetas de escala: `$0` y 3 valores intermedios calculados como fracciones del máximo del set de datos, redondeado hacia arriba a una unidad "linda" (ej. múltiplos de 10.000, 50.000, 100.000 según la magnitud).
- **3 líneas guía horizontales** sutiles (`onSurface.copy(alpha = 0.06f)`) en las posiciones 33%, 66% y 100% del área del gráfico.
- **Etiqueta de monto** sobre cada barra, formateada como moneda abreviada (ej. `$90k`).
- **Relleno con gradiente vertical** (`Brush.verticalGradient`, de un tono más claro arriba a más saturado abajo) en vez de color sólido, dibujado dentro de la misma forma redondeada que el track (sin "corte" entre track y relleno — ya resuelto en una iteración anterior de este mismo gráfico).

El área de dibujo del `Canvas` se divide en: columna izquierda angosta para las etiquetas del eje Y, y el resto para las barras + líneas guía + etiquetas de mes (eje X) debajo.

## Nuevas estadísticas — Datos y cálculo

Todas se agregan a `StatsUiState` y se calculan en `StatsViewModel` a partir de `purchases: List<Purchase>` (ya cacheado vía `getPurchasesFlow()`).

```kotlin
data class StatsUiState(
    val isLoading: Boolean = true,
    val monthlyStats: List<StatSummary> = emptyList(),
    val supermarketStats: List<StatSummary> = emptyList(),
    val topProducts: List<StatSummary> = emptyList(),
    val totalAllTime: Double = 0.0,
    val avgPurchase: Double = 0.0,
    val error: String? = null,
    // --- nuevos campos ---
    val monthlyLimit: Double = 0.0,
    val currentMonthSpent: Double = 0.0,
    val projectedMonthSpent: Double = 0.0,
    val previousMonthSpent: Double = 0.0,
    val monthOverMonthPct: Double? = null,
    val weekdayStats: List<StatSummary> = emptyList(),
    val avgTicketBySupermarket: List<StatSummary> = emptyList(),
    val priceIncreases: List<PriceChange> = emptyList(),
    val purchaseCountThisMonth: Int = 0,
    val avgItemsPerPurchase: Double = 0.0
)

data class PriceChange(
    val productName: String,
    val oldPrice: Double,
    val newPrice: Double,
    val pctChange: Double
)
```

### 1. Presupuesto vs. gasto real + proyección
- `monthlyLimit`: leído desde `ThemeDataStore.monthlyLimit` (mismo dato que usa Home).
- `currentMonthSpent`: suma de `total` de las compras cuyo `date` cae en el mes/año actual.
- `projectedMonthSpent`: `currentMonthSpent / díasTranscurridosDelMes * díasTotalesDelMes`. Si `díasTranscurridosDelMes == 0`, se usa `currentMonthSpent` directamente (sin dividir por cero).

### 2. Comparación con el mes anterior
- `previousMonthSpent`: suma de `total` de las compras del mes calendario inmediatamente anterior.
- `monthOverMonthPct`: `(currentMonthSpent - previousMonthSpent) / previousMonthSpent * 100`. Si `previousMonthSpent == 0`, el valor es `null` y la card no muestra porcentaje (solo el monto actual).

### 3. Gasto por día de la semana
- `weekdayStats`: `List<StatSummary>` con 7 entradas (Lunes a Domingo, en español), cada una con la suma de `total` de todas las compras (histórico completo) cuyo `date.dayOfWeek` corresponde a ese día.

### 4. Ticket promedio por supermercado
- `avgTicketBySupermarket`: para cada supermercado, `totalGastadoEnEseSuper / cantidadDeComprasEnEseSuper` (histórico completo, igual período que `supermarketStats`).

### 5. Productos con mayor aumento de precio
- Se agrupan todos los `Product` (de todas las compras, histórico completo) por `name` normalizado (trim + lowercase).
- Para cada grupo con 2 o más ocurrencias, se ordenan por la fecha de la `Purchase` a la que pertenecen.
- Se compara el `price` de la primera ocurrencia (`oldPrice`) contra el de la última (`newPrice`).
- `pctChange = (newPrice - oldPrice) / oldPrice * 100`.
- `priceIncreases`: los 3 productos con mayor `pctChange` positivo (se descartan `pctChange <= 0` y grupos con `oldPrice == 0`).

### 6. Frecuencia de compras y tamaño de canasta
- `purchaseCountThisMonth`: cantidad de `Purchase` cuyo `date` cae en el mes/año actual.
- `avgItemsPerPurchase`: promedio de `productCount` de las compras del mes actual. Si `purchaseCountThisMonth == 0`, el valor es `0.0`.

## UI — Nuevas cards (estilo glassmorphism existente)

Cada nueva sección sigue el patrón visual ya usado en la app (`Card` con `colorScheme.surface`, esquinas redondeadas, `dotPatternBackground` en el fondo de la pantalla, `GradientDivider` entre secciones):

- **Presupuesto del mes**: card con `SegmentBar` (color dinámico según %), texto `"$ gastado / $ límite"`, porcentaje, y línea de proyección (`"Proyección fin de mes: $X"`) con color de advertencia si supera el límite.
- **Comparación con mes anterior**: card simple con el monto del mes actual en grande + chip de variación (▲ rojo si subió, ▼ verde si bajó), o sin chip si `monthOverMonthPct == null`.
- **Gasto por día de la semana**: mini gráfico de barras horizontal (7 barras, Lun-Dom) reutilizando el mismo estilo de `Canvas`/colores que el gráfico mensual, sin necesidad de eje Y detallado (solo comparación relativa entre días).
- **Ticket promedio por supermercado**: lista simple (ícono/dot de color + nombre + monto), mismo layout que "Gasto por supermercado" pero sin barra de progreso (no aplica un "total" de referencia).
- **Productos con mayor aumento de precio**: reutiliza el layout de "Top productos" (lista con ícono, nombre, y el `pctChange` en rojo/naranja con flecha ▲).
- **Frecuencia y tamaño de canasta**: card con dos valores destacados lado a lado: "Compras este mes: N" y "Items promedio por compra: X.X".

## Manejo de casos sin datos

- Si `purchases` está vacío, todas las nuevas secciones muestran el mismo estado vacío que ya usa el resto de `StatsScreen` (mensaje + ícono, sin crashear por divisiones por cero — todas las divisiones verifican el denominador antes de operar).
- Si `monthlyLimit == 0.0` (usuario no configuró presupuesto), la card de "Presupuesto del mes" muestra un mensaje invitando a configurarlo en Ajustes, en vez de una barra al 0% o infinito.

## Testing

- Tests unitarios para las nuevas funciones de cálculo en `StatsViewModel` (proyección, comparación mensual, agrupación por día de semana, ticket promedio, detección de aumentos de precio, frecuencia/canasta), cubriendo casos límite: lista vacía, un solo mes de datos, `monthlyLimit == 0`, productos sin repetición (sin pares para comparar precio).
- Verificación visual manual de las 4 pestañas en modo claro y oscuro, y de la `SegmentBar` con valores 0%, 50%, 100% y >100% (caso presupuesto excedido).
