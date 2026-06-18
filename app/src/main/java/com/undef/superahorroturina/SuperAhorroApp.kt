// Punto de entrada de la app para Hilt + WorkManager con inyección de dependencias.
// @HiltAndroidApp genera el componente raíz de Hilt.
// Configuration.Provider delega la creación del WorkManager a Hilt para que los
// HiltWorker puedan recibir dependencias inyectadas.
package com.undef.superahorroturina

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SuperAhorroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var themeDataStore: ThemeDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val enabled = themeDataStore.priceAlertsEnabled.first()
            if (enabled) {
                WorkManager.getInstance(this@SuperAhorroApp).enqueueUniquePeriodicWork(
                    PriceAlertWorker.WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
                        .addTag(PriceAlertWorker.WORK_TAG)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            }
        }
    }
}
