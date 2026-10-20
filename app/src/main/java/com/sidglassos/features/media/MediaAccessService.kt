package com.sidglassos.features.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android binds this only after the user grants notification-listener access.
 * The grant is used to discover MediaSessions; notification payloads are ignored.
 */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaBridge.onConnected(this)
    }

    override fun onListenerDisconnected() {
        MediaBridge.onDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        MediaBridge.onDisconnected(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(notification: StatusBarNotification?) { /* Intentionally unread. */ }
    override fun onNotificationRemoved(notification: StatusBarNotification?) { /* Intentionally unread. */ }
}
