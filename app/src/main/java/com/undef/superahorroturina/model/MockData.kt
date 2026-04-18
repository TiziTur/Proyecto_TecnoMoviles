package com.undef.superahorroturina.model

import java.time.LocalDate
import java.time.LocalTime

object MockData {

    val currentUser = User(
        id = 1,
        firstName = "Tiziano",
        lastName = "Turina",
        email = "tiziano@mail.com",
        phone = "+54 9 11 1234-5678"
    )

    val supermarkets = listOf(
        "Carrefour",
        "Dia",
        "Coto",
        "Jumbo",
        "Vea",
        "La Anónima",
        "Walmart",
        "Changomas",
        "Makro"
    )

    val purchases = listOf(
        Purchase(
            id = 1,
            date = LocalDate.of(2026, 4, 15),
            time = LocalTime.of(10, 30),
            supermarket = "Carrefour",
            total = 18750.50,
            products = listOf(
                Product(1, "7790895000084", "Leche La Serenísima 1L", "Leche entera larga vida", 950.00, 2),
                Product(2, "7790040503703", "Yogur Ser Frutado x4", "Yogur con frutas, pack x4 unidades", 2200.00, 1),
                Product(3, "7790040503710", "Manteca La Serenísima 200g", "Manteca sin sal", 1450.00, 1),
                Product(4, "7790040100001", "Arroz Gallo Oro 1kg", "Arroz largo fino", 1200.00, 2),
                Product(5, "7790040100002", "Fideos Matarazzo 500g", "Fideos spaghetti N°15", 850.00, 3),
                Product(6, "7790040100003", "Aceite Cocinero 900ml", "Aceite de girasol", 2100.00, 2)
            )
        ),
        Purchase(
            id = 2,
            date = LocalDate.of(2026, 4, 10),
            time = LocalTime.of(16, 45),
            supermarket = "Dia",
            total = 9320.00,
            products = listOf(
                Product(7, "7790040200001", "Pan lactal Bimbo 500g", "Pan de molde blanco", 1500.00, 1),
                Product(8, "7790040200002", "Jamón cocido Paladini 100g", "Fiambre", 950.00, 2),
                Product(9, "7790040200003", "Queso en fetas Sancor 150g", "Queso pategrás", 1800.00, 1),
                Product(10, "7790040200004", "Gaseosa Coca-Cola 2.25L", "Bebida cola", 2200.00, 1)
            )
        ),
        Purchase(
            id = 3,
            date = LocalDate.of(2026, 4, 5),
            time = LocalTime.of(11, 15),
            supermarket = "Coto",
            total = 24600.75,
            products = listOf(
                Product(11, "7790040300001", "Pollo entero Granja del Sol", "Pollo fresco entero aprox 2kg", 5800.00, 1),
                Product(12, "7790040300002", "Carne picada especial 1kg", "Carne vacuna picada", 7200.00, 1),
                Product(13, "7790040300003", "Papa blanca 2kg", "Bolsa de papas", 1800.00, 1),
                Product(14, "7790040300004", "Tomate kg", "Tomate redondo", 1200.00, 2),
                Product(15, "7790040300005", "Detergente Magistral 750ml", "Lavavajillas", 1650.00, 2)
            )
        ),
        Purchase(
            id = 4,
            date = LocalDate.of(2026, 3, 28),
            time = LocalTime.of(9, 0),
            supermarket = "Jumbo",
            total = 32100.00,
            products = listOf(
                Product(16, "7790040400001", "Café Nescafé Clásico 170g", "Café instantáneo", 3200.00, 1),
                Product(17, "7790040400002", "Yerba Taragüí 1kg", "Yerba mate con palo", 2800.00, 2),
                Product(18, "7790040400003", "Mermelada Arcor frutilla 454g", "Mermelada de frutilla", 1500.00, 1),
                Product(19, "7790040400004", "Shampoo Head & Shoulders 375ml", "Shampoo anticaspa", 3900.00, 1),
                Product(20, "7790040400005", "Papel higiénico Elite 4u", "Pack de 4 rollos doble hoja", 2800.00, 2)
            )
        ),
        Purchase(
            id = 5,
            date = LocalDate.of(2026, 3, 20),
            time = LocalTime.of(18, 30),
            supermarket = "Vea",
            total = 11450.25,
            products = listOf(
                Product(21, "7790040500001", "Azúcar Ledesma 1kg", "Azúcar refinada", 1100.00, 2),
                Product(22, "7790040500002", "Harina 000 Cañuelas 1kg", "Harina de trigo", 950.00, 2),
                Product(23, "7790040500003", "Aceite de oliva Cocinero 500ml", "Extra virgen", 4200.00, 1)
            )
        ),
        Purchase(
            id = 6,
            date = LocalDate.of(2026, 3, 12),
            time = LocalTime.of(14, 0),
            supermarket = "Carrefour",
            total = 21800.00,
            products = listOf(
                Product(24, "7790040600001", "Jabón en polvo Ala 3kg", "Detergente ropa", 5500.00, 1),
                Product(25, "7790040600002", "Lavandina Ayudín 2L", "Hipoclorito de sodio", 1200.00, 2),
                Product(26, "7790040600003", "Papel film Rollo 30m", "Film transparente cocina", 900.00, 1),
                Product(27, "7790040600004", "Agua mineral Villavicencio 2L", "Sin gas", 800.00, 4)
            )
        ),
        Purchase(
            id = 7,
            date = LocalDate.of(2026, 2, 28),
            time = LocalTime.of(12, 20),
            supermarket = "Dia",
            total = 8900.00,
            products = listOf(
                Product(28, "7790040700001", "Galletitas Oreo 222g", "Galletas de chocolate", 1800.00, 2),
                Product(29, "7790040700002", "Alfajor Havanna x6", "Alfajores de maicena", 3200.00, 1),
                Product(30, "7790040700003", "Jugo Tang sobre x20", "Jugo en polvo sabor naranja", 1500.00, 1)
            )
        ),
        Purchase(
            id = 8,
            date = LocalDate.of(2026, 2, 15),
            time = LocalTime.of(10, 0),
            supermarket = "Coto",
            total = 15600.50,
            products = listOf(
                Product(31, "7790040800001", "Pescado merluza filet 500g", "Merluza congelada", 4800.00, 1),
                Product(32, "7790040800002", "Salsa de tomate Arcor 520g", "Salsa lista para usar", 1200.00, 2),
                Product(33, "7790040800003", "Crema de leche Sancor 200ml", "Crema para cocinar", 950.00, 2)
            )
        )
    )

