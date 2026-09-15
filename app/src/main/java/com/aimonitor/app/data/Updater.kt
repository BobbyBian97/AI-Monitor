package com.aimonitor.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.aimonitor.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 应用内更新: 拉取 latest.json → 比对 versionCode → 下载 APK 到私有目录 → SHA-256 校验。
 * 安装包本地清理: [cleanup] 在启动时/下载前删除已安装或残留的更新包。
 */
object Updater {
    private const val MANIFEST_URL = "https://dl.ai-bong.cn/apk/latest.json"
    private val http = OkHttpClient()

    class Manifest(
        val version: String,
        val versionCode: Int,
        val url: String,
        val sha256: String,
        val name: String,
        val notes: List<String>
    ) {
        val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
    }

    fun fetchManifest(): Manifest? = runCatching {
        http.newCall(Request.Builder().url(MANIFEST_URL).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val json = JSONObject(body.string())
            val notes = json.optJSONArray("notes")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()
            Manifest(
                version = json.optString("version"),
                versionCode = json.optInt("versionCode", 0),
                url = json.optString("url"),
                sha256 = json.optString("sha256", ""),
                name = json.optString("name", ""),
                notes = notes
            )
        }
    }.getOrNull()

    /**
     * 流式下载到 filesDir/update/update-<versionCode>.apk。
     * 先写 .part 临时文件, 完成后原子改名; 失败/取消自动删除临时文件。
     */
    suspend fun download(
        ctx: Context,
        url: String,
        versionCode: Int,
        onProgress: (read: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "update").apply { mkdirs() }
        // 下载前清理旧包
        cleanup(ctx)
        val dest = File(dir, "update-$versionCode.apk")
        val tmp = File(dir, "update-$versionCode.part")
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val total = resp.body?.contentLength() ?: -1L
                tmp.outputStream().use { out ->
                    val input = resp.body!!.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        yield() // 支持取消
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) return@withContext null
            dest
        }.onFailure {
            tmp.delete()
        }.getOrNull()
    }

    suspend fun sha256(f: File): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 校验 APK 包名与签名证书均与当前安装的应用一致。
     * 更新信道为 HTTP 明文, manifest 下发的 SHA-256 与 APK 同源, 挡不住中间人替换;
     * 比对签名证书摘要是最终防线, 不匹配的包直接拒绝。
     */
    fun signatureMatches(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val mine = certDigests(pm.getPackageInfo(ctx.packageName, flags))
        val archived = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        mine.isNotEmpty() && archived.packageName == ctx.packageName && mine == certDigests(archived)
    }.getOrDefault(false)

    private fun certDigests(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /**
     * 本地清理: 删除 update 目录下已安装 (versionCode <= 当前) 与无法识别的残留文件。
     * 在 App 启动和每次下载前调用。
     */
    fun cleanup(ctx: Context) {
        val dir = File(ctx.filesDir, "update") ?: return
        val files = dir.listFiles() ?: return
        files.forEach { f ->
            val vc = Regex("update-(\\d+)\\.apk$").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            when {
                vc == null -> f.delete() // .part 残留等
                vc <= BuildConfig.VERSION_CODE -> f.delete() // 已安装或过期
            }
        }
    }
}
