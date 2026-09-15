package com.aimonitor.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 内置皮肤: 每套定义核心色板, on 色与中性色自动推导。
 */
data class Skin(
    val id: String,
    val name: String,
    val dark: Boolean = false,
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val gradient: List<Color>
)

/** 当前皮肤 (顶栏汇总卡 / 空状态渐变取色用) */
val LocalSkin = staticCompositionLocalOf { Skins.all.first() }

/** 状态语义色 (成功/警示/错误/空闲), 亮暗皮肤两套, 供状态点/金额警示/日志级别等取用 */
data class StatusColors(
    val ok: Color,
    val warn: Color,
    val error: Color,
    val idle: Color
)

private val LightStatus = StatusColors(
    ok = Color(0xFF2E9E5B), warn = Color(0xFFF59E0B),
    error = Color(0xFFD93A3A), idle = Color(0xFFAEB8C7)
)

private val DarkStatus = StatusColors(
    ok = Color(0xFF57C584), warn = Color(0xFFF5A83D),
    error = Color(0xFFE57676), idle = Color(0xFF6B7480)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object Skins {
    val all = listOf(
        Skin(
            "blue", "沧海",
            primary = Color(0xFF1565C0), primaryContainer = Color(0xFFD8E7FF),
            secondary = Color(0xFF0F5BA8), tertiary = Color(0xFF00A8CC),
            background = Color(0xFFF4F7FC), surface = Color(0xFFFDFEFF),
            surfaceVariant = Color(0xFFE3EAF5),
            gradient = listOf(Color(0xFF2F80ED), Color(0xFF1565C0), Color(0xFF0B3D91))
        ),
        Skin(
            "midnight", "星夜", dark = true,
            primary = Color(0xFF8FC2FF), primaryContainer = Color(0xFF1D4E89),
            secondary = Color(0xFF7FB5F0), tertiary = Color(0xFF6EE7DC),
            background = Color(0xFF0B0F1A), surface = Color(0xFF131A29),
            surfaceVariant = Color(0xFF1F2940),
            gradient = listOf(Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF0B1026))
        ),
        Skin(
            "sakura", "樱时",
            primary = Color(0xFFD6336C), primaryContainer = Color(0xFFFFDCE9),
            secondary = Color(0xFFC2255C), tertiary = Color(0xFF9C6ADE),
            background = Color(0xFFFFF5F9), surface = Color(0xFFFFFBFD),
            surfaceVariant = Color(0xFFFBE3ED),
            gradient = listOf(Color(0xFFFF8FB8), Color(0xFFE64980), Color(0xFFA61E4D))
        ),
        Skin(
            "matcha", "茶园",
            primary = Color(0xFF2F9E44), primaryContainer = Color(0xFFD3F9D8),
            secondary = Color(0xFF2B8A3E), tertiary = Color(0xFF0CA678),
            background = Color(0xFFF4FAF4), surface = Color(0xFFFCFFFC),
            surfaceVariant = Color(0xFFE4F4E5),
            gradient = listOf(Color(0xFF69DB7C), Color(0xFF37B24D), Color(0xFF2B8A3E))
        ),
        Skin(
            "sunset", "熔金",
            primary = Color(0xFFE8590C), primaryContainer = Color(0xFFFFE8CC),
            secondary = Color(0xFFD9480F), tertiary = Color(0xFFE03131),
            background = Color(0xFFFFF8F0), surface = Color(0xFFFFFDF9),
            surfaceVariant = Color(0xFFFBEADB),
            gradient = listOf(Color(0xFFFFB020), Color(0xFFF76707), Color(0xFFD9480F))
        ),
        Skin(
            "violet", "幻紫",
            primary = Color(0xFF7048E8), primaryContainer = Color(0xFFE5DBFF),
            secondary = Color(0xFF6741D9), tertiary = Color(0xFFE64980),
            background = Color(0xFFF8F6FE), surface = Color(0xFFFDFCFF),
            surfaceVariant = Color(0xFFEBE5FA),
            gradient = listOf(Color(0xFF9775FA), Color(0xFF7048E8), Color(0xFF5F3DC4))
        ),
        Skin(
            "teal", "沧浪",
            primary = Color(0xFF0C8599), primaryContainer = Color(0xFFC5F6FA),
            secondary = Color(0xFF087F8C), tertiary = Color(0xFF099268),
            background = Color(0xFFF2FBFC), surface = Color(0xFFFBFEFF),
            surfaceVariant = Color(0xFFDEEFF2),
            gradient = listOf(Color(0xFF3BC9DB), Color(0xFF1098AD), Color(0xFF0B7285))
        ),
        Skin(
            "rose", "绯红",
            primary = Color(0xFFE03131), primaryContainer = Color(0xFFFFE3E3),
            secondary = Color(0xFFC92A2A), tertiary = Color(0xFFF59F00),
            background = Color(0xFFFFF6F5), surface = Color(0xFFFFFCFC),
            surfaceVariant = Color(0xFFFCEAEA),
            gradient = listOf(Color(0xFFFF8787), Color(0xFFFA5252), Color(0xFFC92A2A))
        ),
        Skin(
            "amber", "琥珀夜", dark = true,
            primary = Color(0xFFFFC078), primaryContainer = Color(0xFF6B4A12),
            secondary = Color(0xFFE8B667), tertiary = Color(0xFF8CE99A),
            background = Color(0xFF15100B), surface = Color(0xFF1F1811),
            surfaceVariant = Color(0xFF322618),
            gradient = listOf(Color(0xFFF59F00), Color(0xFFD97706), Color(0xFF78350F))
        ),
        Skin(
            "graphite", "素岩",
            primary = Color(0xFF475569), primaryContainer = Color(0xFFE2E8F0),
            secondary = Color(0xFF334155), tertiary = Color(0xFF0EA5E9),
            background = Color(0xFFFAFBFC), surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDF0F3),
            gradient = listOf(Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFF334155))
        )
    )

    fun byId(id: String): Skin = all.firstOrNull { it.id == id } ?: all.first()
}

