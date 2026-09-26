package com.aimonitor.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimonitor.app.ui.theme.Dims

private data class HelpEntry(val title: String, val icon: ImageVector, val lines: List<String>)

private val helpEntries = listOf(
    HelpEntry("账户监控", Icons.Filled.QueryStats, listOf(
        "支持 DeepSeek / SiliconFlow / 月之暗面 / 智谱 / 火山引擎 / OpenRouter / OneAPI 类中转及自定义接口。",
        "余额类账户显示总额与各分项; 套餐用量类账户显示各窗口已用百分比进度条。",
        "可设置低额阈值: 余量低于阈值或用量超 80% 时, 卡片与通知栏标红警示。",
        "点击卡片进入编辑, 卡片内按钮单独刷新该账户。"
    )),
    HelpEntry("拖动排序", Icons.Filled.SwapVert, listOf(
        "在主界面长按任意账户卡片, 震动后即可上下拖动调整显示顺序。",
        "排序会立即保存, 通知栏明细顺序同步更新。"
    )),
    HelpEntry("手动与定时刷新", Icons.Filled.Sync, listOf(
        "顶栏刷新按钮或列表下拉可手动刷新全部账户。",
        "设置中可选刷新间隔: 15 秒 ~ 10 分钟 (默认 1 分钟)。",
        "App 在前台时自动按间隔刷新; 退到后台由常驻服务继续同频刷新, 重开 App 直接显示最新数据。"
    )),
    HelpEntry("通知栏速览", Icons.Filled.Notifications, listOf(
        "常驻通知栏展示余量: 折叠时为汇总 + 各账户速览一行, 上下滑动通知可展开查看逐账户明细与进度条。",
        "展开后点击任意账户行可直达该账户编辑页; 更新时间为真实数据抓取时间。",
        "低余量预警: 余量跌破账户阈值时单独高优先级提醒, 恢复后自动解除; 可在设置中开关。",
        "设置中「折叠时显示明细」可关闭折叠态明细, 仅保留系统默认摘要。",
        "点击通知回到 App。"
    )),
    HelpEntry("订阅花费统计", Icons.Filled.Payments, listOf(
        "编辑账户时可设置套餐: 每期价格 + 周期 (周/月/季/年) + 购买日期。",
        "卡片与汇总卡显示已订期数、累计投入和下次续费日期。"
    )),
    HelpEntry("皮肤与背景", Icons.Filled.Palette, listOf(
        "内置 10 套配色主题; 也可从相册选图, 自动提取配色生成专属皮肤并同时设为背景。",
        "自定义背景可在设置中单独清除。"
    )),
    HelpEntry("调试日志", Icons.Filled.Article, listOf(
        "记录每次请求的结果与错误信息, 用于排查接口异常。"
    )),
    HelpEntry("检查更新", Icons.Filled.SystemUpdateAlt, listOf(
        "默认从 GitHub Releases 检查新版本, 也可在设置页填写自建更新清单地址。",
        "检测到新版本会展示更新说明, 下载后自动校验并拉起安装。",
        "更新包下载前与启动时自动清理, 不占存储空间。"
    )),
    HelpEntry("数据安全", Icons.Filled.Lock, listOf(
        "API 密钥加密存储在本机私有目录, 不上传任何服务器。",
        "刷新请求直连各供应商官方接口。"
    ))
)

/** 功能说明 · 帮助页: 卡片点击展开/收起 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    ScreenScaffold(title = "帮助", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Dims.screenH, end = Dims.screenH, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dims.gapM)
        ) {
            items(helpEntries.size) { i ->
                val e = helpEntries[i]
                val expanded = expandedIndex == i
                HelpCard(
                    entry = e,
                    expanded = expanded,
                    onClick = { expandedIndex = if (expanded) null else i }
                )
            }
        }
    }
}

@Composable
private fun HelpCard(entry: HelpEntry, expanded: Boolean, onClick: () -> Unit) {
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "helpArrow"
    )
    AppCard {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { role = Role.Button }
                .animateContentSize(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.rotate(arrow),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            if (expanded) {
                entry.lines.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            } else {
                // 收起时保留首行预览
                Text(
                    entry.lines.first(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
