package com.chaogujia.app
import android.util.Log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

/**
 * 自选页 —— 红条正下方的「指数工具条」。
 *
 * 结构（用户定稿）：
 *   第一排：点数（大粗红） + 涨跌点（小红）
 *   第二排：名称（黑） + 涨跌幅%（红） + ▼（下拉三角）
 *   点 ▼ → 从红条底部向下弹出「指数详情面板」：
 *       - 指数名 / 点数 / 涨跌点 / 涨跌幅 / 成交额
 *       - 当天大分时走势曲线（09:30 - 11:30 - 15:00）
 *       - 成交量 / 成交额 / 涨家数 / 跌家数
 *       - 最右侧一竖列「指数快捷方块」（首字 + 实时涨跌幅%，可上下滑，点击切换）
 */

/** 涨跌颜色：>=0 红，<0 绿。统一引用 StockDetailPage 的全局唯一定义（改那里即全站生效） */
private val COLOR_UP = RED_UP       // 红（涨）
private val COLOR_DOWN = GREEN_DOWN // 绿（跌）
private val COLOR_FLAT = Color(0xFF000000)  // 平——纯黑
private val COLOR_TEXT = Color(0xFF222222)
private val COLOR_SUB = Color(0xFF888888)

private fun colorOf(pct: Double): Color =
    if (pct > 0.000001) COLOR_UP else if (pct < -0.000001) COLOR_DOWN else COLOR_FLAT

/**
 * 是否处于「盘前数据重置时段」：每个交易日 08:45 – 09:15。
 * 此期间自选列表所有股票统一显示为平盘态：
 *   - 涨跌幅色块 → 黑底 + "--"
 *   - 最新价     → 黑字 + "--"
 *   - 分时图     → 中间一条平虚线（无曲线）
 *
 * 时段规则（A 股）：
 *   08:45 – 09:15  重置时段（本函数返回 true）
 *   09:15 – 09:25  集合竞价（返回 false）→ 正常更新真实数据；
 *                  此期间上游分时可能只返回 1 个点，由 MiniMinuteChart 单独绘制
 *   09:25 – 09:30  静默 5 分钟（返回 false）→ 不做特殊处理，保持现状
 *   09:30 起       正式开盘（返回 false）→ 连续竞价，走势图自然铺开
 *
 * 注意：时间基准使用 [Net.serverNow]（服务器时间校正），避免手机时间不准导致误判。
 */
private fun isPreOpenReset(): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = Net.serverNow()
    // 仅周一~周五（1=周日 … 7=周六）
    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
    if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) return false
    val hm = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    return hm in (8 * 60 + 45) until (9 * 60 + 15)   // 525 ~ 554
}

/** 行情数字字体开关（已定稿 = Lato）：
 *  - 选 Lato 的原因：6/9 尾巴长度最接近同花顺（经 4 批对比页实机挑选）
 *  - 想换别的：改成 FontFamily(Font(R.font.xxx)) 即可
 */
// 行情数字字体（同花顺 THS 金融体，从「趣股道」APK 扒出）
// 想换别的：改动下面的 R.font.xxx 即可，或换 ths_money / din_medium
private val NumFont = FontFamily(Font(R.font.ths_regular))

