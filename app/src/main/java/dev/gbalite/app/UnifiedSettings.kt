package dev.gbalite.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.gbalite.core.*
import dev.gbalite.core.DisplayMode
import dev.gbalite.input.*
import dev.gbalite.player.TouchControls
import dev.gbalite.player.PeripheralContent
import dev.gbalite.player.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun UnifiedSettings(model: PlayerViewModel,onBack: ()->Unit,rootBack: Boolean=false,onDepthChanged: (Boolean)->Unit={}) {
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var layout by rememberSaveable {mutableStateOf<Boolean?>(null)}
    var license by rememberSaveable {mutableStateOf<String?>(null)}
    fun back() {when {license!=null->license=null;layout!=null->layout=null;category!=null->category=null;else->onBack()}}
    val nested=category!=null || layout!=null || license!=null
    SideEffect {onDepthChanged(nested)}
    DisposableEffect(Unit) {onDispose {onDepthChanged(false)}}
    BackHandler {back()}
    Column(Modifier.fillMaxSize()) {
        val title=when {license!=null->"开源许可";layout!=null->if(layout!!) "横屏布局" else "竖屏布局";else->category ?: "设置"}
        GbaTopBar(title,if(nested || rootBack) ::back else null)
        when {
            license!=null -> {
                val context=LocalContext.current
                val text by produceState("正在读取许可…",license) {value=withContext(Dispatchers.IO) {context.assets.open("licenses/${license!!}").bufferedReader().use {it.readText()}}}
                LazyColumn(contentPadding=PaddingValues(UiSpacing.lg)) {item {Text(text,style=MaterialTheme.typography.bodySmall)}}
            }
            layout!=null -> LayoutSettings(model,layout!!)
            else -> LazyColumn(Modifier.testTag("settings-list"),contentPadding=PaddingValues(bottom=UiSpacing.xl)) {
                if(category==null) {
                    val d=model.displaySettings
                    val summaries=listOf(displayName(d.displayMode)+" · "+(if(d.scaleMode==ScaleMode.FIT) "Fit" else "Integer")+" · "+(if(d.background==BackgroundTone.BLACK) "黑色背景" else "白色背景"),
                        "竖屏布局 · 横屏布局 · 手柄说明","低延迟音频 · 快进静音策略","快进 · 倒带 · Quick Resume","RTC · Tilt · Gyro · Solar","Quick · 4 Slots · Autosave","版本 · 开源许可 · 完全离线")
                    val icons=listOf("display","controls","audio","speed","peripherals","save","about")
                    items(7) {i ->val name=listOf("显示","控制","音频","游戏运行","外设","存档","关于")[i]
                        GbaSettingRow(name,summaries[i],icons[i],Modifier.testTag("settings-$name")) {category=name}
                        HorizontalDivider(Modifier.padding(start=56.dp,end=UiSpacing.lg),color=UiColors.Line)
                    }
                } else when(category) {
                    "显示" -> {
                        item {GbaSectionHeader("显示模式")}
                        items(DisplayMode.entries.size) {i ->val mode=DisplayMode.entries[i]
                            GbaSettingRow(displayName(mode),listOf("原始像素","清晰显示","GBA 屏幕色彩","轻度 LCD 像素观感")[i],"display",selected=model.displaySettings.displayMode==mode) {model.display(model.displaySettings.copy(displayMode=mode))}}
                        item {GbaSectionHeader("缩放")}
                        items(ScaleMode.entries.size) {i->val scale=ScaleMode.entries[i]
                            GbaSettingRow(if(i==0) "Fit" else "Integer",if(i==0) "保持 3:2 比例，适应可用区域" else "整数倍像素缩放，保持画面完整","display",selected=model.displaySettings.scaleMode==scale) {model.display(model.displaySettings.copy(scaleMode=scale))}}
                        item {GbaSectionHeader("背景颜色")}
                        items(BackgroundTone.entries.size) {i->val tone=BackgroundTone.entries[i]
                            GbaSettingRow(if(i==0) "黑色" else "白色","只改变画面周围的背景","display",Modifier.testTag("background-${tone.name}"),selected=model.displaySettings.background==tone) {model.display(model.displaySettings.copy(background=tone))}}
                    }
                    "控制" -> {
                        item {GbaSettingRow("竖屏布局","拖动 · 大小 · 透明度","controls") {layout=false}}
                        item {GbaSettingRow("横屏布局","独立保存横屏位置","controls") {layout=true}}
                        item {GbaSectionHeader("恢复默认")}
                        item {GbaSettingRow("恢复默认竖屏布局","仅重置竖屏按键","controls") {model.resetProfile(false)}}
                        item {GbaSettingRow("恢复默认横屏布局","仅重置横屏按键","controls") {model.resetProfile(true)}}
                        item {GbaSectionHeader("手柄映射")}
                        item {InfoText("Xbox A / PlayStation × → GBA A\nXbox X / PlayStation □ → GBA B\nL1 / R1 → L / R\nStart → Start，Select / Back → Select\n方向键 / 左摇杆 → 方向\n通过系统设置连接蓝牙或 USB HID 手柄。GBA 原生只有 A/B，不显示无映射的 X/Y。")}
                    }
                    "音频" -> {
                        item {GbaSectionHeader("低延迟音频")}
                        item {InfoText("正常速度自动输出声音，使用系统音量调节。后台暂停时停止出声。")}
                        item {GbaSectionHeader("快进静音策略")}
                        item {InfoText("快进期间静音；恢复 1× 后恢复声音。当前没有独立音量开关。")}
                    }
                    "游戏运行" -> {
                        item {GbaSectionHeader("快进与倒带")}
                        item {InfoText("在暂停菜单中选择 2× / 4× / 8× 持续快进，8× 为尽力模式。按住操作会在松手后恢复正常运行。游戏时长按现实时间计算。")}
                        item {GbaSectionHeader("Quick Resume")}
                        item {InfoText("自动恢复始终开启；从首页继续上次游戏。进程中断后恢复最近一次成功保存的进度。")}
                    }
                    "外设" -> item {Column(Modifier.padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {
                        PeripheralContent(model.peripheralSettings,model::peripherals,model.peripheralDetected,
                            model.sensors.hasTilt,model.sensors.hasGyro,model.sensors.hasLight,model.session.hapticStatus,model::calibrate,
                            if(model.playing) model::returnToManual else null)
                    }}
                    "存档" -> {
                        item {GbaSectionHeader("即时存档")}
                        item {InfoText("Quick Save / Load 与 4 个手动存档位在暂停菜单中管理；正常游戏存档与即时存档分别保存。")}
                        item {GbaSectionHeader("自动保存")}
                        item {InfoText("Quick Resume 与自动存档始终开启。退出或后台先保存正常存档，再写滚动自动存档。")}
                        item {GbaSectionHeader("数据保留")}
                        item {InfoText("数据保存在应用私有空间。移出游戏库会保留存档与截图；卸载或清除应用数据会删除它们。")}
                    }
                    "关于" -> {
                        item {GbaSectionHeader("GBA Lite 0.7.0")}
                        item {InfoText("专注 GBA，完全离线。\n无广告 · 无 Analytics · 无网络权限\nmGBA 0.10.5 · Oboe 1.9.3")}
                        item {GbaSectionHeader("开源许可")}
                        items(4) {i ->val file=listOf("APP-LICENSE.txt","NOTICE.txt","mGBA-LICENSE.txt","Oboe-LICENSE.txt")[i]
                            GbaSettingRow("开源许可 · "+listOf("GBA Lite","归属说明","mGBA","Oboe")[i],icon="about") {license=file}}
                    }
                }
            }
        }
    }
}
internal fun displayName(mode: DisplayMode)=when(mode) {DisplayMode.ORIGINAL->"Original";DisplayMode.SHARP->"Sharp";DisplayMode.GBA_COLOR->"GBA Color";DisplayMode.LCD->"LCD"}
@Composable private fun InfoText(text: String) {Text(text,Modifier.fillMaxWidth().padding(horizontal=UiSpacing.lg,vertical=UiSpacing.sm),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun LayoutSettings(model: PlayerViewModel,landscape: Boolean) {
    var profile by remember(landscape) {mutableStateOf(InputProfile.default(landscape))}
    var selected by remember {mutableStateOf("A")}
    var ready by remember {mutableStateOf(false)}
    var all by remember {mutableStateOf(false)}
    var scaleBase by remember {mutableStateOf(InputProfile.default(landscape))}
    var factor by remember {mutableFloatStateOf(1f)}
    LaunchedEffect(landscape) {profile=withContext(Dispatchers.IO) {model.profiles.read(landscape)};scaleBase=profile;ready=true}
    val router=remember {InputRouter {_,_->}}
    @Composable fun Preview(modifier: Modifier) {
        Surface(modifier,shape=UiShapes.Card,color=UiColors.ControlEdge) {
            AndroidView(factory={TouchControls(it,router)},update={view->view.profile=profile;view.editing=true;view.selectedKey=selected;view.onEdited={profile=it};view.onSelected={selected=it}},modifier=Modifier.fillMaxSize().testTag("layout-preview"))
        }
    }
    @Composable fun Tools(modifier: Modifier) {
        Column(modifier.padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.xs)) {
            Text("拖动按键 · 当前 $selected",style=MaterialTheme.typography.titleMedium)
            Row {FilterChip(selected=!all,onClick={all=false},label={Text("单个")});Spacer(Modifier.width(8.dp));FilterChip(selected=all,onClick={scaleBase=profile;factor=1f;all=true},label={Text("整体")})}
            Text(if(all) "整体大小：${(factor*100).toInt()}%" else "按键大小：${(profile.controls.first {it.key==selected}.size*100).toInt()}%",style=MaterialTheme.typography.bodySmall)
            if(all) {
                val lo=InputProfile.MIN_SIZE/scaleBase.controls.minOf {it.size};val hi=InputProfile.MAX_SIZE/scaleBase.controls.maxOf {it.size}
                if(hi-lo>.0001f) Slider(factor,{factor=it;val sizes=scaleBase.scaledSizes(it).controls.associate {c->c.key to c.size};profile=profile.copy(controls=profile.controls.map {c->c.copy(size=sizes.getValue(c.key))})},valueRange=lo..hi)
            } else Slider(profile.controls.first {it.key==selected}.size,{v->profile=profile.copy(controls=profile.controls.map {if(it.key==selected) it.copy(size=v) else it})},valueRange=InputProfile.MIN_SIZE..InputProfile.MAX_SIZE)
            Text("透明度：${(profile.opacity*100).toInt()}%",style=MaterialTheme.typography.bodySmall)
            Slider(profile.opacity,{profile=profile.copy(opacity=it)},valueRange=.15f..1f)
            Row(horizontalArrangement=Arrangement.spacedBy(UiSpacing.sm)) {
                OutlinedButton(onClick={profile=InputProfile.default(landscape);scaleBase=profile;factor=1f}) {Text("恢复默认")}
                Button(enabled=ready,onClick={model.saveProfile(profile)}) {Text("保存布局")}
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if(maxWidth>maxHeight) Row(Modifier.fillMaxSize()) {Preview(Modifier.weight(1f).fillMaxHeight().padding(UiSpacing.sm));Tools(Modifier.width(300.dp))}
        else Column(Modifier.fillMaxSize()) {Preview(Modifier.weight(1f).fillMaxWidth().padding(horizontal=UiSpacing.lg));Tools(Modifier.fillMaxWidth())}
    }
}
