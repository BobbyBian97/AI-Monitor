package com.aimonitor.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimonitor.app.BuildConfig
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.Updater
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.FieldShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页「版本与更新」分区: 更新地址填写 + 检查/下载/校验/安装全流程,
 * 含新版本、下载进度、安装权限引导三个弹窗。
 */
@Composable
fun UpdateSettingsSection() {
    val ctx = LocalContext.current
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

    Column(verticalArrangement = Arrangement.spacedBy(Dims.gapS)) {
        SectionHeader("版本与更新")
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
}
