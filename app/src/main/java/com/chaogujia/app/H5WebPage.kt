package com.chaogujia.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 状态栏高度注入方式。
 *
 * H5 各页对 CSS 变量 --sat 的语义互不相同（历史遗留），必须按页选择，不能统一注入：
 *  - [NONE]          页面自绘固定 43px 假状态栏（detail.html 的 `#hbar .sbar`），
 *                    注入反而会把红条顶下去，故完全不碰。
 *  - [VAR_DP]        注入 `--sat = 物理px`，页面内部自行除以 devicePixelRatio（index.html）。
 *  - [PAD_SELECTOR]  页面会把自己的 `--sat` 重置成 0px（position.html 的 fit()），
 *                    注入变量无效 → 直接给指定选择器打 `padding-top`（内联 style 表，带 !important）。
 */
enum class H5SatMode { NONE, VAR_DP, PAD_SELECTOR }

/**
 * JS → 原生的最小桥：只暴露 close()。
 *
 * 用途：detail.html / position.html 的返回按钮走 `history.back()`，且写法是
 * `if (history.length > 1) history.back();`——在 WebView 里首屏加载时历史栈只有 1 条，
 * 点了等于没反应（页面卡死）。注入脚本会把 history.back 包一层：栈空时改调本桥关闭页面。
 */
class H5CloseBridge(private val onClose: () -> Unit) {
    @JavascriptInterface
    fun close() {
        Handler(Looper.getMainLooper()).post { onClose() }
    }
}

