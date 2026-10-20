package com.sidglassos.features.media

import android.media.session.PlaybackState
import kotlin.math.roundToInt

internal data class MediaActions(val playPause: Boolean, val previous: Boolean, val next: Boolean)

/** Small policies kept independent of Android services and bitmaps for JVM verification. */
internal object MediaPolicy {
    fun selectedIndex(states: List<Int?>): Int? {
        val playing = states.indexOfFirst { it == PlaybackState.STATE_PLAYING }
        return if (playing >= 0) playing else states.indices.firstOrNull()
    }

    fun actions(state: Int?, supported: Long): MediaActions {
        val action = if (state == PlaybackState.STATE_PLAYING) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        return MediaActions(
            playPause = supported and (action or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
            previous = supported and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            next = supported and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
        )
    }

    fun artworkSize(width: Int, height: Int, maximum: Int = 256): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || maximum <= 0) return null
        val scale = (maximum.toDouble() / maxOf(width, height)).coerceAtMost(1.0)
        return (width * scale).roundToInt().coerceIn(1, maximum) to
            (height * scale).roundToInt().coerceIn(1, maximum)
    }
}
