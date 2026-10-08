package dev.gbalite.player
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import dev.gbalite.input.*
import dev.gbalite.core.GbaButton
import kotlin.math.min

/** All pointers are hit-tested every event, including slides and cancellation. */
class TouchControls(context: Context,private val router: InputRouter): View(context) {
    var profile=InputProfile.default(false); set(value) { field=value; invalidate() }
    var editing=false
    var onEdited: (InputProfile)->Unit={}
    var onSelected: (String)->Unit={}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragKey: String?=null
    private var dragPointer=-1
    private var pointers=emptySet<String>()
    private fun base()=if(profile.landscape) min(width,height)*1.45f else width.toFloat()
    private fun geometry(c: TouchControl): Triple<Float,Float,Float> {
        val r=min(maxOf(c.size*base()/2,24*resources.displayMetrics.density),min(width,height)*.45f)
        return Triple((c.x*width).coerceIn(r,width-r),(c.y*height).coerceIn(r,height-r),r)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for(c in profile.controls) {
            val (x,y,r)=geometry(c)
            paint.color=0xff29445b.toInt(); paint.alpha=(profile.opacity*255).toInt()
            if(c.key=="DPAD") {
                canvas.drawRoundRect(x-r/3,y-r,x+r/3,y+r,12f,12f,paint)
                canvas.drawRoundRect(x-r,y-r/3,x+r,y+r/3,12f,12f,paint)
            } else canvas.drawCircle(x,y,r,paint)
            paint.color=0xffffffff.toInt(); paint.alpha=230; paint.textAlign=Paint.Align.CENTER
            paint.textSize=if(c.key.length>1) r*.42f else r*.8f
            canvas.drawText(if(c.key=="DPAD") "+" else c.key,x,y-(paint.ascent()+paint.descent())/2,paint)
        }
    }
    private fun hit(x: Float,y: Float): TouchControl?=profile.controls.firstOrNull {
        val (cx,cy,r)=geometry(it)
        if(it.key=="DPAD") kotlin.math.abs(x-cx)<=r && kotlin.math.abs(y-cy)<=r
        else (x-cx)*(x-cx)+(y-cy)*(y-cy)<=r*r
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if(event.actionMasked==MotionEvent.ACTION_CANCEL) { router.releaseAll(); release(); return true }
        if(editing) {
            if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                dragKey=hit(event.x,event.y)?.key; dragPointer=event.getPointerId(0)
                dragKey?.let(onSelected)
            }
            val index=event.findPointerIndex(dragPointer)
            if(index>=0 && event.actionMasked==MotionEvent.ACTION_MOVE) {
                profile=profile.copy(controls=profile.controls.map { c-> if(c.key==dragKey)
                    c.copy(x=(event.getX(index)/width).coerceIn(.08f,.92f),y=(event.getY(index)/height).coerceIn(.08f,.92f)) else c })
                onEdited(profile)
            }
            if(event.actionMasked==MotionEvent.ACTION_UP) release()
            return true
        }
        val active=mutableSetOf<String>()
        val observed=(0 until event.pointerCount).map { "touch:${event.getPointerId(it)}" }.toSet()
        if((pointers-observed).isNotEmpty()) { router.releaseAll(); release(); return true }
        for(i in 0 until event.pointerCount) {
            val source="touch:${event.getPointerId(i)}"
            if((event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_POINTER_UP) && i==event.actionIndex) {
                router.releaseSource(source); continue
            }
            active+=source
            val c=hit(event.getX(i),event.getY(i))
            val keys=if(c==null) emptySet() else if(c.key=="DPAD") {
                val (x,y,r)=geometry(c); dpad((event.getX(i)-x)/r,(event.getY(i)-y)/r)
            } else setOf(GbaButton.valueOf(c.key))
            router.update(source,keys)
        }
        (pointers-active).forEach(router::releaseSource); pointers=active
        if(event.actionMasked==MotionEvent.ACTION_UP) { release(); performClick() }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun release() { router.releasePrefix("touch:"); pointers=emptySet(); dragKey=null; dragPointer=-1 }
    override fun onDetachedFromWindow() { router.releaseAll(); release(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { if(!hasWindowFocus) release(); super.onWindowFocusChanged(hasWindowFocus) }
}
