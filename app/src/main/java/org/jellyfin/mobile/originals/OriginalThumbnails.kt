package org.jellyfin.mobile.originals

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File

/**
 * Shared thumbnail loader for the Originals side: ONE ImageLoader with an
 * explicit on-disk cache (100 MB in cacheDir/originals_thumbnails), so
 * posters for shows & movies download once and are served from disk on
 * every later visit — even after the app restarts. archive.org item
 * thumbnails never change, so cache headers are ignored and entries are
 * kept aggressively.
 */
object OriginalThumbnails {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        val existing = loader
        if (existing != null) return existing
        return synchronized(this) {
            loader ?: build(context.applicationContext).also { loader = it }
        }
    }

    private fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "originals_thumbnails"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

    /** A request that always reads/writes both cache layers. */
    fun request(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()

    /** Warm the cache for [urls] in the background (fire and forget). */
    fun prefetch(context: Context, urls: List<String>) {
        val imageLoader = get(context)
        val appContext = context.applicationContext
        for (url in urls.distinct()) {
            imageLoader.enqueue(request(appContext, url))
        }
    }
}
