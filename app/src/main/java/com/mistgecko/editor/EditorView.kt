package com.mistgecko.editor

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

data class Stroke(val points: List<PointF>, val width: Float)

class EditorView(context: Context): View(context) {
    var image: Bitmap? = null
        private set
    var original: Bitmap? = null
        private set
    var brush = 32f
    var comparing = false
        set(value) { field=value; invalidate() }
    var locked = false
    var onChange: () -> Unit = {}
    private val strokes=mutableListOf<Stroke>()
    private val redo=mutableListOf<Stroke>()
    private var drawing=mutableListOf<PointF>()
    private val frame=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun load(bitmap: Bitmap) { val old=image; val base=original; original=bitmap; image=bitmap; if(old !== base) old?.recycle(); base?.recycle(); clear(); invalidate() }
    fun applyResult(bitmap: Bitmap) { val old=image; image=bitmap; if(old !== original) old?.recycle(); clear() }
    fun reset() { val old=image; image=original; if(old !== original) old?.recycle(); clear() }
    fun clear() { strokes.clear(); redo.clear(); drawing.clear(); invalidate(); onChange() }
    fun undo() { if (strokes.isNotEmpty()) redo.add(strokes.removeAt(strokes.lastIndex)); invalidate(); onChange() }
    fun redo() { if (redo.isNotEmpty()) strokes.add(redo.removeAt(redo.lastIndex)); invalidate(); onChange() }
    val hasMask get()=strokes.isNotEmpty()
    private fun drawStroke(canvas: Canvas, stroke: Stroke, color: Int) {
        paint.color=color; paint.strokeWidth=stroke.width; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        val pts=stroke.points
        if (pts.size == 1) { paint.style=Paint.Style.FILL; canvas.drawCircle(pts[0].x,pts[0].y,stroke.width/2,paint) }
        else if (pts.isNotEmpty()) {
            paint.style=Paint.Style.STROKE
            val path=Path(); path.moveTo(pts[0].x,pts[0].y)
            pts.drop(1).forEach { path.lineTo(it.x,it.y) }; canvas.drawPath(path,paint)
        }
        paint.style=Paint.Style.FILL
    }
    fun mask(): Bitmap {
        val bitmap=image ?: error("请先选择图片")
        return Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also { mask ->
            val canvas=Canvas(mask); canvas.drawColor(Color.BLACK)
            strokes.forEach { drawStroke(canvas,it,Color.WHITE) }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(16,25,27))
        val bitmap=(if (comparing) original else image) ?: return
        val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val dx=(width-bitmap.width*scale)/2; val dy=(height-bitmap.height*scale)/2
        frame.set(dx,dy,dx+bitmap.width*scale,dy+bitmap.height*scale)
        paint.isFilterBitmap=true; canvas.drawBitmap(bitmap,null,frame,paint)
        if (!comparing) {
            canvas.save(); canvas.clipRect(frame); canvas.translate(dx,dy); canvas.scale(scale,scale)
            strokes.forEach { drawStroke(canvas,it,0x99B5F56B.toInt()) }
            drawStroke(canvas,Stroke(drawing,brush/scale),0x99B5F56B.toInt()); canvas.restore()
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap=image ?: return false
        if (locked || comparing || frame.width()<=0) return false
        val scale=frame.width()/bitmap.width
        val point=PointF(((event.x-frame.left)/scale).coerceIn(0f,bitmap.width-1f),((event.y-frame.top)/scale).coerceIn(0f,bitmap.height-1f))
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (!frame.contains(event.x,event.y)) return false; parent.requestDisallowInterceptTouchEvent(true); drawing=mutableListOf(point) }
            MotionEvent.ACTION_MOVE -> drawing.add(point)
            MotionEvent.ACTION_UP -> { drawing.add(point); strokes.add(Stroke(drawing.toList(),brush/scale)); drawing.clear(); redo.clear(); performClick(); onChange() }
            MotionEvent.ACTION_CANCEL -> drawing.clear()
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
