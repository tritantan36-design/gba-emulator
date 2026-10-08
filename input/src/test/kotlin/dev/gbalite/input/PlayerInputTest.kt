package dev.gbalite.input
import dev.gbalite.core.GbaButton.*
import org.junit.Test
import org.junit.Assert.*
class PlayerInputTest {
    @Test fun overallScalingKeepsRelativeSizesPositionsAndBounds() {
        val original=InputProfile.default(false)
        for(factor in listOf(.01f,1.4f,100f)) {
            val scaled=original.scaledSizes(factor)
            assertTrue(scaled.valid()); assertEquals(original.opacity,scaled.opacity)
            val ratio=scaled.controls.first().size/original.controls.first().size
            original.controls.zip(scaled.controls).forEach { (before,after)->
                assertEquals(before.x,after.x); assertEquals(before.y,after.y)
                assertEquals(ratio,after.size/before.size,.00001f)
            }
        }
    }
    @Test fun multitouchAndSourcesAreIndependent() {
        val down=mutableSetOf<dev.gbalite.core.GbaButton>()
        val r=InputRouter { b,v-> if(v) down+=b else down-=b }
        for(keys in listOf(setOf(RIGHT,A),setOf(LEFT,B),setOf(UP,A,B),setOf(L,A),setOf(R,B))) {
            keys.forEachIndexed { i,b->r.update("touch:$i",setOf(b)) }; assertEquals(keys,down)
            r.releasePrefix("touch:"); assertTrue(down.isEmpty())
        }
        r.update("touch:1",setOf(A)); r.update("hid:1:key",setOf(A))
        r.releasePrefix("hid:1:"); assertEquals(setOf(A),down)
        r.releaseAll(); assertTrue(down.isEmpty())
        r.update("hid:1:key",setOf(A)); r.releasePrefix("hid:1:"); r.update("hid:1:key",setOf(B)); assertEquals(setOf(B),down)
    }
    @Test fun dpadSlideDiagonalAndDeadZone() {
        assertEquals(setOf(UP),dpad(0f,-.9f)); assertEquals(setOf(RIGHT),dpad(.9f,0f))
        assertEquals(setOf(LEFT),dpad(-.9f,0f)); assertEquals(setOf(DOWN),dpad(0f,.9f))
        assertEquals(setOf(UP,RIGHT),dpad(.8f,-.8f)); assertTrue(dpad(.1f,.1f).isEmpty())
    }
    @Test fun mappingAxesAndOrientationDefaults() {
        assertEquals(A,GamepadMapping.keys[96]); assertEquals(B,GamepadMapping.keys[99])
        assertEquals(setOf(RIGHT,UP),GamepadMapping.axes(.8f,-.8f))
        assertTrue(GamepadMapping.axes(.3f,-.3f).isEmpty())
        assertTrue(GamepadMapping.axes(Float.NaN,Float.NaN).isEmpty())
        assertTrue(InputProfile.default(false).valid()); assertTrue(InputProfile.default(true).valid())
        assertNotEquals(InputProfile.default(false).controls,InputProfile.default(true).controls)
        assertFalse(InputProfile.default(false).copy(opacity=Float.NaN).valid())
    }
}
