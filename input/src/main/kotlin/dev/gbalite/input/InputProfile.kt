package dev.gbalite.input
import dev.gbalite.core.GbaButton
import kotlin.math.abs
data class TouchControl(val key: String,val x: Float,val y: Float,val size: Float)
data class InputProfile(val landscape: Boolean,val controls: List<TouchControl>,val opacity: Float=.65f) {
    fun scaledSizes(factor: Float): InputProfile {
        require(valid() && factor.isFinite() && factor>0)
        val scale=factor.coerceIn(MIN_SIZE/controls.minOf { it.size },MAX_SIZE/controls.maxOf { it.size })
        return copy(controls=controls.map { it.copy(size=(it.size*scale).coerceIn(MIN_SIZE,MAX_SIZE)) })
    }
    fun valid()=opacity.isFinite() && opacity in .15f..1f && controls.map { it.key }.toSet()==keys &&
        controls.size==keys.size && controls.all { it.x.isFinite() && it.y.isFinite() && it.size.isFinite() &&
            it.x in .08f.. .92f && it.y in .08f.. .92f && it.size in MIN_SIZE..MAX_SIZE }
    companion object {
        const val MIN_SIZE=.07f
        const val MAX_SIZE=.60f
        val keys=setOf("DPAD","A","B","L","R","START","SELECT")
        fun default(landscape: Boolean)=InputProfile(landscape,if(landscape) listOf(
            TouchControl("DPAD",.13f,.52f,.25f),TouchControl("A",.90f,.46f,.14f),TouchControl("B",.78f,.61f,.14f),
            TouchControl("L",.10f,.13f,.13f),TouchControl("R",.90f,.13f,.13f),
            TouchControl("SELECT",.36f,.87f,.10f),TouchControl("START",.64f,.87f,.10f))
        else listOf(TouchControl("DPAD",.24f,.43f,.32f),TouchControl("A",.83f,.35f,.20f),TouchControl("B",.65f,.53f,.20f),
            TouchControl("L",.12f,.12f,.17f),TouchControl("R",.88f,.12f,.17f),
            TouchControl("SELECT",.38f,.83f,.16f),TouchControl("START",.63f,.83f,.16f)))
    }
}
interface InputProfileStore { fun read(landscape: Boolean): InputProfile; fun write(profile: InputProfile) }
fun dpad(x: Float,y: Float): Set<GbaButton> {
    if(!x.isFinite() || !y.isFinite() || x*x+y*y < .22f*.22f) return emptySet()
    val magnitude=maxOf(abs(x),abs(y))
    return buildSet {
        if(abs(x)>=magnitude*.45f) add(if(x>0) GbaButton.RIGHT else GbaButton.LEFT)
        if(abs(y)>=magnitude*.45f) add(if(y>0) GbaButton.DOWN else GbaButton.UP)
    }
}
object GamepadMapping {
    val keys=mapOf(96 to GbaButton.A,99 to GbaButton.B,102 to GbaButton.L,103 to GbaButton.R,
        108 to GbaButton.START,109 to GbaButton.SELECT,4 to GbaButton.SELECT,
        19 to GbaButton.UP,20 to GbaButton.DOWN,21 to GbaButton.LEFT,22 to GbaButton.RIGHT,
        54 to GbaButton.A,52 to GbaButton.B,45 to GbaButton.L,51 to GbaButton.R,66 to GbaButton.START,61 to GbaButton.SELECT)
    fun axes(x: Float,y: Float,flat: Float=0f): Set<GbaButton> {
        val threshold=maxOf(.5f,flat.coerceIn(0f,.95f))
        return buildSet {
            if(x.isFinite() && abs(x)>threshold) add(if(x>0) GbaButton.RIGHT else GbaButton.LEFT)
            if(y.isFinite() && abs(y)>threshold) add(if(y>0) GbaButton.DOWN else GbaButton.UP)
        }
    }
}
