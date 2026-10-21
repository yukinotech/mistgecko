package com.mistgecko.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore

object ImageStore {
    // Bound decoded working image to avoid OOM on panoramic / very high resolution photos.
    const val MAX_PIXELS=12_000_000
    fun load(context: Context, uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
        val pixels=info.size.width.toLong()*info.size.height
        require(pixels<=MAX_PIXELS) { "Demo 支持最多 1200 万像素；请先缩小图片" }
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired=true
    }
    fun save(context: Context, bitmap: Bitmap): Uri {
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"MistGecko_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MistGecko")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } ?: error("无法写入文件")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0); resolver.update(uri,values,null,null)
            return uri
        } catch (e: Exception) { resolver.delete(uri,null,null); throw e }
    }
}
