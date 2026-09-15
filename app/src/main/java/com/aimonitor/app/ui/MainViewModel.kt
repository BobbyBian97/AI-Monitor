package com.aimonitor.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.AccountStore
import com.aimonitor.app.data.AppLog
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.BalanceNotifier
import com.aimonitor.app.data.BalanceRepository
import com.aimonitor.app.data.BalanceResult
import com.aimonitor.app.data.ResultCache
import com.aimonitor.app.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)
    private val cache = ResultCache(app)

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _results = MutableStateFlow<Map<Long, BalanceResult>>(emptyMap())
    val results: StateFlow<Map<Long, BalanceResult>> = _results.asStateFlow()

    /** 全局刷新中 (顶栏 spinner / 下拉指示器共用) */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var lastRefreshAt = 0L

    init {
        _accounts.value = store.load()
        // 立即恢复上次结果, 避免重启后整页「尚未刷新」
        _results.value = cache.load()
        updateNotification()
        // 启动清理: 删除已安装/残留的更新 APK
        viewModelScope.launch(Dispatchers.IO) { Updater.cleanup(getApplication()) }
        refreshAll()
    }

    /** 通知栏常驻展示 (刷新后调用; 设置开关切换时也调用) */
    fun updateNotification() {
        BalanceNotifier.update(getApplication(), _accounts.value, _results.value)
    }

    fun refreshAll() {
        val list = _accounts.value
        if (list.isEmpty() || _refreshing.value) return
        _refreshing.value = true
        lastRefreshAt = System.currentTimeMillis()
        // 已有结果的账户保留旧数据 (避免整页闪烁成 Loading), 只给无结果的置 Loading
        _results.update { map ->
            map.toMutableMap().apply {
                list.forEach { if (map[it.id] == null) put(it.id, BalanceResult.Loading) }
            }
        }
        viewModelScope.launch {
            try {
                list.map { account ->
                    async {
                        val r = BalanceRepository.fetch(account)
                        _results.update { it + (account.id to r) }
                    }
                }.awaitAll()
            } finally {
                _refreshing.value = false
                cache.save(_results.value)
                updateNotification()
            }
        }
    }

    fun refreshOne(id: Long) {
        val account = _accounts.value.firstOrNull { it.id == id } ?: return
        _results.update { it + (id to BalanceResult.Loading) }
        viewModelScope.launch {
            try {
                val r = BalanceRepository.fetch(account)
                _results.update { it + (id to r) }
            } finally {
                cache.save(_results.value)
                updateNotification()
            }
        }
    }

    /** 回到前台且数据超过 maxAge 时自动静默刷新 */
    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000) {
        if (System.currentTimeMillis() - lastRefreshAt > maxAgeMs) refreshAll()
    }

    private var periodicJob: Job? = null
    private var periodicWanted = false

    /** 定时刷新间隔 (全局设置, 默认 30 秒) */
    private val _refreshIntervalMs = MutableStateFlow(AppSettings.loadRefreshInterval(app))
    val refreshIntervalMs: StateFlow<Long> = _refreshIntervalMs.asStateFlow()

    /** 前台定时刷新: 每隔设定间隔静默刷新一次, 仅在 App 可见期间运行 */
    fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        periodicWanted = true
        periodicJob = viewModelScope.launch {
            while (isActive) {
                delay(_refreshIntervalMs.value)
                if (!_refreshing.value) {
                    AppLog.i("APP", "定时刷新触发")
                    refreshAll()
                }
            }
        }
    }

    /** 退后台立即停止定时刷新 */
    fun stopPeriodicRefresh() {
        periodicJob?.cancel()
        periodicJob = null
        periodicWanted = false
    }

    /** 全局设置刷新间隔, 前台运行中立即按新间隔重排 */
    fun setRefreshInterval(ms: Long) {
        if (ms == _refreshIntervalMs.value) return
        _refreshIntervalMs.value = ms
        AppSettings.saveRefreshInterval(getApplication(), ms)
        AppLog.i("APP", "刷新间隔设为 ${AppSettings.intervalLabel(ms)}")
        if (periodicWanted) startPeriodicRefresh()
    }

    fun nextId(): Long = store.nextId(_accounts.value)

    fun saveAccount(account: Account) {
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.id == account.id }
        if (idx >= 0) list[idx] = account else list.add(account)
        _accounts.value = list
        store.save(list)
        refreshOne(account.id)
    }

    fun deleteAccount(id: Long) {
        _accounts.value = _accounts.value.filterNot { it.id == id }
        store.save(_accounts.value)
        _results.update { it - id }
        cache.save(_results.value)
        updateNotification()
    }

    /** 拖动排序: 把账户移动到目标位置并持久化 */
    fun moveAccount(id: Long, toIndex: Int) {
        val list = _accounts.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0 || from == toIndex) return
        val item = list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), item)
        _accounts.value = list
        store.save(list)
        updateNotification()
    }
}
