package com.mistgecko.engine

import android.graphics.Bitmap

/** Both images are square, RGB; mask white means erase, black means preserve. */
interface InpaintEngine : AutoCloseable {
    val label: String
    fun infer(image: Bitmap, mask: Bitmap): Bitmap
    override fun close() {}
}
