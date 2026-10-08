package dev.gbalite.renderer

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.EGL14
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.Surface
import android.os.HandlerThread
import android.os.Handler
import android.view.Choreographer
import android.util.Log
import dev.gbalite.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Settings alter the renderer only; the existing Surface API name is retained. */
class OriginalSurface(context: Context, source: FrameSource, private val onSurfaceLost: ()->Unit={}) : TextureView(context), TextureView.SurfaceTextureListener {
    private var fallback: (String)->Unit = {}
    private val renderer=DisplayRenderer(context.applicationContext,source) { message -> post { fallback(message) } }
    private val thread=HandlerThread("GbaRendererGL").apply { start() }
    private val handler=Handler(thread.looper)
    @Volatile private var closed=false
    @Volatile private var foreground=true
    private var texture: SurfaceTexture?=null
    private var nativeSurface: Surface?=null
    private var display=EGL14.EGL_NO_DISPLAY
    private var eglContext=EGL14.EGL_NO_CONTEXT
    private var eglSurface=EGL14.EGL_NO_SURFACE
    private var renderWidth=0;private var renderHeight=0
    private var initialized=false
    private var clock: Choreographer?=null
    private val frameCallback: Choreographer.FrameCallback=Choreographer.FrameCallback { draw.run() }
    private val draw: Runnable=object: Runnable {
        override fun run() {
            if(closed || !foreground || texture==null) return
            if(clock==null) clock=Choreographer.getInstance()
            try {
                if(eglContext==EGL14.EGL_NO_CONTEXT) createContext()
                renderer.onDrawFrame(null)
                if(!EGL14.eglSwapBuffers(display,eglSurface)) destroyContext()
            } catch(e: Exception) {
                destroyContext()
                if(BuildConfig.DEBUG) Log.e("GbaRenderer","EGL recovery: ${e.message}")
            }
            if(!closed && foreground) clock?.postFrameCallback(frameCallback)
        }
    }
    init {
        isOpaque=true;keepScreenOn=true;surfaceTextureListener=this;initialized=true
    }
    fun configure(settings: DisplaySettings, onFallback: (String)->Unit={}) { fallback=onFallback; renderer.settings=settings }
    fun setDisplayMode(mode: DisplayMode) { renderer.settings=renderer.settings.copy(displayMode=mode) }
    fun setScaleMode(mode: ScaleMode) { renderer.settings=renderer.settings.copy(scaleMode=mode) }
    fun diagnostics(): RendererSnapshot=renderer.snapshot()
    /** Fault injection never accepts external GLSL and is disabled in Release. */
    fun failModeForTest(mode: DisplayMode?, stage: FailureStage=FailureStage.COMPILE) {
        check(BuildConfig.DEBUG); handler.post { renderer.inject(mode,stage) }
    }
    private fun cancelDraw() { handler.removeCallbacks(draw);clock?.removeFrameCallback(frameCallback) }
    fun onPause() { foreground=false; handler.post { cancelDraw();destroyContext() } }
    fun onResume() { foreground=true; handler.post { cancelDraw();draw.run() } }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if(initialized && !closed) { if(visibility==VISIBLE) onResume() else onPause() }
    }
    override fun onSurfaceTextureAvailable(value: SurfaceTexture,width: Int,height: Int) {
        handler.post {
            destroyContext();texture=value;renderWidth=width;renderHeight=height
            cancelDraw();draw.run()
        }
    }
    override fun onSurfaceTextureSizeChanged(value: SurfaceTexture,width: Int,height: Int) {
        handler.post {
            if(texture===value) {
                renderWidth=width;renderHeight=height;value.setDefaultBufferSize(width,height)
                renderer.onSurfaceChanged(null,width,height)
            }
        }
    }
    override fun onSurfaceTextureDestroyed(value: SurfaceTexture): Boolean {
        onSurfaceLost()
        handler.post { if(texture===value) { cancelDraw();destroyContext();texture=null };value.release() }
        return false // Release only after EGL stops referencing this SurfaceTexture.
    }
    override fun onSurfaceTextureUpdated(value: SurfaceTexture) {}
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow();closed=true
        handler.post { cancelDraw();destroyContext();renderer.detached();thread.quitSafely() }
    }
    private fun createContext() {
        display=EglDisplayOwner.acquire()
        val attributes=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(eglContext!=EGL14.EGL_NO_CONTEXT)
        nativeSurface=Surface(checkNotNull(texture))
        eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],nativeSurface,intArrayOf(EGL14.EGL_NONE),0)
        check(eglSurface!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))
        EGL14.eglSwapInterval(display,1)
        renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,renderWidth,renderHeight)
    }
    private fun destroyContext() {
        if(display!=EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if(eglSurface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,eglSurface)
            if(eglContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext)
            EglDisplayOwner.release(display)
        }
        eglSurface=EGL14.EGL_NO_SURFACE;eglContext=EGL14.EGL_NO_CONTEXT;display=EGL14.EGL_NO_DISPLAY
        nativeSurface?.release();nativeSurface=null;renderer.contextLost()
    }
}
private object EglDisplayOwner {
    private var users=0
    private var display=EGL14.EGL_NO_DISPLAY
    @Synchronized fun acquire(): android.opengl.EGLDisplay {
        if(users==0) {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version=IntArray(2);check(EGL14.eglInitialize(display,version,0,version,1))
        }
        users++;return display
    }
    @Synchronized fun release(value: android.opengl.EGLDisplay) {
        check(users>0);users--
        if(users==0) { EGL14.eglTerminate(value);display=EGL14.EGL_NO_DISPLAY }
    }
}
enum class FailureStage { COMPILE, LINK, SETUP }
data class RendererSnapshot(val contextId: Int, val viewport: Viewport, val mode: DisplayMode,
    val textures: Int, val programs: Int, val buffers: Int, val draws: Long,
    val cpuAverageMs: Double, val gpuCompletionWaitMs: Double, val fallbacks: Int, val activeSurfaces: Int,
    val cpuTotalNs: Long, val completionWaitTotalNs: Long, val completionSamples: Long)

