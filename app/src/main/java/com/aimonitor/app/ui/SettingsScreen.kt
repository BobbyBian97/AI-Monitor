package com.aimonitor.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimonitor.app.BuildConfig
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.BalanceNotifier
import com.aimonitor.app.data.MonitorService
import com.aimonitor.app.data.Updater
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.CardShape
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.FieldShape
import com.aimonitor.app.ui.theme.ImageSkin
import com.aimonitor.app.ui.theme.Skins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        runCatching {
            val f = File(ctx.filesDir, "bg.img")
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
            ImageSkin.extract(f.absolutePath)?.let { skin ->
                AppSettings.saveCustomSkin(ctx, ImageSkin.serialize(skin))
                customSkin = skin
                onSkinChange(ImageSkin.ID)
                onBgChange(f.absolutePath)
            }
        }
    }

    // ── 应用内更新 ──
    var checking by remember { mutableStateOf(false) }
    var newVersion by remember { mutableStateOf<Updater.Manifest?>(null) }
    var upToDate by remember { mutableStateOf(false) }
    var updError by remember { mutableStateOf<String?>(null) }
    var dlProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    var askInstallPerm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var dlJob by remember { mutableStateOf<Job?>(null) }
    // 更新地址由用户填写并保存在本机, 安装包内不内置任何服务器地址
    var updateUrl by remember { mutableStateOf(AppSettings.loadUpdateUrl(ctx)) }

    fun tryInstall(f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            pendingApk = f
            askInstallPerm = true
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingApk?.let { f ->
            if (Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) {
                pendingApk = null
                askInstallPerm = false
                tryInstall(f)
            }
        }
    }

    fun startDownload(m: Updater.Manifest) {
        newVersion = null
        updError = null
        dlProgress = 0L to -1L
        dlJob = scope.launch(Dispatchers.IO) {
            var lastPct = -1
            val f = Updater.download(ctx, m.url, m.versionCode) { read, total ->
                val pct = if (total > 0) (read * 100 / total).toInt() else -1
                if (pct != lastPct) {
                    lastPct = pct
                    dlProgress = read to total
                }
            }
            when {
                f == null -> withContext(Dispatchers.Main) {
                    dlProgress = null; updError = "下载失败, 请重试"
                }
                m.sha256.isNotBlank() && Updater.sha256(f) != m.sha256.lowercase() -> {
                    f.delete()
                    withContext(Dispatchers.Main) {
                        dlProgress = null; updError = "安装包校验失败 (SHA-256 不符), 已删除"
                    }
                }
                !Updater.signatureMatches(ctx, f) -> {
                    f.delete()
                    withContext(Dispatchers.Main) {
                        dlProgress = null; updError = "安装包签名校验失败, 已删除"
                    }
                }
                else -> withContext(Dispatchers.Main) {
                    dlProgress = null
                    tryInstall(f)
                }
            }
        }
    }

    fun checkUpdate() {
        if (AppSettings.loadUpdateUrl(ctx).isBlank()) {
            upToDate = false
            updError = "请先填写更新地址"
            return
        }
        checking = true
        upToDate = false
        updError = null
        scope.launch(Dispatchers.IO) {
            val m = Updater.fetchManifest(ctx)
            withContext(Dispatchers.Main) {
                checking = false
                when {
                    m == null || m.url.isBlank() -> updError = "获取更新信息失败"
                    m.isNewer -> newVersion = m
                    else -> upToDate = true
                }
            }
        }
    }

    // 新版本弹窗
    newVersion?.let { m ->
        AlertDialog(
            onDismissRequest = { newVersion = null },
            title = { Text("发现新版本 v${m.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.name.isNotBlank()) Text(m.name, fontWeight = FontWeight.SemiBold)
                    m.notes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { startDownload(m) }) { Text("下载并安装") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { newVersion = null }) { Text("暂不") }
            }
        )
    }
    // 下载进度弹窗
    dlProgress?.let { p ->
        val read = p.first
        val total = p.second
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在下载新版本") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "%.1f MB".format(read / 1048576.0) +
                            if (total > 0) " / %.1f MB".format(total / 1048576.0) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dlJob?.cancel()
                    dlProgress = null
                }) { Text("取消") }
            }
        )
    }
    // 安装权限引导
    if (askInstallPerm) {
        AlertDialog(
            onDismissRequest = { askInstallPerm = false },
            title = { Text("允许安装应用") },
            text = {
                Text("首次安装需要授权「AI 余量监控」安装未知应用。授权后返回会自动继续安装。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askInstallPerm = false
                    installPermLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }) { Text("去授权") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askInstallPerm = false }) { Text("取消") }
            }
        )
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val f = File(ctx.filesDir, "bg.img")
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
            onBgChange(f.absolutePath)
        }
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
                Hint("仅 App 在前台时刷新, 退到后台自动停止")
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
                val notifOn = AppSettings.loadNotifEnabled(ctx)
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
                                "App 在后台也按设定间隔刷新, 关闭开关即停止",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifOn,
                            onCheckedChange = { on ->
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
                            enabled = AppSettings.loadNotifEnabled(ctx),
                            onCheckedChange = { on ->
                                notifDetail = on
                                AppSettings.saveNotifDetail(ctx, on)
                                vm.updateNotification()
                            }
                        )
                    }
                }
            }

            // ── 版本与更新 ──
            item { SectionHeader("版本与更新") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedTextField(
                            value = updateUrl,
                            onValueChange = {
                                updateUrl = it
                                AppSettings.saveUpdateUrl(ctx, it)
                            },
                            label = { Text("更新地址") },
                            placeholder = { Text("留空则不检查更新") },
                            supportingText = {
                                Text(
                                    "填写更新清单 latest.json 的完整地址",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            singleLine = true,
                            shape = FieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InsetDivider()
                        Row(
                            Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "当前版本 v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (upToDate) "已是最新版本"
                                    else "检查新版本, 下载校验后自动安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { checkUpdate() },
                                enabled = !checking && dlProgress == null
                            ) {
                                if (checking) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else Text("检查更新")
                            }
                        }
                        updError?.let {
                            Text(
                                it,
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Hint("更新地址仅保存在本机, 不随安装包分发")
            }

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
