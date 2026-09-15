package com.aimonitor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局设计令牌: 间距 / 尺寸 / 圆角 / 数字排版。
 * 圆角收敛为 4 档 (Badge 8 / Field 12 / Card 16 / Hero 20), 间距按 4dp 网格语义化命名。
 */
object Dims {
    val screenH = 16.dp        // 页面水平边距
    val gapS = 8.dp
    val gapM = 12.dp           // 列表卡片间距 / 卡内主间隔
    val gapL = 16.dp           // 卡片内边距 / 设置行内边距
    val gapXL = 24.dp          // 设置页分区之间

    val listBottom = 112.dp    // 列表底部 FAB 避让
    val avatar = 44.dp         // 供应商徽标
    val usageBar = 6.dp        // 用量进度条高度
}

val CardShape = RoundedCornerShape(16.dp)   // 账户卡 / 设置卡 / 帮助卡
val HeroShape = RoundedCornerShape(20.dp)   // 汇总渐变卡
val FieldShape = RoundedCornerShape(12.dp)  // 输入框 / 按钮 / 皮肤预览
val BadgeShape = RoundedCornerShape(8.dp)   // 徽章 / Tag / 胶囊内件

// 大数字排版 (tnum 等宽)
val NumHeroStyle = SpanStyle(
    fontSize = 32.sp, fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum"
)
val NumLargeStyle = SpanStyle(
    fontSize = 28.sp, fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum"
)
val NumUsageStyle = SpanStyle(
    fontSize = 20.sp, fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum"
)
