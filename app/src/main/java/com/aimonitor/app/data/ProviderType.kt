package com.aimonitor.app.data

/**
 * 供应商类型: 各自的余额查询端点与解析规则见 [BalanceRepository]
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val currency: String,
    val needsBaseUrl: Boolean = false
) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "¥"),
    SILICONFLOW("硅基流动", "https://api.siliconflow.cn", "¥"),
    MOONSHOT("Moonshot Kimi", "https://api.moonshot.cn", "¥"),
    ZHIPU("智谱 BigModel", "https://www.bigmodel.cn", "¥"),
    VOLCENGINE("火山方舟", "https://ark.cn-beijing.volces.com", "¥"),
    OPENCODE("OpenCode Zen", "https://opencode.ai", "$"),
    OPENROUTER("OpenRouter", "https://openrouter.ai", "$"),
    ONE_API("OpenAI 兼容中转", "", "$", needsBaseUrl = true),
    CUSTOM("自定义", "", "¥", needsBaseUrl = true);

    companion object {
        fun from(name: String?): ProviderType =
            values().firstOrNull { it.name == name } ?: CUSTOM
    }
}
