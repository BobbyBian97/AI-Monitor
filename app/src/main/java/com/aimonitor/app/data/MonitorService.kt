package com.aimonitor.app.data

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务: App 退到后台/关闭后, 仍按设定间隔刷新余额并更新通知栏,
 * 与应用内定时刷新共用同一间隔 ([AppSettings]); App 在前台时让位给
 * MainViewModel 的定时刷新, 避免重复请求。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: AccountStore
    private lateinit var cache: ResultCache
    private var loopJob: Job? = null

    /** 存储加载完成门闩: 刷新前必须等待, 防止 lateinit 未初始化 */
    private val ready = CompletableDeferred<Unit>()

    companion object {
        /** 通知栏「刷新」按钮触发的动作: 立即重拉一次而非等待定时间隔 */
        const val ACTION_REFRESH = "com.aimonitor.app.action.REFRESH"

        /** App 是否处于前台 (MainActivity ON_RESUME/ON_PAUSE 维护) */
        @Volatile var appVisible = false

        /** 服务是否已在运行: 避免每次 ON_RESUME 重复 startForegroundService 重载存储 */
        @Volatile var running = false

        /** 开启后台刷新 (随通知栏开关); 已在运行或设置关闭时不启动 */
        fun start(ctx: Context) {
            if (running) return
            if (!AppSettings.loadNotifEnabled(ctx)) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        // 立即以轻量占位通知进入前台 (startForeground 5 秒时限),
        // 加密存储与缓存加载在 IO 线程完成后替换为真实通知
        startForeground(BalanceNotifier.NOTIF_ID, BalanceNotifier.placeholder(this))
        scope.launch {
            store = AccountStore(this@MonitorService)
            cache = ResultCache(this@MonitorService)
            ready.complete(Unit)
            val accounts = store.load()
            val n = BalanceNotifier.build(this@MonitorService, accounts, cache.load())
            if (n == null) {
                stopSelf()
            } else {
                NotificationManagerCompat.from(this@MonitorService)
                    .notify(BalanceNotifier.NOTIF_ID, n)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            // 通知栏手动刷新: 不受 appVisible 限制, 立即重拉一次 (不重启定时循环)
            AppLog.i("APP", "通知栏手动刷新")
            scope.launch { refreshOnce() }
        } else {
            loop()
        }
        return START_STICKY
    }

    private fun loop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                delay(AppSettings.loadRefreshInterval(this@MonitorService))
                if (appVisible) continue // 前台由 MainViewModel 定时刷新负责
                refreshOnce()
            }
        }
    }

    private suspend fun refreshOnce() {
        ready.await()
        val accounts = store.load()
        if (accounts.isEmpty()) return
        val results = coroutineScope {
            accounts.map { a -> async { a.id to BalanceRepository.fetch(a) } }.awaitAll().toMap()
        }
        cache.save(results)
        BalanceNotifier.update(this, accounts, results)
        AppLog.i("APP", "后台刷新 ${accounts.size} 个账户")
        // 后台刷新完成后主动推送磁贴: ACTIVE_TILE 模式下系统回调 onStartListening 重读新缓存
        try {
            TileService.requestListeningState(
                this, ComponentName(this, BalanceTileService::class.java)
            )
        } catch (e: Exception) {
            AppLog.e("APP", "磁贴刷新请求失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
