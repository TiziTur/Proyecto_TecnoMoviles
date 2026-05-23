// Acá armo el grafo de navegación completo con NavHost de Compose Navigation.
// Uso popUpTo con inclusive = true para limpiar el backstack en flujos de auth,
// así el usuario no puede volver a Splash o Login con el botón atrás del sistema.
// Los navArguments con NavType.IntType permiten pasar IDs entre pantallas de forma segura.
// Las transiciones usan fadeIn/fadeOut + slideIn/slideOut para una experiencia fluida y profesional.
package com.undef.superahorroturina.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.undef.superahorroturina.ui.screens.auth.LoginScreen
import com.undef.superahorroturina.ui.screens.auth.RegisterScreen
import com.undef.superahorroturina.ui.screens.history.HistoryScreen
import com.undef.superahorroturina.ui.screens.home.HomeScreen
import com.undef.superahorroturina.ui.screens.product.ProductFormScreen
import com.undef.superahorroturina.ui.screens.profile.ProfileScreen
import com.undef.superahorroturina.ui.screens.purchase.NewPurchaseScreen
import com.undef.superahorroturina.ui.screens.purchase.PurchaseDetailScreen
import com.undef.superahorroturina.ui.screens.settings.SettingsScreen
import com.undef.superahorroturina.ui.screens.splash.SplashScreen
import com.undef.superahorroturina.ui.screens.stats.StatsScreen

// Duración estándar de las transiciones (ms)
private const val NAV_ANIM_DURATION = 300

// Entrar: desliza desde la derecha + fade in
private val enterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() },
    animationSpec  = tween(NAV_ANIM_DURATION)
) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))

// Salir: desliza hacia la izquierda + fade out
private val exitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    animationSpec = tween(NAV_ANIM_DURATION)
) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))

// Pop enter: retorna desde la izquierda + fade in
private val popEnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    animationSpec  = tween(NAV_ANIM_DURATION)
) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))

// Pop exit: sale hacia la derecha + fade out
private val popExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() },
    animationSpec = tween(NAV_ANIM_DURATION)
) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController      = navController,
        startDestination   = Routes.Splash.route,
        enterTransition    = { enterTransition },
        exitTransition     = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition  = { popExitTransition }
    ) {
        // ── Auth ─────────────────────────────────────────────────
        composable(
            route           = Routes.Splash.route,
            enterTransition = { fadeIn(animationSpec = tween(400)) },
            exitTransition  = { fadeOut(animationSpec = tween(400)) }
        ) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.Register.route)
                }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Main ─────────────────────────────────────────────────
        composable(Routes.Home.route) {
            HomeScreen(
                onNavigateToNewPurchase = { navController.navigate(Routes.NewPurchase.route) },
                onNavigateToPurchaseDetail = { id ->
                    navController.navigate(Routes.PurchaseDetail.createRoute(id))
                },
                onNavigateToHistory = { navController.navigate(Routes.History.route) },
                onNavigateToStats = { navController.navigate(Routes.Stats.route) },
                onNavigateToProfile = { navController.navigate(Routes.Profile.route) },
                onNavigateToSettings = { navController.navigate(Routes.Settings.route) },
                onLogout = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPurchaseDetail = { id ->
                    navController.navigate(Routes.PurchaseDetail.createRoute(id))
                }
            )
        }

        composable(Routes.Stats.route) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Purchase ─────────────────────────────────────────────
        composable(Routes.NewPurchase.route) {
            NewPurchaseScreen(
                purchaseId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPurchaseDetail = { newId ->
                    // Al crear una compra nueva, ir directo al detalle para agregar productos
                    navController.navigate(Routes.PurchaseDetail.createRoute(newId)) {
                        popUpTo(Routes.NewPurchase.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.EditPurchase.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.IntType })
        ) { backStack ->
            val purchaseId = backStack.arguments?.getInt("purchaseId")
            NewPurchaseScreen(
                purchaseId = purchaseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PurchaseDetail.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.IntType })
        ) { backStack ->
            val purchaseId = backStack.arguments?.getInt("purchaseId") ?: 1
            PurchaseDetailScreen(
                purchaseId = purchaseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Routes.EditPurchase.createRoute(id))
                },
                onNavigateToAddProduct = { id ->
                    navController.navigate(Routes.NewProduct.createRoute(id))
                },
                onNavigateToEditProduct = { purchId, prodId ->
                    navController.navigate(Routes.EditProduct.createRoute(purchId, prodId))
                }
            )
        }

        // ── Product ──────────────────────────────────────────────
        composable(
            route = Routes.NewProduct.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.IntType })
        ) { backStack ->
            val purchaseId = backStack.arguments?.getInt("purchaseId") ?: 0
            ProductFormScreen(
                purchaseId = purchaseId,
                productId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EditProduct.route,
            arguments = listOf(
                navArgument("purchaseId") { type = NavType.IntType },
                navArgument("productId") { type = NavType.IntType }
            )
        ) { backStack ->
            val purchaseId = backStack.arguments?.getInt("purchaseId") ?: 0
            val productId  = backStack.arguments?.getInt("productId")
            ProductFormScreen(
                purchaseId = purchaseId,
                productId = productId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
