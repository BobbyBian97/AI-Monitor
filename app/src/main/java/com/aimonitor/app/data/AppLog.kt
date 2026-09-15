package com.aimonitor.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内调试日志: 内存环形缓冲 + 文件持久化 (最近 [MAX] 条, 重启保留)。
 * 密钥只经 [fp] 记指纹, 绝不落明文。
 */
object AppLog {
    const val MAX = 500

    data class Entry(val time: Long, val level: String, val tag: String, val msg: String)

    private val buffer = ArrayDeque<Entry>(MAX)
    private var file: File? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dumpFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    /** 首次调用时加载历史并压缩日志文件 */
    fun init(ctx: Context) {
        synchronized(buffer) {
            if (file != null) return
            file = File(ctx.filesDir, "applog.txt")
            runCatching {
                file!!.readLines().takeLast(MAX).forEach { line -> parse(line)?.let(buffer::addLast) }
                // 压缩: 只保留缓冲内的行
                file!!.writeText(buffer.joinToString("\n", postfix = "\n") { "${it.time}\t${it.level}\t${it.tag}\t${it.msg}" })
            }
        }
    }

    fun i(tag: String, msg: String) = append("I", tag, msg)
    fun e(tag: String, msg: String) = append("E", tag, msg)

    private fun append(level: String, tag: String, msg: String) {
        val e = Entry(System.currentTimeMillis(), level, tag, msg.replace('\n', ' '))
        synchronized(buffer) {
            buffer.addLast(e)
            while (buffer.size > MAX) buffer.removeFirst()
            runCatching { file?.appendText("${e.time}\t$level\t$tag\t${e.msg}\n") }
        }
    }

    private fun parse(line: String): Entry? {
        val p = line.split('\t', limit = 4)
        if (p.size != 4) return null
        val t = p[0].toLongOrNull() ?: return null
        return Entry(t, p[1], p[2], p[3])
    }

    /** 新的在前 */
    fun entries(): List<Entry> = synchronized(buffer) { buffer.toList().asReversed() }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            runCatching { file?.delete() }
        }
    }

    fun timeLabel(t: Long): String = timeFmt.format(Date(t))

    /** 导出为可分享文本 */
    fun dump(): String = entries().joinToString("\n") {
        "${dumpFmt.format(Date(it.time))} ${it.level}/${it.tag}: ${it.msg}"
    }

    /** 密钥指纹: 前4…后4(长度) */
    fun fp(key: String): String =
        if (key.length <= 8) "(${key.length}字符)" else "${key.take(4)}…${key.takeLast(4)}(${key.length})"
}
