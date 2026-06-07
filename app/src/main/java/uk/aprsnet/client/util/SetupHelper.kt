package uk.aprsnet.client.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import uk.aprsnet.client.MainActivity
import uk.aprsnet.client.R

/**
 * One-shot setup helpers: battery-optimisation exemption and pin-to-home-
 * screen shortcut. Both are no-ops on platforms that do not support them
 * so calling code does not need to gate on API level itself.
 *
 * Why these exist:
 *  - Modern Android puts apps into Doze when the screen is off, which
 *    severs the WebSocket and stops the foreground service from receiving
 *    APRS messages reliably. The battery exemption is the standard fix.
 *  - Since Android 8.0 the system does NOT auto-pin app icons to the home
 *    screen on install; only the app drawer entry is created. Asking the
 *    launcher to pin a shortcut surfaces a system prompt that the user can
 *    accept with one tap, putting the app where they expect it.
 */
object SetupHelper {

    /**
     * True if the app is exempt from battery optimisation (= can run the
     * foreground service and WebSocket without being aggressively killed
     * when the screen is off). Always true on pre-M Android.
     */
    fun isBatteryUnrestricted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * Launch the system battery-optimisation request dialog for our package.
     * The user can tap Allow once and never see this again. Falls back to
     * the generic battery-optimisation settings screen if the direct prompt
     * is unavailable (some manufacturer skins block ACTION_REQUEST_IGNORE_*).
     */
    fun requestBatteryUnrestricted(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(direct)
        }.recoverCatching {
            // Fallback: open the generic battery-optimisation list so the
            // user can find us themselves.
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * Ask the current launcher to pin a shortcut for this app to the home
     * screen. Shows a system prompt; user taps Add. No-op on launchers that
     * do not support pinning. On pre-Oreo, no-op.
     */
    fun requestPinHomeShortcut(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val shortcut = ShortcutInfoCompat.Builder(ctx, "aprs_net_launcher")
            .setShortLabel("APRS Net")
            .setLongLabel("APRS Net")
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(ctx, shortcut, null)
        }.getOrDefault(false)
    }
}