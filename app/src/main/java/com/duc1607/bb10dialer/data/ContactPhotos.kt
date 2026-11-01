package com.duc1607.bb10dialer.data

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Contact photos in memory, so a list shows them from its first frame. */
object ContactPhotos {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_PRELOADED = 200

    private data class Key(val photo: ContactPhoto, val side: Int)

    private val cache = object : LruCache<Key, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.width * value.height * 4
    }

    /** [side] as for [load]. */
    fun cached(photo: ContactPhoto, side: Int = 0): ImageBitmap? = cache.get(Key(photo, side))

    /**
     * Off the main thread. Null when the photo cannot be read. A [side] above 0 is the most it will
     * be shown at, in pixels: a photo bigger than that only costs the GPU more on every frame.
     */
    fun load(resolver: ContentResolver, photo: ContactPhoto, side: Int = 0): ImageBitmap? {
        val key = Key(photo, side)
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            val uri = Uri.parse(photo.uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (side > 0) resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(minOf(bounds.outWidth, bounds.outHeight), side)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null
        // Otherwise the upload to the GPU happens during the first frame that draws it.
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    /** Off the main thread. Top of the list first, as far as it fits. */
    fun preload(resolver: ContentResolver, book: ContactBook) {
        book.sections.asSequence()
            .flatMap { it.items }
            .mapNotNull { it.photo }
            .take(MAX_PRELOADED)
            .forEach { load(resolver, it) }
    }
}

/** The largest halving of a photo with shorter side [photoSide] that still fills [side]. */
internal fun sampleSizeFor(photoSide: Int, side: Int): Int {
    if (side <= 0) return 1
    var sample = 1
    while (photoSide / (sample * 2) >= side) sample *= 2
    return sample
}
