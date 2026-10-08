package dev.gbalite.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object UiColors {
    val Background=Color(0xfff5f5f2)
    val Surface=Color(0xfffdfdfb)
    val Ink=Color(0xff20232b)
    val Secondary=Color(0xff626570)
    val Accent=Color(0xff66519c)
    val AccentTint=Color(0xffede8f6)
    val Line=Color(0xffe2e2de)
    val Danger=Color(0xffa4404d)
    val Control=Color(0xff333842)
    val ControlEdge=Color(0xff181c24)
    val ControlLight=Color(0xff565e6c)
    val Action=Color(0xff864b68)
    val Overlay=Color(0xf022252d)
    val Scrim=Color.Black.copy(alpha=.50f)
}
object UiSpacing {val xs=4.dp;val sm=8.dp;val md=12.dp;val lg=16.dp;val xl=24.dp;val xxl=32.dp}
object UiShapes {val Small=RoundedCornerShape(10.dp);val Card=RoundedCornerShape(14.dp);val Modal=RoundedCornerShape(20.dp);val Capsule=RoundedCornerShape(50)}
object UiElevation {val Flat=0.dp;val Low=2.dp;val Overlay=6.dp}
object UiOpacity {const val Secondary=.72f;const val Control=.90f}
object UiTypography {
    val styles=Typography(
        headlineMedium=TextStyle(fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold),
        headlineSmall=TextStyle(fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
        titleLarge=TextStyle(fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
        titleMedium=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),
        bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
        bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
        bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
        labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
        labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium))
}
private val light=lightColorScheme(primary=UiColors.Accent,onPrimary=Color.White,primaryContainer=UiColors.AccentTint,
    background=UiColors.Background,surface=UiColors.Surface,onSurface=UiColors.Ink,onSurfaceVariant=UiColors.Secondary,
    surfaceVariant=UiColors.Background,outlineVariant=UiColors.Line,error=UiColors.Danger)
private val dark=darkColorScheme(primary=Color(0xffd3bff2),onPrimary=UiColors.Ink,
    surface=UiColors.Overlay,onSurface=Color(0xfff5f4f8),onSurfaceVariant=Color(0xffbcbec9),
    surfaceVariant=UiColors.Control,outlineVariant=UiColors.ControlLight,error=Color(0xffffb2bd))
@Composable fun GbaTheme(content: @Composable ()->Unit) {MaterialTheme(colorScheme=light,typography=UiTypography.styles,content=content)}
@Composable fun GbaGameTheme(content: @Composable ()->Unit) {MaterialTheme(colorScheme=dark,typography=UiTypography.styles,content=content)}

