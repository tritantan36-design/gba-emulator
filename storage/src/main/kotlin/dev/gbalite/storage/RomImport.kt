package dev.gbalite.storage

import dev.gbalite.core.GameId
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile

enum class ImportError { UNREADABLE, TOO_LARGE, INVALID_GBA, ZIP_EMPTY, ZIP_MULTIPLE, ZIP_UNSAFE, MISMATCH, CANCELED }
class ImportFailure(val error: ImportError): IOException(error.name)
data class ValidatedRom(val id: GameId,val file: File,val size: Long,val title: String)
/** No Android, no output paths derived from entry names, no whole-ROM allocation. */
class RomImport(private val root: File,private val checkCanceled: ()->Unit={}) {
    companion object {
        const val INPUT_MAX=40L*1024*1024;const val TOTAL_MAX=40L*1024*1024
        // Require content beyond the header; keep the project's tiny legal homebrew.
        // Native also pads its backing allocation for the pinned cartridge detector.
        const val ROM_MIN=256L
        const val ROM_MAX=32L*1024*1024;const val ENTRY_MAX=64;const val RATIO_MAX=200L
        const val PATH_MAX=240;const val DEPTH_MAX=8;const val TIMEOUT_MS=10000L
        private val importLock=Any()
        private val ownedTemp=Regex("rom\\.tmp\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun title(name: String)=name.substringAfterLast('/').replace(Regex("(?i)\\.(gba|zip)$"),"")
            .replace(Regex("[_-]+")," ").replace(Regex("\\s+")," ").trim().take(120).ifEmpty { "GBA 游戏" }
    }
    private val started=System.nanoTime()
    private fun guard() {checkCanceled();if((System.nanoTime()-started)/1_000_000>TIMEOUT_MS) throw ImportFailure(ImportError.UNREADABLE)}
    private fun fail(error: ImportError=ImportError.ZIP_UNSAFE): Nothing=throw ImportFailure(error)
    private fun temp()=File(root,"rom.tmp.${UUID.randomUUID()}")
    fun import(input: InputStream,name: String,expected: GameId?=null,beforeCommit: ()->Unit={}): ValidatedRom =
        synchronized(importLock) {
            check(root.mkdirs()||root.isDirectory)
            // Only exact private UUID temporaries, serialized against all imports in this process.
            root.listFiles()?.filter {ownedTemp.matches(it.name) && Files.isRegularFile(it.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)}?.forEach {it.delete()}
            importLocked(input,name,expected,beforeCommit)
        }
    private fun importLocked(input: InputStream,name: String,expected: GameId?,beforeCommit: ()->Unit): ValidatedRom {
        check(root.mkdirs()||root.isDirectory)
        val source=temp();val rom=temp()
        try {
            input.use { stream -> FileOutputStream(source).use { copy(stream,it,INPUT_MAX) } }
            val entryName=when {
                name.endsWith(".gba",true) -> {if(source.length()>ROM_MAX) fail(ImportError.TOO_LARGE);Files.copy(source.toPath(),rom.toPath());name}
                name.endsWith(".zip",true) -> unzip(source,rom)
                else -> fail(ImportError.INVALID_GBA)
            }
            if(rom.length() !in ROM_MIN..ROM_MAX) fail(ImportError.INVALID_GBA)
            RandomAccessFile(rom,"r").use {
                // Match the pinned core's initial ARM branch signature as well as
                // the fixed header byte, so rejected images never enter Library.
                it.seek(3);if(it.read()!=0xEA) fail(ImportError.INVALID_GBA)
                it.seek(0xB2);if(it.read()!=0x96) fail(ImportError.INVALID_GBA)
            }
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            rom.inputStream().use {val b=ByteArray(65536);while(true) {guard();val n=it.read(b);if(n<0) break;digest.update(b,0,n)}}
            val id=GameId(digest.digest().joinToString("") {"%02x".format(it)})
            if(expected!=null && id!=expected) fail(ImportError.MISMATCH)
            guard();beforeCommit();guard()
            FileOutputStream(rom,true).use {it.fd.sync()}
            val target=File(root,"${id.value}.gba")
            Files.move(rom.toPath(),target.toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
            return ValidatedRom(id,target,target.length(),title(entryName))
        } catch(e: ImportFailure) {throw e}
        catch(e: java.util.concurrent.CancellationException) {throw e}
        catch(e: Exception) {throw ImportFailure(if(name.endsWith(".zip",true)) ImportError.ZIP_UNSAFE else ImportError.UNREADABLE).apply {initCause(e)}}
        finally {source.delete();rom.delete()}
    }
    private fun copy(input: InputStream,output: OutputStream,max: Long,crc: CRC32?=null): Long {
        val b=ByteArray(65536);var size=0L
        while(true) {guard();val n=input.read(b);if(n<0) break;if(n==0) fail(ImportError.UNREADABLE)
            size+=n;if(size>max) fail(ImportError.TOO_LARGE);crc?.update(b,0,n);output.write(b,0,n)}
        return size
    }
    private data class Entry(val name: String,val flags: Int,val method: Int,val crc: Long,val compressed: Long,val size: Long,val offset: Long)
    private fun directory(file: File): List<Entry> = RandomAccessFile(file,"r").use {f ->
        fun u16(): Int {val a=f.read();val b=f.read();if(a<0||b<0) fail();return a or (b shl 8)}
        fun u32(): Long=u16().toLong() or (u16().toLong() shl 16)
        val length=f.length();if(length<22) fail()
        var eocd=-1L
        for(pos in length-22 downTo maxOf(0,length-22-65535)) {
            guard();f.seek(pos);if(u32()==0x06054b50L) {f.seek(pos+20);if(pos+22+u16()==length) {eocd=pos;break}}
        }
        if(eocd<0) fail();f.seek(eocd+4)
        if(u16()!=0 || u16()!=0) fail()
        val count=u16();if(count!=u16() || count>ENTRY_MAX) fail()
        val size=u32();val offset=u32()
        if(offset+size!=eocd || size==0xffffffffL || offset==0xffffffffL) fail()
        f.seek(offset);val entries=ArrayList<Entry>();val names=HashSet<String>();var total=0L;var compressedTotal=0L
        repeat(count) {
            guard();if(u32()!=0x02014b50L) fail();u16();val version=u16();val flags=u16();val method=u16();u16();u16()
            val crc=u32();val compressed=u32();val expanded=u32();val n=u16();val extra=u16();val comment=u16()
            val disk=u16();u16();val attributes=u32();val local=u32()
            if(version>20 || disk!=0 || flags and 0xF7F1!=0 || method !in listOf(0,8) || n !in 1..PATH_MAX ||
                compressed==0xffffffffL || expanded==0xffffffffL || local>=offset || (attributes shr 16).toInt() and 0xF000==0xA000) fail()
            val bytes=ByteArray(n);f.readFully(bytes)
            val name=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            if(flags and 0x800==0 && bytes.any {it<0}) fail()
            val pieces=name.removeSuffix("/").split('/')
            if(name.startsWith('/') || name.contains('\\') || name.contains(':') || name.any {it.code<32 || it.code==127} ||
                pieces.size>DEPTH_MAX || pieces.any {it.isEmpty()||it=="."||it==".."} ||
                !names.add(Normalizer.normalize(name.removeSuffix("/"),Normalizer.Form.NFC).lowercase(Locale.ROOT))) fail()
            if(name.endsWith("/")) {if(expanded!=0L || compressed!=0L || crc!=0L) fail()}
            else if(!name.endsWith(".gba",true) && !pieces.last().equals("README.txt",true) && !pieces.last().equals("LICENSE",true)) fail()
            if(expanded>TOTAL_MAX || expanded>maxOf(1,compressed)*RATIO_MAX) fail()
            total+=expanded;compressedTotal+=compressed
            if(total>TOTAL_MAX || total>maxOf(1,compressedTotal)*RATIO_MAX) fail()
            if(name.endsWith(".gba",true) && expanded>ROM_MAX) fail(ImportError.TOO_LARGE)
            f.seek(f.filePointer+extra+comment);if(f.filePointer>eocd) fail()
            entries+=Entry(name,flags,method,crc,compressed,expanded,local)
        }
        if(f.filePointer!=eocd) fail()
        var previousEnd=0L
        for(e in entries.sortedBy {it.offset}) {
            f.seek(e.offset);if(e.offset<previousEnd || u32()!=0x04034b50L) fail()
            if(u16()>20) fail();if(u16()!=e.flags || u16()!=e.method) fail();u16();u16()
            val crc=u32();val c=u32();val s=u32();val n=u16();val extra=u16()
            if(n !in 1..PATH_MAX) fail();val bytes=ByteArray(n);f.readFully(bytes)
            if(bytes.toString(Charsets.UTF_8)!=e.name) fail()
            if(e.flags and 8==0 && (crc!=e.crc||c!=e.compressed||s!=e.size)) fail()
            previousEnd=f.filePointer+extra+e.compressed
            if(previousEnd>offset) fail()
            if(e.flags and 8!=0) {
                f.seek(previousEnd);val first=u32();val descriptorCrc=if(first==0x08074b50L) u32() else first
                if(descriptorCrc!=e.crc || u32()!=e.compressed || u32()!=e.size) fail()
                previousEnd=f.filePointer;if(previousEnd>offset) fail()
            }
        }
        entries
    }
    private fun unzip(source: File,rom: File): String {
        val entries=directory(source);val gba=entries.filter {it.name.endsWith(".gba",true)}
        if(gba.isEmpty()) fail(ImportError.ZIP_EMPTY);if(gba.size!=1) fail(ImportError.ZIP_MULTIPLE)
        ZipFile(source,Charsets.UTF_8).use {zip ->
            if(zip.size()!=entries.size) fail()
            for(e in entries) {
                guard();if(e.name.endsWith('/')) continue
                val z=zip.getEntry(e.name) ?: fail();val crc=CRC32()
                val n=zip.getInputStream(z).use {input ->
                    if(e==gba.single()) FileOutputStream(rom).use {copy(input,it,minOf(ROM_MAX,e.size),crc)}
                    else copy(input,object: OutputStream() {override fun write(b: Int) {};override fun write(b: ByteArray,off: Int,len: Int) {}},e.size,crc)
                }
                if(n!=e.size || crc.value!=e.crc) fail()
            }
        }
        return gba.single().name
    }
}
