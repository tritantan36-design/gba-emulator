package dev.gbalite.player
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.animation.ValueAnimator
import androidx.compose.ui.graphics.toArgb
import dev.gbalite.player.ui.UiColors
import android.view.MotionEvent
import android.view.View
import dev.gbalite.input.*
import dev.gbalite.core.GbaButton
import kotlin.math.min

/** All pointers are hit-tested every event, including slides and cancellation. */
class TouchControls(context: Context,private val router: InputRouter): View(context) {
    init {contentDescription="GBA 触控：方向键、A、B、L、R、Start、Select"}
    var profile=InputProfile.default(false); set(value) { field=value; invalidate() }
    var editing=false
    var onEdited: (InputProfile)->Unit={}
    var onSelected: (String)->Unit={}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragKey: String?=null
    private var dragPointer=-1
    private var pointers=emptySet<String>()
    private fun base()=if(profile.landscape) min(width,height)*1.45f else width.toFloat()
    private val rect=RectF()
    private var pressed=emptySet<String>()
    private var fading=emptySet<String>()
    private var fade=0f
    private val feedback=ValueAnimator.ofFloat(1f,0f).apply {
        duration=100
        addUpdateListener {fade=it.animatedValue as Float;invalidate()}
    }
    private fun pressed(keys: Set<String>) {
        if(keys==pressed) return
        fading=pressed-keys;pressed=keys
        feedback.cancel();feedback.start();invalidate()
    }
    private fun extents(c: TouchControl,r: Float): Pair<Float,Float> = when(c.key) {
        "L","R" -> r*1.20f to maxOf(24*resources.displayMetrics.density,r*.40f)
        "START","SELECT" -> maxOf(36*resources.displayMetrics.density,r*1.25f) to maxOf(24*resources.displayMetrics.density,r*.38f)
        else -> r to r
    }
    private fun geometry(c: TouchControl): Triple<Float,Float,Float> {
        val r=min(maxOf(c.size*base()/2,24*resources.displayMetrics.density),min(width,height)*.45f)
        val (rx,ry)=extents(c,r)
        return Triple(if(width>=2*rx) (c.x*width).coerceIn(rx,width-rx) else width/2f,if(height>=2*ry) (c.y*height).coerceIn(ry,height-ry) else height/2f,r)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density=resources.displayMetrics.density
        for(c in profile.controls) {
            val (x,y,r)=geometry(c);val (rx,ry)=extents(c,r)
            val amount=if(c.key in pressed) 1f else if(c.key in fading) fade else 0f
            val alpha=(profile.opacity*255).toInt()
            val face=if(c.key=="A" || c.key=="B") UiColors.Action.toArgb() else UiColors.Control.toArgb()
            fun shape(inset: Float=0f,dy: Float=0f) {
                if(c.key=="A" || c.key=="B") canvas.drawCircle(x,y+dy,r-inset,paint)
                else {
                    rect.set(x-rx+inset,y-ry+inset+dy,x+rx-inset,y+ry-inset+dy)
                    canvas.drawRoundRect(rect,if(c.key=="DPAD") 16*density else ry,if(c.key=="DPAD") 16*density else ry,paint)
                }
            }
            paint.style=Paint.Style.FILL;paint.alpha=alpha;paint.color=UiColors.ControlEdge.toArgb();shape(dy=3*density)
            paint.color=face;paint.alpha=alpha;shape()
            paint.color=UiColors.ControlLight.toArgb();paint.alpha=(alpha*(.25f+amount*.50f)).toInt();shape(inset=density)
            paint.color=face;paint.alpha=alpha;shape(inset=2*density,dy=amount*density)
            if(c.key=="DPAD") {
                // Full backplate preserves the existing square diagonal interaction bounds.
                paint.color=UiColors.ControlEdge.toArgb();paint.alpha=alpha
                canvas.drawRoundRect(x-r*.32f,y-r*.90f,x+r*.32f,y+r*.90f,4*density,4*density,paint)
                canvas.drawRoundRect(x-r*.90f,y-r*.32f,x+r*.90f,y+r*.32f,4*density,4*density,paint)
                paint.color=UiColors.ControlLight.toArgb();paint.alpha=alpha
                canvas.drawCircle(x,y,r*.17f,paint)
                paint.color=0xffb9bec9.toInt();paint.alpha=(alpha*.7f).toInt();paint.textSize=r*.25f;paint.textAlign=Paint.Align.CENTER
                canvas.drawText("↑",x,y-r*.59f,paint);canvas.drawText("↓",x,y+r*.76f,paint)
                canvas.drawText("‹",x-r*.67f,y+r*.09f,paint);canvas.drawText("›",x+r*.67f,y+r*.09f,paint)
            } else {
                paint.color=0xfff6f5f8.toInt();paint.alpha=alpha;paint.textAlign=Paint.Align.CENTER
                paint.typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
                paint.textSize=if(c.key.length>1) min(13*density,r*.42f) else if(c.key=="L" || c.key=="R") 16*density else r*.70f
                canvas.drawText(c.key,x,y-(paint.ascent()+paint.descent())/2+amount*density,paint)
            }
            if(editing && c.key==selectedKey) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=0xffcbb7ef.toInt();paint.alpha=255;shape();paint.style=Paint.Style.FILL
            }
        }
    }
    var selectedKey="A";set(value) {field=value;invalidate()}
    private fun hit(x: Float,y: Float): TouchControl?=profile.controls.firstOrNull {
        val (cx,cy,r)=geometry(it);val (rx,ry)=extents(it,r)
        if(it.key in setOf("DPAD","L","R","START","SELECT")) kotlin.math.abs(x-cx)<=rx && kotlin.math.abs(y-cy)<=ry
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
        val visual=mutableSetOf<String>()
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
            if(keys.isNotEmpty()) c?.let {visual+=it.key}
            router.update(source,keys)
        }
        (pointers-active).forEach(router::releaseSource); pointers=active;pressed(visual)
        if(event.actionMasked==MotionEvent.ACTION_UP) { release(); performClick() }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun release() { router.releasePrefix("touch:"); pointers=emptySet(); dragKey=null; dragPointer=-1;pressed(emptySet()) }
    override fun onDetachedFromWindow() { router.releaseAll(); release(); feedback.cancel(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { if(!hasWindowFocus) release(); super.onWindowFocusChanged(hasWindowFocus) }
}
