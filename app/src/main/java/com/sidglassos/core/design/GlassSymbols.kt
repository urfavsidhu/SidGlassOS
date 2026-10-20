package com.sidglassos.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Open Material symbols framed by our own glass geometry, never third-party app artwork. */
object GlassSymbols {
    val categories = linkedMapOf(
        "Phone" to Icons.Outlined.Call,
        "Messages" to Icons.Outlined.ChatBubbleOutline,
        "Camera" to Icons.Outlined.PhotoCamera,
        "Gallery" to Icons.Outlined.PhotoLibrary,
        "Settings" to Icons.Outlined.Settings,
        "Browser" to Icons.Outlined.Language,
        "Files" to Icons.Outlined.FolderOpen,
        "Music" to Icons.Outlined.MusicNote,
        "Calendar" to Icons.Outlined.CalendarMonth,
        "Clock" to Icons.Outlined.Schedule,
        "Calculator" to Icons.Outlined.Calculate,
    )
    fun icon(name: String): ImageVector = categories[name] ?: Icons.Outlined.Apps
}