    // Estadísticas por mes (para gráfico de evolución)
    val monthlyExpenses = listOf(
        StatSummary("Sep", 28500.00),
        StatSummary("Oct", 34200.00),
        StatSummary("Nov", 41000.00),
        StatSummary("Dic", 52000.00),
        StatSummary("Ene", 38500.00),
        StatSummary("Feb", 24500.50),
        StatSummary("Mar", 69950.75),
        StatSummary("Abr", 52070.50)
    )

    // Estadísticas por supermercado
    val expensesBySupermarket = listOf(
        StatSummary("Carrefour", 40550.50),
        StatSummary("Dia", 18220.00),
        StatSummary("Coto", 40201.25),
        StatSummary("Jumbo", 32100.00),
        StatSummary("Vea", 11450.25),
        StatSummary("La Anónima", 0.0)
    ).filter { it.amount > 0 }

    // Productos más comprados
    val topProducts = listOf(
        StatSummary("Leche La Serenísima", 4.0),
        StatSummary("Arroz Gallo Oro", 3.0),
        StatSummary("Fideos Matarazzo", 3.0),
        StatSummary("Aceite Cocinero", 2.0),
        StatSummary("Yerba Taragüí", 2.0)
    )

    // Total gastado este mes
    val totalThisMonth: Double get() = purchases
        .filter { it.date.monthValue == 4 && it.date.year == 2026 }
        .sumOf { it.total }

    // Últimas 5 compras
    val recentPurchases: List<Purchase> get() = purchases.take(5)
}
