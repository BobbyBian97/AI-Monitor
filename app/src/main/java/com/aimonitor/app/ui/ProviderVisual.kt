package com.aimonitor.app.ui

import androidx.compose.ui.graphics.Color
import com.aimonitor.app.data.ProviderType

data class ProviderVisual(val monogram: String, val color: Color, val label: String)

fun ProviderType.visual(): ProviderVisual = when (this) {
    ProviderType.DEEPSEEK -> ProviderVisual("DS", Color(0xFF4D6BFE), "DeepSeek")
    ProviderType.SILICONFLOW -> ProviderVisual("硅", Color(0xFF7C3AED), "硅基流动")
    ProviderType.MOONSHOT -> ProviderVisual("K", Color(0xFF00A99D), "Kimi")
    ProviderType.ZHIPU -> ProviderVisual("智", Color(0xFF3366FF), "智谱")
    ProviderType.VOLCENGINE -> ProviderVisual("火", Color(0xFF0F6DE8), "火山方舟")
    ProviderType.OPENCODE -> ProviderVisual("OC", Color(0xFFF59E0B), "OpenCode")
    ProviderType.OPENROUTER -> ProviderVisual("OR", Color(0xFF8B5CF6), "OpenRouter")
    ProviderType.ONE_API -> ProviderVisual("转", Color(0xFF64748B), "中转站")
    ProviderType.CUSTOM -> ProviderVisual("自", Color(0xFF64748B), "自定义")
}
