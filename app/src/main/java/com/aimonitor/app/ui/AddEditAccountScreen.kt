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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
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
    var showApiKey by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // 字段级错误: key → 提示文案; 输入该字段时清除
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val fieldShape = FieldShape
    val fieldFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String): FocusRequester = fieldFocus.getOrPut(key) { FocusRequester() }

    fun clearError(key: String) {
        if (fieldErrors.isNotEmpty()) fieldErrors = fieldErrors - key
    }

    /** 提交校验: 返回字段级错误 (有序, 首个为焦点定位目标) */
    fun validate(): Map<String, String> {
        val errs = linkedMapOf<String, String>()
        val th = threshold.trim().toDoubleOrNull()
        val akskReady = type == ProviderType.VOLCENGINE &&
            accessKey.isNotBlank() && secretKey.isNotBlank()
        if (apiKey.isBlank() && !akskReady) {
            errs["apiKey"] =
                if (type == ProviderType.VOLCENGINE) "请填写 API Key, 或 AccessKey + Secret"
                else "请填写 API Key"
        }
        if (type == ProviderType.VOLCENGINE && !akskReady &&
            (accessKey.isNotBlank() || secretKey.isNotBlank())
        ) errs["secretKey"] = "AccessKey 与 Secret 需成对填写"
        if (type.needsBaseUrl && baseUrl.isBlank()) errs["baseUrl"] = "请填写接口地址"
        if (type == ProviderType.CUSTOM && customPath.isBlank()) errs["customPath"] = "请填写取值路径"
        if (th == null || th < 0) errs["threshold"] = "阈值需为非负数字"
        if (planCycleDays > 0 && planPrice.isNotBlank() &&
            (planPrice.trim().toDoubleOrNull() ?: 0.0) <= 0
        ) errs["planPrice"] = "每期价格需为正数"
        if (planStart.isNotBlank() && parseDate(planStart.trim()) == null)
            errs["planStart"] = "购买日期格式应为 yyyy-MM-dd"
        return errs
    }

    // 校验失败后聚焦首个错误字段 (聚焦自动滚入视野; 条件渲染字段未组合时忽略)
    LaunchedEffect(fieldErrors) {
        val key = fieldErrors.keys.firstOrNull() ?: return@LaunchedEffect
        runCatching { fieldFocus[key]?.requestFocus() }
    }

    fun save() {
        if (saving) return
        val errs = validate()
        fieldErrors = errs
        if (errs.isNotEmpty()) return
        saving = true
        val th = threshold.trim().toDoubleOrNull() ?: 10.0
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

    ScreenScaffold(
        title = if (existing == null) "添加账户" else "编辑账户",
        onBack = onDone
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .imePadding()
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth()
            )

            // ── 密钥 ──
            SectionHeader("密钥")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; clearError("apiKey") },
                label = { Text(if (type == ProviderType.VOLCENGINE) "API Key (推理密钥, 可选)" else "API Key") },
                supportingText = {
                    Text(
                        fieldErrors["apiKey"]
                            ?: if (type == ProviderType.VOLCENGINE) "用于对话调用; 仅验证密钥时不填 AccessKey 也可" else " "
                    )
                },
                isError = "apiKey" in fieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = fieldShape,
                visualTransformation = if (showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(requester("apiKey"))
            )

            if (type == ProviderType.VOLCENGINE) {
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it; clearError("secretKey") },
                    label = { Text("AccessKey ID (查套餐用量)") },
                    supportingText = {
                        Text(
                            fieldErrors["secretKey"]
                                ?: "火山控制台「API 访问密钥」的 AK/SK, 用于查询 Agent Plan 用量 (与推理密钥独立)"
                        )
                    },
                    isError = "secretKey" in fieldErrors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it; clearError("secretKey") },
                    label = { Text("Secret Access Key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = fieldShape,
                    visualTransformation = if (showSecret) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            fieldErrors["secretKey"]
                                ?: if (secretKey.isNotEmpty() && !secretKey.trim().endsWith("="))
                                    "火山 Secret 以 == 结尾, 当前输入可能不完整"
                                else "IAM「API 访问密钥」的 Secret, 以 == 结尾, 请完整复制"
                        )
                    },
                    isError = "secretKey" in fieldErrors,
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "显示/隐藏"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(requester("secretKey"))
                )
            }

            if (type.needsBaseUrl || type == ProviderType.CUSTOM) SectionHeader("接口")
            if (type.needsBaseUrl) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; clearError("baseUrl") },
                    label = { Text(if (type == ProviderType.ONE_API) "中转站地址" else "接口完整 URL") },
                    supportingText = {
                        Text(
                            fieldErrors["baseUrl"]
                                ?: if (type == ProviderType.ONE_API) "例如 https://api.example.com (不含 /v1)"
                                else "返回 JSON 的完整接口地址"
                        )
                    },
                    isError = "baseUrl" in fieldErrors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next
                    ),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().focusRequester(requester("baseUrl"))
                )
            }

            if (type == ProviderType.CUSTOM) {
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it; clearError("customPath") },
                    label = { Text("取值路径") },
                    supportingText = {
                        Text(fieldErrors["customPath"] ?: "点分路径, 如 data.balance 或 balance_infos[0].total_balance")
                    },
                    isError = "customPath" in fieldErrors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().focusRequester(requester("customPath"))
                )
                OutlinedTextField(
                    value = customCurrency,
                    onValueChange = { customCurrency = it },
                    label = { Text("币种符号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 提醒与订阅 ──
            SectionHeader("提醒与订阅")
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it; clearError("threshold") },
                label = { Text("低余额预警阈值") },
                supportingText = { Text(fieldErrors["threshold"] ?: "余额低于该值时显示红色提醒") },
                isError = "threshold" in fieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (planCycleDays > 0) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth().focusRequester(requester("threshold"))
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
                    onValueChange = { planPrice = it; clearError("planPrice") },
                    label = { Text("每期价格") },
                    supportingText = { Text(fieldErrors["planPrice"] ?: "例如 9.9") },
                    isError = "planPrice" in fieldErrors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().focusRequester(requester("planPrice"))
                )
                // 购买日期: 只读字段 + 系统日期选择器
                OutlinedTextField(
                    value = planStart,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("购买日期") },
                    supportingText = {
                        Text(fieldErrors["planStart"] ?: "点右侧图标选择, 留空默认今天")
                    },
                    isError = "planStart" in fieldErrors,
                    singleLine = true,
                    shape = fieldShape,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "选择日期")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showDatePicker) {
                    val tz = java.util.TimeZone.getDefault()
                    val initMs = planStart.trim().let { parseDate(it) }
                        ?.let { ts -> ts - tz.getOffset(ts) }
                        ?: System.currentTimeMillis()
                    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initMs)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { ms ->
                                    // DatePicker 返回 UTC 零点, 用 UTC 格式化避免时区偏移
                                    val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                    planStart = utcFmt.format(Date(ms))
                                }
                                showDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        }
                    ) {
                        DatePicker(state = pickerState)
                    }
                }
            }

            Button(
                onClick = { save() },
                enabled = !saving,
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else Text("保存", style = MaterialTheme.typography.labelLarge)
            }

            if (existing != null) {
                OutlinedButton(
                    onClick = { askDelete = true },
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

    if (askDelete && existing != null) {
        ConfirmDialog(
            title = "删除账户",
            text = "将删除「${existing.name.ifBlank { existing.type.displayName }}」及其全部查询记录, 不可恢复。",
            onConfirm = {
                askDelete = false
                vm.deleteAccount(existing.id)
                onDone()
            },
            onDismiss = { askDelete = false }
        )
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
