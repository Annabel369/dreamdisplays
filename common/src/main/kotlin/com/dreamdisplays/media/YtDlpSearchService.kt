package com.dreamdisplays.media

import com.dreamdisplays.media.api.MediaSearchResult
import com.dreamdisplays.media.api.MediaSearchService
import com.dreamdisplays.ytdlp.YtDlp
import com.dreamdisplays.ytdlp.YtVideoInfo
import com.dreamdisplays.ytdlp.YouTubeInnerTube

/** [MediaSearchService] backed by [YtDlp] and [YouTubeInnerTube]. */
class YtDlpSearchService : MediaSearchService {

    override fun search(query: String, limit: Int): List<MediaSearchResult> =
        YtDlp.search(query, limit).map { it.toSearchResult() }

    override fun related(videoId: String, limit: Int): List<MediaSearchResult> =
        YtDlp.related(videoId, limit).map { it.toSearchResult() }

    override fun extractVideoId(url: String): String? = YtDlp.extractVideoId(url)

    override fun metadata(videoId: String): MediaSearchResult? =
        YouTubeInnerTube.metadata(videoId)?.toSearchResult()

    private fun YtVideoInfo.toSearchResult() = MediaSearchResult(
        id = id,
        title = title,
        uploader = uploader,
        durationSec = durationSec,
        viewCount = viewCount,
        likeCount = likeCount,
        publishedText = publishedText,
        publishedDaysAgo = publishedDaysAgo,
    )
}