/**
 * 通用 H5 页面容器（第二十七轮：所有页面封装进 App）。
 *
 * 与 AssetAnalysisWebPage 的差别：
 *  1. `url` 由调用方给（各页 URL 统一放 Config.H5_*），支持同一实例切 URL 重载；
 *  2. `satMode` 按页选择状态栏适配方式（见 [H5SatMode]）；
 *  3. 外部链接**不再跳系统浏览器**（原实现是 Intent.ACTION_VIEW 外跳），
 *     统一拦截在 WebView 内 → 「所有页面都封装进 app」；
 *  4. 注入 history.back 兜底 + JS 桥，使 H5 自带返回键在首屏也能退出页面。
 *
 * 沉浸式：进入时 decorFitsSystemWindows=false + 状态栏透明 + 白色图标，
 * 退出时恢复项目默认（状态栏 Config.COLOR_PRIMARY、导航栏 #F5F5F5）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun H5WebPage(
    url: String,
    satMode: H5SatMode = H5SatMode.NONE,
    padSelector: String = "",
    // ★ 第 28 轮：导航栏区底色。详情页底部是黑色功能栏 -> 传 Black，
    //   使黑色从页面底一直铺到物理最底部；其余页面保持 White。
    navBarColor: Int = 0xFFFFFFFF.toInt(),
    // ★ 第 32 轮：通用 H5 页面内带的底部 Tab 导航（App 自己有 bottomBar，否则双底栏）。
    //   传入 CSS 选择器（多个用逗号分隔），注入 display:none !important 隐藏。
    hideSelectors: String = "",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // 用 State 包一层，保证重组后 JS 桥调到的始终是最新回调
    val currentOnClose = rememberUpdatedState(onClose)

    val dm = context.resources.displayMetrics

    // 系统状态栏高度（物理 px）
    val statusBarPx = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    // position/detail 这类页面把内容塞进固定 390px 宽的 #stage 再整体 scale，
    // 故「设计稿内的 css px」→「物理 px」的换算是：phys = css * 屏幕宽 / 390。
    // 反解：要让红条铺满状态栏，需要 padding-top = 状态栏物理高 * 390 / 屏幕宽。
    val padTopCss = remember(dm.widthPixels, statusBarPx) {
        if (dm.widthPixels > 0) Math.round(statusBarPx.toFloat() * 390f / dm.widthPixels) else 0
    }

    var canGoBack by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    // ---- 沉浸式：进页面切换系统栏样式，退出恢复 ----
    DisposableEffect(Unit) {
        val activity = (context as? android.app.Activity)
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }

        // Android 15/16 起系统忽略 navigationBarColor，导航栏透明后会露出窗口底层背景（默认黑），
        // 这里把 decorView 背景刷白兜底，退出时恢复。
        val savedDecorBg = window?.decorView?.let {
            val d = it.background
            it.setBackgroundColor(navBarColor)
            d
        }
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            @Suppress("DEPRECATION")
            it.statusBarColor = 0x00000000
            @Suppress("DEPRECATION")
            it.navigationBarColor = navBarColor
        }
        // H5 各页顶栏均为深色（详情/持仓红条、资产分析蓝条）→ 状态栏白图标
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = (navBarColor == 0xFFFFFFFF.toInt())

        onDispose {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                @Suppress("DEPRECATION")
                it.statusBarColor = Config.COLOR_PRIMARY
                @Suppress("DEPRECATION")
                it.navigationBarColor = 0xFFF5F5F5.toInt()
                if (savedDecorBg != null) it.decorView.background = savedDecorBg
            }
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    // 物理返回键：优先页面回退，栈空再关页面
    BackHandler(enabled = true) {
        val wv = webRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(UiColor.White)) {
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
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
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
                    setOnLongClickListener { true }   // 吃掉长按（防"选择/复制"）
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // H5 自带返回键在历史栈为空时 → 关页面
                    // 桥名必须是 CGJ：position.html 第 427 行自带判断
                    // `else if (window.CGJ && CGJ.close) CGJ.close();`
                    addJavascriptInterface(
                        H5CloseBridge { currentOnClose.value() },
                        "CGJ"
                    )

                    webViewClient = object : WebViewClient() {
                        /**
                         * 只允许内域；其余一律吃掉（**不再外跳系统浏览器**）。
                         * 真正的「禁用外跳」：所有页面都留在 App 的 WebView 里。
                         */
                        private fun handle(target: String?): Boolean {
                            if (target.isNullOrEmpty()) return false
                            val u = target.lowercase()
                            val internal = u.startsWith("http://121.43.178.214") ||
                                u.startsWith("https://121.43.178.214") ||
                                u.startsWith("about:blank") ||
                                u.startsWith("javascript:")
                            return !internal   // true = 拦截
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = handle(request?.url?.toString())

                        @Deprecated("legacy")
                        override fun shouldOverrideUrlLoading(view: WebView?, target: String?): Boolean =
                            handle(target)

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            canGoBack = view?.canGoBack() == true
                            view?.evaluateJavascript(
                                buildInjectJs(satMode, statusBarPx, padTopCss, padSelector, hideSelectors),
                                null
                            )
                        }
                    }

                    tag = url
                    loadUrl(url)
                    webRef = this
                }
            },
            update = { wv ->
                webRef = wv
                canGoBack = wv.canGoBack()
                // url 变化时（如同一覆盖层换股票）重载
                if (wv.tag != url) {
                    wv.tag = url
                    wv.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 生成 onPageFinished 注入脚本：状态栏适配 + 返回栈兜底。
 *
 * 只在每页注入一次（`__cgjHooked` 守卫），避免重复包装 history.back。
 */
private fun buildInjectJs(
    satMode: H5SatMode,
    statusBarPx: Int,
    padTopCss: Int,
    padSelector: String,
    hideSelectors: String
): String {
    val sb = StringBuilder()
    sb.append("(function(){")
    // ①-0 隐藏 H5 自带底部 Tab（避免与 App 原生 bottomBar 双底栏）
    if (hideSelectors.isNotEmpty()) {
        val esc = hideSelectors.replace("'", "")
        sb.append("try{var hd=document.getElementById('cgj-hide');")
        sb.append("if(!hd){hd=document.createElement('style');hd.id='cgj-hide';")
        sb.append("(document.head||document.documentElement).appendChild(hd);}")
        sb.append("hd.textContent='$esc{display:none !important}';}catch(e){}")
    }
    when (satMode) {
        H5SatMode.NONE -> Unit
        H5SatMode.VAR_DP ->
            sb.append("try{document.documentElement.style.setProperty('--sat','${statusBarPx}px');}catch(e){}")

        H5SatMode.PAD_SELECTOR ->
            if (padSelector.isNotEmpty()) {
                sb.append("try{")
                sb.append("var ex=document.getElementById('cgj-sat');")
                sb.append("if(!ex){ex=document.createElement('style');ex.id='cgj-sat';")
                sb.append("(document.head||document.documentElement).appendChild(ex);}")
                sb.append("ex.textContent='$padSelector{padding-top:${padTopCss}px !important}';")
                sb.append("}catch(e){}")
            }
    }
    // ② 页面内返回按钮接管（关键）：页面自己写的是
    //    `if (history.length > 1) history.back();`
    //    —— 首屏栈长=1 时压根不会调 history.back，所以只包 history.back 无效，
    //    必须在捕获阶段先截住点击：
    //      history.length > 1  -> 放行，走页面原生返回；
    //      history.length == 1 -> 阻断后续监听 + 关 App 页面。
    sb.append("try{")
    sb.append("var hk=function(el){if(!el||el.__cgjHk)return;el.__cgjHk=1;")
    sb.append("el.addEventListener('click',function(e){")
    sb.append("if(history.length>1)return;")
    sb.append("if(!(window.CGJ&&CGJ.close))return;")
    sb.append("try{e.preventDefault();e.stopImmediatePropagation();}catch(x){}")
    sb.append("try{CGJ.close();}catch(x){}")
    sb.append("},true);};")
    sb.append("hk(document.getElementById('btnBack'));")      // detail.html / position.html
    sb.append("hk(document.querySelector('.topbar .back'));") // index.html（原为装饰，无事件）
    sb.append("}catch(e){}")
    // ③ history.back 兜底：页面其它地方直接调 history.back() 时，栈空则关页面
    sb.append("if(!window.__cgjHooked){window.__cgjHooked=1;")
    sb.append("try{var ob=history.back.bind(history);history.back=function(){")
    sb.append("if(history.length>1){ob();}else{")
    sb.append("try{if(window.CGJ&&CGJ.close)CGJ.close();}catch(x){}}};}catch(e){}")
    sb.append("}")
    sb.append("})();")
    return sb.toString()
}
