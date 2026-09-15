package com.aimonitor.app.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
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

    companion object {
        /** 通知栏「刷新」按钮触发的动作: 立即重拉一次而非等待定时间隔 */
        const val ACTION_REFRESH = "com.aimonitor.app.action.REFRESH"

        /** App 是否处于前台 (MainActivity ON_RESUME/ON_PAUSE 维护) */
        @Volatile var appVisible = false

        /** 开启后台刷新 (随通知栏开关); 设置关闭时不启动 */
        fun start(ctx: Context) {
            if (!AppSettings.loadNotifEnabled(ctx)) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = AccountStore(this)
        cache = ResultCache(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即进入前台: 先用缓存数据构建常驻通知 (5 秒时限内必须 startForeground)
        val accounts = store.load()
        val n = BalanceNotifier.build(this, accounts, cache.load())
        if (n == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(BalanceNotifier.NOTIF_ID, n)
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
        val accounts = store.load()
        if (accounts.isEmpty()) return
        val results = coroutineScope {
            accounts.map { a -> async { a.id to BalanceRepository.fetch(a) } }.awaitAll().toMap()
        }
        cache.save(results)
        BalanceNotifier.update(this, accounts, results)
        AppLog.i("APP", "后台刷新 ${accounts.size} 个账户")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
