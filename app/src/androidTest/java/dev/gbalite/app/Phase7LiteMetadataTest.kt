package dev.gbalite.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.input.InputProfile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Phase7LiteMetadataTest {
    @Test fun missingInvalidAndFutureInputSettingsFallbackWithoutDestructiveReset() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=Files.createTempDirectory(context.cacheDir.toPath(),"lite-settings-").toFile()
        try {
            val store=PrivateInputProfiles(root)
            assertEquals(InputProfile.default(false),store.read(false))
            val valid=InputProfile.default(true).copy(opacity=.8f)
            store.write(valid)
            val landscape=File(root,"input/landscape.json").readBytes()
            val portrait=File(root,"input/portrait.json")
            for(text in listOf("{invalid","{\"schema\":999}","{\"schema\":1,\"controls\":[]}","{}")) {
                portrait.writeText(text)
                assertEquals(InputProfile.default(false),store.read(false))
                assertEquals(text,portrait.readText())
                assertArrayEquals(landscape,File(root,"input/landscape.json").readBytes())
                assertEquals(valid,store.read(true))
            }
        } finally {root.deleteRecursively()}
    }
}
