package com.undef.superahorroturina.ui.navigation

sealed class Routes(val route: String) {
    // Auth flow
    object Splash   : Routes("splash")
    object Login    : Routes("login")
    object Register : Routes("register")

    // Main flow (bottom nav)
    object Home     : Routes("home")
    object History  : Routes("history")
    object Stats    : Routes("stats")
    object Profile  : Routes("profile")

    // Purchase flow
    object NewPurchase    : Routes("new_purchase")
    object EditPurchase   : Routes("edit_purchase/{purchaseId}") {
        fun createRoute(purchaseId: Int) = "edit_purchase/$purchaseId"
    }
    object PurchaseDetail : Routes("purchase_detail/{purchaseId}") {
        fun createRoute(purchaseId: Int) = "purchase_detail/$purchaseId"
    }

    // Product flow
    object NewProduct  : Routes("new_product/{purchaseId}") {
        fun createRoute(purchaseId: Int) = "new_product/$purchaseId"
    }
    object EditProduct : Routes("edit_product/{purchaseId}/{productId}") {
        fun createRoute(purchaseId: Int, productId: Int) = "edit_product/$purchaseId/$productId"
    }

    // Settings
    object Settings : Routes("settings")
}
