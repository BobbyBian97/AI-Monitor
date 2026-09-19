package com.aimonitor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimonitor.app.data.AppLog
import androidx.compose.runtime.collectAsState
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.LocalStatusColors
import com.aimonitor.app.ui.theme.onOf
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val status = LocalStatusColors.current
    var onlyErrors by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    // 日志变更计数: append/clear 时自动刷新列表, 无需手动刷新
    val logVersion by AppLog.version.collectAsState()
    val entries = remember(logVersion) { AppLog.entries() }
    val shown = if (onlyErrors) entries.filter { it.level == "E" } else entries

    ScreenScaffold(
        title = "调试日志",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(AppLog.dump()))
                Toast.makeText(ctx, "已复制 ${entries.size} 条日志", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部")
            }
            IconButton(onClick = { askClear = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "清空")
            }
        }
    ) { padding ->
        if (askClear) {
            ConfirmDialog(
                title = "清空日志",
                text = "将删除全部 ${entries.size} 条本地日志, 不可恢复。",
                onConfirm = {
                    AppLog.clear()
                    askClear = false
                },
                onDismiss = { askClear = false }
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !onlyErrors,
                    onClick = { onlyErrors = false },
                    label = { Text("全部 ${entries.size}") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = onlyErrors,
                    onClick = { onlyErrors = true },
                    label = { Text("仅错误 ${entries.count { it.level == "E" }}") }
                )
            }

            if (shown.isEmpty()) {
                Text(
                    "暂无日志\n添加账户并刷新后, 这里会记录每次查询的端点、状态码与错误详情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown) { e ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    AppLog.timeLabel(e.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    e.level,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onOf(levelColor(e.level)),
                                    modifier = Modifier
                                        .background(levelColor(e.level), BadgeShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    e.tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (e.level == "E") status.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                e.msg,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: String): Color {
    val status = LocalStatusColors.current
    return when (level) {
        "E" -> status.error
        "W" -> status.warn
        else -> MaterialTheme.colorScheme.outline
    }
}
