package com.aimonitor.app.data

sealed class BalanceResult {
    object Idle : BalanceResult()
    object Loading : BalanceResult()
    data class Success(
        val total: Double,
        val granted: Double? = null,
        val toppedUp: Double? = null,
        val currency: String,
        val fetchedAt: Long = System.currentTimeMillis()
    ) : BalanceResult()
    /** 无数值数据的提示 (如密钥有效性验证结果) */
    data class Info(
        val message: String,
        val fetchedAt: Long = System.currentTimeMillis()
    ) : BalanceResult()
    /** 订阅制套餐用量(百分比窗口), 如 OpenCode Go 的 rolling/weekly/monthly */
    data class Usage(
        val windows: List<UsageWindow>,
        val fetchedAt: Long = System.currentTimeMillis()
    ) : BalanceResult()
    data class UsageWindow(
        val label: String,
        val percent: Int,
        val status: String,
        val resetsAt: Long?,
        /** 绝对已用额度 (如火山 AFP), 为 null 时仅显示百分比 */
        val used: Double? = null,
        /** 窗口总额度 */
        val quota: Double? = null
    )
    data class Error(val message: String) : BalanceResult()
}
