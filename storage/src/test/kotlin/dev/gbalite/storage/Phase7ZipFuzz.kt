package dev.gbalite.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Standalone mutation campaign; not a deterministic fixture test or coverage-guided fuzzer. */
object Phase7ZipFuzz {
    @JvmStatic fun main(args: Array<String>) {
        val seconds=args.firstOrNull()?.toLong() ?: 60L
        require(seconds in 1..3600)
        val root=Files.createTempDirectory("phase7-zip-fuzz").toFile()
        val random=Random(0x7042026)
        val rom=ByteArray(4096).also {random.nextBytes(it);it[0xB2]=0x96.toByte()}
        fun zip(name: String)=ByteArrayOutputStream().also {out ->ZipOutputStream(out).use {z ->z.putNextEntry(ZipEntry(name));z.write(rom);z.closeEntry()}}.toByteArray()
        val seeds=listOf(zip("test.gba"),zip("../test.gba"),zip("a/test.gba"),ByteArray(192),byteArrayOf(0x50,0x4b,3,4))
        val started=System.nanoTime();var count=0L;var accepted=0L;var rejected=0L;var maxNanos=0L
        try {
            while((System.nanoTime()-started)/1_000_000_000<seconds) {
                var b=seeds[random.nextInt(seeds.size)].copyOf()
                when(random.nextInt(5)) {
                    0 -> repeat(1+random.nextInt(16)) {if(b.isNotEmpty()) b[random.nextInt(b.size)]=random.nextInt(256).toByte()}
                    1 -> b=b.copyOf(random.nextInt(b.size+1))
                    2 -> b+=ByteArray(random.nextInt(256)).also {random.nextBytes(it)}
                    3 -> if(b.size>4) {val at=random.nextInt(b.size-3);repeat(4) {b[at+it]=0xff.toByte()}}
                    else -> b=ByteArray(random.nextInt(8192)).also {random.nextBytes(it)}
                }
                val before=System.nanoTime()
                try {
                    val result=RomImport(root).import(b.inputStream(),"mutation.zip")
                    check(result.size in 192..RomImport.ROM_MAX);check(result.file.parentFile==root)
                    accepted++;check(result.file.delete())
                } catch(_: ImportFailure) {rejected++}
                maxNanos=maxOf(maxNanos,System.nanoTime()-before)
                check(root.listFiles()!!.isEmpty()) {"Leaked files at iteration $count"}
                count++
            }
            println("{\"kind\":\"JVM ZIP mutation fuzz, not coverage-guided\",\"seed\":117710886,\"seeds\":5,\"iterations\":$count,\"accepted\":$accepted,\"rejected\":$rejected,\"elapsedMs\":${(System.nanoTime()-started)/1_000_000},\"maxCaseMs\":${maxNanos/1_000_000},\"crashes\":0,\"oom\":0}")
        } finally {root.deleteRecursively()}
    }
}
