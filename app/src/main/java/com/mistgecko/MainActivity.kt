package com.mistgecko

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.mistgecko.data.ImageStore
import com.mistgecko.editor.EditorView
import com.mistgecko.engine.*
import java.util.concurrent.Executors

class MainActivity: Activity() {
    private lateinit var editor: EditorView
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val controls=mutableListOf<View>()
    private val worker=Executors.newSingleThreadExecutor()
    private var busy=false
    private var alive=true
    private val green=Color.rgb(185,243,113)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=Color.rgb(16,25,27); window.navigationBarColor=Color.rgb(16,25,27)
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,16,24,12); setBackgroundColor(Color.rgb(16,25,27)) }
        root.setOnApplyWindowInsetsListener { view,insets -> view.setPadding(24,insets.systemWindowInsetTop+12,24,insets.systemWindowInsetBottom+12); insets }
        fun text(value: String,size: Float,color: Int=Color.WHITE)=TextView(this).apply { text=value; textSize=size; setTextColor(color); setPadding(4,6,4,6) }
        root.addView(text("MistGecko  /  离线消除",25f,green))
        root.addView(text(if (EngineFactory.supportsModel) "LaMa · 照片留在手机上" else "交互 Demo · 非 AI 扩散填充",13f))
        status=text("涂抹红色标记，点击消除试试看",13f,green); root.addView(status)
        editor=EditorView(this); root.addView(editor,LinearLayout.LayoutParams(-1,0,1f))
        fun row(): LinearLayout=LinearLayout(this).also { root.addView(it) }
        fun button(row: LinearLayout,label: String,action: ()->Unit): Button = Button(this).apply {
            text=label; textSize=12f; isAllCaps=false
            row.addView(this,LinearLayout.LayoutParams(0,52.dp(),1f)); controls.add(this)
            setOnClickListener { action() }
        }
        val tools=row()
        button(tools,"选图片") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },1) }
        button(tools,"撤销") { editor.undo() }; button(tools,"重做") { editor.redo() }; button(tools,"清空") { editor.clear() }
        val brushRow=row(); brushRow.addView(text("画笔",13f))
        brushRow.addView(SeekBar(this).apply { max=96; progress=24; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { editor.brush=(p+8).toFloat() }
            override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
        }); controls.add(this) },LinearLayout.LayoutParams(0,48.dp(),1f))
        val actions=row()
        button(actions,"开始消除") { erase() }
        button(actions,"按住对比") {}.setOnTouchListener { v,event ->
            editor.comparing=event.action==MotionEvent.ACTION_DOWN || event.action==MotionEvent.ACTION_MOVE
            if(event.action==MotionEvent.ACTION_UP) v.performClick()
            true
        }
        button(actions,"保存 PNG") { val bitmap=editor.image ?: return@button; task("正在保存…") { ImageStore.save(this,bitmap); { status.text="已保存到 Pictures/MistGecko" } } }
        val extra=row()
        button(extra,"恢复原图") { editor.reset(); status.text="已恢复原图" }
        root.addView(text("本地处理  ·  无网络权限  ·  遮罩外像素保留",11f))
        setContentView(root)
        editor.load(sample())
    }
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
    private fun setBusy(value: Boolean) { busy=value; editor.locked=value; controls.forEach { it.isEnabled=!value } }
    private fun task(message: String, job: () -> (() -> Unit)) {
        if (busy) return
        setBusy(true); status.text=message
        worker.execute {
            try { val done=job(); runOnUiThread { if(alive) { setBusy(false); done() } } }
            catch (e: Exception) { runOnUiThread { if(alive) { setBusy(false); status.text=e.message ?: "操作失败" } } }
        }
    }
    private fun erase() {
        if (!editor.hasMask) { status.text="请先涂抹需要消除的区域"; return }
        val source=editor.image ?: return
        val mask=editor.mask()
        task("正在本地处理，请稍候…") {
            val start=System.nanoTime()
            val result=try { EngineFactory.create(this).use { InpaintPipeline.run(source,mask,it) } } finally { mask.recycle() }
            { editor.applyResult(result); status.text="完成 · %.1f 秒 · 可按住对比".format((System.nanoTime()-start)/1e9) }
        }
    }
    @Deprecated("Platform callback")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        if (requestCode==1) task("正在读取图片…") { val bitmap=ImageStore.load(this,uri); { editor.load(bitmap); status.text="${bitmap.width} × ${bitmap.height} · 涂抹后消除" } }
    }

    private fun sample(): Bitmap = Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas=Canvas(bitmap); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,0f,720f,intArrayOf(Color.rgb(51,109,123),Color.rgb(215,218,174)),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,960f,720f,p); p.shader=null
        p.color=Color.rgb(239,226,161); canvas.drawCircle(740f,160f,65f,p)
        p.color=Color.rgb(35,77,71); val path=Path(); path.moveTo(0f,540f); path.lineTo(270f,230f); path.lineTo(570f,560f); path.lineTo(800f,340f); path.lineTo(960f,540f); path.lineTo(960f,720f); path.lineTo(0f,720f); path.close(); canvas.drawPath(path,p)
        p.color=Color.rgb(226,97,76); canvas.drawRoundRect(450f,490f,502f,615f,12f,12f,p)
        p.color=Color.rgb(255,245,210); canvas.drawCircle(476f,470f,23f,p)
    }
    override fun onDestroy() { alive=false; worker.shutdownNow(); super.onDestroy() }
}