private class DisplayRenderer(private val context: Context, private val source: FrameSource,
    private val onFallback: (String)->Unit): GLSurfaceView.Renderer {
    companion object {
        val contexts=AtomicInteger(); val surfaces=AtomicInteger()
        const val VERTEX="attribute vec2 p; attribute vec2 t; varying vec2 v; void main(){v=t;gl_Position=vec4(p,0.,1.);}"
        const val ORIGINAL="precision mediump float; varying vec2 v; uniform sampler2D tex; void main(){gl_FragColor=vec4(texture2D(tex,v).rgb,1.);}"
    }
    private data class Program(val id: Int,val position: Int,val uv: Int,val tex: Int,val output: Int)
    @Volatile var settings=DisplaySettings()
    @Volatile private var viewport=Viewport(0,0,0,0)
    @Volatile private var contextId=0
    @Volatile private var mode=DisplayMode.ORIGINAL
    @Volatile private var draws=0L
    @Volatile private var cpuNs=0L
    @Volatile private var gpuWaitNs=0L
    @Volatile private var gpuSamples=0L
    @Volatile private var fallbackCount=0
    @Volatile private var programCount=0
    @Volatile private var textureCount=0
    private var registered=false
    private val pixels=ByteBuffer.allocateDirect(240*160*4).order(ByteOrder.nativeOrder())
    private val vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)); position(0)
    }
    private val programs=mutableMapOf<DisplayMode,Program>()
    private val failed=mutableSetOf<DisplayMode>()
    private var texture=0
    private var w=0; private var h=0
    private var applied: DisplaySettings?=null
    private var fault: DisplayMode?=null
    private var faultStage=FailureStage.COMPILE
    fun inject(value: DisplayMode?,stage: FailureStage) {
        fault=value; faultStage=stage; applied=null
        if(value!=null && value!=DisplayMode.ORIGINAL) programs.remove(value)?.let { glDeleteProgram(it.id) }
        failed.clear(); programCount=programs.size
    }
    fun detached() { if(registered) { surfaces.decrementAndGet(); registered=false }; textureCount=0; programCount=0 }
    fun contextLost() { textureCount=0;programCount=0 }
    fun snapshot()=RendererSnapshot(contextId,viewport,mode,textureCount,programCount,if(registered) 2 else 0,
        draws,if(draws>0) cpuNs/1e6/draws else 0.0,if(gpuSamples>0) gpuWaitNs/1e6/gpuSamples else 0.0,
        fallbackCount,surfaces.get(),cpuNs,gpuWaitNs,gpuSamples)
    private fun compile(type: Int,code: String): Int {
        val id=glCreateShader(type); check(id!=0) { "glCreateShader" }
        try {
            glShaderSource(id,code); glCompileShader(id)
            val status=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,status,0)
            check(status[0]!=0) { glGetShaderInfoLog(id).take(512) }; return id
        } catch(e: Exception) { glDeleteShader(id); throw e }
    }
    private fun makeProgram(value: DisplayMode): Program {
        var vertex=0; var fragment=0; var id=0
        try {
            vertex=compile(GL_VERTEX_SHADER,VERTEX)
            val code=if(value==DisplayMode.ORIGINAL) ORIGINAL else context.assets.open("shaders/${value.name.lowercase()}.frag").bufferedReader().use { it.readText() }
            val testedCode=when {
                fault!=value -> code
                faultStage==FailureStage.COMPILE -> "invalid test"
                faultStage==FailureStage.LINK -> "precision mediump float; varying vec3 v; uniform sampler2D tex; void main(){gl_FragColor=texture2D(tex,v.xy);}"
                else -> code
            }
            fragment=compile(GL_FRAGMENT_SHADER,testedCode)
            id=glCreateProgram(); check(id!=0); glAttachShader(id,vertex); glAttachShader(id,fragment)
            glLinkProgram(id)
            val status=IntArray(1); glGetProgramiv(id,GL_LINK_STATUS,status,0)
            check(status[0]!=0) { glGetProgramInfoLog(id).take(512) }
            return Program(id,glGetAttribLocation(id,"p"),glGetAttribLocation(id,"t"),glGetUniformLocation(id,"tex"),glGetUniformLocation(id,"outputSize"))
        } catch(e: Exception) { if(id!=0) glDeleteProgram(id); throw e }
        finally { if(vertex!=0) glDeleteShader(vertex); if(fragment!=0) glDeleteShader(fragment) }
    }
    override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
        // Prior-context names are stale; never delete/reuse them in this context.
        programs.clear(); failed.clear(); applied=null; texture=0; textureCount=0; programCount=0
        contextId=contexts.incrementAndGet()
        if(!registered) { surfaces.incrementAndGet(); registered=true }
        programs[DisplayMode.ORIGINAL]=makeProgram(DisplayMode.ORIGINAL); programCount=1
        val names=IntArray(1); glGenTextures(1,names,0); texture=names[0]; textureCount=1
        glBindTexture(GL_TEXTURE_2D,texture)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        pixels.clear(); while(pixels.hasRemaining()) pixels.put(0); pixels.clear()
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,240,160,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)
        glClearColor(0f,0f,0f,1f)
        if(BuildConfig.DEBUG) Log.d("GbaRenderer","context=$contextId created texture=$texture programs=$programCount")
    }
    override fun onSurfaceChanged(gl: GL10?,width: Int,height: Int) { w=width;h=height;applied=null }
    private fun applySettings(): Program {
        val desired=settings
        if(applied!=desired) {
            val background=if(desired.background==BackgroundTone.WHITE) 1f else 0f
            glClearColor(background,background,background,1f)
            viewport=Viewport.calculate(w,h,desired.scaleMode)
            mode=if(desired.displayMode in failed) DisplayMode.ORIGINAL else desired.displayMode
            try {
                if(!programs.containsKey(mode)) { programs[mode]=makeProgram(mode); programCount=programs.size }
                glUseProgram(programs.getValue(mode).id);glBindTexture(GL_TEXTURE_2D,texture)
                val filter=if(mode==DisplayMode.SHARP) GL_LINEAR else GL_NEAREST
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter)
                if(fault==mode && faultStage==FailureStage.SETUP) glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,-1)
                check(glGetError()==GL_NO_ERROR) { "GL setup failed" }
            } catch(e: Exception) {
                if(mode==DisplayMode.ORIGINAL) throw e
                failed+=mode; programs.remove(mode)?.let { glDeleteProgram(it.id) }; programCount=programs.size
                mode=DisplayMode.ORIGINAL; fallbackCount++
                glUseProgram(programs.getValue(mode).id); glBindTexture(GL_TEXTURE_2D,texture)
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST)
                if(BuildConfig.DEBUG) Log.e("GbaRenderer","fallback context=$contextId: ${e.message}")
                onFallback("显示模式加载失败，已恢复 Original")
            }
            applied=desired
            if(BuildConfig.DEBUG) Log.d("GbaRenderer","context=$contextId mode=$mode viewport=$viewport texture=$texture programs=$programCount buffers=2")
        }
        return programs.getValue(mode)
    }
    override fun onDrawFrame(gl: GL10?) {
        val start=if(BuildConfig.DEBUG) System.nanoTime() else 0L
        val program=applySettings(); val vp=viewport
        glClear(GL_COLOR_BUFFER_BIT);glViewport(vp.x,vp.y,vp.width,vp.height)
        glUseProgram(program.id);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture)
        pixels.clear()
        if(source.copyFrame(pixels)) { pixels.position(0);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,240,160,GL_RGBA,GL_UNSIGNED_BYTE,pixels) }
        glUniform1i(program.tex,0)
        if(program.output>=0) glUniform2f(program.output,vp.width.toFloat(),vp.height.toFloat())
        vertices.position(0);glVertexAttribPointer(program.position,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(program.position)
        vertices.position(2);glVertexAttribPointer(program.uv,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(program.uv)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
        if(BuildConfig.DEBUG) {
            draws++;cpuNs+=System.nanoTime()-start
            if(draws%120==0L) { val submitted=System.nanoTime();glFinish();gpuWaitNs+=System.nanoTime()-submitted;gpuSamples++ }
        }
    }
}
