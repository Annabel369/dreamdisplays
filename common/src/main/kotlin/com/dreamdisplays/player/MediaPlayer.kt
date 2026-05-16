package com.dreamdisplays.player

import com.dreamdisplays.Initializer
import com.dreamdisplays.display.DisplayScreen
import com.dreamdisplays.ffmpeg.FFmpegBinary
import com.dreamdisplays.player.pipeline.AudioSink
import com.dreamdisplays.player.pipeline.PlaybackClock
import com.dreamdisplays.player.pipeline.VideoFramePipe
import com.dreamdisplays.player.process.MediaProcess
import com.dreamdisplays.player.stream.MediaStreamSelector
import com.dreamdisplays.player.util.MediaUtil
import com.dreamdisplays.player.util.daemon
import com.dreamdisplays.player.util.joinSafely
import com.dreamdisplays.utils.GeneralUtil
import com.dreamdisplays.ytdlp.YtDlp
import com.dreamdisplays.ytdlp.YtStream
import com.mojang.blaze3d.textures.GpuTexture
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.core.BlockPos
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Media player for a single YouTube video.
 *
 * Orchestrates stream selection, two FFmpeg processes (video + audio), a [VideoFramePipe]
 * for frame buffering, an [AudioSink] for PCM playback, a [PlaybackClock] for A/V sync,
 * and a watchdog that restarts stalled streams automatically.
 */
