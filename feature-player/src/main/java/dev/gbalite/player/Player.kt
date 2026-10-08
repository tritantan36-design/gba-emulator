package dev.gbalite.player
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import dev.gbalite.player.ui.*
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
    var pausePage by remember {mutableStateOf("root")}
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
        scope.launch { try { val ok=if(load) session.loadState(slot) else session.saveState(slot);notice=if(ok) {if(load) "存档已读取" else "存档已保存"} else "操作未完成，请查看提示。" } finally { saving=false } }
    }
    fun openMenu() { scope.launch { session.input.releaseAll(); session.setPaused(true); hold=null; pausePage="root"; menu=true } }
    fun resume() { scope.launch {
        session.setPaused(false)
        if(toggled) toggled=session.setSpeed(multiplier)
        menu=false
    } }
    BackHandler { if(editor) { editor=false; menu=true } else if(menu && pausePage!="root") pausePage="root" else if(menu) resume() else openMenu() }
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
            AndroidView(factory={TouchControls(it,session.input)},update={it.profile=profile;it.editing=editor;it.onEdited={p->profile=p};it.onSelected={k->selected=k};it.selectedKey=selected},
                modifier=Modifier.fillMaxSize().safeDrawingPadding().testTag("touch-controls"))
        } else Column(Modifier.fillMaxSize()) {
            AndroidView(factory={OriginalSurface(it,session.frames,session.input::releaseAll).also { s->surface.value=s }},
                update={it.configure(displaySettings,onDisplayFallback)},
                modifier=Modifier.fillMaxWidth().aspectRatio(1.5f).testTag("gba-screen"))
            AndroidView(factory={TouchControls(it,session.input)},update={it.profile=profile;it.editing=editor;it.onEdited={p->profile=p};it.onSelected={k->selected=k};it.selectedKey=selected},
                modifier=Modifier.fillMaxWidth().weight(1f).testTag("touch-controls"))
        }
        if(!editor) Surface(modifier=Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top=if(landscape) 8.dp else (LocalConfiguration.current.screenWidthDp.dp-16.dp)/1.5f+24.dp),shape=UiShapes.Capsule,color=UiColors.Overlay) {
            TextButton(onClick=::openMenu,modifier=Modifier.heightIn(min=48.dp).testTag("player-menu")) {
                GbaIcon("menu",Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text(if(toggled) "菜单 · ${multiplier}×" else "菜单")
            }
        }
        if(hold!=null) Surface(modifier=Modifier.align(Alignment.BottomCenter),tonalElevation=4.dp) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.heightIn(min=48.dp).padding(16.dp).pointerInput(hold,multiplier) {
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
    if(menu && !editor && !settingsMenu && !peripheralsMenu) PausePanel(
        title=when(pausePage) {"save"->"保存存档";"load"->"读取存档";"speed"->"快进 / 倒带";"controls"->"显示与控制";"display"->"显示设置";else->"暂停"},
        tag="pause-$pausePage",back=if(pausePage=="root") null else ({pausePage=if(pausePage=="display") "controls" else "root"}),
        dismiss={if(pausePage=="root") resume() else pausePage="root"}) {
        errorMessage?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        notice?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        when(pausePage) {
            "root" -> {
                val actions=listOf(Triple("继续","play","回到游戏"),Triple("保存存档","save","Quick · 4 Slots"),
                    Triple("读取存档","load","恢复游戏进度"),Triple("显示与控制","controls","画面 · 按键布局"),
                    Triple("快进 / 倒带","speed",if(toggled) "持续 ${multiplier}×" else "当前 1×"),Triple("退出游戏","exit","保存后返回首页"))
                actions.chunked(2).forEach {pair -> Row(horizontalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {
                    pair.forEach { (title,icon,summary) -> QuickAction(title,icon,summary,Modifier.weight(1f),enabled=!saving,primary=title=="继续",danger=title=="退出游戏") {
                        when(title) {"继续"->resume();"保存存档"->pausePage="save";"读取存档"->pausePage="load";"显示与控制"->pausePage="controls";"快进 / 倒带"->pausePage="speed";else->{session.input.releaseAll();onExit()}}
                    }
                }}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=screenshot) {Text("截图")}
                    TextButton(onClick={if(onSettingsRequest!=null) onSettingsRequest() else settingsMenu=true}) {Text("设置")}
                }
            }
            "save","load" -> {
                val load=pausePage=="load"
                Text(if(load) "读取会恢复即时存档中的游戏进度。" else "即时存档与游戏内正常存档分别保存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled=!saving,onClick={operation(load)},modifier=Modifier.fillMaxWidth(),shape=UiShapes.Small) {Text(if(load) "Quick Load" else "Quick Save")}
                for(slot in 1..4) {
                    val meta=slots.firstOrNull {it.kind=="manual" && it.slot==slot}
                    val image by produceState<android.graphics.Bitmap?>(null,meta?.createdAt,slot) {
                        value=withContext(Dispatchers.IO) {thumbnail(slot,meta?.createdAt)?.takeIf {it.length() in 1..262144}?.let {BitmapFactory.decodeFile(it.path)}}
                    }
                    Surface(shape=UiShapes.Small,color=MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(UiSpacing.md),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.md)) {
                            if(image!=null) Image(image!!.asImageBitmap(),"Slot $slot 缩略图",Modifier.width(72.dp).aspectRatio(1.5f))
                            else Box(Modifier.size(72.dp,48.dp).background(UiColors.ControlEdge),contentAlignment=Alignment.Center) {GbaIcon("save",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            Column(Modifier.weight(1f)) {Text("Slot $slot",style=MaterialTheme.typography.titleMedium)
                                Text(meta?.createdAt?.let {runCatching {java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))}.getOrDefault(it.take(16))} ?: "暂无存档",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            TextButton(enabled=!saving && (!load || meta!=null),onClick={operation(load,slot)}) {Text(if(load) "读取 $slot" else "保存 $slot")}
                        }
                    }
                }
            }
            "speed" -> {
                Text("持续快进",style=MaterialTheme.typography.titleMedium)
                Text("选择倍数后回到游戏持续快进。8× 为尽力模式。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {listOf(2,4,8).forEach {n -> OutlinedButton(onClick={scope.launch {
                    session.setPaused(false);multiplier=n;toggled=session.setSpeed(n);menu=false
                }},modifier=Modifier.weight(1f)) {Text("$n×")}}}
                TextButton(onClick={scope.launch {val stop=toggled;session.setPaused(false);toggled=if(stop) {session.setSpeed(1);false} else session.setSpeed(multiplier);menu=false}}) {Text(if(toggled) "关闭快进（恢复1×）" else "快进（切换）")}
                HorizontalDivider()
                Text("按住操作",style=MaterialTheme.typography.titleMedium)
                Text("松手立即恢复正常运行。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick={scope.launch {session.setPaused(false);toggled=false;menu=false;hold="ff"}},modifier=Modifier.fillMaxWidth()) {Text("快进（按住）")}
                OutlinedButton(onClick={scope.launch {session.setPaused(false);toggled=false;menu=false;hold="rewind"}},modifier=Modifier.fillMaxWidth()) {Text("倒带（按住）")}
            }
            "controls" -> {
                GbaSettingRow("显示设置","四种模式 · Fit / Integer · 背景","display") {pausePage="display"}
                GbaSettingRow("调整按键","拖动 · 大小 · 透明度","controls") {menu=false;editor=true;scaleAll=false;session.input.releaseAll()}
                Text("仅显示 GBA 原生按键 A / B / L / R / START / SELECT。手柄映射说明在设置 → 控制。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "display" -> {
                Text("显示模式",style=MaterialTheme.typography.titleMedium)
                val names=listOf("Original","Sharp","GBA Color","LCD")
                val summaries=listOf("原始像素","清晰显示","GBA 屏幕色彩","轻度 LCD 像素观感")
                DisplayMode.entries.forEachIndexed {index,mode -> GbaSettingRow(names[index],summaries[index],"display",selected=displaySettings.displayMode==mode) {onDisplay(displaySettings.copy(displayMode=mode))}}
                Text("缩放",style=MaterialTheme.typography.titleMedium)
                Row {ScaleMode.entries.forEach {mode -> FilterChip(selected=displaySettings.scaleMode==mode,onClick={onDisplay(displaySettings.copy(scaleMode=mode))},label={Text(if(mode==ScaleMode.FIT) "Fit" else "Integer")},modifier=Modifier.padding(end=8.dp))}}
                Text("背景颜色",style=MaterialTheme.typography.titleMedium)
                Row {BackgroundTone.entries.forEach {tone -> FilterChip(selected=displaySettings.background==tone,onClick={onDisplay(displaySettings.copy(background=tone))},label={Text(if(tone==BackgroundTone.BLACK) "黑色" else "白色")},modifier=Modifier.padding(end=8.dp).testTag("background-${tone.name}"))}}
                TextButton(onClick={pausePage="root";resume()}) {Text("返回游戏")}
            }
        }
    }
    if(settingsMenu) PausePanel("设置","pause-settings",{settingsMenu=false},{settingsMenu=false}) {
        GbaSettingRow("外设","RTC · Tilt · Gyro · Solar") {settingsMenu=false;peripheralsMenu=true}
    }
    if(peripheralsMenu) PeripheralDialog(peripheralSettings,onPeripherals,detected,hasTilt,hasGyro,hasLight,
        session.hapticStatus,{peripheralsMenu=false},onCalibrate,{
            scope.launch {peripheralsMenu=false;menu=false;session.setPaused(false);manualPanel=true}
        })
    if(manualPanel) ManualPeripheralPanel(onManual,{onManual(0f,0f,0f);manualPanel=false;openMenu()})
}

@Composable private fun QuickAction(title: String,icon: String,summary: String,modifier: Modifier=Modifier,enabled: Boolean=true,primary: Boolean=false,danger: Boolean=false,click: ()->Unit) {
    Surface(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=100.dp),shape=UiShapes.Card,
        color=if(primary) MaterialTheme.colorScheme.primary.copy(alpha=.17f) else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {
            GbaIcon(icon,color=if(danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun PausePanel(title: String,tag: String,back: (() -> Unit)?,dismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view) {(view.parent as? DialogWindowProvider)?.window?.setDimAmount(.50f);onDispose {}}
        GbaGameTheme {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(UiSpacing.lg),contentAlignment=Alignment.Center) {
                Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight).testTag(tag),shape=UiShapes.Modal,color=UiColors.Overlay,shadowElevation=UiElevation.Overlay) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            if(back!=null) IconButton(onClick=back) {GbaIcon("back",description="返回菜单")}
                            Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        }
                        content()
                    }
                }
            }
        }
    }
}
