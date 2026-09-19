@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.aimonitor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimonitor.app.data.AppSettings
import com.aimonitor.app.data.AppLog
import com.aimonitor.app.data.MonitorService
import com.aimonitor.app.ui.AddEditAccountScreen
import com.aimonitor.app.ui.HelpScreen
import com.aimonitor.app.ui.ChangelogScreen
import com.aimonitor.app.ui.HomeScreen
import com.aimonitor.app.ui.LogScreen
import com.aimonitor.app.ui.MainViewModel
import com.aimonitor.app.ui.SettingsScreen
import com.aimonitor.app.ui.decodeBg
import com.aimonitor.app.ui.theme.AIMonitorTheme
import com.aimonitor.app.ui.theme.ImageSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Screen {
    abstract val rank: Int

    object Home : Screen() { override val rank = 0 }
    data class Edit(val accountId: Long?) : Screen() { override val rank = 2 }
    object Logs : Screen() { override val rank = 1 }
    object Changelog : Screen() { override val rank = 1 }
    object Help : Screen() { override val rank = 1 }
    object Settings : Screen() { override val rank = 1 }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(applicationContext)
        AppLog.i("APP", "启动 v${BuildConfig.VERSION_NAME}")
        setContent {
            val ctx = LocalContext.current
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
            var skinId by remember { mutableStateOf(AppSettings.loadSkinId(ctx)) }
            var bgPath by remember { mutableStateOf(AppSettings.loadBgPath(ctx)) }
            // 异步解码: 大图解码不再阻塞首帧
            val bgBitmap by produceState<ImageBitmap?>(null, bgPath) {
                value = withContext(Dispatchers.IO) { decodeBg(bgPath) }
            }
            val skin = remember(skinId) { ImageSkin.resolve(ctx, skinId) }
            val vm: MainViewModel = viewModel()
            // Android 13+ 通知权限 (通知栏常驻展示)
            val notifPerm = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) vm.updateNotification() }
            DisposableEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    AppSettings.loadNotifEnabled(ctx)
                ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                onDispose { }
            }
            AIMonitorTheme(skin = skin, bgBitmap = bgBitmap) {
                // 仅前台: 进入/回前台刷新过期数据并启动定时刷新; 退后台立即停止
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                MonitorService.appVisible = true
                                MonitorService.start(ctx)
                                vm.refreshIfStale()
                                vm.startPeriodicRefresh()
                            }
                            Lifecycle.Event.ON_PAUSE -> {
                                MonitorService.appVisible = false
                                vm.stopPeriodicRefresh()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        vm.stopPeriodicRefresh()
                    }
                }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                // 页面转场: 深层页右滑入, 返回反向; 背景图在主题层不参与滑动
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val dir = if (targetState.rank >= initialState.rank) 1 else -1
                        ContentTransform(
                            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 4 } +
                                fadeIn(tween(200)),
                            slideOutHorizontally(tween(240)) { -dir * it / 6 } + fadeOut(tween(160))
                        )
                    },
                    label = "screen"
                ) { s ->
                    when (s) {
                        is Screen.Home -> HomeScreen(
                            vm = vm,
                            onAdd = { screen = Screen.Edit(null) },
                            onEdit = { screen = Screen.Edit(it) },
                            onLogs = { screen = Screen.Logs },
                            onChangelog = { screen = Screen.Changelog },
                            onSettings = { screen = Screen.Settings },
                            onHelp = { screen = Screen.Help }
                        )
                        is Screen.Edit -> AddEditAccountScreen(
                            vm = vm,
                            accountId = s.accountId,
                            onDone = { screen = Screen.Home }
                        )
                        is Screen.Logs -> LogScreen(onBack = { screen = Screen.Home })
                        is Screen.Changelog -> ChangelogScreen(onBack = { screen = Screen.Home })
                        is Screen.Help -> HelpScreen(onBack = { screen = Screen.Home })
                        is Screen.Settings -> SettingsScreen(
                            vm = vm,
                            onBack = { screen = Screen.Home },
                            skinId = skinId,
                            onSkinChange = {
                                skinId = it
                                AppSettings.saveSkinId(ctx, it)
                            },
                            bgPath = bgPath,
                            onBgChange = { p ->
                                bgPath = p
                                AppSettings.saveBgPath(ctx, p)
                            }
                        )
                    }
                }
            }
        }
    }
}
