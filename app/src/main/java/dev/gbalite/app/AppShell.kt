package dev.gbalite.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
@Composable private fun Artwork(path: String?) {
    val image by produceState<android.graphics.Bitmap?>(null,path) {
        value=if(path==null) null else withContext(Dispatchers.IO) {runCatching {ArtworkCache.load(path)}.getOrNull()}
    }
    if(image!=null) Image(image!!.asImageBitmap(),"游戏缩略图",Modifier.size(72.dp,48.dp))
    else Canvas(Modifier.size(72.dp,48.dp)) {
        drawRoundRect(Color(0xff556b78),topLeft=Offset(0f,size.height*.1f),size=Size(size.width,size.height*.8f))
        drawRect(Color(0xffd4e6e0),topLeft=Offset(size.width*.18f,size.height*.25f),size=Size(size.width*.64f,size.height*.35f))
        repeat(5) {drawRect(Color(0xffc8aa66),Offset(size.width*(.2f+it*.12f),size.height*.8f),Size(size.width*.07f,size.height*.12f))}
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
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if(page!="设置") Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("GBA Lite",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
            if(page!="设置") TextButton(enabled=!model.loading,onClick={pick()}) {Text("添加游戏")}
        }
        model.message?.let {message -> Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(message,Modifier.weight(1f));model.imported?.let {game -> TextButton(enabled=!model.loading,onClick={model.play(game)}) {Text("打开游戏")}}
            TextButton(onClick=model::dismissMessage) {Text("关闭")}
        }}
        if(model.loading) Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp));Text(if(model.importing) "正在添加游戏…" else "正在打开游戏…",Modifier.padding(8.dp))
            if(model.importing) TextButton(onClick=model::cancelImport) {Text("取消")}
        }
        Box(Modifier.weight(1f)) {
            when {
                detail!=null -> GameDetails(detail,model,{detailId=null;page="游戏库"},{pick(detail.record.gameId)},{settingsReturnPage="游戏库";settingsReturnDetail=detail.record.gameId.value;page="设置";detailId=null})
                page=="设置" -> UnifiedSettings(model,onBack={page=settingsReturnPage;detailId=settingsReturnDetail},onDepthChanged={settingsNested=it})
                else -> {
                    val selected=if(page=="首页") games.filter {it.lastPlayedAt.isNotEmpty()}.sortedByDescending {it.lastPlayedAt}.take(5)
                        else librarySelection(games,query,LibrarySort.valueOf(sort))
                    Column(Modifier.fillMaxSize()) {
                        if(page=="首页") {
                            model.lastGame?.let {last -> Button(enabled=!model.loading,onClick=model::resumeLast,modifier=Modifier.fillMaxWidth().padding(16.dp)) {Text("继续上次游戏")}}
                            if(games.isNotEmpty()) Text("最近游戏",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.titleMedium)
                        } else {
                            OutlinedTextField(query,{query=it},label={Text("搜索游戏")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("library-search"))
                            var expanded by remember {mutableStateOf(false)}
                            Box {TextButton(onClick={expanded=true}) {Text("排序："+sortName(LibrarySort.valueOf(sort)))}
                                DropdownMenu(expanded,{expanded=false}) {LibrarySort.entries.forEach {value -> DropdownMenuItem(text={Text(sortName(value))},onClick={sort=value.name;expanded=false})}}
                            }
                        }
                        if(games.isEmpty()) Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("还没有游戏",style=MaterialTheme.typography.titleLarge);Text("选择一个 GBA 游戏文件开始")
                            Text("支持 .gba 或包含一个 .gba 的 .zip",style=MaterialTheme.typography.bodySmall)
                            Button(enabled=!model.loading,onClick={pick()}) {Text("选择游戏")}
                        } else if(selected.isEmpty()) Column(Modifier.padding(24.dp)) {
                            Text(if(page=="首页") "游戏已添加，到游戏库开始游玩" else "没有找到游戏")
                            if(page=="首页") TextButton(onClick={page="游戏库"}) {Text("浏览游戏库")}
                            else TextButton(onClick={query=""}) {Text("清除搜索")}
                        }
                        LazyColumn(Modifier.fillMaxSize().testTag("library-list"),contentPadding=PaddingValues(12.dp)) {
                            items(selected,key={it.record.gameId.value}) {game ->
                                Card(onClick={model.play(game.record)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp).testTag("game-${game.record.gameId.value}")) {
                                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Artwork(game.artwork);Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                                            Text(game.record.displayName,style=MaterialTheme.typography.titleMedium)
                                            Text(playTimeText(game.playTimeMs),style=MaterialTheme.typography.bodySmall)
                                            Text(if(game.unavailable) "文件已失效，请重新选择" else if(game.lastPlayedAt.isEmpty()) "尚未游玩" else "上次游玩：${game.lastPlayedAt.take(16).replace('T',' ')}",style=MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick={leaveInput();detailId=game.record.gameId.value}) {Text("详情")}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(detail==null && !(page=="设置" && settingsNested)) NavigationBar(containerColor=dev.gbalite.player.ui.UiColors.Surface,tonalElevation=0.dp) {listOf("首页","游戏库","设置").forEach {tab -> NavigationBarItem(selected=page==tab,onClick={
            leaveInput()
            if(tab=="设置" && page!="设置") {settingsReturnPage=page;settingsReturnDetail=detailId}
            page=tab;detailId=null
        },modifier=Modifier.testTag("nav-$tab"),colors=NavigationBarItemDefaults.colors(indicatorColor=dev.gbalite.player.ui.UiColors.AccentTint),icon={dev.gbalite.player.ui.GbaIcon(when(tab) {"首页"->"home";"游戏库"->"library";else->"settings"})},label={Text(tab)})}}

    }
}
private fun sortName(sort: LibrarySort)=when(sort) {LibrarySort.RECENT->"最近游玩";LibrarySort.NAME->"名称";LibrarySort.ADDED->"最近添加";LibrarySort.PLAY_TIME->"游戏时长"}
@Composable private fun GameDetails(game: LibraryGame,model: PlayerViewModel,back: ()->Unit,relink: ()->Unit,settings: ()->Unit) {
    var renaming by remember {mutableStateOf(false)};var name by remember(game.record.displayName) {mutableStateOf(game.record.displayName)}
    var removing by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick=back) {Text("返回游戏库")};Artwork(game.artwork);Text(game.record.displayName,style=MaterialTheme.typography.headlineSmall)}
        item {Text("游戏时长：${playTimeText(game.playTimeMs)}");Text(if(game.lastPlayedAt.isEmpty()) "尚未游玩" else "上次游玩：${game.lastPlayedAt.take(16).replace('T',' ')}")}
        item {Button(onClick={model.play(game.record)},enabled=!model.loading) {Text("继续 / 开始游戏")}}
        item {TextButton(onClick={model.openSaves(game.record)}) {Text("存档管理 · 4 个存档位")};TextButton(onClick=settings) {Text("游戏设置")}}
        item {TextButton(onClick={renaming=true}) {Text("重命名")};TextButton(onClick=relink,enabled=!model.loading) {Text("重新选择原 ROM")}}
        item {TextButton(onClick={removing=true}) {Text("移出游戏库")};Text("移出不会删除正常存档、即时存档或截图。",style=MaterialTheme.typography.bodySmall)}
    }
    if(renaming) AlertDialog(onDismissRequest={renaming=false},title={Text("重命名游戏")},text={OutlinedTextField(name,{name=it.take(120)},singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank(),onClick={model.rename(game.record.gameId,name);renaming=false}) {Text("保存名称")}},dismissButton={TextButton(onClick={renaming=false}) {Text("取消")}})
    if(removing) AlertDialog(onDismissRequest={removing=false},title={Text("移出游戏库？")},text={Text("保留所有存档和截图；可随时重新添加原 ROM。")},
        confirmButton={TextButton(onClick={model.remove(game.record.gameId);removing=false;back()}) {Text("确认移出")}},dismissButton={TextButton(onClick={removing=false}) {Text("取消")}})
}