/** Original, single-stroke icon vocabulary; no downloaded asset or icon dependency. */
@Composable fun GbaIcon(kind: String,modifier: Modifier=Modifier,color: Color=LocalContentColor.current,description: String?=null) {
    Canvas(modifier.size(24.dp).then(if(description==null) Modifier else Modifier.semantics {contentDescription=description})) {
        val unit=size.minDimension/24f;val stroke=Stroke(1.8f*unit,cap=StrokeCap.Round)
        fun line(x: Float,y: Float,xx: Float,yy: Float)=drawLine(color,Offset(x*unit,y*unit),Offset(xx*unit,yy*unit),stroke.width,StrokeCap.Round)
        fun box(x: Float,y: Float,w: Float,h: Float)=drawRoundRect(color,Offset(x*unit,y*unit),Size(w*unit,h*unit),androidx.compose.ui.geometry.CornerRadius(2*unit),style=stroke)
        when(kind) {
            "back"->{line(15f,5f,8f,12f);line(8f,12f,15f,19f)}
            "next"->{line(9f,6f,15f,12f);line(15f,12f,9f,18f)}
            "home"->{line(3f,11f,12f,4f);line(12f,4f,21f,11f);line(6f,9f,6f,20f);line(6f,20f,18f,20f);line(18f,20f,18f,9f)}
            "library"->{box(4f,4f,6f,16f);box(14f,4f,6f,16f);line(6f,8f,8f,8f);line(16f,8f,18f,8f)}
            "play"->{val p=Path().apply {moveTo(8*unit,5*unit);lineTo(19*unit,12*unit);lineTo(8*unit,19*unit);close()};drawPath(p,color,style=stroke)}
            "add"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
            "save","load"->{box(5f,4f,14f,16f);line(8f,4f,8f,9f);line(8f,9f,16f,9f);box(8f,14f,8f,6f)}
            "display"->{box(3f,5f,18f,12f);line(8f,21f,16f,21f);line(12f,17f,12f,21f)}
            "controls"->{box(3f,7f,18f,12f);line(8f,10f,8f,16f);line(5f,13f,11f,13f);drawCircle(color,unit,Offset(16*unit,11*unit));drawCircle(color,unit,Offset(18*unit,15*unit))}
            "speed"->{line(5f,5f,12f,12f);line(12f,12f,5f,19f);line(12f,5f,19f,12f);line(19f,12f,12f,19f)}
            "exit"->{line(10f,4f,4f,4f);line(4f,4f,4f,20f);line(4f,20f,10f,20f);line(9f,12f,21f,12f);line(16f,7f,21f,12f);line(21f,12f,16f,17f)}
            "audio"->{line(5f,9f,9f,9f);line(9f,9f,14f,5f);line(14f,5f,14f,19f);line(14f,19f,9f,15f);line(9f,15f,5f,15f);line(5f,15f,5f,9f);line(18f,8f,20f,12f);line(20f,12f,18f,16f)}
            "search"->{drawCircle(color,6*unit,Offset(10*unit,10*unit),style=stroke);line(15f,15f,21f,21f)}
            "settings"->{line(5f,7f,19f,7f);line(5f,17f,19f,17f);drawCircle(color,3*unit,Offset(9*unit,7*unit),style=stroke);drawCircle(color,3*unit,Offset(15*unit,17*unit),style=stroke)}
            "about"->{drawCircle(color,9*unit,center,style=stroke);line(12f,11f,12f,17f);drawCircle(color,unit,Offset(12*unit,7*unit))}
            "peripherals"->{box(7f,3f,10f,14f);line(10f,7f,14f,7f);line(12f,17f,12f,21f);line(9f,21f,15f,21f)}
            "menu"->{line(5f,7f,19f,7f);line(5f,12f,19f,12f);line(5f,17f,19f,17f)}
            "check"->{line(5f,12f,10f,17f);line(10f,17f,19f,7f)}
            else->{drawCircle(color,9*unit,center,style=stroke);drawCircle(color,2*unit,center,style=stroke)}
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GbaTopBar(title: String,back: (() -> Unit)?=null,actions: @Composable RowScope.()->Unit={}) {
    TopAppBar(title={Text(title,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)},
        navigationIcon={if(back!=null) IconButton(onClick=back) {GbaIcon("back",description="返回")}},actions=actions,
        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
}
@Composable fun GbaSectionHeader(text: String) {Text(text,Modifier.padding(horizontal=UiSpacing.lg,vertical=UiSpacing.md),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun GbaSettingRow(title: String,summary: String="",icon: String="settings",modifier: Modifier=Modifier,selected: Boolean=false,enabled: Boolean=true,onClick: ()->Unit) {
    Row(modifier.fillMaxWidth().heightIn(min=64.dp).clickable(enabled=enabled,onClick=onClick).padding(horizontal=UiSpacing.lg,vertical=UiSpacing.md),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.lg)) {
        GbaIcon(icon,color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge)
            if(summary.isNotEmpty()) Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        GbaIcon(if(selected) "check" else "next",modifier=Modifier.size(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun GbaEmptyState(title: String,description: String,action: String,enabled: Boolean=true,onClick: ()->Unit) {
    Column(Modifier.fillMaxWidth().padding(UiSpacing.xxl),verticalArrangement=Arrangement.spacedBy(UiSpacing.md),horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=UiShapes.Card,color=MaterialTheme.colorScheme.primaryContainer) {GbaIcon("controls",Modifier.padding(UiSpacing.lg),MaterialTheme.colorScheme.primary)}
        Text(title,style=MaterialTheme.typography.titleLarge);Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick=onClick,enabled=enabled,shape=UiShapes.Small) {Text(action)}
    }
}
