package dev.gbalite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dev.gbalite.core.PeripheralSettings
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/** One application-lifetime store; unrelated to battery, state and input-profile files. */
class PeripheralPreferences(context: Context) {
    private val store = singleton(context.applicationContext)
    val settings: Flow<PeripheralSettings> = store.data
    suspend fun write(value: PeripheralSettings) { store.updateData { value } }
    companion object {
        @Volatile private var instance: DataStore<PeripheralSettings>? = null
        private fun singleton(context: Context): DataStore<PeripheralSettings> = instance ?: synchronized(this) {
            instance ?: DataStoreFactory.create(serializer=object: Serializer<PeripheralSettings> {
                override val defaultValue = PeripheralSettings()
                override suspend fun readFrom(input: InputStream): PeripheralSettings {
                    val bytes=ByteArray(257); var size=0
                    while(size<bytes.size) { val n=input.read(bytes,size,bytes.size-size); if(n<0) break; size+=n }
                    return if(size>256) PeripheralSettings() else PeripheralSettings.decode(String(bytes,0,size,Charsets.UTF_8))
                }
                override suspend fun writeTo(t: PeripheralSettings, output: OutputStream) { output.write(t.encode().toByteArray(Charsets.UTF_8)) }
            }, scope=CoroutineScope(SupervisorJob()+Dispatchers.IO),
                produceFile={File(context.filesDir,"settings/peripherals-v1.settings")}).also { instance=it }
        }
    }
}
