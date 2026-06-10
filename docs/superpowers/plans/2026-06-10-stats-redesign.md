# Rediseño de Estadísticas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar `StatsScreen.kt` en 4 pestañas, arreglar la barra de progreso de "Gasto por supermercado" (reemplazar `LinearProgressIndicator` por una barra custom de una sola pieza), modernizar el gráfico de evolución mensual (escala, líneas guía, valores, gradiente) y agregar 6 estadísticas nuevas de finanzas del hogar.

**Architecture:** Toda la lógica de cálculo nueva vive en funciones puras en `StatsCalculations.kt` (testeable sin Android/Hilt). `StatsViewModel` las invoca y expone el resultado en `StatsUiState` ampliado. `StatsScreen.kt` pasa a tener un `TabRow` con 4 pestañas, cada una implementada en su propio archivo (`StatsGeneralTab.kt`, `StatsBudgetTab.kt`, `StatsSupermarketsTab.kt`, `StatsProductsTab.kt`). Un nuevo composable `SegmentBar.kt` reemplaza el `LinearProgressIndicator`.

**Tech Stack:** Jetpack Compose + Material3, Hilt, Kotlin `java.time`, JUnit (tests en `app/src/test`).

---

## Task 1: Agregar `PriceChange` al modelo de datos

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/model/Models.kt`

- [ ] **Step 1: Agregar la data class `PriceChange`**

Al final de `Models.kt` (después de `StatSummary`, líneas 52-55), agregar:

```kotlin

// Representa el cambio de precio de un producto entre su primera y última compra registrada.
data class PriceChange(
    val productName: String,
    val oldPrice: Double,
    val newPrice: Double,
    val pctChange: Double
)
```

- [ ] **Step 2: Compilar para verificar que no rompe nada**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/model/Models.kt
git commit -m "feat: add PriceChange model for price increase stats"
```

---

## Task 2: Crear `StatsCalculations.kt` con presupuesto, proyección y comparación mensual (TDD)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt`
- Test: `app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt`

- [ ] **Step 1: Escribir el archivo de test con casos de presupuesto/proyección/comparación**

Crear `app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt`:

```kotlin
package com.undef.superahorroturina.ui.screens.stats

import com.undef.superahorroturina.model.Product
import com.undef.superahorroturina.model.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private fun purchase(
    id: Int,
    date: LocalDate,
    supermarket: String = "Coto",
    total: Double = 100.0,
    products: List<Product> = emptyList(),
    productCount: Int = products.size
) = Purchase(
    id = id,
    date = date,
    time = LocalTime.NOON,
    supermarket = supermarket,
    total = total,
    products = products,
    productCount = productCount
)

private fun product(name: String, price: Double) = Product(
    id = 0, code = "", name = name, description = "", price = price, quantity = 1
)

class StatsCalculationsTest {

    private val today = LocalDate.of(2026, 6, 10) // 10 de junio de 2026 (mes de 30 días)

    @Test
    fun `calcCurrentMonthSpent suma solo las compras del mes y anio actual`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 6, 1), total = 100.0),
            purchase(2, LocalDate.of(2026, 6, 9), total = 200.0),
            purchase(3, LocalDate.of(2026, 5, 31), total = 999.0),
            purchase(4, LocalDate.of(2025, 6, 9), total = 999.0)
        )
        assertEquals(300.0, calcCurrentMonthSpent(purchases, today), 0.001)
    }

    @Test
    fun `calcPreviousMonthSpent suma solo el mes calendario anterior`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 5, 15), total = 150.0),
            purchase(2, LocalDate.of(2026, 5, 20), total = 50.0),
            purchase(3, LocalDate.of(2026, 6, 1), total = 999.0),
            purchase(4, LocalDate.of(2025, 5, 20), total = 999.0)
        )
        assertEquals(200.0, calcPreviousMonthSpent(purchases, today), 0.001)
    }

    @Test
    fun `calcProjectedMonthSpent proyecta linealmente segun dias transcurridos`() {
        // 10 de junio: transcurrieron 10 dias de 30 -> proyeccion = gasto * 3
        assertEquals(900.0, calcProjectedMonthSpent(300.0, today), 0.001)
    }

    @Test
    fun `calcProjectedMonthSpent con gasto cero proyecta cero`() {
        assertEquals(0.0, calcProjectedMonthSpent(0.0, today), 0.001)
    }

    @Test
    fun `calcMonthOverMonthPct calcula variacion porcentual`() {
        assertEquals(50.0, calcMonthOverMonthPct(300.0, 200.0)!!, 0.001)
        assertEquals(-50.0, calcMonthOverMonthPct(100.0, 200.0)!!, 0.001)
    }

    @Test
    fun `calcMonthOverMonthPct devuelve null si no hay datos del mes anterior`() {
        assertNull(calcMonthOverMonthPct(300.0, 0.0))
    }
}
```

- [ ] **Step 2: Correr los tests para verificar que fallan (no compila aún)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: FAIL — "Unresolved reference: calcCurrentMonthSpent" (y similares)

