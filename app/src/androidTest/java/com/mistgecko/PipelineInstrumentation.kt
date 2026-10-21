package com.mistgecko

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import com.mistgecko.engine.*

/** Device tests without an extra test framework. Run with adb shell am instrument -w. */
class PipelineInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle()
        try {
            val fake=object: InpaintEngine {
                override val label="test"
                override fun infer(image: Bitmap,mask: Bitmap): Bitmap {
                    check(image.width==512 && image.height==512)
                    check(mask.width==512 && mask.height==512)
                    return Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                }
            }
            for ((w,h) in listOf(100 to 80,80 to 100,1200 to 10)) {
                val source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                for ((x,y) in listOf(0 to 0,w/2 to h/2,w-1 to h-1)) {
                    val mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK); setPixel(x,y,Color.WHITE) }
                    val out=InpaintPipeline.run(source,mask,fake)
                    check(out.width==w && out.height==h)
                    for(py in 0 until h) for(px in 0 until w) {
                        check(out.getPixel(px,py)==if(px==x && py==y) Color.RED else Color.BLUE) { "pixel preservation / crop failed at $px,$py" }
                    }
                    check(source.getPixel(x,y)==Color.BLUE) { "source mutated" }
                    out.recycle(); mask.recycle()
                }
                val empty=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                check(runCatching { InpaintPipeline.run(source,empty,fake) }.exceptionOrNull() is IllegalArgumentException)
                source.recycle(); empty.recycle()
            }
            if (EngineFactory.supportsModel) {
                val image=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
                val mask=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
                for(y in 0 until 512) for(x in 0 until 512) image.setPixel(x,y,Color.rgb(65+y/8,130+y/8,145+y/8))
                for(y in 210..310) for(x in 225..285) {
                    image.setPixel(x,y,Color.RED); mask.setPixel(x,y,Color.WHITE)
                }
                val start=System.nanoTime()
                val output=EngineFactory.create(targetContext).use { InpaintPipeline.run(image,mask,it) }
                val seconds=(System.nanoTime()-start)/1e9
                var changed=0; var brightness=0L
                for(y in 0 until 512) for(x in 0 until 512) {
                    val value=output.getPixel(x,y)
                    if(Color.red(mask.getPixel(x,y))==0) check(value==image.getPixel(x,y))
                    else { if(value!=image.getPixel(x,y)) changed++; brightness+=Color.green(value) }
                }
                check(changed>100) { "Real model did not change the selected region" }
                check(brightness/6161>10) { "Real output unexpectedly dark; check range" }
                for((name,bitmap) in listOf("ai-input" to image,"ai-mask" to mask,"ai-output" to output)) {
                    java.io.File(targetContext.filesDir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
                result.putString("ai","REAL LaMa CPU PASS: %.2f seconds, %d selected pixels changed; unmasked pixels identical".format(seconds,changed))
                image.recycle(); mask.recycle(); output.recycle()
            }
            result.putString("stream","PASS: dimensions, edge crops, panorama, unmasked pixel identity, source immutability, empty mask rejection\n")
            finish(Activity.RESULT_OK,result)
        } catch(e: Throwable) { result.putString("stream",e.stackTraceToString()); finish(Activity.RESULT_CANCELED,result) }
    }
}
