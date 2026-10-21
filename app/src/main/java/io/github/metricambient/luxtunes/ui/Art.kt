package io.github.metricambient.luxtunes.ui

import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/**
 * A picture for a song: the album cover LuxTunes copied (`cover`), or else the
 * art embedded in the song file itself (`song`), e.g. when a sync hasn't made
 * the cover yet.
 */
data class Art(val cover: File?, val song: File?)

/** Loads an [Art] off the main thread. */
class ArtFetcher(private val art: Art, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val cover = art.cover
        if (cover != null && cover.isFile) {
            return SourceResult(ImageSource(cover.toOkioPath(), FileSystem.SYSTEM), null, DataSource.DISK)
        }
        val bytes = art.song?.takeIf { it.isFile }?.let(::embeddedPicture)
            ?: throw IOException("No artwork")
        return SourceResult(ImageSource(Buffer().write(bytes), options.context), null, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Art> {
        override fun create(data: Art, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(data, options)
    }
}

/** Memory-cache key; changes when the cover file appears or changes. */
class ArtKeyer : Keyer<Art> {
    override fun key(data: Art, options: Options): String =
        "art:${data.cover?.path}:${data.cover?.lastModified() ?: 0L}|${data.song?.path}"
}

private fun embeddedPicture(file: File): ByteArray? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.path)
        retriever.embeddedPicture
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
