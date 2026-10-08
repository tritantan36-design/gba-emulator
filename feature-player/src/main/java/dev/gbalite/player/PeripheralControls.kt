package dev.gbalite.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.gbalite.core.*

@Composable fun PeripheralDialog(s: PeripheralSettings,change: (PeripheralSettings)->Unit,detected: Int,
    tilt: Boolean,gyro: Boolean,light: Boolean,haptic: String,done: ()->Unit,calibrate: ()->Unit,manual: (() -> Unit)?) {
    AlertDialog(onDismissRequest=done,title={Text("外设")},confirmButton={TextButton(onClick=done) { Text("返回菜单") }},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("实时时钟：自动（系统时间）")
            fun availability(flag: Int,available: Boolean)=if(detected and flag==0) "游戏未自动检测到；可手动启用"
                else if(available) "设备支持" else "设备不支持，使用手动控制"
            ModeSelector("倾斜",s.tilt,availability(16,tilt)) { change(s.copy(tilt=it)) }
            ModeSelector("陀螺仪",s.gyro,availability(8,gyro)) { change(s.copy(gyro=it)) }
            TextButton(enabled=tilt || gyro,onClick=calibrate) { Text("以刚才的姿态校准中立") }
            Text("先以舒适姿态游玩，再打开此菜单校准。旋转屏幕后可重新校准。",style=MaterialTheme.typography.bodySmall)
            ModeSelector("太阳能",s.solar,availability(4,light)) { change(s.copy(solar=it)) }
            Text("手动日照强度：${s.sunlight}%")
            Slider(value=s.sunlight.toFloat(),onValueChange={change(s.copy(sunlight=it.toInt()))},
                valueRange=0f..100f,steps=9,enabled=s.solar==PeripheralMode.MANUAL || !light)
            Text("日照控制卡带输入，不改变屏幕亮度。",style=MaterialTheme.typography.bodySmall)
            if(manual!=null) TextButton(onClick=manual) { Text("打开手动倾斜／旋转控制") }
            Text("真实震动当前未启用")
            Text(if(haptic.contains("BLOCKED_BY_PERMISSION_APPROVAL")) "为保持零权限，游戏震动暂不可用。" else haptic,
                style=MaterialTheme.typography.bodySmall)
        }})
}
@Composable private fun ModeSelector(title: String,mode: PeripheralMode,status: String,select: (PeripheralMode)->Unit) {
    Text(title);Text(status,style=MaterialTheme.typography.bodySmall)
    Row { PeripheralMode.entries.forEach { m -> TextButton(onClick={select(m)}) {
        Text((if(m==mode) "✓ " else "")+when(m) { PeripheralMode.AUTO->"自动";PeripheralMode.MANUAL->"手动";PeripheralMode.DISABLED->"关闭" })
    } } }
}
/** Modal live control: game runs; listeners remain scoped to Session state. Release resets axes. */
@Composable internal fun ManualPeripheralPanel(send: (Float,Float,Float)->Unit,done: ()->Unit) {
    DisposableEffect(Unit) { onDispose { send(0f,0f,0f) } }
    var x by remember { mutableFloatStateOf(0f) };var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    AlertDialog(onDismissRequest=done,title={Text("手动控制")},confirmButton={TextButton(onClick=done) { Text("完成") }},text={Column {
        Text("先在外设设置选手动。拖动圆盘倾斜；松开回中立。")
        Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(Unit) {
            detectDragGestures(onDragStart={ p ->
                x=(p.x/size.width*2-1).coerceIn(-1f,1f);y=(1-p.y/size.height*2).coerceIn(-1f,1f);send(x,y,z)
            },onDragEnd={x=0f;y=0f;send(0f,0f,z)},onDragCancel={x=0f;y=0f;send(0f,0f,z)}) { c,_ ->
                c.consume();x=(c.position.x/size.width*2-1).coerceIn(-1f,1f)
                y=(1-c.position.y/size.height*2).coerceIn(-1f,1f);send(x,y,z)
            }
        }) {
            drawCircle(Color.LightGray,radius=size.minDimension/2)
            drawCircle(Color.DarkGray,12.dp.toPx(),Offset((x+1)*size.width/2,(1-y)*size.height/2))
        }
        Text("旋转角速度（独立于倾斜，松手停止）")
        Slider(value=z,onValueChange={z=it;send(x,y,z)},onValueChangeFinished={z=0f;send(x,y,0f)},valueRange=-1f..1f)
    }})
}
