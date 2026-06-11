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
        assertEquals("Sábado", result[5].label)
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
}
