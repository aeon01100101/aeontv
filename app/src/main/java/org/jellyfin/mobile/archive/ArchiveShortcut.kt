package org.jellyfin.mobile.archive

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import org.jellyfin.mobile.R

/**
 * Publishes a launcher long-press shortcut ("Archive Mode") so the archive.org
 * mode is reachable any time, including while connected to a server.
 */
object ArchiveShortcut {
    private const val SHORTCUT_ID = "archive_mode"

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (shortcutManager.dynamicShortcuts.any { it.id == SHORTCUT_ID }) return
        val intent = Intent(context, ArchiveActivity::class.java).apply {
            action = Intent.ACTION_VIEW
        }
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Archive Mode")
            .setLongLabel("Watch free classics from archive.org")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        try {
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        } catch (e: Exception) {
            // Shortcut limits etc. — Archive Mode stays reachable from the connect screen
        }
    }
}
