package com.aimonitor.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimonitor.app.ui.theme.CardShape
import kotlin.math.abs

/**
 * 二级页面统一骨架: 背景色容器顶栏 + 返回箭头 + titleLarge 标题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = actions
            )
        },
        content = content
    )
}

/** 危险操作确认弹窗: 确认键 error 色 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 设置分区标题 */
@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

/** 统一卡片容器 (无内边距, 内容行自带 padding) */
@Composable
fun AppCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

/** 卡片下方灰色说明 */
@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

/** 卡内行间通栏分隔线 */
@Composable
fun InsetDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

/** 状态点: 8dp 圆点; pulse=true 时呼吸闪烁 (加载中) */
@Composable
fun StatusDot(color: Color, pulse: Boolean = false, modifier: Modifier = Modifier) {
    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition()
        val a by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
        )
        a
    } else 1f
    Box(
        modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 数字滚动: 值变化时从旧值平滑滚到新值 (600ms)。初值取当前值, 冷启动不从头滚。
 */
@Composable
fun RollingNumber(
    value: Double,
    style: SpanStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val shown = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        if (abs(shown.value - value) > 0.005) {
            shown.animateTo(value.toFloat(), tween(600, easing = FastOutSlowInEasing))
        }
    }
    // 叶子 Composable: 动画值的读取隔离在叶子作用域内, 父级不随动画帧重组
    RollingNumberText(shown, style, modifier, color)
}

@Composable
private fun RollingNumberText(
    shown: Animatable<Float, AnimationVector1D>,
    style: SpanStyle,
    modifier: Modifier,
    color: Color
) {
    Text(
        buildAnnotatedString { withStyle(style) { append("%.2f".format(shown.value)) } },
        color = color,
        maxLines = 1,
        modifier = modifier
    )
}
