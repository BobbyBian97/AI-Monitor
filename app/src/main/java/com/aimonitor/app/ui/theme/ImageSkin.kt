package com.aimonitor.app.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aimonitor.app.data.AppSettings

/**
 * 从图片提取配色生成皮肤 (androidx.palette 取 vibrant 主色 + HSL 推导整套色板)。
 * 序列化存 AppSettings, 图片本身只留作背景, 不进安装包。
 */
object ImageSkin {
    const val ID = "custom"
    const val NAME = "图片色"

    /** 解码小图 → palette 提色 → 生成皮肤; 失败返回 null */
    fun extract(path: String): Skin? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 192) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val palette = Palette.from(bmp).maximumColorCount(24).generate()
        val base = palette.vibrantSwatch ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: return null

        // 平均亮度决定明/暗系
        var lumSum = 0.0
        var n = 0
        for (x in 0 until bmp.width step 3) for (y in 0 until bmp.height step 3) {
            val p = bmp.getPixel(x, y)
            lumSum += (0.299 * android.graphics.Color.red(p) + 0.587 * android.graphics.Color.green(p) + 0.114 * android.graphics.Color.blue(p)) / 255.0
            n++
        }
        val dark = n > 0 && lumSum / n < 0.38

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base.rgb, hsv)
        val h = hsv[0]
        val s = hsv[1].coerceIn(0.30f, 0.95f)

        val colors = if (dark) listOf(
            hsvColor(h, s * 0.65f, 0.78f),          // primary
            hsvColor(h, s * 0.80f, 0.30f),          // primaryContainer
            hsvColor(h, s * 0.60f, 0.65f),          // secondary
            hsvColor((h + 40) % 360, s * 0.50f, 0.68f), // tertiary
            hsvColor(h, s * 0.35f, 0.075f),         // background
            hsvColor(h, s * 0.35f, 0.115f),         // surface
            hsvColor(h, s * 0.35f, 0.19f),          // surfaceVariant
            hsvColor(h, s * 0.90f, 0.55f),          // gradient 1
            hsvColor(h, s * 0.95f, 0.38f),          // gradient 2
            hsvColor(h, s * 0.90f, 0.22f)           // gradient 3
        ) else listOf(
            hsvColor(h, s, 0.55f),                  // primary
            hsvColor(h, (s * 0.55f).coerceAtMost(0.5f), 0.90f),
            hsvColor(h, s * 0.90f, 0.42f),
            hsvColor((h + 40) % 360, s * 0.80f, 0.70f),
            hsvColor(h, s * 0.25f, 0.968f),
            hsvColor(h, s * 0.15f, 0.988f),
            hsvColor(h, s * 0.30f, 0.925f),
            hsvColor(h, s, 0.68f),
            hsvColor(h, s, 0.55f),
            hsvColor(h, (s + 0.05f).coerceAtMost(1f), 0.32f)
        )

        Skin(
            ID, NAME, dark,
            primary = colors[0], primaryContainer = colors[1],
            secondary = colors[2], tertiary = colors[3],
            background = colors[4], surface = colors[5], surfaceVariant = colors[6],
            gradient = colors.subList(7, 10)
        )
    }.getOrNull()

    private fun hsvColor(h: Float, s: Float, v: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

    fun serialize(skin: Skin): String =
        (if (skin.dark) 1 else 0).toString() + "," +
            (listOf(skin.primary, skin.primaryContainer, skin.secondary, skin.tertiary,
                skin.background, skin.surface, skin.surfaceVariant) + skin.gradient)
                .joinToString(",") { "%06X".format(it.toArgb() and 0xFFFFFF) }

    fun deserialize(raw: String?): Skin? {
        if (raw == null) return null
        return runCatching {
            val parts = raw.split(",")
            val dark = parts[0] == "1"
            val c = parts.subList(1, 11).map { Color(0xFF000000.toInt() or it.toInt(16)) }
            Skin(
                ID, NAME, dark,
                primary = c[0], primaryContainer = c[1], secondary = c[2], tertiary = c[3],
                background = c[4], surface = c[5], surfaceVariant = c[6],
                gradient = c.subList(7, 10)
            )
        }.getOrNull()
    }

    /** 当前生效皮肤: id 为 custom 且本地有提取结果时返回图片皮肤 */
    fun resolve(ctx: Context, id: String): Skin =
        if (id == ID) deserialize(AppSettings.loadCustomSkin(ctx)) ?: Skins.byId("blue")
        else Skins.byId(id)
}