- [ ] **Step 3: Crear `StatsCalculations.kt` con las funciones de presupuesto/proyección/comparación**

Crear `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt`:

```kotlin
// Funciones puras de cálculo para las estadísticas de StatsScreen.
// Sin dependencias de Android/Hilt para poder testearlas directo con JUnit.
package com.undef.superahorroturina.ui.screens.stats

import com.undef.superahorroturina.model.PriceChange
import com.undef.superahorroturina.model.Purchase
import com.undef.superahorroturina.model.StatSummary
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun calcCurrentMonthSpent(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double =
    purchases
        .filter { YearMonth.from(it.date) == YearMonth.from(today) }
        .sumOf { it.total }

fun calcPreviousMonthSpent(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double =
    purchases
        .filter { YearMonth.from(it.date) == YearMonth.from(today).minusMonths(1) }
        .sumOf { it.total }

fun calcProjectedMonthSpent(currentMonthSpent: Double, today: LocalDate = LocalDate.now()): Double {
    val daysElapsed = today.dayOfMonth
    val daysInMonth = today.lengthOfMonth()
    return currentMonthSpent / daysElapsed * daysInMonth
}

fun calcMonthOverMonthPct(currentMonthSpent: Double, previousMonthSpent: Double): Double? =
    if (previousMonthSpent == 0.0) null
    else (currentMonthSpent - previousMonthSpent) / previousMonthSpent * 100
```

- [ ] **Step 4: Correr los tests para verificar que pasan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt
git commit -m "feat: add budget projection and month comparison calculations"
```

---

## Task 3: Agregar gasto por día de semana y ticket promedio por supermercado (TDD)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt`
- Modify: `app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt`

- [ ] **Step 1: Agregar tests para `calcWeekdayStats` y `calcAvgTicketBySupermarket`**

Agregar al final de la clase `StatsCalculationsTest` (antes del `}` de cierre):

```kotlin

    @Test
    fun `calcWeekdayStats devuelve 7 dias en orden Lunes a Domingo con sumas correctas`() {
        // 2026-06-08 es lunes, 2026-06-09 es martes, 2026-06-13 es sabado
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 6, 8), total = 100.0),  // Lunes
            purchase(2, LocalDate.of(2026, 6, 8), total = 50.0),   // Lunes
            purchase(3, LocalDate.of(2026, 6, 9), total = 30.0),   // Martes
            purchase(4, LocalDate.of(2026, 6, 13), total = 200.0)  // Sabado
        )
        val result = calcWeekdayStats(purchases)
        assertEquals(7, result.size)
        assertEquals("Lunes", result[0].label)
        assertEquals(150.0, result[0].amount, 0.001)
        assertEquals("Martes", result[1].label)
        assertEquals(30.0, result[1].amount, 0.001)
        assertEquals("Sabado".replace("a", "á"), result[5].label)
        assertEquals(200.0, result[5].amount, 0.001)
        assertEquals("Domingo", result[6].label)
        assertEquals(0.0, result[6].amount, 0.001)
    }

    @Test
    fun `calcAvgTicketBySupermarket calcula promedio por visita y ordena descendente`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 6, 1), supermarket = "Walmart", total = 100.0),
            purchase(2, LocalDate.of(2026, 6, 2), supermarket = "Walmart", total = 300.0),
            purchase(3, LocalDate.of(2026, 6, 3), supermarket = "Coto", total = 50.0)
        )
        val result = calcAvgTicketBySupermarket(purchases)
        assertEquals(2, result.size)
        assertEquals("Walmart", result[0].label)
        assertEquals(200.0, result[0].amount, 0.001) // (100+300)/2
        assertEquals("Coto", result[1].label)
        assertEquals(50.0, result[1].amount, 0.001)
    }

    @Test
    fun `calcAvgTicketBySupermarket limita a 5 supermercados`() {
        val purchases = (1..6).map { i ->
            purchase(i, LocalDate.of(2026, 6, i), supermarket = "Super$i", total = (i * 10).toDouble())
        }
        assertEquals(5, calcAvgTicketBySupermarket(purchases).size)
    }
```

- [ ] **Step 2: Correr los tests para verificar que fallan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: FAIL — "Unresolved reference: calcWeekdayStats" / "calcAvgTicketBySupermarket"

- [ ] **Step 3: Implementar las funciones en `StatsCalculations.kt`**

Agregar al final de `StatsCalculations.kt`:

```kotlin

private val WEEKDAY_LABELS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun calcWeekdayStats(purchases: List<Purchase>): List<StatSummary> {
    val sums = DoubleArray(7)
    purchases.forEach { purchase -> sums[purchase.date.dayOfWeek.value - 1] += purchase.total }
    return WEEKDAY_LABELS.indices.map { StatSummary(WEEKDAY_LABELS[it], sums[it]) }
}

fun calcAvgTicketBySupermarket(purchases: List<Purchase>): List<StatSummary> =
    purchases
        .groupBy { it.supermarket }
        .map { (name, ps) -> StatSummary(name, ps.sumOf { it.total } / ps.size) }
        .sortedByDescending { it.amount }
        .take(5)
```

