package dev.gbalite.player
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.*
import dev.gbalite.input.*
import dev.gbalite.core.*
import dev.gbalite.core.DisplayMode
import dev.gbalite.renderer.OriginalSurface
import dev.gbalite.session.EmulatorSession
import java.io.File

@Composable fun Player(session: EmulatorSession, foreground: Boolean, onExit: () -> Unit,
    errorMessage: String?=null, profiles: InputProfileStore?=null, screenshot: ()->Unit={},
    displaySettings: DisplaySettings=DisplaySettings(), onDisplay: (DisplaySettings)->Unit={}, onDisplayFallback: (String)->Unit={},
    peripheralSettings: PeripheralSettings=PeripheralSettings(), onPeripherals: (PeripheralSettings)->Unit={}, detected: Int=0,
    hasTilt: Boolean=false,hasGyro: Boolean=false,hasLight: Boolean=false,onCalibrate: ()->Unit={},
    onManual: (Float,Float,Float)->Unit={_,_,_->},
    onSettingsRequest: (() -> Unit)?=null,manualOnStart: Boolean=false,menuOnStart: Boolean=false,onRequestsConsumed: ()->Unit={},
    thumbnail: (Int,String?)->File?={_,_->null}) {
    val landscape=LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val surface=remember { mutableStateOf<OriginalSurface?>(null) }
    val scope=rememberCoroutineScope { Dispatchers.Main.immediate }
    var menu by remember { mutableStateOf(session.pausedByUser.value || menuOnStart) }
    var editor by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    var peripheralsMenu by remember { mutableStateOf(false) }
    var manualPanel by remember { mutableStateOf(manualOnStart) }
    LaunchedEffect(Unit) {
        if(manualOnStart) {menu=false;session.setPaused(false)} else if(menuOnStart) session.setPaused(true)
        onRequestsConsumed()
    }
    var dragging by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf<String?>(null) }
    var multiplier by remember { mutableIntStateOf(2) }
    var toggled by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var profile by remember(landscape) { mutableStateOf(InputProfile.default(landscape)) }
    var selected by remember { mutableStateOf("A") }
    var scaleAll by remember { mutableStateOf(false) }
    var scaleBase by remember { mutableStateOf(InputProfile.default(landscape)) }
    var groupScale by remember { mutableFloatStateOf(1f) }
    var notice by remember { mutableStateOf<String?>(null) }
    val slots by session.slots.collectAsState(context=Dispatchers.Main.immediate)
    LaunchedEffect(landscape) { withContext(Dispatchers.Main.immediate) {
        profile=withContext(Dispatchers.IO) { profiles?.read(landscape) ?: InputProfile.default(landscape) }
    } }
    fun operation(load: Boolean,slot: Int?=null) {
        if(saving) return
        saving=true; session.input.releaseAll()
        scope.launch { try { if(load) session.loadState(slot) else session.saveState(slot) } finally { saving=false } }
    }
    fun openMenu() { scope.launch { session.input.releaseAll(); session.setPaused(true); hold=null; menu=true } }
    fun resume() { scope.launch {
        session.setPaused(false)
        if(toggled) toggled=session.setSpeed(multiplier)
        menu=false
    } }
    BackHandler { if(editor) { editor=false; menu=true } else if(menu) resume() else openMenu() }
    DisposableEffect(session) { onDispose { session.input.releaseAll(); surface.value?.onPause() } }
    LaunchedEffect(foreground,surface.value) {
        if(foreground) surface.value?.onResume()
        else { session.input.releaseAll(); toggled=false; hold=null; manualPanel=false;onManual(0f,0f,0f);surface.value?.onPause() }
    }
    val playerBounds=if(landscape) Modifier.fillMaxSize()
        else Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=8.dp,vertical=4.dp)
    val background=if(displaySettings.background==BackgroundTone.WHITE) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    Box(Modifier.fillMaxSize().background(background).then(playerBounds)) {
        if(landscape) {
            AndroidView(factory={OriginalSurface(it,session.frames,session.input::releaseAll).also { s->surface.value=s }},
                update={it.configure(displaySettings,onDisplayFallback)},
                modifier=Modifier.fillMaxSize().testTag("gba-screen"))
            AndroidView(factory={TouchControls(it,session.input)},update={it.profile=profile;it.editing=editor;it.onEdited={p->profile=p};it.onSelected={k->selected=k}},
                modifier=Modifier.fillMaxSize().safeDrawingPadding().testTag("touch-controls"))
        } else Column(Modifier.fillMaxSize()) {
            AndroidView(factory={OriginalSurface(it,session.frames,session.input::releaseAll).also { s->surface.value=s }},
                update={it.configure(displaySettings,onDisplayFallback)},
                modifier=Modifier.fillMaxWidth().aspectRatio(1.5f).testTag("gba-screen"))
            AndroidView(factory={TouchControls(it,session.input)},update={it.profile=profile;it.editing=editor;it.onEdited={p->profile=p};it.onSelected={k->selected=k}},
                modifier=Modifier.fillMaxWidth().weight(1f).testTag("touch-controls"))
        }
        if(!editor) TextButton(onClick=::openMenu,modifier=Modifier.align(Alignment.TopCenter).safeDrawingPadding()) { Text(if(toggled) "菜单 · ${multiplier}×" else "菜单") }
        if(hold!=null) Surface(modifier=Modifier.align(Alignment.BottomCenter),tonalElevation=4.dp) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.padding(16.dp).pointerInput(hold,multiplier) {
                    detectTapGestures(onPress={
                        try {
                            if(hold=="ff") session.setSpeed(multiplier) else session.setRewinding(true)
                            tryAwaitRelease()
                        } finally { withContext(NonCancellable) {
                            if(hold=="ff") session.setSpeed(1) else session.setRewinding(false)
                        } }
                    })
                }) { Text(if(hold=="ff") "按住快进 ${multiplier}×" else "按住倒带") }
                TextButton(onClick=::openMenu) { Text("完成") }
            }
        }
        if(editor && dragging) Surface(Modifier.align(Alignment.TopCenter),tonalElevation=4.dp) {
            TextButton(onClick={dragging=false}) { Text("完成拖动") }
        }
        if(editor && !dragging) Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(),tonalElevation=4.dp) {
            Column(Modifier.padding(8.dp)) {
                Text("拖动按键；当前：$selected · ${if(landscape) "横屏" else "竖屏"}")
                Row {
                    TextButton(onClick={scaleAll=false}) { Text(if(!scaleAll) "✓ 单个" else "单个") }
                    TextButton(onClick={scaleBase=profile;groupScale=1f;scaleAll=true}) { Text(if(scaleAll) "✓ 整体" else "整体") }
                }
                if(scaleAll) {
                    val minimum=InputProfile.MIN_SIZE/scaleBase.controls.minOf { it.size }
                    val maximum=InputProfile.MAX_SIZE/scaleBase.controls.maxOf { it.size }
                    Text("整体大小：${kotlin.math.round(groupScale*100).toInt()}%（保持各按键大小比例和位置）")
                    if(maximum-minimum>.0001f) Slider(value=groupScale,onValueChange={v->
                        groupScale=v
                        val sizes=scaleBase.scaledSizes(v).controls.associate { it.key to it.size }
                        profile=profile.copy(controls=profile.controls.map { it.copy(size=sizes.getValue(it.key)) })
                    },valueRange=minimum..maximum)
                    else Text("按键已到尺寸边界，可用单个模式调整。")
                } else {
                    Text("按键大小：${kotlin.math.round(profile.controls.first { it.key==selected }.size*100).toInt()}%（受可用区域边界限制）")
                    Slider(value=profile.controls.first { it.key==selected }.size,onValueChange={v->
                        profile=profile.copy(controls=profile.controls.map { if(it.key==selected) it.copy(size=v) else it })
                    },valueRange=InputProfile.MIN_SIZE..InputProfile.MAX_SIZE)
                }
                Text("透明度")
                Slider(value=profile.opacity,onValueChange={profile=profile.copy(opacity=it)},valueRange=.15f..1f)
                Row {
                    TextButton(onClick={dragging=true}) { Text("拖动布局") }
                    TextButton(onClick={profile=InputProfile.default(landscape);scaleBase=profile;groupScale=1f}) { Text("恢复默认") }
                    TextButton(onClick={scope.launch {
                        try { withContext(Dispatchers.IO) { profiles?.write(profile) }; editor=false; menu=true }
                        catch(_: Exception) { notice="布局保存失败，请重试。" }
                    }}) { Text("保存布局") }
                    TextButton(onClick={scope.launch {
                        profile=withContext(Dispatchers.IO) { profiles?.read(landscape) ?: InputProfile.default(landscape) }
                        editor=false;menu=true
                    }}) { Text("取消") }
                }
                notice?.let { Text(it) }
            }
        }
    }
    if(menu && !displayMenu && !settingsMenu && !peripheralsMenu) AlertDialog(onDismissRequest=::resume,confirmButton={TextButton(onClick=::resume) { Text("继续") }},
        title={Text("暂停")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            errorMessage?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            TextButton(onClick={if(onSettingsRequest!=null) onSettingsRequest() else settingsMenu=true}) { Text("设置") }
            TextButton(onClick={displayMenu=true}) { Text("显示设置") }
            Text("手柄映射：方向键／左摇杆 → 方向；下方键（Xbox A／PS ×）→ GBA A；左侧键（Xbox X／PS □）→ GBA B；L1/LB → L；R1/RB → R；Start/Menu → START；Select/View/Back → SELECT。以系统上报的键位为准。",
                style=MaterialTheme.typography.bodySmall)
            Row { TextButton(enabled=!saving,onClick={operation(false)}) { Text("Quick Save") }
                TextButton(enabled=!saving,onClick={operation(true)}) { Text("Quick Load") } }
            for(slot in 1..4) {
                val meta=slots.firstOrNull { it.kind=="manual" && it.slot==slot }
                val image by produceState<android.graphics.Bitmap?>(null,meta?.createdAt,slot,menu) {
                    withContext(Dispatchers.Main.immediate) {
                        value=withContext(Dispatchers.IO) { thumbnail(slot,meta?.createdAt)?.let { BitmapFactory.decodeFile(it.path) } }
                    }
                }
                image?.let { Image(it.asImageBitmap(),"Slot $slot 缩略图",Modifier.width(120.dp).aspectRatio(1.5f)) }
                Text("Slot $slot · "+(meta?.createdAt?.let { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) } ?: "空"))
                Row { TextButton(enabled=!saving,onClick={operation(false,slot)}) { Text("保存 $slot") }
                    TextButton(enabled=!saving,onClick={operation(true,slot)}) { Text("读取 $slot") } }
            }
            Text("点选倍数立即持续快进；从菜单关闭恢复 1×。")
            Row { listOf(2,4,8).forEach { n-> TextButton(onClick={scope.launch {
                session.setPaused(false)
                multiplier=n; toggled=session.setSpeed(n); menu=false
            }}) { Text("$n×") } } }
            TextButton(onClick={scope.launch {
                val stop=toggled; session.setPaused(false)
                toggled=if(stop) { session.setSpeed(1); false } else session.setSpeed(multiplier)
                menu=false
            }}) { Text(if(toggled) "关闭快进（恢复1×）" else "快进（切换）") }
            TextButton(onClick={scope.launch { session.setPaused(false);toggled=false;menu=false;hold="ff" }}) { Text("快进（按住）") }
            TextButton(onClick={scope.launch { session.setPaused(false);toggled=false;menu=false;hold="rewind" }}) { Text("倒带（按住）") }
            TextButton(onClick=screenshot) { Text("截图") }
            TextButton(onClick={menu=false;editor=true;scaleAll=false;session.input.releaseAll()}) { Text("调整按键") }
            TextButton(enabled=!saving,onClick={session.input.releaseAll(); onExit()}) { Text("退出游戏") }
        }})
    if(settingsMenu) AlertDialog(onDismissRequest={settingsMenu=false},
        confirmButton={TextButton(onClick={settingsMenu=false}) { Text("返回菜单") }},
        title={Text("设置")},text={Column {
            TextButton(onClick={settingsMenu=false;peripheralsMenu=true}) { Text("外设") }
            Text("背景颜色")
            Text("调整游戏周围及按键区域的背景，游戏画面保持原样。",style=MaterialTheme.typography.bodySmall)
            BackgroundTone.entries.forEach { tone ->
                TextButton(onClick={onDisplay(displaySettings.copy(background=tone))},modifier=Modifier.testTag("background-${tone.name}")) {
                    Text((if(displaySettings.background==tone) "✓ " else "")+if(tone==BackgroundTone.BLACK) "黑色" else "白色")
                }
            }
        }})
    if(peripheralsMenu) PeripheralDialog(peripheralSettings,onPeripherals,detected,hasTilt,hasGyro,hasLight,
        session.hapticStatus,{peripheralsMenu=false},onCalibrate,{
            scope.launch { peripheralsMenu=false;menu=false;session.setPaused(false);manualPanel=true }
        })
    if(manualPanel) ManualPeripheralPanel(onManual,{
        onManual(0f,0f,0f);manualPanel=false;openMenu()
    })
    if(displayMenu) AlertDialog(onDismissRequest={displayMenu=false},
        confirmButton={TextButton(onClick={displayMenu=false;resume()}) { Text("返回游戏") }},
        title={Text("显示设置")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            val descriptions=listOf("原始像素显示","适合高分辨率屏幕的清晰显示","模拟 GBA 屏幕综合色彩","轻度模拟掌机 LCD 像素观感")
            DisplayMode.entries.forEachIndexed { index,mode ->
                val name=when(mode) { DisplayMode.ORIGINAL->"Original"; DisplayMode.SHARP->"Sharp";DisplayMode.GBA_COLOR->"GBA Color";DisplayMode.LCD->"LCD" }
                TextButton(onClick={onDisplay(displaySettings.copy(displayMode=mode))}) { Text((if(displaySettings.displayMode==mode) "✓ " else "")+name) }
                Text(descriptions[index],style=MaterialTheme.typography.bodySmall)
            }
            Text("缩放")
            ScaleMode.entries.forEach { mode -> TextButton(onClick={onDisplay(displaySettings.copy(scaleMode=mode))}) {
                Text((if(displaySettings.scaleMode==mode) "✓ " else "")+if(mode==ScaleMode.FIT) "Fit" else "Integer")
            } }
        }})
}
