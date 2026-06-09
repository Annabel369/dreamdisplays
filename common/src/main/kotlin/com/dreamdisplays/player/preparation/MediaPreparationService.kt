package com.dreamdisplays.player.preparation

import com.dreamdisplays.client.core.DreamServices
import com.dreamdisplays.client.core.get
import com.dreamdisplays.media.api.MediaResolverChain
import com.dreamdisplays.media.api.MediaSource
import com.dreamdisplays.player.stream.MediaStreamSelector
import com.dreamdisplays.player.stream.StreamSet

/**
 * Resolves stream metadata via [MediaResolverChain] and picks the best video and audio tracks.
 * Runs on a background thread; throws on failure so the caller can handle retries.
 */
internal object MediaPreparationService {

    /**
     * Resolves [url] through the registry's [MediaResolverChain], then selects the best video
     * and audio tracks according to [quality] and [lang].
     *
     * @param url     raw media URL (YouTube, direct stream, etc.)
     * @param lang    preferred audio language (empty = default)
     * @param quality preferred video quality string, e.g. "720p"
     * @throws IllegalStateException if the chain is not registered or no usable streams are found
     */
    fun prepare(url: String, lang: String, quality: String): PreparedMedia {
        val chain = DreamServices.registry.get<MediaResolverChain>()
        val source = MediaSource.from(url)
        val resolved = chain.resolve(source)

        check(resolved.streams.isNotEmpty()) { "No streams available for $url." }

        val videoStreams = resolved.videoStreams
        val audioStreams = resolved.audioStreams

        val requestedQuality = MediaStreamSelector.parseQualityValue(quality, 720)
        val pickedVideo = MediaStreamSelector.pickVideo(videoStreams, requestedQuality)
            ?: videoStreams.firstOrNull()
            ?: throw IllegalStateException("No usable video stream for $url.")
        val pickedAudio = MediaStreamSelector.pickAudio(audioStreams, lang, pickedVideo)
            ?: throw IllegalStateException("No usable audio stream for $url.")

        val durationNanos = resolved.metadata.duration?.inWholeNanoseconds ?: 0L

        return PreparedMedia(
            streamSet = StreamSet(videoStreams, audioStreams, pickedVideo, pickedAudio),
            isLive = resolved.isLive,
            isSeekable = resolved.isSeekable,
            durationNanos = durationNanos,
        )
    }
}
