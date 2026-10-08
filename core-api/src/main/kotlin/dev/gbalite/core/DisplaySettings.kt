package dev.gbalite.core

enum class DisplayMode { ORIGINAL, SHARP, GBA_COLOR, LCD }
enum class ScaleMode { FIT, INTEGER }
enum class BackgroundTone { BLACK, WHITE }
data class DisplaySettings(val displayMode: DisplayMode = DisplayMode.SHARP, val scaleMode: ScaleMode = ScaleMode.FIT,
    val background: BackgroundTone = BackgroundTone.BLACK) {
    fun encode(): String = "2\n${displayMode.name}\n${scaleMode.name}\n${background.name}\n"
    companion object {
        val FALLBACK = DisplaySettings(DisplayMode.ORIGINAL, ScaleMode.FIT)
        fun decode(text: String): DisplaySettings {
            val lines = text.lines()
            val legacy=lines.size==4 && lines[0]=="1" && lines[3].isEmpty()
            val current=lines.size==5 && lines[0]=="2" && lines[4].isEmpty()
            if(!legacy && !current) return FALLBACK
            return DisplaySettings(DisplayMode.entries.firstOrNull { it.name == lines[1] } ?: DisplayMode.ORIGINAL,
                ScaleMode.entries.firstOrNull { it.name == lines[2] } ?: ScaleMode.FIT,
                if(current) BackgroundTone.entries.firstOrNull { it.name==lines[3] } ?: BackgroundTone.BLACK else BackgroundTone.BLACK)
        }
    }
}
