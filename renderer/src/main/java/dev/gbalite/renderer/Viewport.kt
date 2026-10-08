package dev.gbalite.renderer
import dev.gbalite.core.ScaleMode

data class Viewport(val x: Int, val y: Int, val width: Int, val height: Int) {
    companion object {
        fun calculate(w: Int, h: Int, mode: ScaleMode): Viewport {
            if (w <= 0 || h <= 0) return Viewport(0,0,0,0)
            val integer = minOf(w / 240, h / 160)
            // Integer 3:2 units keep Fit exact too; leave at most two extra pixels at edges.
            val unit = if (mode == ScaleMode.INTEGER && integer > 0) integer * 80 else minOf(w / 3, h / 2)
            val width = unit * 3; val height = unit * 2
            return Viewport((w-width)/2,(h-height)/2,width,height)
        }
    }
}
