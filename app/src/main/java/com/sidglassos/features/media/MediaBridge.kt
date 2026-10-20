package com.sidglassos.features.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.sidglassos.features.widgets.WidgetRefresh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaSnapshot(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val artwork: Bitmap? = null,
    val active: Boolean = false,
    val canPlayPause: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val accessGranted: Boolean = false,
)

/**
 * Local, callback-driven media information. No notification contents are inspected.
 * Access comes only from the user's enabled listener, not MEDIA_CONTENT_CONTROL.
 * https://developer.android.com/reference/android/media/session/MediaSessionManager
 */
object MediaBridge {
    private const val MAX_OBSERVATIONS = 16
    private const val REBIND_INTERVAL_MS = 10_000L
    private val lock = Any()
    private val mutableState = MutableStateFlow(MediaSnapshot())
    val state: StateFlow<MediaSnapshot> = mutableState.asStateFlow()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null
    private var owner: MediaAccessService? = null
    private var manager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val observations = linkedMapOf<MediaSession.Token, Observation>()
    private var selected: MediaController? = null
    private var lastRebindAt: Long? = null

    private class Observation(val controller: MediaController) {
        lateinit var callback: MediaController.Callback
        var playback: Int? = null
        var actions: Long = 0L
        var title: String = "Unknown track"
        var artist: String = ""
        var artwork: Bitmap? = null
    }

    fun hasAccess(context: Context): Boolean = try {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(component(context)) == true
    } catch (_: RuntimeException) { false }

    fun refreshAccess(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
        if (!hasAccess(context)) {
            lastRebindAt = null
            clearLocked(accessGranted = false)
        } else if (owner == null) {
            // Android may still be binding the enabled listener. Do not query until connected.
            publishLocked(MediaSnapshot(accessGranted = true))
            requestRebindLocked(context)
        } else {
            attachLocked()
        }
    }

