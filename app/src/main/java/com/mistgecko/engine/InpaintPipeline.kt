package com.mistgecko.engine

import android.graphics.*
import kotlin.math.*

object InpaintPipeline {
    const val SIZE = 512
    /** Context crop stays square with black letterboxing at edges; only selected pixels change. */
    fun run(source: Bitmap, mask: Bitmap, engine: InpaintEngine): Bitmap {
        require(source.width == mask.width && source.height == mask.height)
        val w = source.width; val h = source.height
        val m = IntArray(w*h); mask.getPixels(m, 0, w, 0, 0, w, h)
        var left=w; var top=h; var right=-1; var bottom=-1
        for (i in m.indices) if (Color.red(m[i]) > 0) {
            val x=i%w; val y=i/w
            left=min(left,x); right=max(right,x); top=min(top,y); bottom=max(bottom,y)
        }
        require(right >= left) { "请先涂抹需要消除的区域" }
        val side = max(64, ceil(max(right-left+1,bottom-top+1)*1.6).toInt())
        val x = (left+right+1-side)/2; val y = (top+bottom+1-side)/2
        fun crop(bitmap: Bitmap, filter: Boolean): Bitmap {
            val out=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.BLACK)
                scale(SIZE.toFloat()/side,SIZE.toFloat()/side)
                translate(-x.toFloat(),-y.toFloat())
                drawBitmap(bitmap,0f,0f,Paint().apply { isFilterBitmap=filter })
            }
            return out
        }
        val input=crop(source,true); val selection=crop(mask,false)
        val generated = try { engine.infer(input,selection) } finally { input.recycle(); selection.recycle() }
        try {
            require(generated.width == SIZE && generated.height == SIZE) { "模型输出尺寸错误" }
            val output=source.copy(Bitmap.Config.ARGB_8888,true)
            // Sample directly from the model output: never allocate a huge square for panoramas.
            for (py in max(0,top)..min(h-1,bottom)) for (px in max(0,left)..min(w-1,right)) {
                if (Color.red(m[py*w+px]) > 0) {
                    val sx=((px-x+0.5f)*SIZE/side-0.5f).coerceIn(0f,SIZE-1f)
                    val sy=((py-y+0.5f)*SIZE/side-0.5f).coerceIn(0f,SIZE-1f)
                    val ix=sx.toInt(); val iy=sy.toInt(); val fx=sx-ix; val fy=sy-iy
                    val colors=intArrayOf(generated.getPixel(ix,iy),generated.getPixel(min(ix+1,SIZE-1),iy),
                        generated.getPixel(ix,min(iy+1,SIZE-1)),generated.getPixel(min(ix+1,SIZE-1),min(iy+1,SIZE-1)))
                    fun blend(shift: Int): Int {
                        val values=colors.map { (it shr shift) and 255 }
                        return ((values[0]*(1-fx)+values[1]*fx)*(1-fy)+(values[2]*(1-fx)+values[3]*fx)*fy).roundToInt()
                    }
                    val alpha=Color.red(m[py*w+px])/255f
                    val old=source.getPixel(px,py)
                    fun channel(shift: Int)=(((old shr shift) and 255)*(1-alpha)+blend(shift)*alpha).roundToInt()
                    output.setPixel(px,py,Color.argb(Color.alpha(old),channel(16),channel(8),channel(0)))
                }
            }
            return output
        } finally { generated.recycle() }
    }
}
