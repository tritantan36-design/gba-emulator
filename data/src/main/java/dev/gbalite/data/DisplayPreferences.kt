package dev.gbalite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dev.gbalite.core.DisplaySettings
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/** One application-lifetime store; unrelated to battery, state and input-profile files. */
class DisplayPreferences(context: Context) {
    private val store = singleton(context.applicationContext)
    val settings: Flow<DisplaySettings> = store.data
    suspend fun write(value: DisplaySettings) { store.updateData { value } }
    companion object {
        @Volatile private var instance: DataStore<DisplaySettings>? = null
        private fun singleton(context: Context): DataStore<DisplaySettings> = instance ?: synchronized(this) {
            instance ?: DataStoreFactory.create(serializer=object: Serializer<DisplaySettings> {
                override val defaultValue = DisplaySettings()
                override suspend fun readFrom(input: InputStream): DisplaySettings {
                    val bytes=ByteArray(257); var size=0
                    while(size<bytes.size) { val n=input.read(bytes,size,bytes.size-size); if(n<0) break; size+=n }
                    return if(size>256) DisplaySettings.FALLBACK else DisplaySettings.decode(String(bytes,0,size,Charsets.UTF_8))
                }
                override suspend fun writeTo(t: DisplaySettings, output: OutputStream) { output.write(t.encode().toByteArray(Charsets.UTF_8)) }
            }, scope=CoroutineScope(SupervisorJob()+Dispatchers.IO),
                produceFile={File(context.filesDir,"settings/display-v1.settings")}).also { instance=it }
        }
    }
}