    /** Opens system access settings only when an explicit UI action calls this method. */
    fun openAccessSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            if (launchSettings(context, detail)) return true
        }
        return launchSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** True means a supported request was dispatched; playback changes only through callbacks. */
    fun playPause(): Boolean = request(Command.PLAY_PAUSE)
    fun previous(): Boolean = request(Command.PREVIOUS)
    fun next(): Boolean = request(Command.NEXT)

    internal fun onConnected(service: MediaAccessService) = synchronized(lock) {
        clearLocked(accessGranted = false)
        val context = service.applicationContext
        appContext = context
        if (!hasAccess(context)) return@synchronized
        owner = service
        attachLocked()
    }

    internal fun onDisconnected(service: MediaAccessService) = synchronized(lock) {
        if (owner === service) clearLocked(accessGranted = hasAccess(service))
    }

    private fun component(context: Context) = ComponentName(context, MediaAccessService::class.java)

    private fun attachLocked() {
        val connection = owner ?: return
        val context = appContext ?: return
        if (!hasAccess(context)) {
            clearLocked(accessGranted = false)
            return
        }
        try {
            val sessionManager = manager ?: context.getSystemService(MediaSessionManager::class.java)
            if (sessionManager == null) {
                clearLocked(accessGranted = true, keepConnection = true)
                return
            }
            manager = sessionManager
            if (sessionsListener == null) {
                lateinit var listener: MediaSessionManager.OnActiveSessionsChangedListener
                listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
                    synchronized(lock) {
                        // Ignore callbacks queued from an earlier, failed registration.
                        if (owner === connection && sessionsListener === listener) {
                            if (!hasAccess(context)) clearLocked(accessGranted = false)
                            else replaceSessionsLocked(sessions.orEmpty())
                        }
                    }
                }
                sessionsListener = listener
                sessionManager.addOnActiveSessionsChangedListener(listener, component(context), handler)
            }
            readSessionsLocked()
        } catch (_: RuntimeException) {
            clearAfterFailureLocked()
        }
    }

    private fun requestRebindLocked(context: Context) {
        val now = SystemClock.elapsedRealtime()
        val last = lastRebindAt
        if (last != null && now - last < REBIND_INTERVAL_MS) return
        lastRebindAt = now
        // Documented as safe before connection/after disconnection. This neither
        // grants access nor starts a retry loop; only a later refresh can retry.
        try { NotificationListenerService.requestRebind(component(context)) } catch (_: RuntimeException) { }
    }

    private fun readSessionsLocked() {
        val context = appContext ?: return
        if (owner == null || !hasAccess(context)) {
            clearLocked(accessGranted = false)
            return
        }
        try {
            replaceSessionsLocked(manager?.getActiveSessions(component(context)).orEmpty())
        } catch (_: SecurityException) {
            clearAfterFailureLocked()
        } catch (_: RuntimeException) {
            // A dead media service/session is an empty state, not stale private content.
            clearAfterFailureLocked()
        }
    }

    private fun replaceSessionsLocked(controllers: List<MediaController>) {
        val connection = owner ?: return
        try {
            // Bound retained thumbnails/callbacks while keeping a playing session
            // eligible even when it appears later in the manager's priority list.
            val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            val desired = (sequenceOf(playing).filterNotNull() + controllers.asSequence())
                .distinctBy { it.sessionToken }.take(MAX_OBSERVATIONS).toList()
            val desiredTokens = desired.map { it.sessionToken }.toSet()
            observations.keys.filter { it !in desiredTokens }.forEach { token ->
                observations.remove(token)?.let { unregister(it) }
            }
            for (controller in desired) {
                if (controller.sessionToken in observations) continue
                val observation = Observation(controller)
                observation.callback = object : MediaController.Callback() {
                    private fun update(change: () -> Unit) = synchronized(lock) {
                        if (owner !== connection || observations[controller.sessionToken] !== observation) return@synchronized
                        val context = appContext ?: return@synchronized
                        if (!hasAccess(context)) {
                            clearLocked(accessGranted = false)
                            return@synchronized
                        }
                        try {
                            change()
                            publishSelectedLocked()
                        } catch (_: SecurityException) {
                            clearAfterFailureLocked()
                        } catch (_: RuntimeException) {
                            observations.remove(controller.sessionToken)
                            unregister(observation)
                            publishSelectedLocked()
                        }
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) = update {
                        observation.playback = state?.state
                        observation.actions = state?.actions ?: 0L
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) = update {
                        readTrack(observation, metadata)
                    }

                    override fun onSessionDestroyed() = update {
                        observations.remove(controller.sessionToken)
                        unregister(observation)
                    }
                }
                observations[controller.sessionToken] = observation
                controller.registerCallback(observation.callback, handler)
                val playback = controller.playbackState
                observation.playback = playback?.state
                observation.actions = playback?.actions ?: 0L
                readTrack(observation, controller.metadata)
            }
            // Preserve the manager's priority order even when an existing token moves.
            val ordered = desired.mapNotNull { observations[it.sessionToken] }
            observations.clear()
            ordered.forEach { observations[it.controller.sessionToken] = it }
            publishSelectedLocked()
        } catch (_: SecurityException) {
            clearAfterFailureLocked()
        } catch (_: RuntimeException) {
            clearAfterFailureLocked()
        }
    }

    private fun publishSelectedLocked() {
        val list = observations.values.toList()
        val observation = MediaPolicy.selectedIndex(list.map { it.playback })?.let(list::get)
        selected = observation?.controller
        if (observation == null) {
            publishLocked(MediaSnapshot(accessGranted = true))
            return
        }
        val controls = MediaPolicy.actions(observation.playback, observation.actions)
        publishLocked(MediaSnapshot(
            title = observation.title, artist = observation.artist,
            playing = observation.playback == PlaybackState.STATE_PLAYING,
            artwork = observation.artwork, active = true,
            canPlayPause = controls.playPause, canPrevious = controls.previous, canNext = controls.next,
            accessGranted = true,
        ))
    }

    private fun readTrack(observation: Observation, metadata: MediaMetadata?) {
        fun text(vararg keys: String): String = keys.asSequence()
            .mapNotNull { metadata?.getText(it)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()?.take(240).orEmpty()
        observation.title = text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE)
            .ifEmpty { "Unknown track" }
        observation.artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val source = try {
            sequenceOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON).mapNotNull { metadata?.getBitmap(it) }.firstOrNull()
        } catch (_: RuntimeException) { null }
        observation.artwork = thumbnail(source)
        // Only strings and the bounded thumbnail survive this method; never retain MediaMetadata.
    }

    private fun thumbnail(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null
        return try {
            val (width, height) = MediaPolicy.artworkSize(source.width, source.height) ?: return null
            if (source.width == width && source.height == height) source.copy(Bitmap.Config.ARGB_8888, false)
            else Bitmap.createScaledBitmap(source, width, height, true)
        } catch (_: RuntimeException) { null }
        catch (_: OutOfMemoryError) { null }
        // Source and published bitmaps are never recycled: external readers may still hold them.
    }

    private fun publishLocked(snapshot: MediaSnapshot) {
        val old = mutableState.value
        val sameFields = old.copy(artwork = null) == snapshot.copy(artwork = null)
        val sameArtwork = old.artwork === snapshot.artwork || (old.artwork != null && snapshot.artwork != null &&
            try { old.artwork.sameAs(snapshot.artwork) } catch (_: RuntimeException) { false })
        if (sameFields && sameArtwork) return
        mutableState.value = snapshot
        appContext?.let { context ->
            try { WidgetRefresh.refreshMusic(context) } catch (_: RuntimeException) { /* Widget refresh is best effort. */ }
        }
    }

    private fun clearAfterFailureLocked() {
        val granted = appContext?.let(::hasAccess) == true
        // Failed IPC does not mean the OS disconnected this listener. Preserve
        // that identity while dropping all private data and failed registrations,
        // so a later refreshAccess can reattach if the user grant is still valid.
        clearLocked(accessGranted = granted, keepConnection = granted)
    }

    private fun clearLocked(accessGranted: Boolean, keepConnection: Boolean = false) {
        if (!keepConnection || !accessGranted) owner = null
        selected = null
        observations.values.forEach(::unregister)
        observations.clear()
        val listener = sessionsListener
        sessionsListener = null
        if (listener != null) {
            try { manager?.removeOnActiveSessionsChangedListener(listener) } catch (_: RuntimeException) { }
        }
        manager = null
        publishLocked(MediaSnapshot(accessGranted = accessGranted))
    }

    private fun unregister(observation: Observation) {
        try { observation.controller.unregisterCallback(observation.callback) } catch (_: RuntimeException) { }
        observation.artwork = null
    }

    private enum class Command { PLAY_PAUSE, PREVIOUS, NEXT }

    private fun request(command: Command): Boolean = synchronized(lock) {
        val context = appContext ?: return@synchronized false
        if (owner == null || !hasAccess(context)) {
            clearLocked(accessGranted = hasAccess(context))
            return@synchronized false
        }
        val controller = selected ?: return@synchronized false
        try {
            val playback = controller.playbackState
            val actions = MediaPolicy.actions(playback?.state, playback?.actions ?: 0L)
            when (command) {
                Command.PLAY_PAUSE -> {
                    if (!actions.playPause) return@synchronized false
                    if (playback?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
                    else controller.transportControls.play()
                }
                Command.PREVIOUS -> {
                    if (!actions.previous) return@synchronized false
                    controller.transportControls.skipToPrevious()
                }
                Command.NEXT -> {
                    if (!actions.next) return@synchronized false
                    controller.transportControls.skipToNext()
                }
            }
            // No optimistic state change. The selected session owns the eventual result.
            true
        } catch (_: SecurityException) {
            clearAfterFailureLocked()
            false
        } catch (_: RuntimeException) {
            clearAfterFailureLocked()
            false
        }
    }

    private fun launchSettings(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: RuntimeException) { false }
}
