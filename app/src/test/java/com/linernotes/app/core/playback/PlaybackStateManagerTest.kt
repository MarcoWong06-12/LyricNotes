package com.linernotes.app.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class PlaybackStateManagerTest {

    @Test
    fun updateMetadataAndCover_preservesHigherQualityCoverAndClockOnSameTrack() {
        val fakeNow = AtomicLong(1_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        val initial = manager.updateMetadataAndCover(
            title = "Cruel Summer",
            artist = "Taylor Swift",
            album = "Lover",
            durationMs = 178_426L,
            coverUrl = "https://itunes.example.com/hd.jpg",
            coverQuality = CoverQuality.ITUNES_HD,
            packageName = "com.spotify.music",
            trackId = "spotify:track:1"
        )
        manager.updatePlaybackClock(
            positionMs = 24_500L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            lastUpdateTimeMs = 1_000L
        )
        manager.updatePlaybackModes(isShuffleActive = true, repeatMode = 2)
        val queue = listOf(QueueTrackItem(id = 10L, title = "Lover", artist = "Taylor Swift"))
        manager.updateQueueItems(queue)

        val refreshed = manager.updateMetadataAndCover(
            title = "Cruel Summer",
            artist = "Taylor Swift",
            album = "Lover (Deluxe)",
            durationMs = 178_426L,
            coverUrl = "https://spotify.example.com/medium.jpg",
            coverQuality = CoverQuality.NATIVE_METADATA,
            packageName = "com.spotify.music"
        )

        assertEquals(initial.trackEpoch, refreshed.trackEpoch)
        assertEquals("https://itunes.example.com/hd.jpg", refreshed.coverUrl)
        assertEquals("Lover (Deluxe)", refreshed.album)
        assertEquals("spotify:track:1", refreshed.trackId)
        assertEquals(24_500L, refreshed.currentPositionMs)
        assertTrue(refreshed.isPlaying)
        assertTrue(refreshed.isShuffleActive)
        assertEquals(2, refreshed.repeatMode)
        assertEquals(queue, refreshed.queueItems)
    }

    @Test
    fun updateMetadataAndCover_trackChangeIncrementsEpochAndClearsOldCover() {
        val fakeNow = AtomicLong(2_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        val first = manager.updateMetadataAndCover(
            title = "Track One",
            artist = "Artist A",
            durationMs = 200_000L,
            coverUrl = "https://example.com/track1.jpg",
            coverQuality = CoverQuality.ITUNES_HD,
            packageName = "com.apple.android.music"
        )
        manager.updatePlaybackClock(positionMs = 80_000L, isPlaying = true, lastUpdateTimeMs = 2_000L)

        fakeNow.set(5_000L)
        val second = manager.updateMetadataAndCover(
            title = "Track Two",
            artist = "Artist B",
            durationMs = 180_000L,
            coverUrl = null,
            packageName = "com.apple.android.music"
        )

        assertEquals(first.trackEpoch + 1L, second.trackEpoch)
        assertNull(second.coverUrl)
        assertEquals(0L, second.currentPositionMs)
        assertEquals(5_000L, second.lastUpdateTimeMs)
        assertTrue(second.isPlaying)
        assertEquals(MediaSourceApp.APPLE_MUSIC, second.sourceApp)
    }

    @Test
    fun updatePlaybackClock_preservesMetadataCoverAndQueue() {
        val fakeNow = AtomicLong(10_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        val meta = manager.updateMetadataAndCover(
            title = "Midnight Rain",
            artist = "Taylor Swift",
            album = "Midnights",
            durationMs = 174_000L,
            coverUrl = "https://example.com/midnights.jpg",
            coverQuality = CoverQuality.NATIVE_METADATA,
            packageName = "com.spotify.music"
        )
        val queue = listOf(QueueTrackItem(id = 1L, title = "Anti-Hero", artist = "Taylor Swift"))
        manager.updateQueueItems(queue)

        val clocked = manager.updatePlaybackClock(
            positionMs = 45_000L,
            isPlaying = true,
            playbackSpeed = 1.25f,
            lastUpdateTimeMs = 9_500L
        )

        assertEquals(meta.trackEpoch, clocked.trackEpoch)
        assertEquals("https://example.com/midnights.jpg", clocked.coverUrl)
        assertEquals("Midnights", clocked.album)
        assertEquals(queue, clocked.queueItems)
        assertEquals(45_000L, clocked.currentPositionMs)
        assertEquals(1.25f, clocked.playbackSpeed, 0.0001f)
        assertEquals(9_500L, clocked.lastUpdateTimeMs)
        assertEquals(clocked.toClock(), manager.playbackClock.value)
    }

    @Test
    fun concurrentClockCoverAndQueueUpdates_preserveHdCoverAndFinalMetadata() {
        val fakeNow = AtomicLong(1_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        val initial = manager.updateMetadataAndCover(
            title = "Concurrent Song",
            artist = "Test Artist",
            album = "Initial Album",
            durationMs = 300_000L,
            coverUrl = null,
            packageName = "com.netease.cloudmusic"
        )
        val epoch = initial.trackEpoch

        val pool = Executors.newFixedThreadPool(4)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)
        val failure = AtomicReference<Throwable?>(null)

        val expectedQueue = listOf(
            QueueTrackItem(id = 1L, title = "Next A", artist = "Artist"),
            QueueTrackItem(id = 2L, title = "Next B", artist = "Artist")
        )

        pool.submit {
            try {
                startLatch.await()
                for (i in 1..100) {
                    fakeNow.addAndGet(500L)
                    manager.updatePlaybackClock(
                        positionMs = i * 1_000L,
                        isPlaying = true,
                        playbackSpeed = 1.0f,
                        lastUpdateTimeMs = fakeNow.get()
                    )
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        pool.submit {
            try {
                startLatch.await()
                manager.updateCoverIfBetter(
                    expectedTitle = "Concurrent Song",
                    expectedArtist = "Test Artist",
                    coverUrl = "/cache/notif.png",
                    quality = CoverQuality.NOTIFICATION_LOCAL,
                    expectedEpoch = epoch
                )
                manager.updateCoverIfBetter(
                    expectedTitle = "Concurrent Song",
                    expectedArtist = "Test Artist",
                    coverUrl = "https://hd.example.com/cover.jpg",
                    quality = CoverQuality.ITUNES_HD,
                    expectedEpoch = epoch
                )
                // Lower quality cover must never downgrade ITUNES_HD
                assertFalse(
                    manager.updateCoverIfBetter(
                        expectedTitle = "Concurrent Song",
                        expectedArtist = "Test Artist",
                        coverUrl = "https://genius.example.com/low.jpg",
                        quality = CoverQuality.GENIUS,
                        expectedEpoch = epoch
                    )
                )
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        pool.submit {
            try {
                startLatch.await()
                for (i in 1..40) {
                    manager.updateMetadataAndCover(
                        title = "Concurrent Song",
                        artist = "Test Artist",
                        album = "Initial Album",
                        durationMs = 300_000L,
                        coverUrl = if (i % 2 == 0) "https://native.example.com/art.jpg" else null,
                        coverQuality = CoverQuality.NATIVE_METADATA,
                        packageName = "com.netease.cloudmusic"
                    )
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        pool.submit {
            try {
                startLatch.await()
                for (i in 1..30) {
                    manager.updateQueueItems(listOf(QueueTrackItem(id = i.toLong(), title = "Temp $i")))
                    manager.updatePlaybackModes(isShuffleActive = i % 2 == 0, repeatMode = i % 3)
                }
                manager.updateQueueItems(expectedQueue)
                manager.updatePlaybackModes(isShuffleActive = true, repeatMode = 1)
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertNull(failure.get())
        val finalState = manager.playbackState.value
        assertEquals(epoch, finalState.trackEpoch)
        assertEquals("https://hd.example.com/cover.jpg", finalState.coverUrl)
        assertEquals(expectedQueue, finalState.queueItems)
        assertTrue(finalState.isShuffleActive)
        assertEquals(1, finalState.repeatMode)
    }

    @Test
    fun clockEstimation_usesAnchorTimeSpeedAndClampsBounds() {
        val state = TrackPlaybackState(
            title = "Anchor Test",
            artist = "Artist",
            durationMs = 100_000L,
            currentPositionMs = 20_000L,
            isPlaying = true,
            playbackSpeed = 1.5f,
            lastUpdateTimeMs = 10_000L
        )

        // Anchor was 4 seconds earlier at 1.5x speed -> 20_000 + 6_000 = 26_000
        assertEquals(26_000L, state.getEstimatedPositionMs(nowMs = 14_000L))
        assertEquals(26_000L, state.toClock().getEstimatedPositionMs(nowMs = 14_000L))

        // Clock jitter before anchor does not rewind below anchor position
        assertEquals(20_000L, state.getEstimatedPositionMs(nowMs = 9_500L))

        // Upper clamp to durationMs
        assertEquals(100_000L, state.getEstimatedPositionMs(nowMs = 90_000L))

        // Paused state ignores elapsed time
        val paused = state.copy(isPlaying = false)
        assertEquals(20_000L, paused.getEstimatedPositionMs(nowMs = 50_000L))
    }

    @Test
    fun updatePlaybackClock_deduplicatesWithinToleranceAndAppliesSeeks() {
        val fakeNow = AtomicLong(1_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        manager.updateMetadataAndCover(
            title = "Clock Sync",
            artist = "Artist",
            durationMs = 200_000L,
            packageName = "com.spotify.music"
        )
        manager.updatePlaybackClock(
            positionMs = 10_000L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            lastUpdateTimeMs = 1_000L
        )

        // 2s later, estimated is 12_000ms; report of 12_150ms is within 250ms tolerance -> deduplicated
        fakeNow.set(3_000L)
        val afterSmallDrift = manager.updatePlaybackClock(
            positionMs = 12_150L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            lastUpdateTimeMs = 3_000L
        )
        assertEquals(10_000L, afterSmallDrift.currentPositionMs)
        assertEquals(1_000L, afterSmallDrift.lastUpdateTimeMs)

        // Seek to 55_000ms at anchor 3_000L -> accepted immediately
        val afterSeek = manager.updatePlaybackClock(
            positionMs = 55_000L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            lastUpdateTimeMs = 3_000L
        )
        assertEquals(55_000L, afterSeek.currentPositionMs)
        assertEquals(3_000L, afterSeek.lastUpdateTimeMs)
    }

    @Test
    fun spotifyBroadcastGuard_blocksBackgroundHijackOnMetadataAndPlayback() {
        val fakeNow = AtomicLong(5_000L)
        val manager = PlaybackStateManager { fakeNow.get() }

        manager.updateMetadataAndCover(
            title = "Apple Track",
            artist = "Apple Artist",
            durationMs = 210_000L,
            packageName = "com.apple.android.music"
        )
        manager.updatePlaybackClock(positionMs = 30_000L, isPlaying = true, lastUpdateTimeMs = 5_000L)

        // Background Spotify metadatachanged while Apple Music is playing must be rejected
        val blockedPlaying = manager.updateMetadataAndCover(
            title = "Spotify Background",
            artist = "Other Artist",
            durationMs = 190_000L,
            packageName = SpotifyBroadcastReceiver.SPOTIFY_PACKAGE,
            sourceApp = MediaSourceApp.SPOTIFY,
            precondition = { cur -> SpotifyBroadcastReceiver.shouldAcceptSpotifyBroadcast(cur, isPlayingExtra = false) }
        )
        assertEquals("Apple Track", blockedPlaying.title)
        assertEquals(MediaSourceApp.APPLE_MUSIC, blockedPlaying.sourceApp)

        // Even when Apple Music is paused, a non-playing Spotify metadata broadcast must not hijack
        manager.updatePlaybackClock(positionMs = 30_000L, isPlaying = false, lastUpdateTimeMs = 6_000L)
        val blockedPaused = manager.updateMetadataAndCover(
            title = "Spotify Background",
            artist = "Other Artist",
            durationMs = 190_000L,
            packageName = SpotifyBroadcastReceiver.SPOTIFY_PACKAGE,
            sourceApp = MediaSourceApp.SPOTIFY,
            precondition = { cur -> SpotifyBroadcastReceiver.shouldAcceptSpotifyBroadcast(cur, isPlayingExtra = false) }
        )
        assertEquals("Apple Track", blockedPaused.title)

        // Only when other player is paused AND Spotify explicitly starts playing is takeover allowed
        val allowedTakeover = manager.updateMetadataAndCover(
            title = "Spotify Active",
            artist = "Spotify Artist",
            durationMs = 190_000L,
            packageName = SpotifyBroadcastReceiver.SPOTIFY_PACKAGE,
            sourceApp = MediaSourceApp.SPOTIFY,
            precondition = { cur -> SpotifyBroadcastReceiver.shouldAcceptSpotifyBroadcast(cur, isPlayingExtra = true) }
        )
        assertEquals("Spotify Active", allowedTakeover.title)
        assertEquals(MediaSourceApp.SPOTIFY, allowedTakeover.sourceApp)
    }

    @Test
    fun isMusicApp_andQueueCoverRetention_coverAllTargetPlayersAndExcludeFalsePositives() {
        val supported = listOf(
            "com.spotify.music",
            "com.apple.android.music",
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "cn.kuwo.player",
            "com.google.android.apps.youtube.music",
            "com.aspiro.tidal",
            "deezer.android.app",
            "com.soundcloud.android",
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.maxmpz.audioplayer",
            "tv.plex.labs.plexamp",
            "com.neutroncode.mp"
        )
        supported.forEach { pkg ->
            assertTrue("Expected music app: $pkg", MediaPlaybackSyncService.isMusicApp(pkg))
        }

        val rejected = listOf(
            null,
            "",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.example.alarmplayer",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.mxtech.videoplayer.ad",
            "com.android.soundrecorder",
            "com.google.android.youtube"
        )
        rejected.forEach { pkg ->
            assertFalse("Expected non-music rejection: $pkg", MediaPlaybackSyncService.isMusicApp(pkg))
        }

        assertEquals(24, MediaPlaybackSyncService.computeQueueCoverRetention(10))
        assertEquals(54, MediaPlaybackSyncService.computeQueueCoverRetention(50))
    }
}
