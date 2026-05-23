// La única Activity de la app. Lee la preferencia de dark mode desde ThemeDataStore
// y la pasa a SuperAhorroTheme — así el cambio en Settings se aplica globalmente.
package com.undef.superahorroturina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.navigation.NavGraph
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeDataStore: ThemeDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by themeDataStore.isDarkMode.collectAsState(initial = false)
            SuperAhorroTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
