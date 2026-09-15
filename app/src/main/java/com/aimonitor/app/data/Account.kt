package com.aimonitor.app.data

data class Account(
    val id: Long,
    val type: ProviderType,
    val name: String = "",
    val apiKey: String,
    /** 火山方舟: 账号 AccessKey ID, 用于 OpenAPI 查询套餐用量 (与推理密钥独立) */
    val accessKey: String = "",
    /** 火山方舟: 账号 Secret Access Key */
    val secretKey: String = "",
    val baseUrl: String = "",
    val customPath: String = "",
    val customCurrency: String = "¥",
    val threshold: Double = 10.0,
    /** 订阅套餐: 每期价格 (0 = 未设置) */
    val planPrice: Double = 0.0,
    /** 订阅套餐: 周期天数 (7/30/90/365, 0 = 未设置) */
    val planCycleDays: Int = 0,
    /** 订阅套餐: 购买日期 (时间戳, 0 = 未设置) */
    val planStartAt: Long = 0
) {
    val displayName: String get() = name.ifBlank { type.displayName }
    val currency: String get() = if (type == ProviderType.CUSTOM) customCurrency else type.currency

    /** 是否配置了订阅套餐 */
    val hasPlan: Boolean get() = planCycleDays > 0 && planPrice > 0 && planStartAt > 0

    /**
     * 订阅统计: 已付期数 (当前这期计入) / 累计花费 / 下期续费时间。
     */
    fun planStats(now: Long = System.currentTimeMillis()): PlanStats? {
        if (!hasPlan) return null
        val cycleMs = planCycleDays * 86_400_000L
        val paid = (((now - planStartAt) / cycleMs).toInt() + 1).coerceAtLeast(1)
        return PlanStats(paid, paid * planPrice, planStartAt + paid * cycleMs)
    }
}

data class PlanStats(
    val cyclesPaid: Int,
    val spent: Double,
    val nextRenewAt: Long
)
