package com.chaogujia.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChaoGuJiaApp()
        }
    }
}

private data class TabItem(
    val key: String,
    val label: String,
    val normalRes: Int,
    val checkedRes: Int
)

// ============ 轻量导航栈：详情页状态（App 级） ============
// 记录"从哪一页、哪一批股票、第几只"进入详情页；null = 未打开详情页。
// 这样红条由 MainActivity 统一渲染（全 App 唯一一条详情红条），
// 返回时弹栈回到真正的来源页（自选 / 搜索 …）。
data class DetailState(
    val from: String,              // 来源页："watch" / "search" ...
    val list: List<StockItem>,
    val index: Int
)

@Composable
fun ChaoGuJiaApp() {
    val tabs = listOf(
        TabItem("index", "首页", R.drawable.tab_home, R.drawable.tab_home_checked),
        TabItem("quote", "行情", R.drawable.tab_quote, R.drawable.tab_quote_checked),
        TabItem("watch", "自选", R.drawable.tab_watch, R.drawable.tab_watch_checked),
        TabItem("trade", "交易", R.drawable.tab_trade, R.drawable.tab_trade_checked),
        TabItem("news", "资讯", R.drawable.tab_news, R.drawable.tab_news_checked)
    )

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE) }
    // 启动默认进"行情"页
    var current by remember { mutableStateOf("quote") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var uname by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var showLogin by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var targetTab by remember { mutableStateOf("index") }
    // ★ 交易页账户类型（A股/基金/期货/黄金/模拟）：状态提升到 App 级，供顶部红条内切换按钮使用
    var tradeTab by remember { mutableStateOf("A股") }

    // ============ 轻量导航栈：详情页状态提升到 App 级 ============
    var detail by remember { mutableStateOf<DetailState?>(null) }

    // ★ 详情页专属：开启 edge-to-edge，使 Compose 能绘制到系统导航栏区域，
    //   从而让底部黑底（Spacer(navInset)）铺满到物理最底。
    //   退出详情页立即恢复；其他页面完全不受影响。
    val view = LocalView.current
    DisposableEffect(detail) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val ctrl = WindowCompat.getInsetsController(window, view)
            if (detail != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                // 状态栏透明：让详情页顶部红条自身铺满状态栏区域，避免“系统红 + 内容红”两截色
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.BLACK
                ctrl.isAppearanceLightStatusBars = false
                ctrl.isAppearanceLightNavigationBars = false
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.statusBarColor = android.graphics.Color.parseColor("#F03036")
                window.navigationBarColor = android.graphics.Color.parseColor("#F5F5F5")
                ctrl.isAppearanceLightStatusBars = false
                ctrl.isAppearanceLightNavigationBars = true
            }
        }
        onDispose { }
    }
    // 详情页当前下标（受控；◀▶ 切换时更新）
    var detailIdx by remember { mutableStateOf(0) }
    // 详情页当前显示的名称（由 StockDetailPage 回吐，供红条显示）
    var detailName by remember { mutableStateOf("") }

    // ★ 返回键处理：
    //   1) 详情页打开 → 关详情（回到来源页）
    //   2) 搜索页打开 → 关搜索
    //   3) 登录页打开 → 关登录
    //   4) 其余（Tab 页）→ 连按两下才退出（2 秒内）
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        when {
            detail != null -> {
                val from = detail?.from
                detail = null
                detailName = ""
                if (from != null) current = from
            }
            showSearch -> showSearch = false
            showLogin -> showLogin = false
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    (ctx as? android.app.Activity)?.finish()
                } else {
                    lastBackAt = now
                    android.widget.Toast.makeText(
                        ctx,
                        "再按一次退出应用",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 打开详情页：记录来源页 + 列表 + 下标，形成"栈帧"
    fun openDetail(from: String, list: List<StockItem>, index: Int) {
        detail = DetailState(from = from, list = list, index = index)
        detailIdx = index
        detailName = if (index in list.indices) list[index].name else ""
    }
    // 关闭详情页 = 返回上一步：弹栈，回到 from 指定的来源页
    fun closeDetail() {
        val from = detail?.from
        detail = null
        detailName = ""
        if (from != null) current = from
    }

    // 会员信息（5 页共用，由 MainActivity 统一拉）
    var member by remember { mutableStateOf(MemberInfo()) }

    // token 变化时重新拉会员信息（登录后立刻刷新）
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            try {
                member = withContext(Dispatchers.IO) { Api.member(token) }
            } catch (e: Exception) {
                member = MemberInfo()
            }
        } else {
            member = MemberInfo()
        }
    }

    // 退出登录
    fun doLogout() {
        token = ""
        uname = ""
        prefs.edit().remove("token").remove("username").apply()
        current = "quote"
        member = MemberInfo()
    }

    // ★ 最外层 Box：让全屏搜索页能盖住红条 + 底部导航（铺满整个屏幕）
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            // 红色会员顶栏：5 个 Tab 页面统一渲染，高度一致。
            // ★ 详情页打开时，红条切换为 detail 模式（全 App 唯一一条详情红条），
            //   内容 = [←返回] ◀名称/代码▶ [图二][图三]，不再出现第二条红条。
            val d = detail
            if (d != null) {
                MemberHeaderBar(
                    current = "detail",
                    username = uname,
                    isMember = member.isMember,
                    levelName = member.levelName,
                    expireAt = member.expireAt,
                    onOpenSearch = { showSearch = true },
                    detailName = detailName,
                    detailCode = if (detailIdx in d.list.indices) d.list[detailIdx].code else "",
                    detailCanPrev = detailIdx > 0,
                    detailCanNext = detailIdx < d.list.size - 1,
                    onDetailBack = { closeDetail() },
                    onDetailPrev = { if (detailIdx > 0) detailIdx-- },
                    onDetailNext = { if (detailIdx < d.list.size - 1) detailIdx++ }
                )
            } else {
                MemberHeaderBar(
                    current = current,
                    username = uname,
                    isMember = member.isMember,
                    levelName = member.levelName,
                    expireAt = member.expireAt,
                    onOpenSearch = { showSearch = true },   // ★ 点自选页红条最右图标 → 全屏搜索页
                    tradeTab = tradeTab,
                    onTradeTabSelect = { tradeTab = it }
                )
            }
        },
        bottomBar = {
            // 个股详情页打开时隐藏底部导航栏
            if (detail == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    // 普通页 decorFits=true，系统已将 Scaffold 裁到导航栏之上；
                    // 若此处再加 navigationBarsPadding() 会双重避让，导致从详情页返回后底栏被顶高
            ) {
                tabs.forEach { t ->
                    val selected = current == t.key
                    val color = if (selected) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_MAIN)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (t.key != "quote" && token.isEmpty()) {
                                        targetTab = t.key
                                        showLogin = true
                                    } else {
                                        current = t.key
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TabIcon(selected, t.normalRes, t.checkedRes)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t.label,
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            }
        }
    ) { padding ->
        // ★ 详情页需要把黑色功能栏铺到物理最底：
        //   Scaffold 的 padding 默认已让出底部导航栏 inset；若详情页仍消费底部 inset，
        //   StockDetailPage 底边会停在按键区上方，黑底无法铺到底。
        //   故详情页时只消费顶部 inset（底部归零），其余页面保持原样。
        val contentModifier = if (detail != null) {
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        } else {
            Modifier
                .fillMaxSize()
                .padding(padding)
        }
        Box(
            modifier = contentModifier.background(Color.White)
        ) {
            when (current) {
                "index" -> HomeScreen(
                    token = token,
                    username = uname,
                    onLogout = { doLogout() },
                    onMemberChanged = { member = it }
                )
                "quote" -> FontComparePage()
                // 自选页：红条正下方紧贴两排工具条；下方是白色内容区。
                // 点 ▼ 展开的「指数详情面板」悬浮在内容区之上（不挤压内容区高度）。
                "watch" -> Box(Modifier.fillMaxSize().background(Color.White)) {
                    // 纯白背景层（红条与分割线由 IndexBar 内部绘制，详情面板 overlay 悬浮其上）
                    // 叠在最上方：顶部工具条 + 分割线 + （展开时）详情面板
                    IndexBar(
                        token = token,
                        onOpenNews = { current = "news" },
                        // ★ 点股票 → 状态提升到 App 级，由 MainActivity 统一打开详情页
                        onOpenDetail = { list, index -> openDetail("watch", list, index) }
                    )
                }
                // ★ 交易页（持仓）：需登录（无 token 时底部导航已统一拦截并弹登录）
                "trade" -> TradePage(
                    token = token,
                    subTab = tradeTab,
                    onSubTabChange = { tradeTab = it },
                    onOpenDetail = { list, index -> openDetail("trade", list, index) }
                )
                "news" -> EmptyScreen()
            }

            // ★ 详情页：App 级覆盖层（位于红条下方的内容区）。
            //   红条已由 MainActivity 的 detail 模式渲染，这里不再自绘红条。
            val d = detail
            if (d != null) {
                StockDetailPage(
                    token = token,
                    list = d.list,
                    index = detailIdx,
                    showOwnHeader = false,
                    onIndexChange = { detailIdx = it },
                    onNameChange = { detailName = it },
                    onClose = { closeDetail() },
                    onOpenSearch = { showSearch = true }
                )
            }

            if (showLogin) {
                LoginScreen(
                    onSuccess = { tk, un ->
                        token = tk
                        uname = un
                        prefs.edit().putString("token", tk).putString("username", un).apply()
                        showLogin = false
                        current = targetTab
                    },
                    onClose = { showLogin = false }
                )
            }
        }
    }

        // ★ 全屏搜索页：铺满屏幕，盖住红条 + 底部导航（从状态栏往下）
        if (showSearch) {
            SearchPage(
                token = token,
                onClose = { showSearch = false },
                // ★ 搜索页点股票 → 状态提升到 App 级；返回时先关搜索页再回来源页
                onOpenDetail = { list, index -> openDetail("search", list, index) }
            )
        }
    }
}
// 页面内容区：暂时留空，等后续接入真实接口后再填充
@Composable
fun EmptyScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    )
}

// 底部导航图标
@Composable
fun TabIcon(selected: Boolean, normalRes: Int, checkedRes: Int) {
    Image(
        painter = painterResource(if (selected) checkedRes else normalRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        contentScale = ContentScale.Fit
    )
}