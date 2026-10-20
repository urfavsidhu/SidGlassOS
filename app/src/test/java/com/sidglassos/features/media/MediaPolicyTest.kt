package com.sidglassos.features.media

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class MediaPolicyTest {
    @Test fun playbackTakesPriorityAndEmptyListsHaveNoSelection() {
        assertEquals(1, MediaPolicy.selectedIndex(listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_PLAYING)))
        assertEquals(0, MediaPolicy.selectedIndex(listOf(null, PlaybackState.STATE_PAUSED)))
        assertNull(MediaPolicy.selectedIndex(emptyList()))
    }

    @Test fun playAndPauseAreEnabledOnlyForTheCurrentDirection() {
        assertTrue(MediaPolicy.actions(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY).playPause)
        assertFalse(MediaPolicy.actions(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY).playPause)
        assertTrue(MediaPolicy.actions(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE).playPause)
        assertFalse(MediaPolicy.actions(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE).playPause)
        assertTrue(MediaPolicy.actions(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY_PAUSE).playPause)
        assertTrue(MediaPolicy.actions(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE).playPause)
    }

    @Test fun skipActionsRemainIndependent() {
        val previous = MediaPolicy.actions(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        assertTrue(previous.previous)
        assertFalse(previous.next)
        assertFalse(previous.playPause)
        val next = MediaPolicy.actions(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_SKIP_TO_NEXT)
        assertTrue(next.next)
        assertFalse(next.previous)
        assertEquals(MediaActions(false, false, false), MediaPolicy.actions(null, 0L))
    }

    @Test fun artworkBoundsPreserveAspectRatioWithoutUpscaling() {
        assertEquals(256 to 128, MediaPolicy.artworkSize(4096, 2048))
        assertEquals(128 to 256, MediaPolicy.artworkSize(2048, 4096))
        assertEquals(64 to 32, MediaPolicy.artworkSize(64, 32))
        assertEquals(1 to 256, MediaPolicy.artworkSize(1, 10000))
        assertNull(MediaPolicy.artworkSize(0, 240))
    }
}
