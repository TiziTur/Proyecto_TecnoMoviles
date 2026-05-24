// PriceAlertWorker.kt — Worker de WorkManager que corre diariamente.
// Consulta el endpoint de comparativa de precios y lanza una notificación
// si encuentra productos donde el usuario podría ahorrar significativamente.
// Se inyecta con HiltWorker para acceder a ApiService y SessionDataStore.
package com.undef.superahorroturina.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.undef.superahorroturina.MainActivity
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: ApiService,
    private val session: SessionDataStore
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID   = "price_alerts"
        const val CHANNEL_NAME = "Alertas de precios"
        const val WORK_TAG     = "price_alert_daily"
        // Solo notificar si el ahorro es mayor a este % del precio
        private const val MIN_SAVINGS_PCT = 10
    }

    override suspend fun doWork(): Result {
        return try {
            val token = session.bearerToken.first()
            // Si no hay sesión, no hacer nada
            if (token == "Bearer ") return Result.success()

            val response = api.getPriceComparisons(token)
            if (!response.isSuccessful) return Result.retry()

            val comparisons = response.body()?.comparisons ?: emptyList()

            // Filtrar productos con ahorro significativo
            val goodDeals = comparisons.filter { it.savingsPct >= MIN_SAVINGS_PCT }

            if (goodDeals.isNotEmpty()) {
                val topDeal = goodDeals.first()
                val message = if (goodDeals.size == 1) {
                    "${topDeal.productName} es ${topDeal.savingsPct}% más barato en ${topDeal.cheapestAt.replaceFirstChar { it.uppercaseChar() }}"
                } else {
                    "${topDeal.productName} y ${goodDeals.size - 1} producto(s) más están más baratos en otros supermercados"
                }
                showNotification(
                    title   = "💰 Oportunidades de ahorro",
                    message = message
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Crear el canal (Android 8+, idempotente)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertas cuando un producto está más barato en otro supermercado"
        }
        manager.createNotificationChannel(channel)

        // Intent para abrir la comparativa al tocar la notificación
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "price_comparison")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
