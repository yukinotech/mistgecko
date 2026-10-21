package com.mistgecko.engine

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.FloatBuffer

object EngineFactory {
    const val supportsModel = true
    fun create(context: Context): InpaintEngine {
        val metadata=context.assets.open("models/model.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val sha=metadata.getString("sha256")
        val model=File(context.filesDir,"lama-$sha.onnx")
        if (!model.exists() || model.length()!=metadata.getLong("size")) {
            val pending=File(context.filesDir,"model.pending")
            try {
                val digest=java.security.MessageDigest.getInstance("SHA-256")
                context.assets.open("models/lama_fp32.onnx").use { input ->
                    java.security.DigestInputStream(input,digest).use { verified -> pending.outputStream().use { verified.copyTo(it) } }
                }
                check(pending.length()==metadata.getLong("size") && digest.digest().joinToString("") { "%02x".format(it) }==sha) { "内置模型校验失败，请重新安装完整 APK" }
                check(pending.renameTo(model)) { "模型准备失败，请检查剩余存储空间" }
                context.filesDir.listFiles()?.filter { (it.name.startsWith("lama-") || it.name=="lama_fp32.onnx") && it!=model }?.forEach { it.delete() }
            } finally { pending.delete() }
        }
        return LaMaEngine(model)
    }
}

/** Contract for Carve lama_fp32: NCHW float RGB [0,1], white hole mask, RGB output [0,255]. */
private class LaMaEngine(model: File): InpaintEngine {
    override val label="LaMa · ONNX CPU · 离线"
    private val env=OrtEnvironment.getEnvironment()
    private val session=OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        env.createSession(model.absolutePath,options)
    }
    init {
        try {
            require(session.inputNames == setOf("image","mask")) { "模型输入必须为 image 和 mask" }
            for ((name,node) in session.inputInfo) {
                val info=node.info as? TensorInfo ?: error("模型输入不是 Tensor")
                val channels=if (name == "image") 3L else 1L
                val expected=longArrayOf(1,channels,512,512)
                require(info.type == OnnxJavaType.FLOAT && info.shape.size == 4 && info.shape.indices.all { info.shape[it] == expected[it] || info.shape[it] == -1L }) { "不支持的 $name: ${info.type} ${info.shape.contentToString()}；需要兼容 512×512 FP32 LaMa" }
            }
        } catch (e: Exception) { session.close(); throw e }
    }
    override fun infer(image: Bitmap, mask: Bitmap): Bitmap {
        val n=512*512
        val rgb=FloatArray(n*3); val holes=FloatArray(n)
        val pixels=IntArray(n); val masks=IntArray(n)
        image.getPixels(pixels,0,512,0,0,512,512); mask.getPixels(masks,0,512,0,0,512,512)
        for (i in 0 until n) {
            rgb[i]=Color.red(pixels[i])/255f; rgb[n+i]=Color.green(pixels[i])/255f; rgb[2*n+i]=Color.blue(pixels[i])/255f
            holes[i]=if (Color.red(masks[i])>0) 1f else 0f
        }
        // Keep colored object edges out of the model's known context after resizing.
        // This dilation affects inference only; compositing still uses the user's original mask.
        val horizontal=FloatArray(n)
        val radius=8
        for(y in 0 until 512) for(x in 0 until 512) {
            for(dx in -radius..radius) {
                if(x+dx in 0 until 512 && holes[y*512+x+dx]>0f) { horizontal[y*512+x]=1f; break }
            }
        }
        for(y in 0 until 512) for(x in 0 until 512) {
            holes[y*512+x]=0f
            for(dy in -radius..radius) {
                if(y+dy in 0 until 512 && horizontal[(y+dy)*512+x]>0f) { holes[y*512+x]=1f; break }
            }
        }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),longArrayOf(1,3,512,512)).use { im ->
            OnnxTensor.createTensor(env,FloatBuffer.wrap(holes),longArrayOf(1,1,512,512)).use { ma ->
                session.run(mapOf("image" to im,"mask" to ma)).use { result ->
                    val tensor=result[0] as OnnxTensor
                    require(tensor.info.shape.contentEquals(longArrayOf(1,3,512,512))) { "不支持的输出形状" }
                    val out=tensor.floatBuffer
                    for (i in 0 until n) {
                        fun channel(index: Int): Int { val v=out.get(index); require(v.isFinite()); return v.toInt().coerceIn(0,255) }
                        pixels[i]=Color.rgb(channel(i),channel(n+i),channel(2*n+i))
                    }
                    return Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888)
                }
            }
        }
    }
    override fun close() { session.close() }
}
