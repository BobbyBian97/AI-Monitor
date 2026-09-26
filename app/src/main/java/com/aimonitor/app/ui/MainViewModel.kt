package com.aimonitor.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.AccountStore
import com.aimonitor.app.data.AppLog
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.BalanceAlerter
import com.aimonitor.app.data.BalanceNotifier
import com.aimonitor.app.data.BalanceRepository
import com.aimonitor.app.data.BalanceResult
import com.aimonitor.app.data.BalanceWidget
import com.aimonitor.app.data.ResultCache
import com.aimonitor.app.data.Updater
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // 加密存储创建与加载在 IO 线程完成 (见 init);
    // 完成前所有写持久化操作经 [storeReady] 排队, 防止用空列表覆盖账户
    private val storeReady = CompletableDeferred<Unit>()
    private lateinit var store: AccountStore
    private lateinit var cache: ResultCache

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _results = MutableStateFlow<Map<Long, BalanceResult>>(emptyMap())
    val results: StateFlow<Map<Long, BalanceResult>> = _results.asStateFlow()

    /** 全局刷新中 (顶栏 spinner / 下拉指示器共用) */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var lastRefreshAt = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val appContext = getApplication<Application>()
            store = AccountStore(appContext)
            cache = ResultCache(appContext)
            val accounts = store.load()
            // 立即恢复上次结果, 避免重启后整页「尚未刷新」
            val results = cache.load()
            storeReady.complete(Unit)
            withContext(Dispatchers.Main) {
                _accounts.value = accounts
                _results.value = results
            }
            updateNotification()
            // 启动清理: 删除已安装/残留的更新 APK
            Updater.cleanup(appContext)
            refreshAll()
        }
    }

    /** 通知栏常驻展示与桌面小部件 (刷新后调用; 设置开关切换时也调用); 构建移 IO 线程 */
    fun updateNotification() {
        val app = getApplication<Application>()
        val accounts = _accounts.value
        val results = _results.value
        viewModelScope.launch(Dispatchers.IO) {
            BalanceNotifier.update(app, accounts, results)
            // 低余量预警评估: 此函数是 init/refreshAll/refreshOne/deleteAccount 的公共漏斗
            BalanceAlerter.evaluate(app, accounts, results)
            BalanceWidget.push(app, accounts, results)
        }
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
                persistResults()
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
                persistResults()
                updateNotification()
            }
        }
    }

    /** 结果缓存写盘 (IO 线程) */
    private fun persistResults() {
        val snapshot = _results.value
        viewModelScope.launch(Dispatchers.IO) { cache.save(snapshot) }
    }

    /** 账户列表写盘 (IO 线程; 等待初始加载完成, 防止用空列表覆盖) */
    private fun persistAccounts(list: List<Account>) {
        viewModelScope.launch(Dispatchers.IO) {
            storeReady.await()
            store.save(list)
        }
    }

    /** 回到前台且数据超过 maxAge 时自动静默刷新 */
    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000) {
        if (System.currentTimeMillis() - lastRefreshAt > maxAgeMs) refreshAll()
    }

    private var periodicJob: Job? = null
    private var periodicWanted = false

    /** 定时刷新间隔 (全局设置, 默认 1 分钟) */
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

    fun nextId(): Long = (_accounts.value.maxOfOrNull { it.id } ?: 0L) + 1L

    fun saveAccount(account: Account) {
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.id == account.id }
        if (idx >= 0) list[idx] = account else list.add(account)
        _accounts.value = list
        persistAccounts(list)
        refreshOne(account.id)
    }

    fun deleteAccount(id: Long) {
        _accounts.value = _accounts.value.filterNot { it.id == id }
        persistAccounts(_accounts.value)
        _results.update { it - id }
        persistResults()
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
        persistAccounts(list)
        updateNotification()
    }
}