- [ ] **Step 4: Correr los tests para verificar que pasan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt
git commit -m "feat: add weekday spending and average ticket per supermarket calculations"
```

---

## Task 4: Agregar aumentos de precio y frecuencia/canasta (TDD)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt`
- Modify: `app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt`

- [ ] **Step 1: Agregar tests para `calcPriceIncreases`, `calcPurchaseCountThisMonth` y `calcAvgItemsPerPurchase`**

Agregar al final de la clase `StatsCalculationsTest`:

```kotlin

    @Test
    fun `calcPriceIncreases detecta suba de precio entre primera y ultima compra del mismo producto`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 4, 1), products = listOf(product("Aceite", 100.0))),
            purchase(2, LocalDate.of(2026, 6, 1), products = listOf(product("Aceite", 150.0)))
        )
        val result = calcPriceIncreases(purchases)
        assertEquals(1, result.size)
        assertEquals("Aceite", result[0].productName)
        assertEquals(100.0, result[0].oldPrice, 0.001)
        assertEquals(150.0, result[0].newPrice, 0.001)
        assertEquals(50.0, result[0].pctChange, 0.001)
    }

    @Test
    fun `calcPriceIncreases ignora productos sin repeticion y sin aumento`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 4, 1), products = listOf(
                product("Unico", 100.0),
                product("Bajo", 200.0)
            )),
            purchase(2, LocalDate.of(2026, 6, 1), products = listOf(
                product("Bajo", 150.0) // bajó de precio, no cuenta
            ))
        )
        assertEquals(0, calcPriceIncreases(purchases).size)
    }

    @Test
    fun `calcPriceIncreases devuelve maximo 3 ordenados por mayor aumento`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 1, 1), products = listOf(
                product("A", 100.0), product("B", 100.0), product("C", 100.0), product("D", 100.0)
            )),
            purchase(2, LocalDate.of(2026, 6, 1), products = listOf(
                product("A", 110.0), // +10%
                product("B", 200.0), // +100%
                product("C", 150.0), // +50%
                product("D", 130.0)  // +30%
            ))
        )
        val result = calcPriceIncreases(purchases)
        assertEquals(3, result.size)
        assertEquals("B", result[0].productName)
        assertEquals("C", result[1].productName)
        assertEquals("D", result[2].productName)
    }

    @Test
    fun `calcPurchaseCountThisMonth cuenta solo compras del mes actual`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 6, 1)),
            purchase(2, LocalDate.of(2026, 6, 9)),
            purchase(3, LocalDate.of(2026, 5, 31))
        )
        assertEquals(2, calcPurchaseCountThisMonth(purchases, today))
    }

    @Test
    fun `calcAvgItemsPerPurchase promedia productCount del mes actual`() {
        val purchases = listOf(
            purchase(1, LocalDate.of(2026, 6, 1), productCount = 10),
            purchase(2, LocalDate.of(2026, 6, 9), productCount = 4),
            purchase(3, LocalDate.of(2026, 5, 31), productCount = 999)
        )
        assertEquals(7.0, calcAvgItemsPerPurchase(purchases, today), 0.001)
    }

    @Test
    fun `calcAvgItemsPerPurchase devuelve cero si no hay compras este mes`() {
        val purchases = listOf(purchase(1, LocalDate.of(2026, 5, 31), productCount = 10))
        assertEquals(0.0, calcAvgItemsPerPurchase(purchases, today), 0.001)
    }
```

- [ ] **Step 2: Correr los tests para verificar que fallan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: FAIL — "Unresolved reference: calcPriceIncreases" / "calcPurchaseCountThisMonth" / "calcAvgItemsPerPurchase"

- [ ] **Step 3: Implementar las funciones en `StatsCalculations.kt`**

Agregar al final de `StatsCalculations.kt`:

```kotlin

fun calcPriceIncreases(purchases: List<Purchase>): List<PriceChange> {
    data class PriceEntry(val date: LocalDate, val price: Double, val displayName: String)

    return purchases
        .flatMap { purchase ->
            purchase.products.map { product ->
                product.name.trim().lowercase() to PriceEntry(purchase.date, product.price, product.name)
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (_, entries) ->
            if (entries.size < 2) return@mapNotNull null
            val sorted = entries.sortedBy { it.date }
            val oldest = sorted.first()
            val newest = sorted.last()
            if (oldest.price <= 0.0) return@mapNotNull null
            val pctChange = (newest.price - oldest.price) / oldest.price * 100
            if (pctChange <= 0.0) return@mapNotNull null
            PriceChange(
                productName = newest.displayName,
                oldPrice = oldest.price,
                newPrice = newest.price,
                pctChange = pctChange
            )
        }
        .sortedByDescending { it.pctChange }
        .take(3)
}

fun calcPurchaseCountThisMonth(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Int =
    purchases.count { YearMonth.from(it.date) == YearMonth.from(today) }

fun calcAvgItemsPerPurchase(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double {
    val thisMonth = purchases.filter { YearMonth.from(it.date) == YearMonth.from(today) }
    return if (thisMonth.isEmpty()) 0.0
    else thisMonth.sumOf { it.productCount }.toDouble() / thisMonth.size
}
```