private fun onOf(c: Color): Color =
    if (c.luminance() > 0.55f) Color(0xFF15181E) else Color.White

private fun Skin.toScheme(): ColorScheme =
    if (dark) darkColorScheme(
        primary = primary, onPrimary = onOf(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = onOf(primaryContainer),
        secondary = secondary, onSecondary = onOf(secondary),
        tertiary = tertiary, onTertiary = onOf(tertiary),
        background = background, onBackground = onOf(background),
        surface = surface, onSurface = onOf(surface),
        surfaceVariant = surfaceVariant, onSurfaceVariant = onOf(surfaceVariant),
        outline = Color(0xFF6B7480), outlineVariant = Color(0xFF39414C),
        error = Color(0xFFEF9A9A), onError = Color(0xFF5B1010)
    ) else lightColorScheme(
        primary = primary, onPrimary = onOf(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = onOf(primaryContainer),
        secondary = secondary, onSecondary = onOf(secondary),
        tertiary = tertiary, onTertiary = onOf(tertiary),
        background = background, onBackground = onOf(background),
        surface = surface, onSurface = onOf(surface),
        surfaceVariant = surfaceVariant, onSurfaceVariant = Color(0xFF5B6472),
        outline = Color(0xFF8D97A7), outlineVariant = Color(0xFFDFE5EF),
        error = Color(0xFFD32F2F), onError = Color.White
    )

/**
 * 字体层级: 中文行高放宽, body/label 系全局 tnum 等宽数字 (时间/金额/百分比纵向对齐)。
 */
private val tnum = "tnum"

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp,
        fontFeatureSettings = tnum
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp,
        fontFeatureSettings = tnum
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp,
        fontFeatureSettings = tnum
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = tnum
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
        fontFeatureSettings = tnum
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
        fontFeatureSettings = tnum
    )
)

/**
 * 主题入口。[bgBitmap] 非空时以图片为全局背景 (加半透明遮罩保证可读性, 卡片表面转为半透明)。
 */
@Composable
fun AIMonitorTheme(
    skin: Skin = Skins.all.first(),
    bgBitmap: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val hasBg = bgBitmap != null
    val scheme = skin.toScheme().let {
        if (hasBg) it.copy(
            background = Color.Transparent,
            surface = it.surface.copy(alpha = 0.90f),
            surfaceVariant = it.surfaceVariant.copy(alpha = 0.85f)
        ) else it
    }
    CompositionLocalProvider(
        LocalSkin provides skin,
        LocalStatusColors provides if (skin.dark) DarkStatus else LightStatus
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            Box(Modifier.fillMaxSize()) {
                if (hasBg) {
                    Image(
                        bitmap = bgBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                }
                content()
            }
        }
    }
}
