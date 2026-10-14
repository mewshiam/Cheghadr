package ir.cheghadr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.cheghadr.core.common.DigitStyle
import ir.cheghadr.core.common.MoneyFormatter
import ir.cheghadr.core.model.AlertRule
import ir.cheghadr.core.model.AlertType
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.Quote
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification channels and price-alert notification rendering.
 * Quiet-hours enforcement and permission checks happen in the worker that
 * calls this class.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_PRICE_ALERTS,
                context.getString(R.string.channel_price_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_price_alerts_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Posts the notification for a fired alert. [openIntent] is supplied by the
     * app module (activity deep link with the asset id).
     */
    fun notifyAlert(
        rule: AlertRule,
        asset: Asset,
        quote: Quote,
        openIntent: Intent,
        digitStyle: DigitStyle,
        notificationId: Int = (rule.id % 100_000L).toInt() + 1000,
    ) {
        if (!areNotificationsEnabled()) return
        ensureChannels()

        val threshold = MoneyFormatter.formatPrice(rule.threshold, digitStyle, minFraction = 0, maxFraction = 8)
        val current = MoneyFormatter.formatPrice(
            if (rule.type == AlertType.PERCENT_UP || rule.type == AlertType.PERCENT_DOWN) {
                quote.changePercent ?: BigDecimal.ZERO
            } else {
                quote.price
            },
            digitStyle,
        )

        val text = when (rule.type) {
            AlertType.PRICE_ABOVE -> context.getString(R.string.notif_alert_above, threshold, current)
            AlertType.PRICE_BELOW -> context.getString(R.string.notif_alert_below, threshold, current)
            AlertType.PERCENT_UP -> context.getString(R.string.notif_alert_pct_up, threshold, current)
            AlertType.PERCENT_DOWN -> context.getString(R.string.notif_alert_pct_down, threshold, current)
        }

        val pending = PendingIntent.getActivity(
            context,
            rule.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PRICE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_cheghadr)
            .setContentTitle(context.getString(R.string.notif_alert_title, asset.nameFa))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    companion object {
        const val CHANNEL_PRICE_ALERTS = "price_alerts"
        const val EXTRA_ASSET_ID = "ir.cheghadr.extra.ASSET_ID"
    }
}
