package com.aimonitor.app.data

/**
 * 汇总统计: 按币种聚合 (首页汇总卡与通知栏共用, 避免多处重复实现)。
 */
object BalanceSummary {

    /** 成功结果的余额按币种累加 (保持首次出现顺序) */
    fun currencyTotals(results: Collection<BalanceResult>): LinkedHashMap<String, Double> {
        val totals = LinkedHashMap<String, Double>()
        results.forEach { r ->
            if (r is BalanceResult.Success) {
                totals[r.currency] = (totals[r.currency] ?: 0.0) + r.total
            }
        }
        return totals
    }

    /** 套餐累计投入按币种累加 (保持首次出现顺序) */
    fun planTotals(accounts: List<Account>): LinkedHashMap<String, Double> {
        val totals = LinkedHashMap<String, Double>()
        accounts.forEach { a ->
            a.planStats()?.let { st ->
                totals[a.currency] = (totals[a.currency] ?: 0.0) + st.spent
            }
        }
        return totals
    }
}
