package dev.gbalite.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.gbalite.player.ui.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.gbalite.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ArtworkCache {
    val images=object: android.util.LruCache<String,android.graphics.Bitmap>(2*1024*1024) {
        override fun sizeOf(key: String,value: android.graphics.Bitmap)=value.allocationByteCount
    }
    @Synchronized fun trim() { images.evictAll() }
    @Synchronized fun load(path: String): android.graphics.Bitmap? {
        val file=java.io.File(path);if(!file.isFile || file.length() !in 1..262144) return null
        val key="$path:${file.lastModified()}:${file.length()}"
        images.get(key)?.let {return it}
        val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeFile(path,options)
        if(options.outWidth !in 1..1024 || options.outHeight !in 1..1024) return null
        options.inJustDecodeBounds=false;options.inSampleSize=maxOf(1,options.outWidth/160)
        return android.graphics.BitmapFactory.decodeFile(path,options)?.also {images.put(key,it)}
    }
}
@Composable private fun Artwork(path: String?,width: androidx.compose.ui.unit.Dp=80.dp) {
    val image by produceState<android.graphics.Bitmap?>(null,path) {
        value=if(path==null) null else withContext(Dispatchers.IO) {runCatching {ArtworkCache.load(path)}.getOrNull()}
    }
    if(image!=null) Image(image!!.asImageBitmap(),"游戏缩略图",Modifier.width(width).aspectRatio(1.5f).clip(UiShapes.Small))
    else Canvas(Modifier.width(width).aspectRatio(1.5f).clip(UiShapes.Small)) {
        drawRoundRect(UiColors.Accent.copy(alpha=.22f),topLeft=Offset(0f,size.height*.1f),size=Size(size.width,size.height*.8f))
        drawRect(UiColors.Accent.copy(alpha=.40f),topLeft=Offset(size.width*.18f,size.height*.25f),size=Size(size.width*.64f,size.height*.35f))
        repeat(5) {drawRect(UiColors.Secondary.copy(alpha=.30f),Offset(size.width*(.2f+it*.12f),size.height*.8f),Size(size.width*.07f,size.height*.12f))}
    }
}
internal fun playTimeText(ms: Long): String {
    if(ms<60000) return "${ms/1000} 秒"
    val minutes=ms/60000
    return if(minutes<60) "$minutes 分钟" else "${minutes/60} 小时 ${minutes%60} 分钟"
}
@Composable fun AppShell(model: PlayerViewModel,games: List<LibraryGame> = model.library) {
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun leaveInput() {focusManager.clearFocus();keyboard?.hide()}
    var settingsNested by remember {mutableStateOf(false)}
    var page by rememberSaveable {mutableStateOf("首页")}
    var detailId by rememberSaveable {mutableStateOf<String?>(null)}
    var settingsReturnPage by rememberSaveable {mutableStateOf("首页")}
    var settingsReturnDetail by rememberSaveable {mutableStateOf<String?>(null)}
    var query by rememberSaveable {mutableStateOf("")}
    var sort by rememberSaveable {mutableStateOf(LibrarySort.RECENT.name)}
    var relink by remember {mutableStateOf<GameId?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) {model.add(uri,relink);page="游戏库"};relink=null
    }
    fun pick(id: GameId?=null) {leaveInput();relink=id;picker.launch(arrayOf("*/*"))}
    val detail=games.firstOrNull {it.record.gameId.value==detailId}
    BackHandler(enabled=detailId!=null || page!="首页") {if(detailId!=null) {detailId=null;page="游戏库"} else page="首页"}
    Column(Modifier.fillMaxSize().background(UiColors.Background).safeDrawingPadding()) {
        if(page!="设置" && detail==null) GbaTopBar(if(page=="首页") "GBA Lite" else "游戏库",actions={
            TextButton(enabled=!model.loading,onClick={pick()},modifier=Modifier.testTag("add-game")) {GbaIcon("add",Modifier.size(18.dp));Spacer(Modifier.width(UiSpacing.xs));Text("添加游戏")}
        })
        model.message?.let {message -> AppMessage(message,model.imported!=null,!model.loading,model::dismissMessage,
            {model.imported?.let {model.play(it)}},{pick(detail?.record?.gameId)})}
        if(model.loading) LoadingNotice(model.importing,model::cancelImport)
        Box(Modifier.weight(1f)) {
            when {
                detail!=null -> GameDetails(detail,model,{detailId=null;page="游戏库"},{pick(detail.record.gameId)},{settingsReturnPage="游戏库";settingsReturnDetail=detail.record.gameId.value;page="设置";detailId=null})
                page=="设置" -> UnifiedSettings(model,onBack={page=settingsReturnPage;detailId=settingsReturnDetail},onDepthChanged={settingsNested=it})
                else -> {
                    val selected=if(page=="首页") games.filter {it.lastPlayedAt.isNotEmpty()}.sortedByDescending {it.lastPlayedAt}.take(5)
                        else librarySelection(games,query,LibrarySort.valueOf(sort))
                    Column(Modifier.fillMaxSize()) {
                        if(page=="游戏库" && games.isNotEmpty()) {
                            OutlinedTextField(query,{query=it},label={Text("搜索游戏")},leadingIcon={GbaIcon("search")},singleLine=true,shape=UiShapes.Small,
                                modifier=Modifier.fillMaxWidth().padding(horizontal=UiSpacing.lg).testTag("library-search"))
                            var expanded by remember {mutableStateOf(false)}
                            Row(Modifier.fillMaxWidth().padding(horizontal=UiSpacing.sm),verticalAlignment=Alignment.CenterVertically) {
                                Text("${selected.size} 个游戏",Modifier.weight(1f).padding(start=UiSpacing.sm),style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary)
                                Box {TextButton(onClick={leaveInput();expanded=true}) {Text("排序："+sortName(LibrarySort.valueOf(sort)))}
                                    DropdownMenu(expanded,{expanded=false}) {LibrarySort.entries.forEach {value -> DropdownMenuItem(text={Text(sortName(value))},onClick={sort=value.name;expanded=false})}}
                                }
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize().testTag("library-list"),contentPadding=PaddingValues(bottom=UiSpacing.xl)) {
                            if(page=="首页") {
                                item {model.lastGame?.let {last ->
                                    val row=games.firstOrNull {it.record.gameId==last.gameId}
                                    Surface(onClick=model::resumeLast,enabled=!model.loading,shape=UiShapes.Card,color=UiColors.AccentTint,modifier=Modifier.fillMaxWidth().padding(UiSpacing.lg).testTag("home-continue")) {
                                        Column(Modifier.padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.md)) {
                                            Text("继续上次游戏",style=MaterialTheme.typography.labelLarge,color=UiColors.Accent)
                                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.lg)) {
                                                Artwork(row?.artwork,96.dp)
                                                Column(Modifier.weight(1f)) {Text(last.displayName,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                                    Text(row?.lastPlayedAt?.takeIf {it.isNotEmpty()}?.let(::lastPlayedText) ?: "恢复最近一次保存的进度",style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary)}
                                                GbaIcon("play",color=UiColors.Accent)
                                            }
                                        }
                                    }
                                }}
                                if(selected.isNotEmpty()) item {Row(Modifier.fillMaxWidth().padding(horizontal=UiSpacing.lg),verticalAlignment=Alignment.CenterVertically) {
                                    Text("最近游戏",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                    TextButton(onClick={page="游戏库"}) {Text("查看全部")}
                                }}
                            }
                            if(games.isEmpty()) item {GbaEmptyState("还没有游戏","选择一个 GBA 游戏文件开始","选择游戏",!model.loading) {pick()}}
                            else if(selected.isEmpty()) item {GbaEmptyState(if(page=="首页") "准备好开始了吗" else "没有找到游戏",
                                if(page=="首页") "游戏已添加，到游戏库开始游玩" else "试试其他名称或清除搜索",
                                if(page=="首页") "浏览游戏库" else "清除搜索") {if(page=="首页") page="游戏库" else query=""}}
                            items(selected,key={it.record.gameId.value}) {game ->
                                Surface(onClick={leaveInput();model.play(game.record)},enabled=!model.loading,color=UiColors.Surface,modifier=Modifier.fillMaxWidth().testTag("game-${game.record.gameId.value}")) {
                                    Row(Modifier.padding(horizontal=UiSpacing.lg,vertical=UiSpacing.md),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.md)) {
                                        Artwork(game.artwork)
                                        Column(Modifier.weight(1f)) {
                                            Text(game.record.displayName,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                            Text(if(game.unavailable) "文件不可用，请重新选择" else if(game.playTimeMs>0) playTimeText(game.playTimeMs) else "尚未游玩",style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary)
                                        }
                                        TextButton(enabled=!model.loading,onClick={leaveInput();detailId=game.record.gameId.value}) {Text("详情")}
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start=108.dp,end=UiSpacing.lg),color=UiColors.Line)
                            }
                        }
                    }
                }
            }
        }
        if(detail==null && !(page=="设置" && settingsNested)) NavigationBar(containerColor=UiColors.Surface,tonalElevation=UiElevation.Flat) {
            listOf("首页","游戏库","设置").forEach {tab -> NavigationBarItem(selected=page==tab,onClick={
                leaveInput();if(tab=="设置" && page!="设置") {settingsReturnPage=page;settingsReturnDetail=detailId};page=tab;detailId=null
            },modifier=Modifier.testTag("nav-$tab"),colors=NavigationBarItemDefaults.colors(indicatorColor=UiColors.AccentTint),icon={GbaIcon(when(tab) {"首页"->"home";"游戏库"->"library";else->"settings"})},label={Text(tab)})}
        }
    }
}
private fun sortName(sort: LibrarySort)=when(sort) {LibrarySort.RECENT->"最近游玩";LibrarySort.NAME->"名称";LibrarySort.ADDED->"最近添加";LibrarySort.PLAY_TIME->"游戏时长"}
private fun lastPlayedText(value: String)=runCatching {java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))}.getOrDefault(value.take(16).replace('T',' '))
@Composable private fun LoadingNotice(importing: Boolean,cancel: ()->Unit) {
    Row(Modifier.fillMaxWidth().padding(UiSpacing.lg).testTag("app-loading"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.md)) {
        CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
        Text(if(importing) "正在添加游戏…" else "正在打开游戏…",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        if(importing) TextButton(onClick=cancel) {Text("取消")}
    }
}
@Composable private fun AppMessage(message: String,imported: Boolean,enabled: Boolean,dismiss: ()->Unit,play: ()->Unit,reselect: ()->Unit) {
    val error=!imported && listOf("无法","失败","损坏","不安全","不匹配","过大","请选择","多个","没有 GBA").any {message.contains(it)}
    Surface(Modifier.fillMaxWidth().padding(horizontal=UiSpacing.lg,vertical=UiSpacing.sm).testTag(if(error) "app-error" else "app-message"),shape=UiShapes.Small,
        color=if(error) MaterialTheme.colorScheme.errorContainer else UiColors.AccentTint) {
        Column(Modifier.padding(UiSpacing.md),verticalArrangement=Arrangement.spacedBy(UiSpacing.xs)) {
            Text(if(error) "操作未完成" else if(imported) "准备就绪" else "提示",style=MaterialTheme.typography.titleMedium)
            Text(message,style=MaterialTheme.typography.bodyMedium)
            Row {if(imported) TextButton(enabled=enabled,onClick=play) {Text("打开游戏")}
                else if(error) TextButton(enabled=enabled,onClick=reselect) {Text("重新选择")}
                Spacer(Modifier.weight(1f));TextButton(onClick=dismiss) {Text("关闭")}}
        }
    }
}
@Composable private fun GameDetails(game: LibraryGame,model: PlayerViewModel,back: ()->Unit,relink: ()->Unit,settings: ()->Unit) {
    var renaming by remember {mutableStateOf(false)};var name by remember(game.record.displayName) {mutableStateOf(game.record.displayName)}
    var removing by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        GbaTopBar("游戏详情",back)
        LazyColumn(Modifier.fillMaxSize().testTag("game-details"),contentPadding=PaddingValues(bottom=UiSpacing.xl)) {
            item {Column(Modifier.padding(UiSpacing.lg),verticalArrangement=Arrangement.spacedBy(UiSpacing.lg)) {
                Artwork(game.artwork,128.dp);Text(game.record.displayName,style=MaterialTheme.typography.headlineSmall)
                Button(onClick={model.play(game.record)},enabled=!model.loading,shape=UiShapes.Small,modifier=Modifier.fillMaxWidth().testTag("details-play")) {GbaIcon("play",Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(game.lastPlayedAt.isEmpty()) "开始游戏" else "继续游戏")}
                Row(horizontalArrangement=Arrangement.spacedBy(UiSpacing.xl)) {
                    Column(Modifier.weight(1f)) {Text("游戏时长",style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary);Text(playTimeText(game.playTimeMs))}
                    Column(Modifier.weight(1f)) {Text("上次游玩",style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary);Text(if(game.lastPlayedAt.isEmpty()) "尚未游玩" else lastPlayedText(game.lastPlayedAt))}
                }
            }}
            item {GbaSettingRow("存档管理 · 4 个存档位","Quick · 手动存档 · 自动恢复","save") {model.openSaves(game.record)}}
            item {GbaSettingRow("游戏设置","显示 · 控制 · 外设","settings",enabled=!model.loading,onClick=settings)}
            item {GbaSectionHeader("管理")}
            item {GbaSettingRow("重命名","仅修改显示名称","library") {renaming=true}}
            item {GbaSettingRow("重新选择原 ROM","文件移动或访问失效时重新绑定","library",enabled=!model.loading,onClick=relink)}
            item {TextButton(onClick={removing=true},modifier=Modifier.padding(horizontal=UiSpacing.sm)) {Text("移出游戏库",color=UiColors.Danger)}}
            item {Text("移出不会删除正常存档、即时存档或截图。",Modifier.padding(horizontal=UiSpacing.lg),style=MaterialTheme.typography.bodySmall,color=UiColors.Secondary)}
        }
    }
    if(renaming) AlertDialog(onDismissRequest={renaming=false},title={Text("重命名游戏")},text={OutlinedTextField(name,{name=it.take(120)},singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank(),onClick={model.rename(game.record.gameId,name);renaming=false}) {Text("保存名称")}},dismissButton={TextButton(onClick={renaming=false}) {Text("取消")}})
    if(removing) AlertDialog(onDismissRequest={removing=false},title={Text("移出游戏库？")},text={Text("保留所有存档和截图；可随时重新添加原 ROM。")},
        confirmButton={TextButton(onClick={model.remove(game.record.gameId);removing=false;back()}) {Text("确认移出")}},dismissButton={TextButton(onClick={removing=false}) {Text("取消")}})
}
