package dev.gbalite.app
import android.graphics.Bitmap
import android.util.AtomicFile
import dev.gbalite.core.*
import dev.gbalite.input.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
class PlayerImages(private val root: File): StateScreenshotWriter {
    private fun bitmap(frames: FrameSource): Bitmap {
        val buffer=ByteBuffer.allocateDirect(240*160*4)
        check(frames.copyFrame(buffer)); buffer.rewind()
        val pixels=IntArray(240*160) {
            val r=buffer.get().toInt() and 255; val g=buffer.get().toInt() and 255
            val b=buffer.get().toInt() and 255; buffer.get()
            (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels,240,160,Bitmap.Config.ARGB_8888)
    }
    private fun encode(file: File,frames: FrameSource) {
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val image=bitmap(frames)
        val atomic=AtomicFile(file); val output=atomic.startWrite()
        try {
            @Suppress("DEPRECATION")
            check(image.compress(Bitmap.CompressFormat.WEBP,100,output)); atomic.finishWrite(output)
        } catch(e: Exception) { atomic.failWrite(output); throw e }
        finally { image.recycle() }
    }
    override fun write(id: GameId,key: String,frames: FrameSource,createdAt: String) {
        require(key.matches(Regex("slot-[1-4]|quick|auto-[abc]")))
        encode(File(root,"thumbnails/${id.value}/$key.webp"),frames)
        val stamp=AtomicFile(File(root,"thumbnails/${id.value}/$key.time")); val output=stamp.startWrite()
        try { output.write(createdAt.toByteArray()); stamp.finishWrite(output) } catch(e: Exception) { stamp.failWrite(output); throw e }
    }
    fun screenshot(frames: FrameSource,id: GameId?=null): File=File(root,"screenshots/${id?.value?.plus('/') ?: ""}${Instant.now().toEpochMilli()}-${UUID.randomUUID()}.webp").also { encode(it,frames) }
    fun thumbnail(id: GameId?,slot: Int,createdAt: String?): File?=id?.let {
        val file=File(root,"thumbnails/${it.value}/slot-$slot.webp")
        val time=File(root,"thumbnails/${it.value}/slot-$slot.time")
        file.takeIf { it.exists() && runCatching { time.readText()==createdAt }.getOrDefault(false) }
    }
}
class PrivateInputProfiles(private val root: File): InputProfileStore {
    private fun file(landscape: Boolean)=AtomicFile(File(root,"input/${if(landscape) "landscape" else "portrait"}.json"))
    override fun read(landscape: Boolean): InputProfile=runCatching {
        val f=file(landscape); check(f.baseFile.length() in 1..16384)
        val obj=JSONObject(f.readFully().toString(Charsets.UTF_8)); check(obj.getInt("schema")==1)
        val c=obj.getJSONArray("controls"); check(c.length()==7)
        InputProfile(landscape,(0 until c.length()).map { i-> c.getJSONObject(i).let {
            TouchControl(it.getString("key"),it.getDouble("x").toFloat(),it.getDouble("y").toFloat(),it.getDouble("size").toFloat())
        } },obj.getDouble("opacity").toFloat()).also { check(it.valid()) }
    }.getOrElse { InputProfile.default(landscape) }
    override fun write(profile: InputProfile) {
        require(profile.valid()); val atomic=file(profile.landscape)
        check(atomic.baseFile.parentFile!!.mkdirs() || atomic.baseFile.parentFile!!.isDirectory)
        val data=JSONObject().put("schema",1).put("opacity",profile.opacity).put("controls",JSONArray().apply {
            profile.controls.forEach { put(JSONObject().put("key",it.key).put("x",it.x).put("y",it.y).put("size",it.size)) }
        }).toString().toByteArray()
        val output=atomic.startWrite()
        try { output.write(data); atomic.finishWrite(output) } catch(e: Exception) { atomic.failWrite(output); throw e }
    }
}
