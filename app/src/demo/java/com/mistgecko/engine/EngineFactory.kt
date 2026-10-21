package com.mistgecko.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

object EngineFactory {
    const val supportsModel = false
    fun create(context: Context): InpaintEngine = DemoEngine()
}

/** Local diffusion for exercising the editor. This is deliberately NOT an AI model. */
private class DemoEngine : InpaintEngine {
    override val label = "流程演示 · 非 AI 扩散填充"
    override fun infer(image: Bitmap, mask: Bitmap): Bitmap {
        val w = image.width
        val pixels = IntArray(w * w)
        val masks = IntArray(w * w)
        image.getPixels(pixels, 0, w, 0, 0, w, w)
        mask.getPixels(masks, 0, w, 0, 0, w, w)
        var known = BooleanArray(w * w) { Color.red(masks[it]) < 128 }
        require(known.any { it }) { "遮罩覆盖全部区域，请缩小涂抹范围" }
        while (known.any { !it }) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val next = known.copyOf()
            var changed = false
            for (i in pixels.indices) if (!known[i]) {
                val x = i % w; val y = i / w
                var r = 0; var g = 0; var b = 0; var n = 0
                for (j in intArrayOf(if (x > 0) i-1 else -1, if (x+1 < w) i+1 else -1,
                    if (y > 0) i-w else -1, if (y+1 < w) i+w else -1)) {
                    if (j >= 0 && known[j]) { r += Color.red(pixels[j]); g += Color.green(pixels[j]); b += Color.blue(pixels[j]); n++ }
                }
                if (n > 0) { pixels[i] = Color.rgb(r/n, g/n, b/n); next[i] = true; changed = true }
            }
            check(changed) { "演示填充无法继续" }
            known = next
        }
        return Bitmap.createBitmap(pixels, w, w, Bitmap.Config.ARGB_8888)
    }
}
