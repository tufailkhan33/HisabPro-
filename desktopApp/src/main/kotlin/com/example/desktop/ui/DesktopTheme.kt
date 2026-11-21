package com.example.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.model.SyncStatus
import org.jetbrains.skia.Image as SkiaImage

object DesktopColors {
    val Navy = Color(0xFF172C46)
    val NavyDark = Color(0xFF0F1E32)
    val NavyLight = Color(0xFF244266)
    val NavyContainer = Color(0xFFE6ECF4)
    val SoftBlueBtn = Color(0xFFEAF0F7)
    val White = Color(0xFFFFFFFF)
    val LightGray = Color(0xFFF4F6F9)
    val SurfaceVariant = Color(0xFFEBEFF5)
    val Border = Color(0xFFDDE3EC)
    val BlackText = Color(0xFF111111)
    val SecondaryGray = Color(0xFF6B7280)
    val SuccessGreen = Color(0xFF16834A)
    val SuccessBg = Color(0xFFE8F5EE)
    val WarningOrange = Color(0xFFE58A22)
    val WarningBg = Color(0xFFFDF3E6)
    val ErrorRed = Color(0xFFD94343)
    val ErrorBg = Color(0xFFFCECEC)
    val GoldAccent = Color(0xFFD4A843)
    val KpiBlueBg = Color(0xFFEEF4FC)
    val KpiPurpleBg = Color(0xFFF3EEFC)
    val KpiPurpleIcon = Color(0xFF7C3AED)
}

private val DesktopLightColorScheme = lightColorScheme(
    primary = DesktopColors.Navy,
    onPrimary = DesktopColors.White,
    primaryContainer = DesktopColors.NavyContainer,
    onPrimaryContainer = DesktopColors.NavyDark,
    secondary = DesktopColors.NavyLight,
    onSecondary = DesktopColors.White,
    background = DesktopColors.LightGray,
    onBackground = DesktopColors.BlackText,
    surface = DesktopColors.White,
    onSurface = DesktopColors.BlackText,
    surfaceVariant = DesktopColors.SurfaceVariant,
    onSurfaceVariant = DesktopColors.SecondaryGray,
    error = DesktopColors.ErrorRed,
    outline = DesktopColors.Border
)

@Composable
fun HisabProDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DesktopLightColorScheme,
        content = content
    )
}

fun loadBundledLogoBitmap(): ImageBitmap? = try {
    val bytes = object {}.javaClass.classLoader
        ?.getResourceAsStream("hisabpro_logo.png")
        ?.use { it.readBytes() }
    if (bytes != null) SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() else null
} catch (_: Exception) {
    null
}

@Composable
fun HisabProLogo(sizeDp: Int = 44, modifier: Modifier = Modifier) {
    val bitmap = remember { loadBundledLogoBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "HisabPro Logo",
            contentScale = ContentScale.Fit,
            modifier = modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DesktopColors.Navy),
            contentAlignment = Alignment.Center
        ) {
            Text("HP", color = DesktopColors.GoldAccent, fontWeight = FontWeight.Bold, fontSize = (sizeDp / 3).sp)
        }
    }
}

@Composable
fun DesktopCard(
    modifier: Modifier = Modifier,
    containerColor: Color = DesktopColors.White,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DesktopColors.Border),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun DesktopKpiCard(
    title: String,
    value: String,
    subtitle: String,
    bgColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DesktopColors.Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesktopColors.SecondaryGray)
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(subtitle, fontSize = 11.sp, color = DesktopColors.SecondaryGray)
        }
    }
}

@Composable
fun SyncStatusBadge(statusKey: String) {
    val status = SyncStatus.fromKey(statusKey)
    val (bg, fg) = when (status) {
        SyncStatus.SYNCED -> DesktopColors.SuccessBg to DesktopColors.SuccessGreen
        SyncStatus.PENDING_SYNC -> DesktopColors.WarningBg to DesktopColors.WarningOrange
        SyncStatus.SYNCING -> DesktopColors.KpiBlueBg to DesktopColors.Navy
        SyncStatus.SYNC_FAILED, SyncStatus.CONFLICT_REQUIRES_REVIEW -> DesktopColors.ErrorBg to DesktopColors.ErrorRed
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = status.label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
