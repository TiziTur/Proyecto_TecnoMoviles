// Punto de entrada de la app para Hilt + WorkManager con inyección de dependencias.
// @HiltAndroidApp genera el componente raíz de Hilt.
// Configuration.Provider delega la creación del WorkManager a Hilt para que los
// HiltWorker puedan recibir dependencias inyectadas.
package com.undef.superahorroturina

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SuperAhorroApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailyPriceAlert()
    }

    private fun scheduleDailyPriceAlert() {
        val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(
            repeatInterval      = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .addTag(PriceAlertWorker.WORK_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PriceAlertWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,  // No re-programar si ya existe
            request
        )
    }
}
