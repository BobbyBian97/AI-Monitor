package com.aimonitor.app.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.AccountStore
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.BalanceWidget
import com.aimonitor.app.data.ResultCache
import com.aimonitor.app.data.WidgetConfig
import com.aimonitor.app.ui.theme.AIMonitorTheme
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.ImageSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 明细小部件配置页: 添加组件时由系统拉起, 勾选该实例显示的账户;
 * 长按已放置的组件 → 「重新配置」可再次进入。
 * 系统契约: setResult(RESULT_OK) + EXTRA_APPWIDGET_ID 才保留组件;
 * 返回取消 = 启动器移除; 配置完成不回调 onUpdate, 须自行渲染一次。
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // 默认取消: 返回键/手势退出 = 组件不添加
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        setContent {
            WidgetConfigScreen(activity = this, appWidgetId = appWidgetId)
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    fun saveAndFinish(appWidgetId: Int, ids: Set<Long>) {
        WidgetConfig.save(this, appWidgetId, ids)
        // 首次添加配置完成后系统不再回调 onUpdate, 自行渲染该实例
        Thread {
            val ctx = applicationContext
            val accounts = AccountStore(ctx).load()
            val results = ResultCache(ctx).load()
            val awm = AppWidgetManager.getInstance(ctx)
            awm.updateAppWidget(
                appWidgetId, BalanceWidget.renderFor(awm, appWidgetId, ctx, accounts, results)
            )
        }.start()
        setResult(RESULT_OK, resultIntent(appWidgetId))
        finish()
    }
}

private data class ConfigUiState(val accounts: List<Account>, val initial: Set<Long>)

@Composable
private fun WidgetConfigScreen(activity: WidgetConfigActivity, appWidgetId: Int) {
    val ctx = LocalContext.current
    // 皮肤解析与主界面同口径 (跟随系统时亮=沧海 暗=星夜)
    val skinId = AppSettings.loadSkinId(ctx)
    val systemDark = isSystemInDarkTheme()
    val skin = remember(skinId, systemDark) {
        if (skinId == "system") ImageSkin.resolve(ctx, if (systemDark) "midnight" else "blue")
        else ImageSkin.resolve(ctx, skinId)
    }
    AIMonitorTheme(skin = skin) {
        // 账户列表与初始勾选 (未配置过的实例 = 全选)
        val state by produceState<ConfigUiState?>(null, appWidgetId) {
            value = withContext(Dispatchers.IO) {
                val accounts = AccountStore(ctx).load()
                val pre = WidgetConfig.load(ctx, appWidgetId)
                ConfigUiState(accounts, pre ?: accounts.map { it.id }.toSet())
            }
        }
        var checked by remember { mutableStateOf<Set<Long>?>(null) }
        ScreenScaffold(title = "小部件账户筛选", onBack = { activity.finish() }) { padding ->
            val data = state
            if (data == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@ScreenScaffold
            }
            val sel = checked ?: data.initial
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Dims.screenH, end = Dims.screenH, top = 4.dp, bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dims.gapM)
                ) {
                    item {
                        Column {
                            SectionHeader("该组件显示哪些账户")
                            Hint("全部不勾选 = 显示全部; 每个组件实例独立配置, 之后长按组件可重新配置")
                        }
                    }
                    if (data.accounts.isEmpty()) {
                        item {
                            AppCard {
                                Text(
                                    "暂无账户, 可先保存组件, 添加账户后自动显示",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            AppCard {
                                data.accounts.forEach { a ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = a.id in sel,
                                            onCheckedChange = { on ->
                                                checked = if (on) sel + a.id else sel - a.id
                                            }
                                        )
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            Text(a.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${a.type.displayName} · ${a.currency}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { activity.saveAndFinish(appWidgetId, sel) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dims.screenH)
                ) {
                    Text(if (sel.isEmpty()) "保存 (显示全部)" else "保存 ${sel.size} 个账户")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
