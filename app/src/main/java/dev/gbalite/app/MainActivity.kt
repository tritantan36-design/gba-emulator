package dev.gbalite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gbalite.player.Player

class MainActivity: ComponentActivity() {
    private lateinit var model: PlayerViewModel
    private lateinit var hid: HidInput
    private var foreground by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        model=ViewModelProvider(this)[PlayerViewModel::class.java]
        hid=HidInput(this,model.session.input)
        val previousCallback=window.callback
        window.callback=object: android.view.Window.Callback by previousCallback {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if(model.playing && model.session.state.value==dev.gbalite.session.SessionState.RUNNING && hid.key(event)) return true
                return previousCallback.dispatchKeyEvent(event)
            }
            override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
                if(model.playing && model.session.state.value==dev.gbalite.session.SessionState.RUNNING && hid.motion(event)) return true
                return previousCallback.dispatchGenericMotionEvent(event)
            }
        }
        setContent {
            val landscape=androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
            @Suppress("DEPRECATION")
            val sensorRotation=windowManager.defaultDisplay.rotation
            SideEffect { model.sensors.orientation(sensorRotation) }
            val lightBackground=model.displaySettings.background==dev.gbalite.core.BackgroundTone.WHITE
            DisposableEffect(model.playing,model.appSettings,landscape,lightBackground) {
                val controller=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
                if(model.playing && !model.appSettings && landscape) {
                    controller.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars=!model.playing || model.appSettings || lightBackground
                controller.isAppearanceLightNavigationBars=!model.playing || model.appSettings || lightBackground
                window.attributes=window.attributes.apply {
                    if(android.os.Build.VERSION.SDK_INT>=28) layoutInDisplayCutoutMode=
                        if(model.playing && !model.appSettings && landscape) {
                            if(android.os.Build.VERSION.SDK_INT>=30) android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        } else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
                onDispose { controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
            }
            dev.gbalite.player.ui.GbaTheme {
                Surface(Modifier.fillMaxSize(),color=if(model.playing && !model.appSettings) {
                    if(lightBackground) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                } else MaterialTheme.colorScheme.surface) {
                    val error by model.session.persistenceError.collectAsState()
                    if(model.playing && model.appSettings) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {UnifiedSettings(model,model::closeAppSettings,rootBack=true)}
                    } else if(model.playing) {
                        dev.gbalite.player.ui.GbaGameTheme { Column {
                            model.message?.let { Text(it,Modifier.padding(8.dp)) }
                            error?.let { Text(errorText(it),Modifier.padding(8.dp)) }
                            Player(model.session,foreground,model::exit,error?.let(::errorText),model.profiles,model::screenshot,
                                model.displaySettings,model::display,model::displayFallback,
                                model.peripheralSettings,model::peripherals,model.peripheralDetected,
                                model.sensors.hasTilt,model.sensors.hasGyro,model.sensors.hasLight,model::calibrate,model.sensors::manual,
                                model::openAppSettings,model.startManual,model.startMenu,model::consumeRequests) { slot,time ->
                                model.images.thumbnail(model.session.gameId,slot,time)
                            }
                        }
                        }
                    } else AppShell(model)
                }
            }
        }
    }
    override fun onStart() { super.onStart(); hid.start(); foreground=true; model.foreground(true) }
    override fun onStop() { hid.stop(); model.session.input.releaseAll(); foreground=false; model.foreground(false); super.onStop() }
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ArtworkCache.trim()
            model.trimRewind()
        }
    }
}
