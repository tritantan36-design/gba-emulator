package dev.gbalite.core
import org.junit.Assert.*
import org.junit.Test
class DisplaySettingsTest {
    @Test fun allModesRoundTrip() {
        for(display in DisplayMode.entries) for(scale in ScaleMode.entries) for(background in BackgroundTone.entries) {
            val settings=DisplaySettings(display,scale,background)
            assertEquals(settings,DisplaySettings.decode(settings.encode()))
        }
    }
    @Test fun unknownAndDamagedSettingsAreSafe() {
        assertEquals(DisplaySettings.FALLBACK,DisplaySettings.decode("1\nFUTURE\nFUTURE\n"))
        assertEquals(DisplaySettings.FALLBACK,DisplaySettings.decode("2\nSHARP\nINTEGER\n"))
        assertEquals(DisplaySettings.FALLBACK,DisplaySettings.decode("garbage"))
        assertEquals(DisplaySettings(DisplayMode.LCD,ScaleMode.FIT),DisplaySettings.decode("1\nLCD\nretired\n"))
        assertEquals(DisplaySettings(DisplayMode.ORIGINAL,ScaleMode.INTEGER),DisplaySettings.decode("1\nretired\nINTEGER\n"))
        assertEquals(DisplaySettings(DisplayMode.LCD,ScaleMode.INTEGER),DisplaySettings.decode("1\nLCD\nINTEGER\n"))
        assertEquals(DisplaySettings(DisplayMode.SHARP,ScaleMode.FIT),DisplaySettings.decode("2\nSHARP\nFIT\nretired\n"))
    }
}
