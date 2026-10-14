package org.jellyfin.mobile.livetv

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import org.jellyfin.mobile.R

/**
 * Publishes a launcher long-press shortcut ("Live TV"), mirroring the
 * Archive Mode shortcut.
 */
object LiveTvShortcut {
    private const val SHORTCUT_ID = "live_tv"

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (shortcutManager.dynamicShortcuts.any { it.id == SHORTCUT_ID }) return
        val intent = Intent(context, LiveTvActivity::class.java).apply {
            action = Intent.ACTION_VIEW
        }
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Live TV")
            .setLongLabel("Watch live TV channels")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        try {
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        } catch (e: Exception) {
            // Shortcut limits etc. — Live TV stays reachable from the connect screen
        }
    }
}