@Composable
fun IndexBar(
    token: String? = null,
    // 会员权限（自选页门禁，第19轮接入）
    perm: PermissionInfo = PermissionInfo(),
    onOpenNews: () -> Unit = {},
    // ★ 点股票 → 状态提升到 App 级，由 MainActivity 统一打开详情页（红条也由 MainActivity 渲染）
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit = { _, _ -> },
    // 会员引导「立即开通」→ App 级打开「开通/续费会员」页
    onOpenVipPage: () -> Unit = {}
) {
    // 顶部当前选中指数
    var secid by remember { mutableStateOf("1.000001") }
    var quote by remember { mutableStateOf<IndexQuote?>(null) }
    // 指数清单（右侧方块用）
    var list by remember { mutableStateOf<List<IndexItemInfo>>(emptyList()) }
    // 清单每个指数的实时行情（code -> 涨跌幅%），用于右侧方块显示
    var boxQuotes by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // 是否展开详情面板
    var expanded by remember { mutableStateOf(false) }

    // 「多股同列」全屏页是否显示
    var showColumns by remember { mutableStateOf(false) }

    // 「编辑表头」列勾选框是否显示
    var showColEditor by remember { mutableStateOf(false) }

    // ===== 自选分组标签 =====
    // 当前选中的分组 key：负数为内置组（-1 自选股 / -2 持仓股），>=0 为用户自建组 id
    var selectedGroup by remember { mutableStateOf(-1) }
    // ===== 持仓股分组的「账户切换器」状态 =====
    // 券商账户列表（brokerList 结果，用于切换器下拉）
    var brokerAccounts by remember { mutableStateOf<List<MyBroker>>(emptyList()) }
    // 当前选中的券商账户 id（0=未选定，自动取首个）
    var selectedBrokerId by remember { mutableStateOf(0) }
    // 账户下拉菜单是否展开
    var showBrokerMenu by remember { mutableStateOf(false) }
    // ===== 会员权限门禁（自选页，第19轮）=====
    // 无权限时弹出的引导标题（null=不弹）；非空即展示 MemberGateDialog
    var gateTitle by remember { mutableStateOf<String?>(null) }
    /** 受限功能统一入口：有权限执行 action，无权限弹会员引导 */
    fun guard(feat: String, title: String, action: () -> Unit) {
        if (perm.has(feat)) action() else gateTitle = title
    }
    // 用户自建分组（来自后端 watch_group.php）
    var userGroups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    // 当前分组下的股票列表
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    // 列表是否加载中
    var loadingStocks by remember { mutableStateOf(false) }

    // 自选列表分时走势缓存： code -> (trends, preClose)
    var watchTrendMap by remember { mutableStateOf<Map<String, Pair<List<DetailTrend>, Double>>>(emptyMap()) }
    // 持仓代码集合：用于给「属于持仓」的股票名统一上紫色（与当前分组无关）
    var holdingCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 记录上一次已预热的持仓代码集合，仅在集合变化时触发一次 detail 预热（避免每 3 秒重复打）
    var lastWarmCodes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 时间滴答：每 20 秒触发一次重组，保证「08:45–09:15 重置时段」到点自动切换
    // （服务器时间偏移会在每次网络请求后更新，这里只需推动重组即可）
    var clockTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTick = Net.serverNow()
            delay(20000)
        }
    }

    // ===== 股票列表「列配置」=====
    // 当前显示哪些统计列（默认：最新价 + 涨跌幅；以后可由用户自定义）
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var colKeys by remember { mutableStateOf(WatchColumnPrefs.load(ctx)) }
    // 方案 A：启动拉后端列元数据 -> 动态建列 -> 重建当前显示列
    var dynFields by remember { mutableStateOf<List<WatchField>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val fs = withContext(Dispatchers.IO) { Api.fields() }
            if (fs.isNotEmpty()) {
                WatchColumnRegistry.buildFrom(fs)
                dynFields = fs
                // 老配置(无动态列时保存的)若仍有效则保留，否则用后端默认列
                val fixed = WatchColumnPrefs.load(ctx).filter { WatchColumnRegistry.get(it) != null }
                if (fixed.isEmpty()) {
                    val dk = WatchColumnRegistry.defaultKeys(fs)
                    colKeys = dk
                    WatchColumnPrefs.save(ctx, dk)
                } else {
                    colKeys = fixed
                }
            }
        } catch (e: Exception) { /* 拉不到则沿用内置列，不影响使用 */ }
    }
    val activeCols = remember(colKeys, dynFields) { WatchColumnRegistry.resolveDyn(colKeys) }
    // 点列表行进详情页：状态已提升到 MainActivity（App 级），这里不再自持 detailIdx


    // 拉一次分组列表
    LaunchedEffect(token) {
        try {
            val g = withContext(Dispatchers.IO) { Api.watchGroups(token) }
            if (g.isNotEmpty()) userGroups = g.sortedBy { it.sort }
        } catch (e: Exception) { /* 忽略 */ }
    }
    // 拉一次券商账户列表（持仓股分组「账户切换器」用）
    // 若尚未选定账户（selectedBrokerId<=0）或原选中账户已不在列表中，则自动选中首个账户
    LaunchedEffect(token) {
        try {
            val bs = withContext(Dispatchers.IO) { Api.brokerList(token) }
            brokerAccounts = bs
            if (bs.isNotEmpty() && bs.none { it.id == selectedBrokerId }) {
                selectedBrokerId = AccountStore.pick(ctx, bs) { it.id }?.id ?: bs.first().id
            }
        } catch (e: Exception) { /* 忽略 */ }
    }
    // 持仓代码集合：每 3 秒刷新一次（进入持仓股分组时也复用同一集合）
    // 同时在后台对持仓股明细做「预热」：detail.php 冷缓存约 4.3s、热缓存 0.2~0.5s，
    // 这里提前并发打一遍 detail，把后端缓存捂热，用户点持仓分组/进详情页时即为热数据。
    LaunchedEffect(token) {
        while (true) {
            try {
                val sets = withContext(Dispatchers.IO) {
                    val brokers = Api.brokerList(token)
                    val s = mutableSetOf<String>()
                    for (b in brokers) {
                        try {
                            for (p in Api.positions(token, b.id)) {
                                if (p.quantity > 0 && p.code.isNotBlank()) s.add(p.code)
                            }
                        } catch (e: Exception) { /* 忽略该券商 */ }
                    }
                    s
                }
                holdingCodes = sets
                // 后台预热：仅在持仓代码集合发生变化时并发请求一遍 detail，
                // 不阻塞本循环、不影响 UI，失败静默忽略（结果丢弃，只为捂热后端缓存）。
                if (sets.isNotEmpty() && sets != lastWarmCodes) {
                    lastWarmCodes = sets
                    sets.forEach { code ->
                        launch(Dispatchers.IO) {
                            try { Api.detail(code, token = token) } catch (e: Exception) { /* 预热失败忽略 */ }
                        }
                    }
                }
            } catch (e: Exception) { /* 忽略 */ }
            kotlinx.coroutines.delay(3000)
        }
    }
    // 切换分组/切换账户时拉该组股票（自选股=-1 或自建组 id；持仓股=-2 用选中账户的持仓）
    LaunchedEffect(selectedGroup, selectedBrokerId, token) {
        loadingStocks = true
        stocks = emptyList()
        try {
            val gid = if (selectedGroup >= 0) selectedGroup else -1
            if (selectedGroup == -2) {
                // 持仓股分组：仅显示「当前选中账户」的持仓（账户由顶部切换器选择）。
                // ★ 性能根治：watchlist(list) 一次请求即带回全部股票的完整实时行情
                //   （price/change/changePct），且与 detail 数据一致。
                //   故先并发拉 watchlist 建 code->行情 映射，命中即用（快），
                //   未命中才 fallback detail（慢，冷缓存 ~4.3s）。避免逐只调 detail。
                val list = withContext(Dispatchers.IO) {
                    val out = ArrayList<StockItem>()
                    // 1) 建批量行情映射（一次请求，~1.4s）
                    val quoteMap = HashMap<String, StockItem>()
                    try {
                        for (it in Api.stockList(token, -1)) {
                            if (it.code.isNotBlank()) quoteMap[it.code] = it
                        }
                    } catch (e: Exception) { /* 忽略，全部走 detail fallback */ }
                    // 2) 仅取「当前选中账户」的持仓；选中账户无效时取首个账户
                    val brokers = try { Api.brokerList(token) } catch (e: Exception) { emptyList() }
                    val cur = brokers.firstOrNull { it.id == selectedBrokerId } ?: brokers.firstOrNull()
                    Log.e("CGJ_POS", "token=" + (token?.length ?: -1) + " brokers.size=" + brokers.size + " selectedBrokerId=" + selectedBrokerId + " cur=" + (cur?.id ?: -1))
                    if (cur != null) {
                        try {
                            val ps = Api.positions(token, cur.id)
                            Log.e("CGJ_POS", "broker=" + cur.id + " positions.size=" + ps.size + " codes=" + ps.map { it.code + ":" + it.quantity })
                            val valid = ps.filter { it.quantity > 0 && it.code.isNotBlank() }
                            // 3) 未命中批量行情的才需要 fallback detail，仅对它们并发请求
                            val missCodes = valid.filter { quoteMap[it.code] == null }.map { it.code }
                            val dets: Map<String, StockDetail?> = if (missCodes.isEmpty()) emptyMap() else
                                missCodes.map { code ->
                                    async(Dispatchers.IO) {
                                        code to (try { Api.detail(code, token = token) } catch (e: Exception) { null })
                                    }
                                }.awaitAll().toMap()
                            valid.forEach { p ->
                                val q = quoteMap[p.code]
                                if (q != null) {
                                    // 命中：直接用 watchlist 的实时行情
                                    out.add(
                                        StockItem(
                                            code = p.code,
                                            name = p.name.ifBlank { q.name },
                                            price = q.price,
                                            change = q.change,
                                            changePct = q.changePct
                                        )
                                    )
                                } else {
                                    // 未命中：fallback detail
                                    val d = dets[p.code]
                                    val bp = d?.base?.price ?: 0.0
                                    val pre = d?.base?.preClose ?: 0.0
                                    val price = if (bp > 0.0) bp else p.price
                                    val chg = if (bp > 0.0) (d?.base?.change ?: 0.0)
                                              else if (pre > 0.0) price - pre else 0.0
                                    val pct = if (bp > 0.0) (d?.base?.changePct ?: 0.0)
                                              else if (pre > 0.0) (price - pre) / pre * 100.0 else 0.0
                                    out.add(
                                        StockItem(
                                            code = p.code,
                                            name = p.name,
                                            price = price,
                                            change = chg,
                                            changePct = pct
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) { /* 忽略该券商 */ }
                    }
                    // ★ 持仓列表补标签：手拼 StockItem 不含 tags，统一批量回填
                    if (out.isNotEmpty()) {
                        try {
                            val tm = Api.tagsOf(out.map { it.code })
                            for (i in out.indices) {
                                val tl = tm[out[i].code]
                                if (tl != null) out[i] = out[i].copy(tags = tl)
                            }
                        } catch (e: Exception) { }
                    }
                    out
                }
                stocks = list
                Log.e("CGJ_POS", "FINAL stocks.size=" + list.size)
            } else {
                val l = withContext(Dispatchers.IO) { Api.stockList(token, gid) }
                stocks = l
            }
        } catch (e: Exception) { Log.e("CGJ_POS", "outer catch: " + e) }
        loadingStocks = false
    }

    // 初始拉一次指数清单
    LaunchedEffect(Unit) {
        try {
            val l = withContext(Dispatchers.IO) { Api.indexList() }
            if (l.isNotEmpty()) list = l
        } catch (e: Exception) { /* 忽略 */ }
    }

    // 顶部指数行情：5 秒轮询
    LaunchedEffect(secid) {
        while (true) {
            try {
                val q = withContext(Dispatchers.IO) { Api.indexQuotesBy(secid) }
                if (q.isNotEmpty()) quote = q.first()
            } catch (e: Exception) { /* 忽略 */ }
            delay(5000)
        }
    }

    // 右侧方块行情：8 秒轮询全部指数
    LaunchedEffect(list) {
        if (list.isEmpty()) return@LaunchedEffect
        while (true) {
            try {
                val secids = list.joinToString(",") { it.secid }
                val qs = withContext(Dispatchers.IO) { Api.indexQuotesBy(secids) }
                val m = HashMap<String, Double>()
                for (q in qs) m[q.code] = q.changePct
                if (m.isNotEmpty()) boxQuotes = m
            } catch (e: Exception) { /* 忽略 */ }
            delay(8000)
        }
    }

    // 自选列表分时走势：跟随 stocks 拉取每只股票的分时（并发，失败跳过）
    // ★ 性能修复（方案 A）：原先每 3 秒对「全部自选股」并发 forceRefresh=true 强刷，
    //   会绕过本地缓存把后端 detail.php（冷请求 ~5s）打饱和，导致详情页请求排队、体感卡顿。
    //   现改为：走本地磁盘缓存（forceRefresh=false，5 分钟 TTL），刷新间隔放宽到 15 秒。
    //   分时走势由详情页/预热负责更新缓存，此处只消费缓存，秒级延迟可接受。
    LaunchedEffect(stocks) {
        while (true) {
            try {
                val codes = stocks.map { it.code }.filter { it.isNotBlank() }
                if (codes.isNotEmpty()) {
                    val m = HashMap<String, Pair<List<DetailTrend>, Double>>()
                    val results = withContext(Dispatchers.IO) {
                        codes.map { code ->
                            async {
                                try {
                                    // ★ 走缓存：命中即秒回，不绕过缓存，避免打爆后端
                                    val d = Api.detail(code, forceRefresh = false)
                                    if (d != null && d.trends.isNotEmpty())
                                        code to (d.trends to d.base.preClose)
                                    else null
                                } catch (e: Exception) { null }
                            }
                        }.awaitAll()
                    }
                    for (r in results) if (r != null) m[r.first] = r.second
                    if (m.isNotEmpty()) watchTrendMap = m
                }
            } catch (e: Exception) { /* 忽略 */ }
            android.util.Log.d("CGJ_DIAG", "watch stocks.size=" + stocks.size + " codes=" + stocks.map { it.code }.take(5) + " trendMap.size=" + watchTrendMap.size)
            kotlinx.coroutines.delay(15000)
        }
    }
// 顶部红条高度由内容自适应，面板紧贴其底部，无需硬编码
Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {

        // 详情页已提升到 MainActivity（App 级覆盖层 + 统一红条），这里不再渲染详情页


        // ---------- 主列：红条 + 分割线 +（展开时）面板 +（展开时）关闭遮罩 ----------
        Column(Modifier.fillMaxWidth()) {

            // ================= 顶部两排版工具条（红条）=================
            TopBar(
                quote = quote,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onOpenNews = { guard("watch_news", "资讯") { onOpenNews() } },
                // 资金/资产/分析：受限功能，无权限 → 弹会员引导
                onOpenFund = { guard("watch_fund", "资金") { /* TODO 资金 */ } },
                onOpenAsset = { guard("watch_asset", "资产") { /* TODO 资产 */ } },
                onOpenAnalysis = { guard("watch_analysis", "分析") { /* TODO 分析 */ } }
            )

            if (expanded) {
                // 展开时：详情面板【直接】从红条底部向下延伸，中间无分割线、无空白
                IndexDetailPanel(
                    secid = secid,
                    list = list,
                    boxQuotes = boxQuotes,
                    onPick = { sid ->
                        secid = sid
                        expanded = false
                    }
                )

                // 面板以下的剩余区域：透明遮罩，点面板以外任何地方都关闭
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = false }
                )
            } else {
                // 未展开时：红条（指数显示区）与下方白色区之间的分割线
                HorizontalDivider(
                    thickness = 2.dp,
                    color = Color(0xFFEEEEEE)
                )

                // 分组标签栏（自选股 / 持仓股 / 用户自建分组）
                GroupTabsBar(
                    selected = selectedGroup,
                    userGroups = userGroups,
                    onSelect = { selectedGroup = it }
                )

                // 标签栏与股票列表之间的细分隔线
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFEEEEEE)
                )

                // 列表统计/列头：左侧「名称/代码」固定，右侧统计列横向可滑动
                // 表头与列表行共用同一个 hScroll，保证左右滑动时两边同步
                val hScroll = rememberScrollState()
                // ================= 按屏幕宽度自适应（核心） =================
                // 名称列 fixedW 固定；右侧「涨幅」列 pctW 固定；中间为横滑区 midW。
                // 横滑区内：第一个元素是「多股同列」入口（宽 = midW，默认完整遮住三列），
                // 其后依次是统计列。左滑时整个横滑区内元素一起左移，入口钻到名称列下方被遮住，
                // 统计列从入口右缘依次露出 —— 名称列/涨幅列始终钉死不动。
                val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                // 持仓股分组的「账户切换器」图标已移到右侧「多股同列」栏显示，
                // 名称列回归常规宽度，避免压缩统计列/分时图。
                val fixedW = WATCH_FIXED_W
                val pctW = WATCH_PCT_COL_W
                val midW = (screenW - fixedW - pctW).coerceAtLeast(80)
                // 表头：Box 叠放 —— 下层是可横滑的统计列（铺满整宽），上层是固定列（盖在最上面）
                // 往左滑时，统计列会从左往右依次钻到固定列底下被遮住
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color.White)
                ) {
                    // 下层：统计列（从固定列右缘起铺，随列表同步横滑；表头本身不响应手指拖动）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = fixedW.dp)
                            .horizontalScroll(hScroll, enabled = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 第一个元素：多股同列入口（宽 = 中间区 midW），随横滑一起左移、钻入名称列下方。
                        // 内容右对齐（贴入口块右缘）：左滑时先露出右侧空隙，再逐步被名称列盖住，
                        // 避免"滑动一点点入口就整块消失"的突兀感。
                        Row(
                            Modifier
                                .width(midW.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showColumns = true }
                                .padding(end = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_multi_stock_columns),
                                contentDescription = "多股同列",
                                tint = COLOR_SUB,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "多股同列",
                                color = COLOR_TEXT,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        // 「涨幅」列标题 —— 顺序：名称(钉死) | 入口 | 涨幅 | 三列。
                        // 未滑动时视口(=屏宽-名称列)恰好露出「入口+涨幅」；左滑时涨幅与入口一起左移，
                        // 三列从右侧依次进入（只有名称列钉死）。
                        Text(
                            text = "涨幅",
                            color = COLOR_TEXT,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(pctW.dp)
                                .padding(end = 10.dp)
                        )
                        for (col in activeCols) {
                            Text(
                                text = col.title,
                                color = COLOR_TEXT,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(col.width.dp).padding(horizontal = 10.dp),
                                textAlign = if (col.key == "pctPrice") TextAlign.End else TextAlign.Center
                            )
                        }
                    }
                    // 上层：固定列「名称 / 代码」区，带白底，盖住滑过来的统计列
                    // 非持仓股分组：显示两个 18dp 图标（①表头管理页 ②列表排序），左滑后变「编辑表头 [图标1]」
                    // 持仓股分组(-2)：暂仍显示原文字，后续单独实现
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(fixedW.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        if (selectedGroup != -2) {
                            // 左滑(hScroll.value>0)时：图标2隐藏，显示「编辑表头 [图标1]」
                            // 未左滑：显示两个 18dp 图标（点击无视觉反馈）
                            if (hScroll.value == 0) {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_add),
                                        contentDescription = "编辑表头",
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                showColEditor = true
                                            }
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_share),
                                        contentDescription = "排序",
                                        modifier = Modifier
                                            .padding(start = 18.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // 排序：暂不接逻辑（空按钮）
                                            }
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "编辑表头",
                                        color = COLOR_TEXT,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_add),
                                        contentDescription = "编辑表头",
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                showColEditor = true
                                            }
                                    )
                                }
                            }
                        } else {
                            // 持仓股分组：顶部「账户切换器」= 证券图标 + 证券名称 + 下拉弯箭头
                            // 三者垂直居中对齐；点击整块展开账户下拉列表，选中即切到该账户持仓
                            val curBroker = brokerAccounts.firstOrNull { it.id == selectedBrokerId }
                                ?: brokerAccounts.firstOrNull()
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { if (brokerAccounts.isNotEmpty()) showBrokerMenu = !showBrokerMenu }
                                    .padding(start = 8.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BrokerHeaderIcon(
                                    brokerName = curBroker?.broker ?: "",
                                    key = curBroker?.key ?: "",
                                    sizeDp = 16
                                )
                                Spacer(Modifier.width(5.dp))
                                // 账户名：完整显示（最多 4 字，超出才省略），不压缩
                                Text(
                                    text = curBroker?.broker ?: "选择账户",
                                    color = COLOR_TEXT,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 58.dp)
                                )
                                // 下拉弯箭头：紧跟在账户名右侧，与名称、图标垂直居中对齐。
                                // 「┛」顺时针 90° 再逆时针 45° = 净 45°，浅灰。
                                Text(
                                    text = "┛",
                                    color = Color(0xFF999999),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(start = 3.dp)
                                        .graphicsLayer(rotationZ = 45f)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFEEEEEE)
                )
                // 该组股票列表
                if (stocks.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = if (loadingStocks) "加载中…" else "「${groupNameOf(selectedGroup, userGroups)}」暂无股票",
                            color = Color(0xFF999999),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 40.dp)
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(stocks) { s ->
                            // key 里带上 clockTick：每 20 秒强制重建该行，保证重置时段到点切换
                            androidx.compose.runtime.key(clockTick) {
                                StockRow(
                                    s = s,
                                    cols = activeCols,
                                    hScroll = hScroll,
                                    trend = watchTrendMap[s.code],
                                    fixedW = fixedW,
                                    pctW = pctW,
                                    midW = midW,
                                    nameColor = if (s.code in holdingCodes) Color(0xFF9D27B1) else COLOR_TEXT,
                                    onOpen = {
                                        val i = stocks.indexOfFirst { it.code == s.code }
                                        if (i >= 0) onOpenDetail(stocks, i)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= 「多股同列」全屏页（图标入口在红条右侧）=================
        if (showColumns) {
            MultiStockColumnsScreen(
                token = token,
                onClose = { showColumns = false }
            )
        }

        // ================= 「编辑表头」列勾选框 =================
        if (showColEditor) {
            ColumnEditorDialog(
                keys = colKeys,
                onToggle = { k, on ->
                    val next = if (on) (colKeys + k).distinct() else colKeys.filter { it != k }
                    colKeys = next
                    WatchColumnPrefs.save(ctx, next)
                },
                onReset = {
                    val dk = WatchColumnRegistry.defaultKeys(dynFields)
                    colKeys = dk
                    WatchColumnPrefs.save(ctx, dk)
                },
                onClose = { showColEditor = false }
            )
        }

        // ================= 会员专享引导弹窗（自选页受限功能）=================
        if (gateTitle != null) {
            MemberGateDialog(
                title = gateTitle!!,
                onDismiss = { gateTitle = null },
                onOpen = { gateTitle = null; onOpenVipPage() }
            )
        }

        // ===== 持仓股分组：账户切换下拉弹层 =====
        if (showBrokerMenu && brokerAccounts.isNotEmpty()) {
            BrokerSwitchDialog(
                accounts = brokerAccounts,
                selectedId = selectedBrokerId,
                onPick = { id -> selectedBrokerId = id; AccountStore.set(ctx, id); showBrokerMenu = false },
                onDismiss = { showBrokerMenu = false }
            )
        }
    }
}

/* ---------------- 持仓股分组：账户切换器相关 ---------------- */

/** 券商圆形小图标：有 broker_<key>.png 用图片，否则圆圈+首字兜底。sizeDp 控制尺寸 */
@Composable
private fun BrokerHeaderIcon(brokerName: String, key: String, sizeDp: Int) {
    if (key.isNotEmpty()) {
        val ctx = LocalContext.current
        val id = ctx.resources.getIdentifier("broker_$key", "drawable", ctx.packageName)
        if (id != 0) {
            Image(
                painter = painterResource(id),
                contentDescription = null,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .clip(CircleShape)
            )
            return
        }
    }
    val ch = if (brokerName.isNotEmpty()) brokerName.substring(0, 1) else "券"
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(fallbackColor(brokerName)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ch,
            color = Color.White,
            fontSize = (sizeDp * 0.55f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 兜底圆圈底色：按券商名 hash 稳定取色 */
private fun fallbackColor(name: String): Color {
    val palette = listOf(
        Color(0xFFE23B3B), Color(0xFF2A7BFF), Color(0xFF00A870),
        Color(0xFFF59A23), Color(0xFF8E44AD), Color(0xFF16A3B0)
    )
    var h = 0
    for (c in name) h = h * 31 + c.code
    val i = ((h % palette.size) + palette.size) % palette.size
    return palette[i]
}

/** 账户切换弹层：列出当前用户全部券商资金账户，每项 = 图标 + 券商名，选中即切 */
@Composable
private fun BrokerSwitchDialog(
    accounts: List<MyBroker>,
    selectedId: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = "选择资金账户",
                    color = COLOR_SUB,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp)
                )
                accounts.forEach { b ->
                    val selected = b.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPick(b.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrokerHeaderIcon(brokerName = b.broker, key = b.key, sizeDp = 24)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = b.broker,
                                color = COLOR_TEXT,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = b.account,
                                color = COLOR_SUB,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) {
                            Text(text = "✓", color = COLOR_UP, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 「编辑表头」列勾选框 ---------------- */

/**
 * 列勾选框（从简版）：全屏半透明遮罩 + 白卡内容。
 * - 列出 ALL 中除固定列(PCT_PRICE)外的所有列，Checkbox 即时勾选
 * - 顶部搜索框按标题过滤（列多时好用）
 * - 底部按钮：重置默认 / 完成
 */
@Composable
private fun ColumnEditorDialog(
    keys: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val allCols = WatchColumnRegistry.available().filter { it.key != "pctPrice" }
    var kw by remember { mutableStateOf("") }
    val shown = remember(kw) {
        if (kw.isBlank()) allCols else allCols.filter { it.title.contains(kw.trim()) || it.key.contains(kw.trim(), true) }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("编辑表头", color = COLOR_TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("共 ${keys.size} 列", color = COLOR_SUB, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                // 搜索框（用简单边框 Box + BasicTextField 实现）
                var kwEdit by remember { mutableStateOf(kw) }
                androidx.compose.foundation.text.BasicTextField(
                    value = kwEdit,
                    onValueChange = { kwEdit = it; kw = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = COLOR_TEXT, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) { inner ->
                    if (kwEdit.isEmpty()) Text("搜索列名…", color = COLOR_SUB, fontSize = 14.sp)
                    inner()
                }
                Spacer(Modifier.height(8.dp))
                // 列列表
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.key }) { c ->
                        val checked = keys.contains(c.key)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(c.key, !checked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { v -> onToggle(c.key, v == true) }
                            )
                            Text(c.title, color = COLOR_TEXT, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(c.key, color = COLOR_SUB, fontSize = 11.sp)
                        }
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFF2F2F2))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 底部按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "重置默认",
                        color = COLOR_UP,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReset() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "完成",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(COLOR_UP)
                            .clickable { onClose() }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/* ---------------- 顶部两排版 ---------------- */

@Composable
private fun TopBar(
    quote: IndexQuote?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenFund: () -> Unit = {},
    onOpenAsset: () -> Unit = {},
    onOpenAnalysis: () -> Unit = {}
) {
    val pct = quote?.changePct ?: 0.0
    val c = colorOf(pct)
    val priceText = quote?.let { fmt(it.price) } ?: "--"
    val chgText = quote?.let { fmt(it.change) } ?: "--"
    val pctText = quote?.let { fmt(it.changePct) + "%" } ?: "--"
    val nameText = quote?.name ?: "上证指数"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.White)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // 第一排：点数（大粗红） + 涨跌点（小红）  —— 数字用真窄体 Oswald
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = priceText,
                    color = c,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumFont
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = chgText,
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NumFont,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 两排之间加大间距
            Spacer(Modifier.height(6.dp))
            // 第二排：名称（纯黑，中粗，别太粗） + 涨跌幅%（红） + ▼
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nameText,
                    color = Color(0xFF000000),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = pctText,
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NumFont
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = Color(0xFF111111),
                    fontSize = 12.sp,
                    modifier = Modifier.scale(1f, 0.6f)
                )
            }
        }

        // ---------- 右侧功能按钮（上图标 + 下文字）----------
        // 注：「多股同列」入口已移到自选列表表头行第二列，此处不再放
        TopActionButton(R.drawable.btn_fund, "资金", enabled = true) { onOpenFund() }
        TopActionButton(R.drawable.btn_news, "资讯", enabled = true) { onOpenNews() }
        TopActionButton(R.drawable.btn_asset, "资产", enabled = true) { onOpenAsset() }
        TopActionButton(R.drawable.btn_analysis, "分析", enabled = true) { onOpenAnalysis() }
    }
}

/** 顶部功能按钮：上图标 + 下文字。回调由调用方按权限决定（无权限时回调内弹会员引导） */
@Composable
private fun TopActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .clickable(enabled = enabled) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 11.sp,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* ---------------- 自选股票列表行 ---------------- */

/** 列表左侧「名称/代码」固定列宽度（dp），表头与数据行共用 */ 
internal val WATCH_FIXED_W = 96

/**
 * 名称列可用文字宽度（dp）= 名称列宽 − 左内边距 16dp。
 * 用于按名称字数自适应字号。
 */
internal const val WATCH_NAME_TEXT_W = 80f

/**
 * 名称显示文本：按「汉字宽度」截断，满 [NAME_MAX_HAN] 个汉字宽就停，超出加「…」。
 *  · 汉字（含全角标点）= 1 个宽度；
 *  · 数字 / 英文字母等半角字符 = 0.5 个宽度；
 * 例：「沪深300ETF华泰柏瑞」→ 沪深(2) 300ETF(3) 华(1) = 6 → 显示「沪深300ETF华…」
 */
internal fun watchNameDisplay(name: String): String {
    if (name.isEmpty()) return name
    var acc = 0f
    val sb = StringBuilder()
    for (ch in name) {
        val w = charWidth(ch)
        if (acc + w > NAME_MAX_HAN) {
            return sb.append("…").toString()
        }
        acc += w
        sb.append(ch)
    }
    return sb.toString()
}

/** 单字符占「汉字宽度」：汉字/全角 = 1.0，半角（数字/字母/半角符号）= 0.5 */
private fun charWidth(ch: Char): Float = if (ch.code <= 0x7F) 0.5f else 1f

/**
 * 计算名称的「汉字宽度」：汉字/全角 = 1.0，数字/字母/半角符号 = 0.5。
 * 例：「沪深300ETF华泰柏瑞」→ 沪(1)+深(1)+300ETF(3)+华(1)+… = 6+ 。
 */
internal fun watchNameWidth(name: String): Float {
    var acc = 0f
    for (ch in name) acc += charWidth(ch)
    return acc
}

/**
 * 股票名称字号：按「每只股票自己」的名称汉字宽度分档，互不影响。
 *  · ≤4 字   → 原始大小（14sp）
 *  · 5 字    → 稍微缩小（13sp）
 *  · 6 字    → 缩小更多（12sp）
 *  · ≥7 字   → 与 6 字同字号（12sp），文本由 [watchNameDisplay] 截为「6 汉字宽 + …」
 * 注意：这里的缩放只作用于当前这一只股票，不会影响同列表其他股票。
 */
internal fun watchNameFontSize(name: String): Float {
    val w = watchNameWidth(name)
    return when {
        w <= 4f -> NAME_FONT_L0_SP   // ≤4 字：默认大小
        w <= 5f -> NAME_FONT_L1_SP   // 5 字：稍微缩小
        w <= 6f -> NAME_FONT_L2_SP   // 6 字：缩得更多
        else    -> NAME_FONT_L3_SP   // ≥7 字：同 6 字字号 + 省略号
    }
}

/** 名称最多显示的「汉字宽度」数（汉字=1，数字/字母=0.5），超过则截断加省略号 */
private const val NAME_MAX_HAN = 6f

/* 名称字号分档（sp）：只影响「该只股票自己」，与其他股票无关 */
/** ≤4 汉字宽：撑满名称列（可用宽 80dp / 4 字 = 20sp，取 18sp 兼顾行高 44dp 与下方代码行） */
private const val NAME_FONT_L0_SP = 18f
/** 5 汉字宽：稍微缩小（80/5=16，取 14sp 留余量） */
private const val NAME_FONT_L1_SP = 14f
/** 6 汉字宽：缩小更多（80/6≈13.3，取 12.5sp） */
private const val NAME_FONT_L2_SP = 12.5f
/** ≥7 汉字宽：与 6 字同字号（文本已截断为 6 汉字宽 + …，需容纳省略号留量） */
private const val NAME_FONT_L3_SP = 12.5f

/** 表头中层「多股同列」入口宽度；其后留白给后续功能，「涨幅」列标题显示在最右 */
internal val WATCH_COLS_ENTRY_W = 120

/** 表头/列表行最右「涨幅」列宽度（dp）：显示涨跌幅百分比 + 最新价，右对齐 */
internal val WATCH_PCT_COL_W = 92

/**
 * 自选股票行。
 * 布局：左侧「名称/代码」固定不动；右侧统计列横向可滑动（与表头共用 [hScroll]）；
 *      点左半区（固定列）进入个股详情页。
 */
@Composable
private fun StockRow(
    s: StockItem,
    cols: List<WatchColumn>,
    hScroll: androidx.compose.foundation.ScrollState,
    trend: Pair<List<DetailTrend>, Double>?,
    fixedW: Int,
    pctW: Int,
    midW: Int,
    nameColor: androidx.compose.ui.graphics.Color = COLOR_TEXT,
    onOpen: () -> Unit
) {
    // 盘前重置时段（交易日 08:45–09:15）：全部显示平盘黑 + "--"，分时图重置
    val reset = isPreOpenReset()
    val c = if (reset) COLOR_FLAT else colorOf(s.changePct)
    Column(Modifier.fillMaxWidth()) {
        // Box 叠放：下层是可横滑的统计列（铺满整宽），上层是固定列（白底，盖在最上面）
        // 往左滑时，统计列会从左往右依次钻到固定列底下被遮住
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            // 下层：统计列（从固定列右缘起铺，与表头同步横滑）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = fixedW.dp)
                    .horizontalScroll(hScroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 与表头对齐：最前放一个「入口同宽」的透明占位（数据行不显示入口文字，
                // 左滑时它同样左移钻入名称列下方，统计列从它右缘依次露出）
                Spacer(Modifier.width(midW.dp))
                // 「涨幅」列（上行涨跌幅色块 + 下行最新价）—— 顺序：名称(钉死) | 入口 | 涨幅 | 三列。
                // 与表头一致：未滑动时恰好露出「入口+涨幅」，左滑时随入口一起左移（不再钉死）。
                Box(
                    Modifier
                        .width(pctW.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .padding(end = 10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .width(78.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (reset) COLOR_FLAT
                                    else if (s.changePct > 0.000001) COLOR_UP
                                    else if (s.changePct < -0.000001) COLOR_DOWN
                                    else COLOR_FLAT
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (reset) "--"
                                else if (s.changePct < -0.000001)
                                    String.format(Locale.CHINA, "-%.2f%%", kotlin.math.abs(s.changePct))
                                else
                                    String.format(Locale.CHINA, "%.2f%%", kotlin.math.abs(s.changePct)),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = NumFont,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (reset) "--" else String.format(Locale.CHINA, "%.2f", s.price),
                            color = if (reset) COLOR_TEXT else COLOR_SUB,
                            fontSize = 12.sp,
                            fontFamily = NumFont,
                            maxLines = 1
                        )
                    }
                }
                for (col in cols) {
                    val txt = if (reset && col.key == "changePct") "--" else col.value(s)
                    when (col.colorMode) {
                        WatchColumn.ColorMode.UP_DOWN -> {
                            // 涨跌幅用色块底，其余用带色文字
                            if (col.key == "changePct") {
                                Box(
                                    Modifier
                                        .width(col.width.dp)
                                        .padding(horizontal = 10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(c)
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = txt,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = NumFont
                                    )
                                }
                            } else {
                                Text(
                                    text = txt,
                                    color = c,
                                    fontSize = 15.sp,
                                    fontFamily = NumFont,
                                    modifier = Modifier.width(col.width.dp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = txt,
                                color = COLOR_TEXT,
                                fontSize = 14.sp,
                                fontFamily = NumFont,
                                modifier = Modifier.width(col.width.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            // 名称/代码 与 涨幅 之间的分时走势图（从左向右逐点延伸的折线）
            // 交互：与其它统计列同机制 —— 跟随横滑一起左移（translationX = -hScroll.value），
            //      被上层名称列白底 Box 从右往左"一点一点盖住"，而不是用透明度淡出。
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(start = (fixedW + 8).dp, end = (pctW + 8).dp)
                    .graphicsLayer { translationX = -hScroll.value.toFloat() }
            ) {
                MiniMinuteChart(
                    trends = trend?.first ?: emptyList(),
                    preClose = trend?.second ?: 0.0,
                    reset = reset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }

            // 上层：固定列「名称 + 代码」，带白底（点击进详情页），盖住滑过来的统计列
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(fixedW.dp)
                    .fillMaxHeight()
                    .background(Color.White)
                    .clickable { onOpen() }
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = watchNameDisplay(s.name),
                    color = nameColor,
                    fontSize = watchNameFontSize(s.name).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 代码前：融/创/新/次新（橙底白字）
                    val preTags = s.tags.filter { it == "融" || it == "创" || it == "新" || it == "次新" }
                    if (preTags.isNotEmpty()) {
                        StockTagRow(tags = preTags, maxShow = 0, fontSize = 7.5f, leftScroll = false)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = s.code,
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        fontFamily = NumFont
                    )
                    // 代码后：T+0 优先，无 T+0 才显示 场内（灰框灰字，仅 ETF）
                    val postTags = s.tags.filter { it == "T+0" }.take(1)
                        .ifEmpty { s.tags.filter { it == "场内" }.take(1) }
                    if (postTags.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        StockTagRow(tags = postTags, maxShow = 0, fontSize = 7.5f, leftScroll = false)
                    }
                }
            }
        }
        // 每只股票行底部的细分隔线（很淡的浅灰，跟随列表一起滑动）
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFFF0F0F0)
        )
    }
}


/* ---------------- 自选分组标签栏 ---------------- */

/** 内置两个固定分组：自选股(-1) / 持仓股(-2) */
/** 选择项 key 对应的显示名：-1 自选股 / -2 持仓股 / >=0 用户分组名 */
private fun groupNameOf(key: Int, groups: List<WatchGroup>): String = when (key) {
    -1 -> "自选股"
    -2 -> "持仓股"
    else -> groups.firstOrNull { it.id == key }?.name?.ifEmpty { "分组$key" } ?: "自选股"
}

data class GroupTabItem(val key: Int, val name: String)

/**
 * 分组标签栏：横向可滑动。
 * 前两个固定为「自选股」「持仓股」，其后为用户自建分组。
 * 点亮态 = 纯黑加粗 + 名字下方居中一根短红线。
 * 点击某个分组时，会自动把该分组滚动到容器正中间；靠边的分组受滚动边界限制，最多只能滚到中间。
 */
@Composable
private fun GroupTabsBar(
    selected: Int,
    userGroups: List<WatchGroup>,
    onSelect: (Int) -> Unit
) {
    val items = remember(userGroups) {
        val base = arrayListOf(
            GroupTabItem(-1, "自选股"),
            GroupTabItem(-2, "持仓股")
        )
        for (g in userGroups) base.add(GroupTabItem(g.id, g.name.ifEmpty { "分组${g.id}" }))
        base
    }
    val scrollState = rememberScrollState()
    val centerTargets = remember { mutableStateMapOf<Int, Int>() }

    // 选中项变化时，把选中标签滚到容器正中间（animateScrollTo 自动夹在 [0, maxValue] 内 → 靠边的组最多只能滚到中间）
    LaunchedEffect(selected, items.size) {
        val center = centerTargets[selected] ?: return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        scrollState.animateScrollTo((center - viewport / 2).coerceAtLeast(0))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(44.dp).background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(8.dp))
            for (it in items) {
                GroupTab(
                    name = it.name,
                    active = it.key == selected,
                    onClick = { onSelect(it.key) },
                    onCenterX = { cx -> centerTargets[it.key] = cx }
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}
/** 单个分组标签：上文字、下短红线（点亮时）。红线固定一个字宽并居中。 */
@Composable
private fun GroupTab(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    onCenterX: (Int) -> Unit
) {
    val textColor = if (active) Color(0xFF000000) else Color(0xFF888888)
    val fontSize = if (active) 16.sp else 15.sp
    // 外层 Box 填满标签栏高度：文字在整条高度里【垂直居中】，红线固定贴在底部
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInParent()
                onCenterX(((b.left + b.right) / 2f).toInt())
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        // 短红线：短而粗（14dp x 3dp），贴底居中；未点亮时透明，避免跳动
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .width(14.dp)
                .height(3.dp)
                .background(if (active) Color(0xFFF03036) else Color.Transparent)
        )
    }
}

/* ---------------- 详情大面板 ---------------- */

@Composable
private fun IndexDetailPanel(
    secid: String,
    list: List<IndexItemInfo>,
    boxQuotes: Map<String, Double>,
    onPick: (String) -> Unit
) {
    var detail by remember { mutableStateOf<IndexDetail?>(null) }

    // 详情数据：3 秒轮询（只拉当前指数）
    LaunchedEffect(secid) {
        while (true) {
            try {
                val d = withContext(Dispatchers.IO) { Api.indexDetail(secid) }
                if (d != null) detail = d
            } catch (e: Exception) { /* 忽略 */ }
            delay(3000)
        }
    }

    val d = detail

    Row(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFFF7F7F7))
    ) {
        // 左侧：详情主体（可上下滚动）
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            if (d == null) {
                Text("加载中…", color = COLOR_SUB, fontSize = 12.sp)
            } else {
                val c = colorOf(d.changePct)

                // 指数名 + 点数 + 涨跌点 + 涨跌幅
                Text(d.name, color = COLOR_TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(d.price), color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = NumFont)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (d.change >= 0) "+" else "") + fmt(d.change),
                        color = c, fontSize = 13.sp, fontFamily = NumFont, modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (d.changePct >= 0) "+" else "") + fmt(d.changePct) + "%",
                        color = c, fontSize = 13.sp, fontFamily = NumFont, modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("成交额 " + fmtAmount(d.amount), color = COLOR_SUB, fontSize = 11.sp)

                Spacer(Modifier.height(10.dp))

                // 分时走势大曲线
                TrendChart(d, Modifier.fillMaxWidth().height(180.dp))

                Spacer(Modifier.height(6.dp))

                // 成交量（迷你柱）
                VolumeChart(d, Modifier.fillMaxWidth().height(48.dp))

                Spacer(Modifier.height(10.dp))

                // 成交额 + 涨跌家数
                InfoRow("成交额", fmtAmount(d.amount), COLOR_TEXT)
                InfoRow("涨家数", d.upCount.toString(), COLOR_UP)
                InfoRow("跌家数", d.downCount.toString(), COLOR_DOWN)
                InfoRow("平家数", d.flatCount.toString(), COLOR_FLAT)
            }
        }

        // 右侧：一竖列指数快捷方块（可上下滑）
        Box(
            Modifier
                .width(58.dp)
                .fillMaxHeight()
                .background(Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { Spacer(Modifier.height(6.dp)) }
                items(list) { it2 ->
                    val pct = boxQuotes[it2.code]
                    IndexChip(
                        short = it2.short.ifEmpty { it2.name.take(1) },
                        pct = pct,
                        selected = it2.secid == secid,
                        onClick = { onPick(it2.secid) }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

/** 右侧竖排方块：首字 + 实时涨跌幅% */
@Composable
private fun IndexChip(short: String, pct: Double?, selected: Boolean, onClick: () -> Unit) {
    val c = if (pct == null) COLOR_SUB else colorOf(pct)
    Column(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFFDECEA) else Color(0xFFF2F2F2))
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = short, color = COLOR_TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (pct == null) "--" else (if (pct >= 0) "+" else "") + fmt(pct) + "%",
            color = c,
            fontSize = 9.sp,
            fontFamily = NumFont
        )
    }
}

/* ---------------- 分时走势曲线 ---------------- */

@Composable
private fun TrendChart(d: IndexDetail, modifier: Modifier = Modifier) {
    val trends = d.trends
    val priceColor = colorOf(d.changePct)

    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = size.width
            val h = size.height

            // 基线（昨收）在竖直方向居中：以 preClose 为中轴，取上下对称幅度
            val base = if (d.preClose > 0) d.preClose else d.price

            // 计算最大偏差（含均价）
            var maxDev = 0.0
            for (t in trends) {
                maxDev = maxOf(maxDev, Math.abs(t.p - base))
                maxDev = maxOf(maxDev, Math.abs(t.avg - base))
            }
            if (maxDev <= 0.0) maxDev = base * 0.005

            val cy = h / 2f
            // 上下各留约 8% 边距
            val scale = (h * 0.42) / maxDev

            // 横向网格线：上 / 中 / 下 三条
            val gridColor = Color(0xFFDDDDDD)
            drawLine(gridColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.12f), Offset(w, h * 0.12f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.88f), Offset(w, h * 0.88f), strokeWidth = 1f)

            if (trends.size < 2) return@Canvas

            val n = trends.size
            fun xOf(i: Int): Float = if (n <= 1) 0f else w * i / (n - 1).toFloat()
            fun yOf(p: Double): Float = (cy - ((p - base) * scale)).toFloat()

            // 价格曲线
            val pricePath = Path()
            for (i in 0 until n) {
                val x = xOf(i)
                val y = yOf(trends[i].p)
                if (i == 0) pricePath.moveTo(x, y) else pricePath.lineTo(x, y)
            }
            // 价格曲线下方淡色填充
            val fill = Path()
            fill.addPath(pricePath)
            fill.lineTo(w, cy)
            fill.lineTo(0f, cy)
            fill.close()
            drawPath(fill, color = priceColor.copy(alpha = 0.08f))
            drawPath(pricePath, color = priceColor, style = Stroke(width = 2.5f))

            // 均价曲线（黄色细线）
            val avgPath = Path()
            var started = false
            for (i in 0 until n) {
                if (trends[i].avg <= 0.0) continue
                val x = xOf(i)
                val y = yOf(trends[i].avg)
                if (!started) { avgPath.moveTo(x, y); started = true } else avgPath.lineTo(x, y)
            }
            if (started) drawPath(avgPath, color = Color(0xFFE6A23C), style = Stroke(width = 1.2f))
        }

        // 时间轴标签：左 / 中 / 右
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("09:30", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
            Text("11:30/13:00", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
            Text("15:00", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
        }
    }
}

/* ---------------- 成交量迷你柱 ---------------- */

@Composable
private fun VolumeChart(d: IndexDetail, modifier: Modifier = Modifier) {
    val trends = d.trends
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val n = trends.size
        if (n < 1) return@Canvas

        var maxV = 0.0
        for (t in trends) if (t.v > maxV) maxV = t.v
        if (maxV <= 0.0) return@Canvas

        val bw = (w / n) * 0.7f
        for (i in 0 until n) {
            val x = w * i / n.toFloat()
            val bh = (trends[i].v / maxV * h).toFloat()
            // 与前一柱比较判红绿
            val up = if (i > 0) trends[i].p >= trends[i - 1].p else true
            drawRect(
                color = if (up) COLOR_UP.copy(alpha = 0.7f) else COLOR_DOWN.copy(alpha = 0.7f),
                topLeft = Offset(x, h - bh),
                size = Size(bw, bh)
            )
        }
    }
}

/* ---------------- 小工具 ---------------- */

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = COLOR_SUB, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = NumFont)
    }
}

/** 点数两位小数 */
private fun fmt(v: Double): String = String.format(Locale.CHINA, "%.2f", v)

// fmtAmount 统一定义在 WatchColumns.kt（public），此处不再重复定义，避免 Conflicting overloads。

/* ================================================================
 * 「多股同列」全屏页
 *
 * 入口：自选页顶部红条右侧「多股同列」按钮（图标 bt1.png）。
 * 功能：把当前分组下的股票，以 1/2/3/4 列的可切换网格同时显示实时行情。
 * 数据：复用 Api.stockList(token, gid)，自动刷新。
 * ================================================================ */
@Composable
fun MultiStockColumnsScreen(token: String?, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 当前分组（默认自选股 -1）
    var gid by remember { mutableStateOf(-1) }
    // 分组名映射
    var groups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    // 股票列表
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    // 每行显示几列（1 / 2 / 3 / 4）
    var columns by remember { mutableStateOf(2) }
    // 刷新
    var loading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf("") }
    // 分时走势缓存： code -> (trends, preClose)
    var trendMap by remember { mutableStateOf<Map<String, Pair<List<DetailTrend>, Double>>>(emptyMap()) }

    // 加载分组
    LaunchedEffect(token) {
        try { groups = withContext(Dispatchers.IO) { Api.watchGroups(token) }.sortedBy { it.sort } }
        catch (e: Exception) { }
    }

    // 加载股票 + 定时刷新（3 秒）
    LaunchedEffect(token, gid) {
        while (true) {
            loading = true
            try {
                val list = withContext(Dispatchers.IO) { Api.stockList(token, gid) }
                stocks = list
                android.util.Log.i("CGJ_DIAG", "stockList size=" + list.size + " firstCode=" + (list.firstOrNull()?.code ?: "null"))
                // 拉取每只股票的分时走势（并发请求，失败跳过）——避免串行导致首屏长时间空白
                val m = HashMap<String, Pair<List<DetailTrend>, Double>>()
                if (list.isNotEmpty()) {
                    val results = withContext(Dispatchers.IO) {
                        list.map { s ->
                            async {
                                try {
                                    val d = Api.detail(s.code)
                                    if (d != null && d.trends.isNotEmpty())
                                        s.code to (d.trends to d.base.preClose)
                                    else null
                                } catch (e: Exception) { null }
                            }
                        }.awaitAll()
                    }
                    for (r in results) if (r != null) m[r.first] = r.second
                }
                android.util.Log.i("CGJ_DIAG", "detail fetched m.size=" + m.size + " keys=" + m.keys.take(3))
                if (m.isNotEmpty()) trendMap = m
                val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                    .format(java.util.Date())
                lastUpdate = now
            } catch (e: Exception) { }
            loading = false
            kotlinx.coroutines.delay(3000)
        }
    }

    // 全屏铺满
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ---------- 顶栏：关闭 + 标题 + 刷新状态 ----------
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .background(RED_UP).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("多股同列", color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (loading) "刷新中…" else "更新 $lastUpdate",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp
                )
            }

            // ---------- 分组标签 + 列数切换 ----------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分组：自选股 + 用户自建组
                val groupTabs = buildList {
                    add(-1 to "自选股")
                    groups.forEach { add(it.id to it.name) }
                }
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(groupTabs) { (id, name) ->
                        val on = id == gid
                        Text(
                            text = name,
                            color = if (on) Color.White else Color(0xFF666666),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .background(
                                    if (on) RED_UP else Color(0xFFF2F2F2),
                                    androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                )
                                .clickable { gid = id }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                // 列数切换 1/2/3/4
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1, 2, 3, 4).forEach { n ->
                        val on = n == columns
                        Text(
                            text = "$n",
                            color = if (on) Color.White else Color(0xFF666666),
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(
                                    if (on) RED_UP else Color(0xFFF2F2F2),
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable { columns = n }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFEEEEEE))

            // ---------- 网格 ----------
            if (stocks.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (loading) "加载中…" else "暂无股票", color = Color(0xFF999999), fontSize = 13.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(stocks) { s ->
                        Column(
                            Modifier
                                .background(Color(0xFFFAFAFA), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFEEEEEE), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(watchNameDisplay(s.name), color = Color(0xFF111111),
                                fontSize = watchNameFontSize(s.name).sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(s.code, color = Color(0xFF888888), fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            // 分时走势：从左向右逐点延伸的折线（涨红跌绿 + 昨收基准线）
                            val resetCard = isPreOpenReset()
                            val tr = trendMap[s.code]
                            android.util.Log.i("CGJ_DIAG", "card code=" + s.code + " hit=" + (tr != null) + " trendSize=" + (tr?.first?.size ?: -1))
                            MiniMinuteChart(
                                trends = tr?.first ?: emptyList(),
                                preClose = tr?.second ?: 0.0,
                                reset = resetCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (resetCard) "--" else String.format(java.util.Locale.CHINA, "%.2f", s.price),
                                color = if (resetCard) COLOR_FLAT else colorOf(s.changePct), fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, fontFamily = NumFont
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (resetCard) "--"
                                else String.format(java.util.Locale.CHINA, "%+.2f  %+.2f%%", s.change, s.changePct),
                                color = if (resetCard) COLOR_FLAT else colorOf(s.changePct),
                                fontSize = 12.sp, fontFamily = NumFont
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多股同列卡片里的迷你分时走势图。
 * 一条从左向右逐点延伸的折线：开盘时点少、靠左；随交易推进逐步铺满整栏。
 * 涨（相对昨收）红、跌绿、平灰；含昨收基准水平线；无成交量。
 */
@Composable
private fun MiniMinuteChart(
    trends: List<DetailTrend>,
    preClose: Double,
    reset: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 盘前重置时段（08:45–09:15）：画一条位于正中间的浅灰虚线，无曲线
        if (reset) {
            val dashPath = Path().apply {
                moveTo(0f, h / 2f)
                lineTo(w, h / 2f)
            }
            drawPath(
                path = dashPath,
                color = Color(0xFFDDDDDD),
                style = Stroke(
                    width = 1.6f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 8f), 0f
                    )
                )
            )
            return@Canvas
        }

        // 无数据：画一条浅灰占位提示线，让用户明确看到分时图的位置
        if (trends.isEmpty() || trends.first().price <= 0.0) {
            drawLine(
                color = Color(0xFFEEEEEE),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.6f
            )
            return@Canvas
        }

        // 集合竞价时段（约 09:15–09:30，连续竞价前）：上游只返回 1 个点，
        // 此时不画空占位线，而是把该点（集合竞价价位）画在昨收基准线上，
        // 让用户在开盘前也能看到集合竞价的价位水平与涨跌方向。
        if (trends.size == 1) {
            val p0 = trends.first().price
            val range0 = if (preClose > 0) preClose * 0.01 else 1.0
            val top0 = maxOf(preClose, p0) + range0 * 0.35
            val bot0 = minOf(preClose, p0) - range0 * 0.35
            val span0 = (top0 - bot0).let { if (it <= 0.0) 1.0 else it }
            fun y1(p: Double): Float = (h * (1.0 - (p - bot0) / span0)).toFloat()
            // 昨收基准线
            drawLine(
                color = Color(0xFFDDDDDD),
                start = Offset(0f, y1(preClose)),
                end = Offset(w, y1(preClose)),
                strokeWidth = 1f
            )
            val c1 = when {
                p0 > preClose -> RED_UP
                p0 < preClose -> GREEN_DOWN
                else -> Color(0xFF888888)
            }
            // 该点在横轴上的位置：按其时间（min/240）定位，早期靠左、随开盘向右成长
            val x1 = ((trends.first().min.toFloat() / 240f).coerceIn(0f, 1f)) * w
            // 从最左到该点的短线段（体现「开盘到现在」的走势方向）
            drawLine(
                color = c1,
                start = Offset(0f, y1(preClose)),
                end = Offset(x1, y1(p0)),
                strokeWidth = 2.4f
            )
            // 该点本身：实心圆点，醒目
            drawCircle(color = c1, radius = 3.2f, center = Offset(x1, y1(p0)))
            return@Canvas
        }

        var maxP = preClose
        var minP = preClose
        trends.forEach { p ->
            if (p.price > maxP) maxP = p.price
            if (p.price < minP) minP = p.price
        }
        var range = maxP - minP
        if (range <= 0.0) range = if (maxP > 0) maxP * 0.01 else 1.0
        val pad = range * 0.08
        val top = maxP + pad
        val bot = minP - pad
        val span = (top - bot).let { if (it <= 0.0) 1.0 else it }
        fun yOf(p: Double): Float = (h * (1.0 - (p - bot) / span)).toFloat()

        drawLine(
            color = Color(0xFFDDDDDD),
            start = Offset(0f, yOf(preClose)),
            end = Offset(w, yOf(preClose)),
            strokeWidth = 1f
        )

        // x 轴固定为全天跨度（0 ~ 240 分钟，后端已折叠午休 11:30-13:00）。
        // 这样行情从 09:30 起由左向右“一点一点”延伸，15:00 刚好铺满整栏；
        // 不再用“当前已有数据的最大 min”作右边界，否则数据一出现就被拉满整栏。
        fun xOf(i: Int): Float = ((trends[i].min.toFloat() / 240f).coerceIn(0f, 1f)) * w

        val last = trends.last().price
        // 线色：相对昨收 涨红 / 跌绿 / 平灰 —— 线用纯色
        val lineColor = when {
            last > preClose -> RED_UP
            last < preClose -> GREEN_DOWN
            else -> Color(0xFF888888)
        }

        val path = Path()
        // 平滑曲线（中点二次贝塞尔）：消除分钟级抖动尖角，观感对标同花顺 mini 图。
        val xs = FloatArray(trends.size)
        val ys = FloatArray(trends.size)
        trends.forEachIndexed { i, p ->
            xs[i] = xOf(i)
            ys[i] = yOf(p.price)
        }
        if (trends.size == 1) {
            path.moveTo(xs[0], ys[0])
        } else {
            path.moveTo(xs[0], ys[0])
            for (i in 1 until trends.size) {
                val midX = (xs[i - 1] + xs[i]) / 2f
                val midY = (ys[i - 1] + ys[i]) / 2f
                path.quadraticBezierTo(xs[i - 1], ys[i - 1], midX, midY)
            }
            path.lineTo(xs[trends.size - 1], ys[trends.size - 1])
        }

        // 线下方面积：竖向渐变“背景色”，上端浓、往下渐淡
        val areaPath = Path().apply {
            addPath(path)
            lineTo(xOf(trends.size - 1), h)
            lineTo(xOf(0), h)
            close()
        }
        val areaBrush = Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = 0.28f),
                lineColor.copy(alpha = 0.04f)
            ),
            startY = 0f,
            endY = h
        )
        drawPath(path = areaPath, brush = areaBrush)

        // 最后画线，压在面积之上，保证线清晰、醒目加粗
        drawPath(path = path, color = lineColor, style = Stroke(width = 3.5f))
    }
}

/**
 * 股票标签行（详情页/自选列表共用）
 * 样式对标同花顺：
 *  - 主色标签（融/创/新/次新/T+0/场内）：实底暖橙色 + 米白字，无边框
 *  - 灰色标签：透明底 + 浅灰字 + 浅灰细边框
 * 横向排布，超宽可横向滚动（leftScroll=true），maxShow 限数
 */
@Composable
fun StockTagRow(
    tags: List<String>,
    maxShow: Int = 2,
    fontSize: Float = 9f,
    leftScroll: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val shown = if (maxShow <= 0) tags else tags.take(maxShow)
    val rowModifier = if (leftScroll) modifier.horizontalScroll(rememberScrollState()) else modifier
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { tag ->
            StockTagChip(tag, fontSize)
            Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable
private fun StockTagChip(tag: String, fontSize: Float = 9f) {
    val main = isMainColorTag(tag)
    val fg = if (main) Color(Config.COLOR_TAG_FG) else Color(Config.COLOR_TAG_GRAY_FG)
    val bg = if (main) Color(Config.COLOR_TAG_BG) else Color.Transparent
    Box(
        modifier = Modifier
            .background(bg)
            .then(
                if (main) Modifier
                else Modifier.border(0.8.dp, Color(Config.COLOR_TAG_GRAY_BORDER))
            )
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag,
            color = fg,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

/** 主色标签判定（实底橙）：融/创/新/次新；ETF 的 T+0/场内 用灰框 */
private fun isMainColorTag(tag: String): Boolean {
    return tag == "融" || tag == "创" || tag == "新" || tag == "次新"
}

