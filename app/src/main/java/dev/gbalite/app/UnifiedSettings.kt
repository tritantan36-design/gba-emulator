package dev.gbalite.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.gbalite.core.*
import dev.gbalite.core.DisplayMode
import dev.gbalite.input.*
import dev.gbalite.player.TouchControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun UnifiedSettings(model: PlayerViewModel,onBack: ()->Unit) {
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var layout by rememberSaveable {mutableStateOf<Boolean?>(null)}
    var license by rememberSaveable {mutableStateOf<String?>(null)}
    fun back() {when {license!=null->license=null;layout!=null->layout=null;category!=null->category=null;else->onBack()}}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {TextButton(onClick=::back) {Text("返回")};Text(category ?: "设置",style=MaterialTheme.typography.headlineSmall)}
        when {
            license!=null -> {
                val context=LocalContext.current
                val text by produceState("正在读取许可…",license) {value=withContext(Dispatchers.IO) {context.assets.open("licenses/${license!!}").bufferedReader().use {it.readText()}}}
                LazyColumn {item {Text(text,style=MaterialTheme.typography.bodySmall)}}
            }
            layout!=null -> LayoutSettings(model,layout!!)
            category=="外设" -> dev.gbalite.player.PeripheralDialog(model.peripheralSettings,model::peripherals,model.peripheralDetected,
                model.sensors.hasTilt,model.sensors.hasGyro,model.sensors.hasLight,model.session.hapticStatus,{category=null},model::calibrate,
                if(model.playing) model::returnToManual else null)
            else -> LazyColumn(Modifier.testTag("settings-list"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(category==null) items(7) {i -> val name=listOf("显示","控制","音频","游戏运行","外设","存档","关于")[i]
                    TextButton(onClick={category=name},modifier=Modifier.fillMaxWidth()) {Text(name)}
                } else when(category) {
                    "显示" -> {
                        item {Text("显示模式")}
                        items(DisplayMode.entries.size) {i -> val mode=DisplayMode.entries[i]
                            TextButton(onClick={model.display(model.displaySettings.copy(displayMode=mode))}) {Text((if(model.displaySettings.displayMode==mode) "✓ " else "")+listOf("Original","Sharp","GBA Color","LCD")[i])}}
                        items(ScaleMode.entries.size) {i->val scale=ScaleMode.entries[i];TextButton(onClick={model.display(model.displaySettings.copy(scaleMode=scale))}) {Text((if(model.displaySettings.scaleMode==scale) "✓ " else "")+if(i==0) "Fit" else "Integer")}}
                        item {Text("背景颜色")}
                        items(BackgroundTone.entries.size) {i->val tone=BackgroundTone.entries[i];TextButton(onClick={model.display(model.displaySettings.copy(background=tone))},modifier=Modifier.testTag("background-${tone.name}")) {Text((if(model.displaySettings.background==tone) "✓ " else "")+if(i==0) "黑色" else "白色")}}
                    }
                    "控制" -> {
                        item {TextButton(onClick={layout=false}) {Text("竖屏布局")};TextButton(onClick={layout=true}) {Text("横屏布局")}}
                        item {TextButton(onClick={model.resetProfile(false)}) {Text("恢复默认竖屏布局")};TextButton(onClick={model.resetProfile(true)}) {Text("恢复默认横屏布局")}}
                        item {Text("手柄映射",style=MaterialTheme.typography.titleMedium);Text("Xbox A / PlayStation × → GBA A\nXbox X / PlayStation □ → GBA B\nL1 / R1 → L / R\nStart → Start，Select / Back → Select\n方向键 / 左摇杆 → 方向\n通过系统设置连接蓝牙或 USB HID 手柄。")}
                    }
                    "音频" -> item {Text("正常速度使用 Oboe / AAudio 输出。快进期间静音，恢复 1× 后恢复声音；后台暂停。当前没有独立音量开关，请使用系统音量。")}
                    "游戏运行" -> item {Text("快进支持 2× / 4× / 8×，8× 为尽力模式。游戏菜单中选择倍数后持续快进，关闭恢复 1×。倒带按住使用；两者的游戏时长均按现实时间计算。")}
                    "存档" -> item {Text("Quick Resume 与自动存档当前始终开启。退出或后台先保存正常存档，再写滚动自动存档。\n\n4 个手动存档位及 Quick Save / Load 在游戏菜单中管理。\n\n数据保存在应用私有空间；移出游戏库保留存档，卸载或清除应用数据会删除存档。")}
                    "关于" -> {
                        item {Text("GBA Lite 0.7.0\nmGBA 0.10.5 · MPL-2.0\nOboe 1.9.3 · Apache-2.0\n完全离线，无广告，无 Analytics，无网络权限。")}
                        items(4) {i ->val file=listOf("APP-LICENSE.txt","NOTICE.txt","mGBA-LICENSE.txt","Oboe-LICENSE.txt")[i]
                            TextButton(onClick={license=file}) {Text("开源许可 · "+listOf("GBA Lite","归属说明","mGBA","Oboe")[i])}}
                    }
                }
            }
        }
    }
}
@Composable private fun ColumnScope.LayoutSettings(model: PlayerViewModel,landscape: Boolean) {
    var profile by remember(landscape) {mutableStateOf(InputProfile.default(landscape))}
    var selected by remember {mutableStateOf("A")}
    var ready by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(landscape) {profile=withContext(Dispatchers.IO) {model.profiles.read(landscape)};ready=true}
    val router=remember {InputRouter {_,_->}}
    Text("拖动按键；当前：$selected")
    AndroidView(factory={TouchControls(it,router)},update={view->view.profile=profile;view.editing=true;view.onEdited={profile=it};view.onSelected={selected=it}},
        modifier=Modifier.fillMaxWidth().weight(1f))
    Text("按键大小")
    Slider(profile.controls.first {it.key==selected}.size,{v->profile=profile.copy(controls=profile.controls.map {if(it.key==selected) it.copy(size=v) else it})},valueRange=InputProfile.MIN_SIZE..InputProfile.MAX_SIZE)
    Text("透明度");Slider(profile.opacity,{profile=profile.copy(opacity=it)},valueRange=.15f..1f)
    Button(enabled=ready,onClick={model.saveProfile(profile)}) {Text("保存布局")}
}
