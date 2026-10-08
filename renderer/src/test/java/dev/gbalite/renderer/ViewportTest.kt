package dev.gbalite.renderer
import dev.gbalite.core.ScaleMode
import org.junit.Assert.*
import org.junit.Test
class ViewportTest {
    @Test fun fitNeverCropsStretchesOrLosesCenter() {
        for(w in listOf(239,240,480,1080,2400)) for(h in listOf(159,160,320,701,1080,2400)) {
            val v=Viewport.calculate(w,h,ScaleMode.FIT)
            assertEquals(v.width*2,v.height*3)
            assertTrue(v.width<=w && v.height<=h)
            assertTrue(kotlin.math.abs(w-v.width-2*v.x)<=1 && kotlin.math.abs(h-v.height-2*v.y)<=1)
            assertTrue(w-v.width<3 || h-v.height<2)
        }
    }
    @Test fun integerIsLargestLegalMultipleAndTinyFallsBack() {
        for(w in listOf(239,240,480,1080,2400)) for(h in listOf(159,160,320,701,1080)) {
            val factor=minOf(w/240,h/160); val v=Viewport.calculate(w,h,ScaleMode.INTEGER)
            if(factor==0) assertEquals(Viewport.calculate(w,h,ScaleMode.FIT),v)
            else { assertEquals(240*factor,v.width);assertEquals(160*factor,v.height) }
        }
        assertEquals(Viewport(0,0,0,0),Viewport.calculate(0,100,ScaleMode.FIT))
    }
}