- [ ] **Step 4: Correr los tests para verificar que pasan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: PASS (15 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt
git commit -m "feat: add price increase detection and purchase frequency calculations"
```

---

## Task 5: Agregar helpers para el gráfico (escala y formato de moneda) (TDD)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt`
- Modify: `app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt`

- [ ] **Step 1: Agregar tests para `niceAxisMax` y `formatCompactCurrency`**

Agregar al final de la clase `StatsCalculationsTest`:

```kotlin

    @Test
    fun `niceAxisMax redondea hacia arriba a un valor lindo`() {
        assertEquals(100.0, niceAxisMax(85.0), 0.001)
        assertEquals(200.0, niceAxisMax(110.0), 0.001)
        assertEquals(500.0, niceAxisMax(450.0), 0.001)
        assertEquals(1000.0, niceAxisMax(900.0), 0.001)
        assertEquals(100000.0, niceAxisMax(90000.0), 0.001)
    }

    @Test
    fun `niceAxisMax con cero o negativo devuelve 1`() {
        assertEquals(1.0, niceAxisMax(0.0), 0.001)
        assertEquals(1.0, niceAxisMax(-50.0), 0.001)
    }

    @Test
    fun `formatCompactCurrency formatea miles y millones`() {
        assertEquals("$ 500", formatCompactCurrency(500.0))
        assertEquals("$ 12k", formatCompactCurrency(12345.0))
        assertEquals("$ 3M", formatCompactCurrency(3_200_000.0))
    }
```

- [ ] **Step 2: Correr los tests para verificar que fallan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: FAIL — "Unresolved reference: niceAxisMax" / "formatCompactCurrency"

- [ ] **Step 3: Implementar las funciones en `StatsCalculations.kt`**

Agregar al final de `StatsCalculations.kt`:

```kotlin

fun niceAxisMax(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val normalized = value / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceNormalized * magnitude
}

fun formatCompactCurrency(amount: Double): String = "$ " + when {
    amount >= 1_000_000 -> "${(amount / 1_000_000).toInt()}M"
    amount >= 1_000 -> "${(amount / 1_000).toInt()}k"
    else -> amount.toInt().toString()
}
```

- [ ] **Step 4: Correr los tests para verificar que pasan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.undef.superahorroturina.ui.screens.stats.StatsCalculationsTest"`
Expected: PASS (18 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculations.kt app/src/test/java/com/undef/superahorroturina/ui/screens/stats/StatsCalculationsTest.kt
git commit -m "feat: add chart axis scaling and compact currency formatting helpers"
```

---

## Task 6: Ampliar `StatsUiState` y `StatsViewModel` con las nuevas estadísticas

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsViewModel.kt`

- [ ] **Step 1: Reescribir `StatsViewModel.kt` completo**

Reemplazar el contenido completo de `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsViewModel.kt` por:

```kotlin
// ViewModel de estadísticas: agrupa compras por mes, supermercado, día de semana,
// presupuesto y precios para alimentar las 4 pestañas de StatsScreen.
package com.undef.superahorroturina.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.PriceChange
import com.undef.superahorroturina.model.Purchase
import com.undef.superahorroturina.model.StatSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val monthlyStats: List<StatSummary> = emptyList(),
    val supermarketStats: List<StatSummary> = emptyList(),
    val topProducts: List<StatSummary> = emptyList(),
    val totalAllTime: Double = 0.0,
    val avgPurchase: Double = 0.0,
    val error: String = "",
    // Presupuesto y proyección
    val monthlyLimit: Double = 0.0,
    val currentMonthSpent: Double = 0.0,
    val projectedMonthSpent: Double = 0.0,
    // Comparación con el mes anterior
    val previousMonthSpent: Double = 0.0,
    val monthOverMonthPct: Double? = null,
    // Gasto por día de semana
    val weekdayStats: List<StatSummary> = emptyList(),
    // Ticket promedio por supermercado
    val avgTicketBySupermarket: List<StatSummary> = emptyList(),
    // Productos con mayor aumento de precio
    val priceIncreases: List<PriceChange> = emptyList(),
    // Frecuencia y tamaño de canasta
    val purchaseCountThisMonth: Int = 0,
    val avgItemsPerPurchase: Double = 0.0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val themeDataStore: ThemeDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            purchaseRepository.refreshPurchases()
            val purchases = purchaseRepository.getPurchasesFlow().first()
            val purchasesWithProducts = purchases.map { purchase ->
                val detail = purchaseRepository.getPurchase(purchase.id)
                if (detail is ApiResult.Success) detail.data else purchase
            }
            val monthlyLimit = themeDataStore.monthlyLimit.first().toDouble()

            val today = LocalDate.now()
            val totalAllTime = purchases.sumOf { it.total }
            val currentMonthSpent = calcCurrentMonthSpent(purchases, today)
            val previousMonthSpent = calcPreviousMonthSpent(purchases, today)

            _uiState.value = StatsUiState(
                isLoading              = false,
                monthlyStats           = buildMonthlyStats(purchases),
                supermarketStats       = buildSupermarketStats(purchases),
                topProducts            = buildTopProducts(purchasesWithProducts),
                totalAllTime           = totalAllTime,
                avgPurchase            = if (purchases.isNotEmpty()) totalAllTime / purchases.size else 0.0,
                monthlyLimit           = monthlyLimit,
                currentMonthSpent      = currentMonthSpent,
                projectedMonthSpent    = calcProjectedMonthSpent(currentMonthSpent, today),
                previousMonthSpent     = previousMonthSpent,
                monthOverMonthPct      = calcMonthOverMonthPct(currentMonthSpent, previousMonthSpent),
                weekdayStats           = calcWeekdayStats(purchases),
                avgTicketBySupermarket = calcAvgTicketBySupermarket(purchases),
                priceIncreases         = calcPriceIncreases(purchasesWithProducts),
                purchaseCountThisMonth = calcPurchaseCountThisMonth(purchases, today),
                avgItemsPerPurchase    = calcAvgItemsPerPurchase(purchases, today)
            )
        }
    }

    private fun buildMonthlyStats(purchases: List<Purchase>): List<StatSummary> {
        val displayFmt = DateTimeFormatter.ofPattern("MMM yy")
        return purchases
            .groupBy { YearMonth.of(it.date.year, it.date.month) }
            .entries
            .sortedBy { (yearMonth, _) -> yearMonth }
            .takeLast(6)
            .map { (yearMonth, ps) ->
                val label = yearMonth.format(displayFmt)
                    .replaceFirstChar { c -> c.uppercaseChar() }
                StatSummary(label, ps.sumOf { it.total })
            }
    }

    private fun buildSupermarketStats(purchases: List<Purchase>): List<StatSummary> =
        purchases
            .groupBy { it.supermarket }
            .map { (name, ps) -> StatSummary(name, ps.sumOf { it.total }) }
            .sortedByDescending { it.amount }
            .take(5)

    private fun buildTopProducts(purchases: List<Purchase>): List<StatSummary> =
        purchases
            .flatMap { it.products }
            .groupBy { it.name }
            .map { (name, ps) -> StatSummary(name, ps.sumOf { it.price * it.quantity }) }
            .sortedByDescending { it.amount }
            .take(5)
}
```

Nota: `error` se mantiene como `String` (no se usaba en `StatsScreen.kt` actual, se conserva igual para no romper otras referencias).

- [ ] **Step 2: Compilar para verificar que no hay errores**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (StatsScreen.kt aún no usa los campos nuevos, pero el ViewModel debe compilar)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsViewModel.kt
git commit -m "feat: wire new stats calculations into StatsViewModel"
```

---

## Task 7: Agregar strings nuevos para las 4 pestañas y secciones

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Agregar los nuevos strings**

En `app/src/main/res/values/strings.xml`, reemplazar el bloque `<!-- Stats -->` (líneas 96-104) por:

```xml
    <!-- Stats -->
    <string name="stats_tab_general">General</string>
    <string name="stats_tab_budget">Presupuesto</string>
    <string name="stats_tab_supermarkets">Supermercados</string>
    <string name="stats_tab_products">Productos</string>
    <string name="stats_monthly_evolution">Evolución mensual</string>
    <string name="stats_by_supermarket">Gasto por supermercado</string>
    <string name="stats_top_products">Productos más comprados</string>
    <string name="stat_purchases">Compras</string>
    <string name="stat_supermarkets">Supermercados</string>
    <string name="stat_total_spent">Total gastado</string>
    <string name="stat_avg_purchase">Promedio</string>
    <string name="stats_no_data">Sin datos aún</string>
    <string name="stats_month_comparison">Comparación con el mes anterior</string>
    <string name="stats_vs_last_month">vs. mes anterior</string>
    <string name="stats_no_previous_month">Sin datos del mes anterior</string>
    <string name="stats_budget_title">Presupuesto del mes</string>
    <string name="stats_budget_no_limit">Configurá un presupuesto mensual en Ajustes para ver esta estadística</string>
    <string name="stats_projection">Proyección fin de mes</string>
    <string name="stats_projection_over_limit">Supera el límite mensual</string>
    <string name="stats_weekday_spending">Gasto por día de la semana</string>
    <string name="stats_avg_ticket">Ticket promedio por supermercado</string>
    <string name="stats_price_increases">Productos con mayor aumento de precio</string>
    <string name="stats_purchase_frequency">Frecuencia de compras</string>
    <string name="stats_purchases_this_month">Compras este mes</string>
    <string name="stats_avg_items">Items promedio por compra</string>
```

- [ ] **Step 2: Compilar para verificar que el XML es válido**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add strings for new stats tabs and sections"
```

---

## Task 8: Crear el componente `SegmentBar` (barra de progreso custom)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/SegmentBar.kt`

- [ ] **Step 1: Crear `SegmentBar.kt`**

```kotlin
// Barra de progreso de una sola pieza: track + relleno son el mismo Box recortado,
// sin el gap/stop-indicator que agrega LinearProgressIndicator en Material3 1.3+.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

@Composable
fun SegmentBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height / 2))
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f))))
        )
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/SegmentBar.kt
git commit -m "feat: add SegmentBar single-piece progress bar component"
```

---

## Task 9: Crear `StatsGeneralTab.kt` (resumen, gráfico modernizado, comparación mensual)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsGeneralTab.kt`

- [ ] **Step 1: Crear `StatsGeneralTab.kt`**

Esta pestaña incluye: cards de Total gastado / Promedio, el gráfico de evolución mensual modernizado (eje Y, líneas guía, valores, gradiente vertical) y la card de comparación con el mes anterior.

```kotlin
// Pestaña "General": total gastado, promedio, evolución mensual y comparación con mes anterior.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.StatCard
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsGeneralTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    chartColors: List<Color>,
    isDark: Boolean
) {
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant
    val guideColor   = MaterialTheme.colorScheme.outlineVariant
    val density      = LocalDensity.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── Summary cards ─────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.stat_total_spent),
                    value = "$ ${moneyFormat.format(uiState.totalAllTime)}",
                    icon  = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.stat_avg_purchase),
                    value = "$ ${moneyFormat.format(uiState.avgPurchase)}",
                    icon  = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Monthly bar chart (modernizado) ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_monthly_evolution))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) {
                    val data = uiState.monthlyStats

                    if (data.isEmpty()) {
                        Text(
                            stringResource(R.string.stats_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
                        )
                    } else {
                        val axisMax        = niceAxisMax(data.maxOf { it.amount })
                        val labelColorArgb = labelColor.toArgb()
                        val guideColorArgb = guideColor.toArgb()
                        val canvasHeight   = 220.dp
                        val bottomLabelArea = 28.dp
                        val topValueArea    = 22.dp
                        val yAxisArea       = 44.dp

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(canvasHeight)
                        ) {
                            val totalW        = size.width
                            val totalH        = size.height
                            val bottomLabelPx = with(density) { bottomLabelArea.toPx() }
                            val topValuePx    = with(density) { topValueArea.toPx() }
                            val yAxisPx       = with(density) { yAxisArea.toPx() }
                            val barAreaH      = totalH - bottomLabelPx - topValuePx
                            val drawW         = totalW - yAxisPx
                            val n             = data.size
                            val groupW        = drawW / n
                            val barW          = groupW * 0.52f
                            val barRadius     = with(density) { 6.dp.toPx() }
                            val labelTextSize = with(density) { 10.sp.toPx() }
                            val valueTextSize = with(density) { 9.5.sp.toPx() }
                            val axisTextSize  = with(density) { 9.sp.toPx() }

                            // Eje Y: 4 etiquetas (0, 1/3, 2/3, máximo) + líneas guía
                            val axisPaint = android.graphics.Paint().apply {
                                color    = labelColorArgb
                                textSize = axisTextSize
                                textAlign = android.graphics.Paint.Align.RIGHT
                                isAntiAlias = true
                            }
                            val guidePaint = android.graphics.Paint().apply {
                                color = guideColorArgb
                                strokeWidth = with(density) { 1.dp.toPx() }
                            }
                            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { ratio ->
                                val y = topValuePx + barAreaH * (1f - ratio)
                                drawContext.canvas.nativeCanvas.drawText(
                                    formatCompactCurrency(axisMax * ratio),
                                    yAxisPx - with(density) { 6.dp.toPx() },
                                    y + axisTextSize / 3f,
                                    axisPaint
                                )
                                if (ratio > 0f) {
                                    drawContext.canvas.nativeCanvas.drawLine(yAxisPx, y, totalW, y, guidePaint)
                                }
                            }

                            // Barras y etiquetas
                            data.forEachIndexed { idx, stat ->
                                val ratio = (stat.amount / axisMax).toFloat().coerceIn(0f, 1f)
                                val barH  = barAreaH * ratio
                                val left  = yAxisPx + groupW * idx + (groupW - barW) / 2f
                                val top   = topValuePx + barAreaH - barH
                                val color = chartColors[idx % chartColors.size]

                                drawRoundRect(
                                    color        = color.copy(alpha = 0.18f),
                                    topLeft      = Offset(left, topValuePx),
                                    size         = Size(barW, barAreaH),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )
                                drawRoundRect(
                                    brush        = Brush.verticalGradient(
                                        colors = listOf(color.copy(alpha = 0.7f), color),
                                        startY = top,
                                        endY   = topValuePx + barAreaH
                                    ),
                                    topLeft      = Offset(left, top),
                                    size         = Size(barW, barH.coerceAtLeast(with(density) { 4.dp.toPx() })),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )

                                val centerX = yAxisPx + groupW * idx + groupW / 2f

                                drawContext.canvas.nativeCanvas.drawText(
                                    formatCompactCurrency(stat.amount),
                                    centerX,
                                    (top - with(density) { 4.dp.toPx() }).coerceAtLeast(topValuePx - with(density) { 2.dp.toPx() }),
                                    android.graphics.Paint().apply {
                                        this.color    = color.toArgb()
                                        this.textSize = valueTextSize
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias   = true
                                        isFakeBoldText = true
                                    }
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    stat.label,
                                    centerX,
                                    totalH - with(density) { 4.dp.toPx() },
                                    android.graphics.Paint().apply {
                                        this.color    = labelColorArgb
                                        this.textSize = labelTextSize
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias   = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Comparación con el mes anterior ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_month_comparison))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$ ${moneyFormat.format(uiState.currentMonthSpent)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val pct = uiState.monthOverMonthPct
                    if (pct == null) {
                        Text(
                            text = stringResource(R.string.stats_no_previous_month),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val isUp = pct >= 0
                        val color = if (isUp) Color(0xFFEF4444) else Color(0xFF10B981)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${if (isUp) "+" else ""}${pct.toInt()}% ${stringResource(R.string.stats_vs_last_month)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsGeneralTab.kt
git commit -m "feat: add StatsGeneralTab with modernized monthly chart and month comparison"
```

---

## Task 10: Crear `StatsBudgetTab.kt` (presupuesto, proyección, gasto por día de semana)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsBudgetTab.kt`

- [ ] **Step 1: Crear `StatsBudgetTab.kt`**

```kotlin
// Pestaña "Presupuesto": presupuesto del mes vs gasto real + proyección, y gasto por día de semana.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsBudgetTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    isDark: Boolean
) {
    val density    = LocalDensity.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── Presupuesto del mes ────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_budget_title))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.monthlyLimit <= 0.0) {
                        Text(
                            text = stringResource(R.string.stats_budget_no_limit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val pct = (uiState.currentMonthSpent / uiState.monthlyLimit).toFloat()
                        val barColor = when {
                            pct < 0.8f -> Color(0xFF10B981)
                            pct <= 1.0f -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        SegmentBar(progress = pct, color = barColor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$ ${moneyFormat.format(uiState.currentMonthSpent)} / $ ${moneyFormat.format(uiState.monthlyLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = barColor
                            )
                        }
                        val overLimit = uiState.projectedMonthSpent > uiState.monthlyLimit
                        Text(
                            text = "${stringResource(R.string.stats_projection)}: $ ${moneyFormat.format(uiState.projectedMonthSpent)}" +
                                if (overLimit) " — ${stringResource(R.string.stats_projection_over_limit)}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overLimit) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Gasto por día de la semana ────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_weekday_spending))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val data = uiState.weekdayStats
                    val maxVal = data.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
                    val labelColorArgb = labelColor.toArgb()

                    if (data.all { it.amount == 0.0 }) {
                        Text(
                            stringResource(R.string.stats_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val totalW = size.width
                            val totalH = size.height
                            val bottomLabelPx = with(density) { 18.dp.toPx() }
                            val barAreaH = totalH - bottomLabelPx
                            val n = data.size
                            val groupW = totalW / n
                            val barW = groupW * 0.5f
                            val barRadius = with(density) { 4.dp.toPx() }
                            val shortLabels = listOf("L", "M", "M", "J", "V", "S", "D")

                            data.forEachIndexed { idx, stat ->
                                val ratio = (stat.amount / maxVal).toFloat().coerceIn(0f, 1f)
                                val barH = (barAreaH * ratio).coerceAtLeast(with(density) { 4.dp.toPx() })
                                val left = groupW * idx + (groupW - barW) / 2f
                                val top  = barAreaH - barH

                                drawRoundRect(
                                    color        = Color(0xFF3B82F6),
                                    topLeft      = Offset(left, top),
                                    size         = Size(barW, barH),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    shortLabels[idx],
                                    left + barW / 2f,
                                    totalH - with(density) { 4.dp.toPx() },
                                    android.graphics.Paint().apply {
                                        this.color = labelColorArgb
                                        this.textSize = with(density) { 10.sp.toPx() }
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsBudgetTab.kt
git commit -m "feat: add StatsBudgetTab with budget progress and weekday spending chart"
```

---

## Task 11: Crear `StatsSupermarketsTab.kt` (gasto por súper con `SegmentBar` + ticket promedio)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsSupermarketsTab.kt`

- [ ] **Step 1: Crear `StatsSupermarketsTab.kt`**

```kotlin
// Pestaña "Supermercados": gasto por supermercado (barra custom de una sola pieza) y ticket promedio.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsSupermarketsTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    chartColors: List<Color>,
    isDark: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── By supermarket (con SegmentBar) ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_by_supermarket))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val total = uiState.supermarketStats.sumOf { it.amount }
                    if (uiState.supermarketStats.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.supermarketStats.forEachIndexed { idx, stat ->
                            val pct = if (total > 0) (stat.amount / total).toFloat() else 0f
                            val color = chartColors[idx % chartColors.size]
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Text(
                                            text = stat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$ ${moneyFormat.format(stat.amount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = color
                                        )
                                        Text(
                                            text = "${(pct * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                SegmentBar(progress = pct, color = color)
                            }
                            if (idx < uiState.supermarketStats.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Ticket promedio por supermercado ──────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_avg_ticket))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.avgTicketBySupermarket.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.avgTicketBySupermarket.forEachIndexed { idx, stat ->
                            val color = chartColors[idx % chartColors.size]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = stat.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$ ${moneyFormat.format(stat.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = color
                                )
                            }
                            if (idx < uiState.avgTicketBySupermarket.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsSupermarketsTab.kt
git commit -m "feat: add StatsSupermarketsTab with SegmentBar and average ticket"
```

---

## Task 12: Crear `StatsProductsTab.kt` (top productos, aumentos de precio, frecuencia/canasta)

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsProductsTab.kt`

- [ ] **Step 1: Crear `StatsProductsTab.kt`**

```kotlin
// Pestaña "Productos": top productos, mayores aumentos de precio y frecuencia/canasta.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsProductsTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    chartColors: List<Color>,
    isDark: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── Top products ──────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_top_products))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.topProducts.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.topProducts.forEachIndexed { idx, stat ->
                            val medalColor = when (idx) {
                                0 -> Color(0xFFFFB800)
                                1 -> Color(0xFFADB5BD)
                                2 -> Color(0xFFCD7F32)
                                else -> chartColors[idx % chartColors.size]
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(medalColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (idx) {
                                                0 -> "🥇"
                                                1 -> "🥈"
                                                2 -> "🥉"
                                                else -> "${idx + 1}"
                                            },
                                            style = if (idx < 3) MaterialTheme.typography.bodyMedium
                                                    else MaterialTheme.typography.labelLarge,
                                            color = medalColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Total gastado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "$ ${moneyFormat.format(stat.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = medalColor,
                                    maxLines = 1
                                )
                            }
                            if (idx < uiState.topProducts.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Productos con mayor aumento de precio ─────────
        item {
            SectionHeader(title = stringResource(R.string.stats_price_increases))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.priceIncreases.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.priceIncreases.forEachIndexed { idx, change ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = change.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "+${change.pctChange.toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                            if (idx < uiState.priceIncreases.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Frecuencia y tamaño de canasta ────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_purchase_frequency))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${uiState.purchaseCountThisMonth}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stats_purchases_this_month),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "%.1f".format(uiState.avgItemsPerPurchase),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stats_avg_items),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsProductsTab.kt
git commit -m "feat: add StatsProductsTab with price increases and purchase frequency"
```

---

## Task 13: Reescribir `StatsScreen.kt` con `TabRow` de 4 pestañas

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsScreen.kt`

- [ ] **Step 1: Reemplazar el contenido completo de `StatsScreen.kt`**

```kotlin
// Pantalla de estadísticas conectada al StatsViewModel (datos reales del backend).
// Organizada en 4 pestañas: General, Presupuesto, Supermercados y Productos.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.dotPatternBackground

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState     = viewModel.uiState.collectAsStateWithLifecycle().value
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    val chartColors = listOf(
        Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6)
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.stats_tab_general),
        stringResource(R.string.stats_tab_budget),
        stringResource(R.string.stats_tab_supermarkets),
        stringResource(R.string.stats_tab_products)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_stats),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> StatsGeneralTab(uiState, moneyFormat, chartColors, isDark)
                    1 -> StatsBudgetTab(uiState, moneyFormat, isDark)
                    2 -> StatsSupermarketsTab(uiState, moneyFormat, chartColors, isDark)
                    3 -> StatsProductsTab(uiState, moneyFormat, chartColors, isDark)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compilar el proyecto completo**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Correr toda la suite de tests unitarios**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (18 tests de `StatsCalculationsTest` + el `ExampleUnitTest` existente)

- [ ] **Step 4: Verificación manual en el emulador/dispositivo**

- Instalar y abrir la app: `./gradlew :app:installDebug`
- Ir a Estadísticas y verificar:
  - Las 4 pestañas se ven y cambian correctamente (General, Presupuesto, Supermercados, Productos)
  - El gráfico de evolución mensual muestra eje Y con escala, líneas guía y valores sobre las barras
  - "Gasto por supermercado" usa la barra `SegmentBar` sin gap/punto (una sola pieza)
  - "Presupuesto del mes" muestra la barra con color según %, y la proyección de fin de mes
  - Las nuevas secciones (comparación, día de semana, ticket promedio, aumentos de precio, frecuencia/canasta) muestran datos coherentes o el estado "Sin datos aún" si corresponde
  - Probar en modo claro y oscuro

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/stats/StatsScreen.kt
git commit -m "feat: restructure StatsScreen into 4 tabs (General, Presupuesto, Supermercados, Productos)"
```
