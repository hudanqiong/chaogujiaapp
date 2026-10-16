package com.chaogujia.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 资产分析页（AA 页）—— WebView 内嵌版（第十八轮：A 方案）
 *
 * 设计要点：
 *  1. 直接内嵌服务器版 http://121.43.178.214/aa/ （页面自带蓝条顶栏，无 App 红条）。
 *  2. 沉浸式 edge-to-edge：WebView 铺满到状态栏，蓝条背景延伸到屏幕物理顶端；
 *     状态栏图标转【白色】（蓝底配白图标）。退出时恢复原来的状态栏样式。
 *  3. 状态栏高度通过 JS 注入 CSS 变量 --sat，页面蓝条用它做 padding-top，
 *     使蓝底铺满状态栏、文字落在状态栏下方（不遮挡）。
 *  4. 去网页感：禁滚动条 / 禁回弹 / 禁长按菜单 / 禁缩放 / 禁文字选中 / 关点击高亮。
 *
 * 只负责加载与显示，关闭通过 onClose 回调（由 MainActivity 控制 showAssetAnalysis）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AssetAnalysisWebPage(onClose: () -> Unit) {
    val context = LocalContext.current

    // 状态栏高度（px），生成 JS 注入用
    val statusBarPx = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    var canGoBack by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    // ---- 沉浸式：进出页面时切换 / 恢复系统栏样式 ----
    DisposableEffect(Unit) {
        val activity = (context as? android.app.Activity)
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        // 进入：铺满 + 状态栏透明 + 白图标（蓝底要白图标）
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            @Suppress("DEPRECATION")
            it.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            it.navigationBarColor = Color.BLACK
        }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            // 退出：恢复默认（沿用项目现有风格：状态栏红 + 深色图标，导航栏浅灰）
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                @Suppress("DEPRECATION")
                it.statusBarColor = Config.COLOR_PRIMARY
                @Suppress("DEPRECATION")
                it.navigationBarColor = 0xFFF5F5F5.toInt()
            }
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    // 返回键：优先让页面回退，否则关闭本页
    BackHandler(enabled = true) {
        val wv = webRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // ---- 基础 JS / 视口 ----
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE

                    // ---- 去网页感：禁缩放 / 禁滚动条 / 禁回弹 / 禁长按 ----
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.setSupportZoom(false)
                    settings.textZoom = 100
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isLongClickable = false
                    isHapticFeedbackEnabled = false
                    setOnLongClickListener { true }   // 吃掉长按（防弹出"选择/复制"）
                    setBackgroundColor(Color.parseColor("#F2F4F7"))
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // ---- 页面跳转：内部域名留 WebView，外部跳系统浏览器 ----
                    webViewClient = object : WebViewClient() {
                        private fun handle(url: String?): Boolean {
                            if (url.isNullOrEmpty()) return false
                            val u = url.lowercase()
                            val internal = u.startsWith("http://121.43.178.214/") ||
                                u.startsWith("https://121.43.178.214/") ||
                                u.startsWith("http://121.43.178.214:") ||
                                u.startsWith("https://121.43.178.214:")
                            return if (internal) {
                                false
                            } else {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                                true
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = handle(request?.url?.toString())

                        @Deprecated("legacy")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                            handle(url)

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                            // ★ 注入状态栏高度：页面蓝条用它做 padding-top，蓝底铺满状态栏
                            view?.let { wv ->
                                val js = "document.documentElement.style.setProperty('--sat','${statusBarPx}px');"
                                wv.evaluateJavascript(js, null)
                            }
                        }
                    }

                    loadUrl(Config.AA_URL)
                    webRef = this
                }
            },
            update = { wv ->
                webRef = wv
                canGoBack = wv.canGoBack()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
