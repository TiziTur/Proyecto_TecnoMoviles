// La única Activity de la app. Con Compose no necesito múltiples Activities —
// todo el flujo de navegación lo manejo con NavGraph dentro de setContent.
// @AndroidEntryPoint es necesario para que Hilt pueda inyectar en ViewModels que
// se crean desde composables dentro de esta Activity.
package com.undef.superahorroturina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.undef.superahorroturina.ui.navigation.NavGraph
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuperAhorroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
