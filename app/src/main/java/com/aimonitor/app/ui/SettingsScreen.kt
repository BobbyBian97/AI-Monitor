package com.aimonitor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.BalanceAlerter
import com.aimonitor.app.data.BalanceNotifier
import com.aimonitor.app.data.MonitorService
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.CardShape
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.FieldShape
import com.aimonitor.app.ui.theme.ImageSkin
import com.aimonitor.app.ui.theme.Skins
import java.io.File

/** 设置: 定时刷新间隔 / 皮肤 / 自定义背景 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    skinId: String,
    onSkinChange: (String) -> Unit,
    bgPath: String?,
    onBgChange: (String?) -> Unit
) {
    val ctx = LocalContext.current
    val interval by vm.refreshIntervalMs.collectAsState()
    var customSkin by remember {
        mutableStateOf(ImageSkin.deserialize(AppSettings.loadCustomSkin(ctx)))
    }

    // 选图 → 同时生成配色皮肤 + 设为背景
    val pickSkinImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val f = copyPickedImage(ctx, uri) ?: return@rememberLauncherForActivityResult
        runCatching {
            ImageSkin.extract(f.absolutePath)?.let { skin ->
                AppSettings.saveCustomSkin(ctx, ImageSkin.serialize(skin))
                customSkin = skin
                onSkinChange(ImageSkin.ID)
                onBgChange(f.absolutePath)
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copyPickedImage(ctx, uri)?.let { f -> onBgChange(f.absolutePath) }
    }

    fun clearBg() {
        File(ctx.filesDir, "bg.img").delete()
        onBgChange(null)
    }

    ScreenScaffold(title = "设置", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Dims.screenH, end = Dims.screenH, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dims.gapS)
        ) {
            // ── 定时刷新间隔 ──
            item { SectionHeader("定时刷新间隔") }
            item {
                AppCard {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        AppSettings.intervalOptions.forEachIndexed { i, ms ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.setRefreshInterval(ms) }
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = ms == interval,
                                    onClick = { vm.setRefreshInterval(ms) }
                                )
                                Text(AppSettings.intervalLabel(ms))
                                if (ms == AppSettings.DEFAULT_INTERVAL) {
                                    Spacer(Modifier.width(8.dp))
                                    Tag("默认")
                                }
                            }
                            if (i != AppSettings.intervalOptions.lastIndex) InsetDivider()
                        }
                    }
                }
                Hint("前后台均按此间隔自动刷新; 重启或应用更新后自动恢复")
            }

            // ── 皮肤 ──
            item { SectionHeader("皮肤") }
            item {
                AppCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pickSkinImage.launch("image/*") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Palette, contentDescription = null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("从图片生成皮肤", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "提取照片主色生成整套配色, 并设为背景",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight, contentDescription = null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 跟随系统深色: 亮色沧海 / 暗色星夜
            item {
                val systemSelected = skinId == "system"
                AppCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSkinChange("system") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Brightness6, contentDescription = null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("跟随系统", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "亮色用「沧海」, 暗色用「星夜」, 随系统深色模式自动切换",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (systemSelected) {
                            Icon(
                                Icons.Filled.Check, contentDescription = "已选择",
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            val rows = (listOfNotNull(customSkin) + Skins.all).chunked(2)
            items(rows.size) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dims.gapM)) {
                    rows[r].forEach { skin ->
                        SkinItem(
                            skin = skin,
                            selected = skin.id == skinId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSkinChange(skin.id) }
                        )
                    }
                    if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Hint("皮肤立即生效, 重启 App 保留") }

            // ── 通知栏展示 ──
            item { SectionHeader("通知栏展示") }
            item {
                // State 而非普通值: 开关切换后立即反映到 UI (含明细开关联动)
                var notifOn by remember { mutableStateOf(AppSettings.loadNotifEnabled(ctx)) }
                val reqPerm = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> if (granted) vm.updateNotification() }
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("常驻通知栏展示余量", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "App 在后台也按设定间隔刷新, 重启后自动恢复; 关闭开关即停止",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifOn,
                            onCheckedChange = { on ->
                                notifOn = on
                                AppSettings.saveNotifEnabled(ctx, on)
                                if (on) {
                                    MonitorService.start(ctx)
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                            ctx, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) reqPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else vm.updateNotification()
                                } else {
                                    MonitorService.stop(ctx)
                                    BalanceNotifier.cancel(ctx)
                                }
                            }
                        )
                    }
                    var notifDetail by remember { mutableStateOf(AppSettings.loadNotifDetail(ctx)) }
                    InsetDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("折叠时显示明细", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "开: 汇总+全部账户速览  关: 仅系统默认摘要",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifDetail,
                            enabled = notifOn,
                            onCheckedChange = { on ->
                                notifDetail = on
                                AppSettings.saveNotifDetail(ctx, on)
                                vm.updateNotification()
                            }
                        )
                    }
                    var lowAlert by remember { mutableStateOf(AppSettings.loadLowBalanceAlertEnabled(ctx)) }
                    InsetDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("低余量预警", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "余量跌破阈值时单独提醒, 恢复后自动解除; 点击提醒直达账户",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lowAlert,
                            enabled = notifOn,
                            onCheckedChange = { on ->
                                lowAlert = on
                                AppSettings.saveLowBalanceAlertEnabled(ctx, on)
                                if (on) {
                                    // 已授权则立即评估一次 (跌破中即刻提醒), 未授权先补请求
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                            ctx, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) reqPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else vm.updateNotification()
                                } else {
                                    BalanceAlerter.cancelAllAndReset(ctx)
                                }
                            }
                        )
                    }
                }
            }

            // ── 版本与更新 ──
            item { UpdateSettingsSection() }

            // ── 自定义背景 ──
            item { SectionHeader("自定义背景") }
            item {
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (bgPath != null) "已设置背景图" else "未设置, 使用纯色背景",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                                Icon(
                                    Icons.Filled.PhotoLibrary, contentDescription = null,
                                    Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (bgPath == null) "从相册选择" else "更换图片")
                            }
                            if (bgPath != null) {
                                OutlinedButton(onClick = { clearBg() }) {
                                    Icon(
                                        Icons.Filled.Delete, contentDescription = null,
                                        Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("清除背景")
                                }
                            }
                        }
                    }
                }
                Hint("背景图仅保存在本机, 用于整页衬底并自动加深色遮罩")
            }
        }
    }
}

@Composable
private fun SkinItem(
    skin: com.aimonitor.app.ui.theme.Skin,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .let {
                // 选中描边包整卡全周
                if (selected) it.border(2.dp, MaterialTheme.colorScheme.primary, CardShape)
                else it
            }
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(FieldShape)
                .background(Brush.linearGradient(skin.gradient)),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 右下角色板圆点: 主色 / 容器色 / 表面色
                listOf(skin.primary, skin.primaryContainer, skin.surface).forEach { c ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(0.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(skin.name, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = BadgeShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 把选中的图片复制到私有目录 bg.img, 失败返回 null */
private fun copyPickedImage(ctx: android.content.Context, uri: Uri): File? = runCatching {
    val f = File(ctx.filesDir, "bg.img")
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        f.outputStream().use { input.copyTo(it) }
    } ?: return null
    f
}.getOrNull()

/** 解码背景图 (最长边压到 1440, 防大图 OOM), 失败返回 null */
fun decodeBg(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (path == null) return null
    return runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1440) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        bmp.asImageBitmap()
    }.getOrNull()
}
