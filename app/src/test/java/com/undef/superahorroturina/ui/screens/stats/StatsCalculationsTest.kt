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