class MediaPlayer(
    private val youtubeUrl: String,
    private val lang: String,
    private val displayScreen: DisplayScreen,
) {

    companion object {

        val DEBUG: Boolean = System.getProperty("dreamdisplays.debug")?.toBoolean() == true
                || System.getenv("DREAMDISPLAYS_DEBUG").let { it == "1" || it.equals("true", ignoreCase = true) }

        var captureSamples: Boolean = true

        internal val samplesIn = AtomicLong()
        internal val framesToGpu = AtomicLong()
        internal val framesDropped = AtomicLong()

        private const val STOP_WAIT_TIMEOUT_SECONDS = 3L
        private const val STATS_INTERVAL_MS = 2000L
        private const val MAX_FETCH_RETRIES = 3
        private const val WATCHDOG_TIMEOUT_NS = 30_000_000_000L
        private const val WATCHDOG_CHECK_INTERVAL_MS = 5000L

        private val RETRY_BACKOFF_MS = longArrayOf(1000, 3000, 8000)

        private val INIT_THREAD_COUNTER = AtomicInteger()
        private val INIT_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        ) { r -> daemon(r, "MediaPlayer-init-${INIT_THREAD_COUNTER.incrementAndGet()}") }
    }

    /**
     * All currently-selected and available streams. Replaced atomically on quality change or
     * re-initialization, never partially updated.
     */
    private data class StreamSet(
        val availableVideo: List<YtStream>,
        val availableAudio: List<YtStream>,
        val currentVideo: YtStream,
        val currentAudio: YtStream,
    )

    /**
     * Live `FFmpeg` processes and reader threads for one stream start.
     * Replaced in full on each restart; never partially replaced.
     */
    private data class StreamSession(
        val videoProcess: Process,
        val audioProcess: Process,
        val videoThread: Thread,
        val audioThread: Thread,
        val videoStop: AtomicBoolean,
        val audioStop: AtomicBoolean,
    )

    private val debugLabel = "${displayScreen.uuid}/${Integer.toHexString(System.identityHashCode(this))}"

    private val terminated = AtomicBoolean(false)
    private val restartPending = AtomicBoolean(false)

    private val clock = PlaybackClock()
    private val audio = AudioSink(debugLabel)
    private val video = VideoFramePipe(debugLabel)

    private val controlExecutor = Executors.newSingleThreadExecutor { daemon(it, "MediaPlayer-ctrl") }
    private val fitTextureTask = Runnable { displayScreen.fitTexture() }
    private val initCallbacks = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var streams: StreamSet? = null
    @Volatile private var session: StreamSession? = null

    @Volatile private var _initialized = false
    @Volatile private var liveStream = false
    @Volatile private var seekable = false
    @Volatile private var durationHintNanos = 0L
    private var lastQuality = 0
    @Volatile private var fetchRetries = 0
    @Volatile private var playing = false

    @Volatile private var userVolume = Initializer.config.defaultDisplayVolume
    @Volatile private var lastAttenuation = 1.0
    @Volatile private var brightness = 1.0

    @Volatile private var watchdogExecutor: ScheduledExecutorService? = null
    @Volatile private var statsExecutor: ScheduledExecutorService? = null

    init { INIT_EXECUTOR.submit { initialize() } }

    /** Resumes playback from the current seek position. No-op if already playing. */
    fun play() = safeExecute(::doPlay)

    /** Pauses playback, capturing the current position for later resume. */
    fun pause() = safeExecute(::doPause)

    /** Stops playback permanently; the instance must not be used after this call. */
    fun stop() {
        if (terminated.getAndSet(true)) return
        val future = runCatching { controlExecutor.submit(::doStop) }.getOrNull()
        runCatching { future?.get(STOP_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.onFailure { doStop() }
        controlExecutor.shutdownNow()
    }

    /** Seeks to an absolute position in nanos. [fire] triggers [DisplayScreen.afterSeek]. */
    fun seekTo(nanos: Long, fire: Boolean) = safeExecute { doSeek(nanos, fire) }

    /** Seeks [s] seconds relative to the current position. */
    fun seekRelative(s: Double) = safeExecute {
        if (!_initialized || !seekable) return@safeExecute
        val max = (getDuration() - 1).coerceAtLeast(0)
        if (max <= 0) return@safeExecute
        doSeek((getCurrentTime() + (s * 1e9).toLong()).coerceIn(0, max), true)
    }

    /** Current playback position in nanos. Falls back to seek offset when paused or not started. */
    fun getCurrentTime(): Long {
        if (!_initialized || !playing) return clock.seekOffsetNanos
        return clock.currentTime()
    }

    /** Stream duration in nanos, or 0 for live streams. */
    fun getDuration(): Long = if (liveStream) 0L else durationHintNanos

    /** Returns true once stream selection is complete and playback has started. */
    fun isInitialized(): Boolean = _initialized

    /**
     * Runs [callback] immediately if already initialized, otherwise queues it to run once
     * initialization completes. The callback is called on the init thread.
     */
    fun whenInitialized(callback: () -> Unit) {
        if (_initialized) { callback(); return }
        initCallbacks.add(callback)
        if (_initialized && initCallbacks.remove(callback)) callback()
    }

    /**
     * Returns true if the selected stream is a livestream. Livestreams start playing immediately
     * and may not support seeking. Note that this is based on `yt-dlp`'s metadata and may not be perfectly reliable.
     */
    fun isLive(): Boolean = liveStream

    /** Returns true if the selected stream supports seeking. Note that some livestreams may be seekable. */
    fun canSeek(): Boolean = _initialized && seekable

    /** Returns true if the media clock is running (i.e. playback has started and the first frame has arrived). */
    fun isClockRunning(): Boolean = clock.isRunning

    /** True once the first frame has been decoded and is ready for GPU upload. */
    fun textureFilled(): Boolean = video.textureFilled()

    /** Uploads the latest decoded frame to [texture]. Must be called from the render thread. */
    fun updateFrame(texture: GpuTexture) = video.updateFrame(texture, displayScreen.textureWidth,
        displayScreen.textureHeight)

    /** Sets the user-controlled volume (0.0-2.0). Distance attenuation is applied on top. */
    fun setVolume(volume: Float) {
        userVolume = volume.toDouble().coerceIn(0.0, 2.0)
        audio.currentVolume = userVolume * lastAttenuation
    }

    /** Sets the brightness multiplier applied to each frame before GPU upload (0.0–2.0). */
    fun setBrightness(brightness: Float) {
        this.brightness = brightness.toDouble().coerceIn(0.0, 2.0)
    }

    /** Returns the list of available video quality levels (in pixels) for the current stream. */
    fun getAvailableQualities(): List<Int> {
        val cap = if (Initializer.isPremium) 2160 else 1080
        return streams?.availableVideo.orEmpty().asSequence()
            .mapNotNull { it.resolution }
            .map { MediaStreamSelector.parseQualityValue(it, Int.MAX_VALUE) }
            .filter { it != Int.MAX_VALUE && it <= cap }
            .distinct().sorted().toList()
    }

    /** Switches to the closest available stream for [quality] (e.g. "720p"). */
    fun setQuality(quality: String) = safeExecute { changeQuality(quality) }

    /**
     * Updates distance-based volume attenuation. Call every tick from the game thread.
     *
     * @param playerPos  player block position
     * @param maxRadius  radius beyond which the screen is silent
     */
    fun tick(playerPos: BlockPos, maxRadius: Double) {
        if (!_initialized) return
        val attenuation = (1.0 - minOf(1.0, displayScreen.getDistanceToScreen(playerPos) / maxRadius)).let { it * it }
        if (abs(attenuation - lastAttenuation) > 1e-5) {
            lastAttenuation = attenuation
            audio.currentVolume = userVolume * attenuation
        }
    }

    /**
     * Fetches stream metadata via `yt-dlp`, picks the best video and audio tracks, and launches
     * playback. Runs on a background thread from [INIT_EXECUTOR]. On failure, marks the screen as
     * errored; on success, drains any queued [whenInitialized] callbacks.
     */
    private fun initialize() {
        var success = false
        try {
            val videoId = GeneralUtil.extractVideoId(youtubeUrl)?.takeIf { it.isNotEmpty() }
                ?: return errorOut("[MediaPlayer] Could not extract video ID from URL: $youtubeUrl.")
            if (FFmpegBinary.getPath() == null) return errorOut("[MediaPlayer] FFmpeg binary not available.")

            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
            val all = YtDlp.fetch(cleanUrl).takeIf { it.isNotEmpty() }
                ?: return errorOut("[MediaPlayer] No streams available for $cleanUrl.")
            if (terminated.get()) return

            liveStream = all.any(YtStream::isLive)
            seekable = !liveStream && all.any(YtStream::isSeekable)
            durationHintNanos = all.maxOfOrNull(YtStream::durationNanos) ?: 0L

            val videoStreams = all.filter(YtStream::hasVideo)
            val audioStreams = all.filter(YtStream::hasAudio)
            val requestedQuality = MediaStreamSelector.parseQualityValue(displayScreen.quality, 720)
            val pickedVideo = MediaStreamSelector.pickVideo(videoStreams, requestedQuality) ?: videoStreams.firstOrNull()
                ?: return errorOut("[MediaPlayer] No usable streams for $cleanUrl.")
            val pickedAudio = MediaStreamSelector.pickAudio(audioStreams, lang, pickedVideo)
                ?: return errorOut("[MediaPlayer] No usable streams for $cleanUrl.")

            streams = StreamSet(videoStreams, audioStreams, pickedVideo, pickedAudio)
            lastQuality = MediaStreamSelector.parseQuality(pickedVideo)
            fetchRetries = 0
            _initialized = true
            success = true

            if (DEBUG) {
                LoggingManager.info("[MediaPlayer $debugLabel] video=$pickedVideo audio=$pickedAudio")
                LoggingManager.info("[MediaPlayer $debugLabel] live=$liveStream seekable=$seekable dur=$durationHintNanos")
                startStatsReporter()
            }
            safeExecute { if (!terminated.get()) startStreams(pickedVideo, pickedAudio, 0) }
        } catch (e: Exception) {
            LoggingManager.error("[MediaPlayer] Failed to initialize", e)
            displayScreen.errored = true
        } finally {
            drainInitCallbacks(run = success)
        }
    }

    /**
     * Stops any active session, then starts new `FFmpeg` video and audio processes at [offsetNanos].
     * Wires up [VideoFramePipe] and [AudioSink] with the current clock and brightness callbacks,
     * and kicks off the watchdog. Must be called from the control executor thread.
     */
    private fun startStreams(pickedVideo: YtStream, pickedAudio: YtStream, offsetNanos: Long) {
        if (terminated.get()) return
        stopSession()

        val ffmpeg = FFmpegBinary.getPath() ?: return errorOut("[MediaPlayer $debugLabel] FFmpeg binary not available.")
        clock.reset(offsetNanos)

        val tw = displayScreen.textureWidth; val th = displayScreen.textureHeight
        val q = if (lastQuality > 0) lastQuality else MediaStreamSelector.parseQuality(pickedVideo)
        val (w, h) = if (tw > 0 && th > 0) tw to th
                     else MediaStreamSelector.qualityToDims(q).let { it[0] to it[1] }

        if (DEBUG) LoggingManager.info("[MediaPlayer $debugLabel] starting FFmpeg ${w}x${h} offset=${offsetNanos / 1_000_000L}ms.")

        try {
            val vStop = AtomicBoolean(); val aStop = AtomicBoolean()
            val vp = MediaProcess.buildVideo(ffmpeg, pickedVideo.url, w, h, offsetNanos)
            val ap = MediaProcess.buildAudio(ffmpeg, pickedAudio.url, offsetNanos, AudioSink.SAMPLE_RATE)
            val vt = video.start(
                proc = vp, w = w, h = h, seekOffsetNanos = offsetNanos,
                sourceFps = pickedVideo.fps ?: 30.0, stopFlag = vStop, terminated = terminated,
                getAudioClock = { clock.audioClockNanos(audio.framePosition, AudioSink.SAMPLE_RATE) },
                onFirstFrame = { clock.markFirstFrame() }, getBrightness = { brightness },
                onEos = ::handleStreamEnd, fitTexture = fitTextureTask,
            )
            val at = audio.start(ap, terminated, aStop)
            session = StreamSession(vp, ap, vt, at, vStop, aStop)
            playing = true
            startWatchdog()
        } catch (e: IOException) {
            LoggingManager.error("[MediaPlayer $debugLabel] Failed to start FFmpeg", e)
            displayScreen.errored = true
        }
    }

    /**
     * Signals both stop flags, destroys the `FFmpeg` processes, closes the audio line,
     * joins the reader threads, and clears [session]. Also cancels the watchdog.
     * Safe to call when no session is active.
     */
    private fun stopSession() {
        playing = false
        cancelExecutor(watchdogExecutor).also { watchdogExecutor = null }
        session?.let { s ->
            s.videoStop.set(true); s.audioStop.set(true)
            MediaProcess.gracefulDestroy(s.videoProcess)
            MediaProcess.gracefulDestroy(s.audioProcess)
            audio.stop()
            joinSafely(s.videoThread); joinSafely(s.audioThread)
        }
        session = null
    }

    /**
     * Called by [VideoFramePipe] when a stream finishes (normal EOS or error).
     * Decides whether to retry (with or without cache invalidation), loop the video,
     * or mark the screen as permanently errored. Always runs on the reader thread,
     * but delegates restarts to the control executor via [safeExecute].
     */
    private fun handleStreamEnd(stderr: String, normalEos: Boolean) {
        if (terminated.get()) return

        val is403or404 = "403" in stderr || "Forbidden" in stderr || "404" in stderr || "Not Found" in stderr
        if ((is403or404 || MediaUtil.isTransientError(stderr)) && fetchRetries < MAX_FETCH_RETRIES) {
            scheduleRetry(is403or404); return
        }

        if (normalEos && liveStream && fetchRetries < MAX_FETCH_RETRIES) {
            LoggingManager.warn("[MediaPlayer $debugLabel] Live EOS, retrying...")
            scheduleRetry(true); return
        }

        if (normalEos && !liveStream) {
            if (restartPending.compareAndSet(false, true)) {
                safeExecute {
                    try {
                        withStreams { v, a ->
                            if (!terminated.get() && !displayScreen.isPaused) {
                                clock.reset(0); startStreams(v, a, 0); displayScreen.afterSeek()
                            }
                        }
                    } finally { restartPending.set(false) }
                }
            }
            return
        }

        if (stderr.isNotEmpty()) LoggingManager.error("[MediaPlayer $debugLabel] Unrecoverable: ${MediaUtil.truncate(stderr)}.")
        displayScreen.errored = true
    }

    /**
     * Schedules a re-initialization attempt after an exponential backoff delay.
     * If [invalidateCache] is true, the `yt-dlp` cache is purged first so fresh stream
     * URLs are fetched (needed for 403 / 404 errors where the signed URL has expired).
     */
    private fun scheduleRetry(invalidateCache: Boolean) {
        val delayMs = RETRY_BACKOFF_MS[fetchRetries.coerceAtMost(RETRY_BACKOFF_MS.lastIndex)]
        LoggingManager.warn("[MediaPlayer $debugLabel] ${if (invalidateCache) "Cache invalidated" else "Transient error"} — retry ${++fetchRetries}/$MAX_FETCH_RETRIES in ${delayMs}ms.")
        if (invalidateCache) YtDlp.invalidateCache(youtubeUrl)
        _initialized = false
        INIT_EXECUTOR.submit {
            runCatching { Thread.sleep(delayMs) }.onFailure { Thread.currentThread().interrupt(); return@submit }
            if (!terminated.get()) initialize()
        }
    }

    /** Starts `FFmpeg` from the current seek offset. No-op if already playing or not yet initialized. */
    private fun doPlay() {
        if (!_initialized || terminated.get() || playing) return
        withStreams { v, a -> startStreams(v, a, clock.seekOffsetNanos) }
    }

    /**
     * Captures the current position (from the audio line frame counter when available, otherwise
     * from the wall clock), stops the session, and stores it as the new seek offset for resume.
     */
    private fun doPause() {
        if (!playing) return
        val fp = audio.framePosition
        clock.seekOffsetNanos = if (fp >= 0) clock.audioClockNanos(fp, AudioSink.SAMPLE_RATE) else clock.currentTime()
        playing = false
        stopSession()
    }

    /**
     * Full teardown: clears the frame buffer, cancels stats, stops the session, and nulls [streams].
     * After this returns, the player is in a clean idle state. Called both from the control executor
     * and directly from [stop] if the executor is unavailable.
     */
    private fun doStop() {
        _initialized = false
        video.clear()
        cancelExecutor(statsExecutor).also { statsExecutor = null }
        stopSession()
        streams = null
    }

    /**
     * Moves the seek offset to [nanos] and, if playing, restarts `FFmpeg` from that position.
     * Optionally fires [DisplayScreen.afterSeek] so the screen can reset its overlay state.
     */
    private fun doSeek(nanos: Long, fire: Boolean) {
        if (!_initialized || !seekable) return
        clock.seekOffsetNanos = nanos
        withStreams { v, a -> if (playing) startStreams(v, a, nanos) }
        if (fire) displayScreen.afterSeek()
    }

    /**
     * Picks the closest available stream to [desired] quality. If the URL differs from the current
     * stream, updates [streams] via [StreamSet.copy] and restarts `FFmpeg` (or repositions the seek
     * offset if paused). No-op when the quality is already active or unavailable.
     */
    private fun changeQuality(desired: String) {
        val ss = streams ?: return
        val target = MediaStreamSelector.parseQualityValue(desired, -1)
        if (target < 0 || target == lastQuality) return
        val best = MediaStreamSelector.pickVideo(ss.availableVideo, target)
            ?.takeIf { it.url != ss.currentVideo.url } ?: return
        val chosenAudio = MediaStreamSelector.pickAudio(ss.availableAudio, lang, best) ?: ss.currentAudio
        val pos = if (liveStream) 0L else getCurrentTime()
        streams = ss.copy(currentVideo = best, currentAudio = chosenAudio)
        lastQuality = MediaStreamSelector.parseQuality(best)
        if (playing) startStreams(best, chosenAudio, pos) else clock.seekOffsetNanos = pos
    }

    /**
     * Starts a periodic task that checks [VideoFramePipe.lastFrameReceivedNanos] every
     * [WATCHDOG_CHECK_INTERVAL_MS] ms. If no frame has arrived for [WATCHDOG_TIMEOUT_NS] ns,
     * assumes `FFmpeg` stalled and restarts the streams from the current position.
     */
    private fun startWatchdog() {
        video.lastFrameReceivedNanos.set(System.nanoTime())
        watchdogExecutor = singleDaemonScheduler("MediaPlayer-watchdog").also { wd ->
            wd.scheduleAtFixedRate({
                runCatching {
                    if (terminated.get() || !playing) return@scheduleAtFixedRate
                    val elapsed = System.nanoTime() - video.lastFrameReceivedNanos.get()
                    if (elapsed > WATCHDOG_TIMEOUT_NS) {
                        LoggingManager.warn("[Watchdog $debugLabel] No frames for ${elapsed / 1_000_000L}ms. Restarting...")
                        video.lastFrameReceivedNanos.set(System.nanoTime())
                        safeExecute { withStreams { v, a -> startStreams(v, a, if (liveStream) 0L else clock.currentTime()) } }
                    }
                }
            }, WATCHDOG_CHECK_INTERVAL_MS, WATCHDOG_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Starts a periodic stats log every [STATS_INTERVAL_MS] ms that reports decoded FPS, GPU upload
     * FPS, dropped frames per second, and current position. Only active when [DEBUG] is enabled.
     */
    private fun startStatsReporter() {
        if (statsExecutor != null) return
        statsExecutor = singleDaemonScheduler("MediaPlayer-stats").also { exec ->
            exec.scheduleAtFixedRate({
                runCatching {
                    val inN = samplesIn.getAndSet(0); val outN = framesToGpu.getAndSet(0); val dropN = framesDropped.getAndSet(0)
                    val sec = STATS_INTERVAL_MS / 1000.0
                    LoggingManager.info("[MediaPlayer $debugLabel] decode=%.1ffps gpu=%.1ffps dropped=%.1f/s pos=%dms live=%s"
                        .format(inN / sec, outN / sec, dropN / sec, getCurrentTime() / 1_000_000L, liveStream))
                }
            }, STATS_INTERVAL_MS, STATS_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Atomically drains [initCallbacks] and, if [run] is true, invokes each callback.
     * Pass `run = false` on failure so queued callers get no stale notification.
     */
    private fun drainInitCallbacks(run: Boolean) {
        initCallbacks.toList().also { initCallbacks.clear() }.takeIf { run }?.forEach { it() }
    }

    /** Runs [block] with the current video and audio streams, or returns immediately if none are selected. */
    private inline fun withStreams(block: (video: YtStream, audio: YtStream) -> Unit) {
        val ss = streams ?: return
        block(ss.currentVideo, ss.currentAudio)
    }

    /** Logs [msg] as an error and marks the display screen as errored. */
    private fun errorOut(msg: String) { LoggingManager.error(msg); displayScreen.errored = true }

    /** Submits [action] to the control executor if the player is not terminated. */
    private fun safeExecute(action: () -> Unit) {
        if (!terminated.get() && !controlExecutor.isShutdown)
            runCatching { controlExecutor.submit(action) }
    }

    /** Creates a single-thread [ScheduledExecutorService] backed by a daemon thread named [name]. */
    private fun singleDaemonScheduler(name: String): ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { daemon(it, name) }

    /** Shuts down [exec] immediately if non-null. */
    private fun cancelExecutor(exec: ScheduledExecutorService?) = exec?.shutdownNow()
}
