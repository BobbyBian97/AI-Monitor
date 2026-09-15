package com.aimonitor.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.ProviderType
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.FieldShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 解析 yyyy-MM-dd, 非法返回 null */
private fun parseDate(s: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(s)!!.time
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountScreen(
    vm: MainViewModel,
    accountId: Long?,
    onDone: () -> Unit
) {
    val accounts by vm.accounts.collectAsState()
    val existing = accountId?.let { id -> accounts.firstOrNull { it.id == id } }

    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: ProviderType.DEEPSEEK) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var apiKey by remember(existing?.id) { mutableStateOf(existing?.apiKey ?: "") }
    var accessKey by remember(existing?.id) { mutableStateOf(existing?.accessKey ?: "") }
    var secretKey by remember(existing?.id) { mutableStateOf(existing?.secretKey ?: "") }
    var baseUrl by remember(existing?.id) { mutableStateOf(existing?.baseUrl ?: "") }
    var customPath by remember(existing?.id) { mutableStateOf(existing?.customPath ?: "") }
    var customCurrency by remember(existing?.id) { mutableStateOf(existing?.customCurrency ?: "¥") }
    var threshold by remember(existing?.id) { mutableStateOf(existing?.threshold?.toString() ?: "10") }
    var planPrice by remember(existing?.id) {
        mutableStateOf(existing?.planPrice?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "")
    }
    var planCycleDays by remember(existing?.id) { mutableStateOf(existing?.planCycleDays ?: 0) }
    var planStart by remember(existing?.id) {
        mutableStateOf(
            existing?.planStartAt?.takeIf { it > 0 }
                ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""
        )
    }
    var cycleExpanded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldShape = FieldShape

    ScreenScaffold(
        title = if (existing == null) "添加账户" else "编辑账户",
        onBack = onDone
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 账户 ──
            SectionHeader("账户")
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = type.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("供应商") },
                    leadingIcon = { ProviderChip(type) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    ProviderType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.displayName) },
                            leadingIcon = { ProviderChip(t) },
                            onClick = { type = t; typeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("备注名 (可选)") },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth()
            )

            // ── 密钥 ──
            SectionHeader("密钥")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (type == ProviderType.VOLCENGINE) "API Key (推理密钥, 可选)" else "API Key") },
                supportingText = if (type == ProviderType.VOLCENGINE) {
                    { Text("用于对话调用; 仅验证密钥时不填 AccessKey 也可") }
                } else null,
                singleLine = true,
                shape = fieldShape,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (type == ProviderType.VOLCENGINE) {
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    label = { Text("AccessKey ID (查套餐用量)") },
                    supportingText = {
                        Text("火山控制台「API 访问密钥」的 AK/SK, 用于查询 Agent Plan 用量 (与推理密钥独立)")
                    },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Access Key") },
                    singleLine = true,
                    shape = fieldShape,
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            if (secretKey.isNotEmpty() && !secretKey.trim().endsWith("="))
                                "火山 Secret 以 == 结尾, 当前输入可能不完整"
                            else "IAM「API 访问密钥」的 Secret, 以 == 结尾, 请完整复制"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "显示/隐藏"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (type.needsBaseUrl || type == ProviderType.CUSTOM) SectionHeader("接口")
            if (type.needsBaseUrl) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(if (type == ProviderType.ONE_API) "中转站地址" else "接口完整 URL") },
                    supportingText = {
                        Text(
                            if (type == ProviderType.ONE_API) "例如 https://api.example.com (不含 /v1)"
                            else "返回 JSON 的完整接口地址"
                        )
                    },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (type == ProviderType.CUSTOM) {
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    label = { Text("取值路径") },
                    supportingText = { Text("点分路径, 如 data.balance 或 balance_infos[0].total_balance") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customCurrency,
                    onValueChange = { customCurrency = it },
                    label = { Text("币种符号") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 提醒与订阅 ──
            SectionHeader("提醒与订阅")
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text("低余额预警阈值") },
                supportingText = { Text("余额低于该值时显示红色提醒") },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth()
            )

            // ── 订阅套餐 (可选) ──
            val cycleOptions = listOf(0 to "未设置", 7 to "每周", 30 to "每月", 90 to "每季", 365 to "每年")
            ExposedDropdownMenuBox(
                expanded = cycleExpanded,
                onExpandedChange = { cycleExpanded = it }
            ) {
                OutlinedTextField(
                    value = cycleOptions.firstOrNull { it.first == planCycleDays }?.second ?: "未设置",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("订阅周期") },
                    supportingText = { Text("套餐类账户可记录每期价格, 自动累计花费") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cycleExpanded) },
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = cycleExpanded,
                    onDismissRequest = { cycleExpanded = false }
                ) {
                    cycleOptions.forEach { (days, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { planCycleDays = days; cycleExpanded = false }
                        )
                    }
                }
            }
            if (planCycleDays > 0) {
                OutlinedTextField(
                    value = planPrice,
                    onValueChange = { planPrice = it },
                    label = { Text("每期价格") },
                    supportingText = { Text("例如 9.9") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = planStart,
                    onValueChange = { planStart = it },
                    label = { Text("购买日期") },
                    supportingText = { Text("格式 yyyy-MM-dd, 留空默认今天") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 校验错误警示条
            error?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(BadgeShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = {
                    val th = threshold.trim().toDoubleOrNull()
                    val akskReady = type == ProviderType.VOLCENGINE &&
                        accessKey.isNotBlank() && secretKey.isNotBlank()
                    when {
                        apiKey.isBlank() && !akskReady -> error =
                            if (type == ProviderType.VOLCENGINE) "请填写 API Key, 或 AccessKey + Secret"
                            else "请填写 API Key"
                        type == ProviderType.VOLCENGINE && !akskReady &&
                            (accessKey.isNotBlank() || secretKey.isNotBlank()) ->
                            error = "AccessKey 与 Secret 需成对填写"
                        type.needsBaseUrl && baseUrl.isBlank() -> error = "请填写接口地址"
                        type == ProviderType.CUSTOM && customPath.isBlank() -> error = "请填写取值路径"
                        th == null || th < 0 -> error = "阈值需为非负数字"
                        planPrice.isNotBlank() && (planPrice.trim().toDoubleOrNull() ?: 0.0) <= 0 ->
                            error = "每期价格需为正数"
                        planStart.isNotBlank() && parseDate(planStart.trim()) == null ->
                            error = "购买日期格式应为 yyyy-MM-dd"
                        else -> {
                            val id = existing?.id ?: vm.nextId()
                            val price = planPrice.trim().toDoubleOrNull() ?: 0.0
                            val startTs = parseDate(planStart.trim()) ?: System.currentTimeMillis()
                            vm.saveAccount(
                                Account(
                                    id = id,
                                    type = type,
                                    name = name.trim(),
                                    apiKey = apiKey.trim(),
                                    accessKey = accessKey.trim(),
                                    secretKey = secretKey.trim(),
                                    baseUrl = baseUrl.trim(),
                                    customPath = customPath.trim(),
                                    customCurrency = customCurrency.trim().ifBlank { "¥" },
                                    threshold = th,
                                    planPrice = price,
                                    planCycleDays = planCycleDays,
                                    planStartAt = if (planCycleDays > 0 && price > 0) startTs else 0L
                                )
                            )
                            onDone()
                        }
                    }
                },
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("保存", style = MaterialTheme.typography.labelLarge) }

            if (existing != null) {
                OutlinedButton(
                    onClick = { vm.deleteAccount(existing.id); onDone() },
                    shape = fieldShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "删除此账户",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(type: ProviderType) {
    val visual = type.visual()
    Box(
        Modifier
            .size(32.dp)
            .clip(BadgeShape)
            .background(visual.color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            visual.monogram,
            color = visual.color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
