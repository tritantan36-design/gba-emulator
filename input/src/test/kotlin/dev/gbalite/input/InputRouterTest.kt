package dev.gbalite.input
import dev.gbalite.core.GbaButton
import org.junit.Assert.*
import org.junit.Test
class InputRouterTest {
    @Test fun simultaneousButtonsAndCancellationReleaseAll() {
        val events = mutableListOf<Pair<GbaButton,Boolean>>()
        val router = InputRouter { b,p -> events += b to p }
        router.setButton(GbaButton.A,true); router.setButton(GbaButton.A,true)
        router.setButton(GbaButton.UP,true); router.releaseAll(); router.releaseAll()
        assertEquals(listOf(GbaButton.A to true,GbaButton.UP to true,GbaButton.A to false,GbaButton.UP to false),events)
    }
    @Test fun unknownReleaseDoesNotInjectInput() {
        val events = mutableListOf<Boolean>()
        val router = InputRouter { _,p -> events += p }
        router.setButton(GbaButton.L,false)
        assertTrue(events.isEmpty())
    }
}
