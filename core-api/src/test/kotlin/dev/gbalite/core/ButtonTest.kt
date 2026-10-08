package dev.gbalite.core
import org.junit.Assert.*
import org.junit.Test
class ButtonTest {
    @Test fun gbaHardwareBitOrderIsStable() {
        val expected = mapOf(GbaButton.A to 1, GbaButton.B to 2, GbaButton.SELECT to 4, GbaButton.START to 8,
            GbaButton.RIGHT to 16, GbaButton.LEFT to 32, GbaButton.UP to 64, GbaButton.DOWN to 128,
            GbaButton.R to 256, GbaButton.L to 512)
        expected.forEach { (button, mask) -> assertEquals(mask, button.mask) }
        val total: Int = GbaButton.entries.fold(0) { acc, b -> acc or b.mask }
        assertEquals(1023, total)
    }
}
