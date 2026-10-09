package com.chaogujia.app

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

// ===== 字体（与搜索页一致：同花顺数字字体） =====
private val DSpNumFont = FontFamily(Font(R.font.ths_regular))

// ===== 颜色 =====
// 全站涨跌配色单一源头：改这两处，全软件（K线/分时/盘口/涨跌幅/列表）同步生效
internal val RED_UP = Color(0xFFE83030)       // 涨：红 #E83030（全局统一）
internal val GREEN_DOWN = Color(0xFF009900)   // 跌：绿 #009900（图表柱体/影线/量柱/迷你图/顶部配色统一）
internal val GREEN_TEXT = Color(0xFF009900)   // 跌：绿 #009900（涨跌文字，与 GREEN_DOWN 统一）
private val GRAY_FLAT = Color(0xFF888888)    // 平：灰
private val K_NARROW_COLOR = Color(0xFF111111)  // 缩放最小时柱子统一黑色（全历史黑波浪）
// 缩放到最小（一屏约1400+根）时：蜡烛/量柱/均线全部转黑，整体成一条黑波浪线
private const val K_NARROW_VISIBLE = 1500

// ===== BST 买卖标记颜色（后端 _marks.php 定稿的 5 种 color 码）=====
// redB=加仓(红) / greyB=首次建仓(灰) / blueS=减仓(蓝) / greyS=清仓(灰) / T=当日买卖(橙)
private val BST_RED_B  = Color(0xFFF06E6E)
private val BST_GREY_B = Color(0xFF949494)
private val BST_BLUE_S = Color(0xFF7DB2F3)
private val BST_GREY_S = Color(0xFF949494)
private val BST_T      = Color(0xFFFEA65E)

/** 后端 color 码 → 前端颜色；未知码回退灰色。 */
internal fun tradeMarkColor(code: String): Color = when (code) {
    "redB" -> BST_RED_B
    "greyB" -> BST_GREY_B
    "blueS" -> BST_BLUE_S
    "greyS" -> BST_GREY_S
    "T" -> BST_T
    else -> BST_GREY_B
}

/**
 * 日期归一化：把各种来源的日期统一成 "YYYY-MM-DD"（带横线）。
 *
 * 关键背景：本工程 K 线与 marks 的日期「口径不一致」——
 *   - K线(line) 主源为同花顺 d.10jqka.com.cn，date 形如 "20260924"（8 位纯数字，无横线）；
 *     回退源腾讯 fqkline 形如 "2026-09-24"。
 *   - marks 来自后端 _marks.php 的 date('Y-m-d')，形如 "2026-09-24"（带横线）。
 * 若直接用原始字符串比较（如 markByDate[k.date]），同花顺源必然全量失配，
 * 导致 BST 徽标一条都画不出来。所有「按日期对齐」的比较必须先用本函数归一化。
 * 解析失败则原样返回（避免越界崩溃）。
 */
internal fun normDateKey(raw: String): String {
    if (raw.isEmpty()) return raw
    val digits = raw.filter { it.isDigit() }
    if (digits.length >= 8) {
        return digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8)
    }
    val parts = raw.split("-", "/", ".")
    if (parts.size == 3) {
        return parts[0].padStart(4, '0') + "-" + parts[1].padStart(2, '0') + "-" + parts[2].padStart(2, '0')
    }
    return raw
}

/** 徽标字母绘制用的 Paint（白字、居中、随 density 缩放的字号）。 */
internal fun tradeMarkPaint(): android.graphics.Paint = android.graphics.Paint().apply {
    isAntiAlias = true
    color = android.graphics.Color.WHITE
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 22f
    isFakeBoldText = true
}

/**
 * 十字光标「无光标」哨兵值。
 *
 * 关键：所有图层判断是否激活统一用 `cursorMin >= CURSOR_ACTIVE_MIN`（即 -15f），
 * 因此「关闭」必须写入一个 < -15f 的值，否则会被误判为「有光标」并回落到 trends[0]（09:30）。
 * 历史 Bug：曾用 -1f 作关闭值，-1f >= -15f 恒为 true，导致点击关闭后光标"跳"到 09:30。
 */
private const val CURSOR_NONE = -99f
private const val CURSOR_ACTIVE_MIN = -15f
private val TEXT_MAIN = Color(0xFF222222)
private val TEXT_SUB = Color(0xFF888888)
private val TEXT_COL_LABEL = Color(0xFF555555)  // 顶栏三列标签专用（比 TEXT_SUB 更黑）
private val LINE_GRID = Color(0xFFEEEEEE)
private val LINE_AXIS = Color(0xFFF0F0F0)
// 时间轴「尺子」线条/刻度颜色（比普通网格略深，保证看得清）
private val TIME_AXIS_LINE = Color(0xFFBDBDBD)
// 时间轴条带：浅灰底（非纯白）、高度
private val TA_BG = Color(0xFFF5F5F5)
private val MINUTE_BORDER = Color(0xFFDDDDDD)      // 分时画布淡灰边框
private val AUCTION_BG = Color(0xFFEFEFEF)         // 集合竞价栏灰底（与白色分时区明显区分）
private val TA_H = 22.dp
// 缩放按钮：浅灰半透明圆底 + 黑色符号
private val KBTN_BG = Color(0x66EEEEEE)
private val KBTN_FG = Color(0xFF5A5A5A)
// 均线图例“均线”按钮：浅灰底/近白，极淡
private val MA_BTN_BG = Color(0xFFEDEDED)
private val MA_BTN_FG = Color(0xFF9E9E9E)
// 筹码按钮：关闭=黑灰近黑（与头部信息同调），打开=涨红
private val CHIP_BTN_FG = Color(0xFF2A2A2A)
private val CHIP_BTN_ON = Color(0xFFE64A3B)
// 筹码峰：套牢盘（现价之上）=蓝，获利盘（现价之下）=红
private val CHIP_TRAPPED = Color(0xFF3A7BD5)
private val CHIP_PROFIT = Color(0xFFE64A3B)
private val TICK_RED = Color(0x33E64A3B)     // 蜡烛红（半透明填充）
private val TICK_GREEN = Color(0x3319A05A)   // 蜡烛绿（半透明填充）
// ===== 副图指标切换：标签框（长方形、无圆角、宽自适应包住 名称+▼）=====
private val SUB_TAG_BORDER = Color(0xFFBBBBBB)   // 标签框描边：浅灰
private val SUB_TAG_BG = Color(0xB3EEEEEE)       // 标签框底：近白
private val SUB_TAG_FG = Color(0xFF333333)       // 标签框文字
// ===== MACD 三线（柱改线后）=====
private val MACD_DIF_COLOR = Color(0xFF111111)   // DIF 快线：黑
private val MACD_DEA_COLOR = Color(0xFFE64A3B)   // DEA 慢线：红
private val MACD_BAR_COLOR = Color(0xFF2E8BE6)   // MACD 原柱改线：蓝
private val VOL_LABEL_COLOR = Color(0xFF111111)  // 栏顶“量：”文字：黑
private val CURSOR_LINE_COLOR = Color(0xFF000000) // 十字光标竖线：纯黑
private val CURSOR_BOX_BG = Color(0xF21E88E5)     // 十字光标交点数值框：蓝色半透明（透明度已调高）
private val CURSOR_BOX_TX = Color(0xFFFFFFFF)     // 十字光标数值框文字：白

/**
 * 个股详情页
 *
 * 入口：搜索结果行点击进入（携带整条列表，支持左右切换上一只/下一只）。
 *
 * ★ 导航栈改造后：
 *  - 详情状态由 MainActivity（App 级）托管，红条由 MainActivity 统一渲染，本页默认不再自绘红条
 *    （showOwnHeader=false），从而消除"两条红条 / 图标重复"问题。
 *  - 下标受控：外部通过 index + onIndexChange 控制当前股票，本页不再自带 idx 状态。
 *  - 名称回吐：当前股票名/代码变化时通过 onNameChange 通知外部更新红条。
 *
 * @param token    登录 token（用于右上角搜索页入口，可空）
 * @param list     当前列表（搜索结果/自选列表）
 * @param index    当前股票在 list 中的下标（受控）
 * @param showOwnHeader 是否自绘顶部红条（默认 true，独立使用本页时用；
 *                      被 App 级托管时应传 false，避免红条重复）
 * @param onIndexChange 下标变化回调（受控模式；◀▶ 切换时通知外部）
 * @param onNameChange  当前股票名称变化回调（供外部红条显示）
 * @param onClose  返回回调（返回上一步）
 * @param onOpenSearch 点右上角放大镜 → 打开搜索页
 */
@Composable
fun StockDetailPage(
    token: String,
    perm: PermissionInfo = PermissionInfo(),
    list: List<StockItem>,
    index: Int,
    showOwnHeader: Boolean = true,
    onIndexChange: (Int) -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onClose: () -> Unit,
    onOpenSearch: () -> Unit = {},
    // 会员引导「立即开通」→ App 级打开「开通/续费会员」页
    onOpenVipPage: () -> Unit = {}
) {
    // 当前下标（受控：以外部 index 为准，内部只做边界保护）
    val idx = index.coerceIn(0, max(0, list.size - 1))
    val current: StockItem? = list.getOrNull(idx)

    // 下标变化 → 向外回吐（受控模式）
    fun setIdx(next: Int) {
        val n = next.coerceIn(0, max(0, list.size - 1))
        if (n != index) onIndexChange(n)
    }

    // 当前周期：minute / day / week / month
    var period by remember { mutableStateOf("minute") }

    // 详情数据
    var detail by remember { mutableStateOf<StockDetail?>(null) }
    // K线选中的柱子下标（对应 detail.line 的下标；null=未选中，头部显示最新一天）
    var selK by remember { mutableStateOf<Int?>(null) }
        var maPeriods by remember { mutableStateOf(MA_DEFAULT_PERIODS) }
        // 均线设置页显隐（父层持有，齿轮与 ⚙ 共用）
        var showMaSetting by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // ================= 下单弹窗（底部上弹）=================
    var showOrderSheet by remember { mutableStateOf(false) }
    var orderBrokers by remember { mutableStateOf<List<MyBroker>>(emptyList()) }
    var orderBroker by remember { mutableStateOf<MyBroker?>(null) }
    val sdCtx = LocalContext.current
    var orderSide by remember { mutableStateOf("") }
    // H11：真实持仓（用于卖出页「可卖多少股」）——按当前账户拉取 positions.php
    var orderPositions by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    // H11：当前股票在当前账户下的可卖股数（持仓多少股 = 可卖多少股）
    val orderSellableQty = remember(current?.code, orderBroker?.id, orderPositions) {
        val c = current?.code ?: ""
        orderPositions.firstOrNull { it.code == c }?.quantity ?: 0
    }
    // 第十轮需求⑤：用户在弹窗上方点五档价格 → 暂存，传给表单回填
    var orderPickPrice by remember { mutableStateOf(0.0) }
    // H11：下单弹窗打开且账户就绪时，拉取该账户持仓（供卖出页「可卖多少股」）
    LaunchedEffect(showOrderSheet, orderBroker?.id) {
        val bid = orderBroker?.id ?: 0
        orderPositions = if (showOrderSheet && bid > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Api.positions(token, bid)
            }
        } else {
            emptyList()
        }
    }
    var showAuction by remember {
        mutableStateOf(ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
            .getBoolean("minute_auction", false))
    }
    // 分时坐标模式：true=百分比对称(昨收0%线居中,同花顺默认) false=满占(真实高低填满)
    var symmetricAxis by remember {
        mutableStateOf(ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
            .getBoolean("minute_symmetric", true))
    }
    // 盘口五档「手数 / 委托金额」显示切换：true=显示委托金额(元→万/亿)，false=显示手数
    var showOrderAmount by remember {
        mutableStateOf(ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
            .getBoolean("orderbook_show_amount", false))
    }
    // 下方内容切换栏（看点/资讯/盘口/资金/社区/简况/财务）当前选中下标
    var sectionTab by remember { mutableStateOf(0) }
    // ===== 会员权限门禁（个股详情页，第16轮）=====
    // 无权限时弹出的引导标题（null=不弹）；非空即展示 MemberGateDialog
    var gateTitle by remember { mutableStateOf<String?>(null) }
    // 内容 tab 下标 → feature_key
    val sectionFeat = listOf(
        "sd_section_recap", "sd_section_news", "sd_level2",
        "sd_section_fund", "sd_section_community", "sd_section_f10", "sd_section_finance"
    )
    val sectionName = listOf("看点", "资讯", "盘口", "资金", "社区", "简况(F10)", "财务")
    /** 受限功能统一入口：有权限执行 action，无权限弹会员引导 */
    fun guard(feat: String, title: String, action: () -> Unit) {
        if (perm.has(feat)) action() else gateTitle = title
    }
    // 筹码峰展开开关（父层持有，MaLegend 按钮与 KLineChart 共用）
    var showChip by remember { mutableStateOf(false) }
    // 所属概念（含实时涨跌幅）
    var concept by remember { mutableStateOf<Api.Concept?>(null) }
    // 是否已加自选（用于底部“自选”按钮）
    var watched by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    // 十字光标分钟（-1 = 无）：由本页持有，传给 MinuteChart，并驱动顶部「分时信息条」联动
    var cursorMin by remember { mutableStateOf(-1f) }
    // 当日真实成交流水（来自 trades.php，含盘中今日）：驱动分时图 B/S 标
    // 说明：后端 marks 对「今日进行中交易日」不产出，故分时 B/S 不能依赖 marks 的当日条目，
    //       改用用户真实成交流水（direction 1=买/2=卖）。每日至多一次拉取 + 3 秒增量刷新。
    var todayTrades by remember { mutableStateOf<List<TradeRecord>>(emptyList()) }
    // 五档盘口（第十轮：给下单弹窗透出买卖盘，可点击填价；同时取真实涨跌停价）
    var orderBook by remember { mutableStateOf<OrderBook?>(null) }
    val scope = rememberCoroutineScope()
    // A2-D 修复：进入页面即预拉券商账户（否则点下单后才拉，异步期间「可买 N 股」算不出 / 为 0）
    LaunchedEffect(Unit) {
        if (orderBrokers.isEmpty()) {
            val ls = withContext(Dispatchers.IO) {
                try { Api.brokerList(token) } catch (e: Exception) { emptyList<MyBroker>() }
            }
            if (ls.isNotEmpty()) {
                orderBrokers = ls
                if (orderBroker == null) orderBroker = AccountStore.pick(sdCtx, ls) { it.id }
            }
        }
    }
    // 第十轮需求⑤⑦：行情页每 3 秒轮询五档；用于弹窗上方透出的买卖盘 + 实时股价
    LaunchedEffect(current?.code) {
        val code = current?.code ?: return@LaunchedEffect
        while (true) {
            val ob = withContext(Dispatchers.IO) {
                try { Api.orderbook(code, 1, 20) } catch (e: Exception) { null }
            }
            if (ob != null) orderBook = ob
            kotlinx.coroutines.delay(3000)
        }
    }
    // 当日成交流水轮询：每 3 秒拉一次 trades.php，过滤「当前股票 + 今日」，供分时图 B/S 标
    LaunchedEffect(current?.code, orderBroker?.id) {
        val code = current?.code ?: return@LaunchedEffect
        while (true) {
            val bid = orderBroker?.id ?: 0
            if (bid > 0) {
                val all = withContext(Dispatchers.IO) {
                    try { Api.trades(token, bid) } catch (e: Exception) { emptyList<TradeRecord>() }
                }
                val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                todayTrades = all.filter { it.code == code && it.time.trim().startsWith(todayKey) }
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    // 实时股价：优先用 detail 当前价，退回五档买一价
    val livePrice = detail?.base?.price?.takeIf { it > 0.0 }
        ?: orderBook?.price?.takeIf { it > 0.0 }
        ?: orderBook?.bids?.firstOrNull()?.price
        ?: 0.0

    // 切换股票 或 切换周期 → 拉数据
    // 注意：分时是同一份数据（trends），K线按周期取
    // 切换股票 → 拉概念归属 + 自选状态
    LaunchedEffect(current?.code) {
        val code = current?.code ?: return@LaunchedEffect
        concept = null
        watched = null
        val c = withContext(Dispatchers.IO) { Api.concept(code) }
        if (c.ok) concept = c.best
        val w = withContext(Dispatchers.IO) {
            try { Api.allWatchCodes(token).contains(code) } catch (e: Exception) { false }
        }
        watched = w
    }
    // ★ 交易数据版本号：一键模拟/下单/改单成功后 Config.bumpTradeData()，这里重拉 detail 刷新 marks
    val tradeVer = Config.tradeDataVersion
    LaunchedEffect(current?.code, period, tradeVer) {
        val code = current?.code ?: return@LaunchedEffect
        loading = true
        // 分时用 day 数据（同一份 trends）；五日分时 → k=fiveday；其余照原周期
        val k = if (period == "minute") "day" else period
        // ★ 带上 token：detail.php 的 marks（BST 买卖标记）依赖 current_user()，
        //   不带 token 时 marks 恒为空 → K线/分时徽标一个都不画。
        // ★ 秒出策略：优先命中本地磁盘缓存（命中即"零点几秒"出画面）。
        //   若命中缓存，则之后在后台用 forceRefresh 静默拉一次最新数据覆盖刷新；
        //   若未命中（首次访问），第一次请求本身就是"最新"，无需再打第二次。
        val hitCache = withContext(Dispatchers.IO) {
            DetailCache.load(Net.appContext, code, k, 1) != null
        }
        val d = withContext(Dispatchers.IO) { Api.detail(code, k, 1, token) }
        if (d != null) detail = d
        selK = null   // 换股票/换周期 → 取消 K 线选中
        cursorMin = CURSOR_NONE   // 换股票/换周期 → 复位十字光标（信息条恢复显示最新值）
        loading = false
        if (hitCache) {
            // 后台静默刷新：强制绕过缓存拉最新一次，成功后覆盖展示（失败忽略，保留缓存画面）
            try {
                val fresh = withContext(Dispatchers.IO) { Api.detail(code, k, 1, token, forceRefresh = true) }
                if (fresh != null) detail = fresh
            } catch (e: Exception) { /* 静默刷新失败：保留已展示的缓存数据 */ }
        }
    }

    // 名称/代码变化 → 回吐给外部（红条显示用）
    LaunchedEffect(current?.code, detail?.name) {
        val nm = (detail?.name?.ifEmpty { null }) ?: current?.name ?: ""
        if (nm.isNotEmpty()) onNameChange(nm)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // detail-overlay-swallow：吞掉自身空白区点击，阻止穿透到下层 IndexBar 触发「编辑表头」
            .pointerInput(Unit) { detectTapGestures { /* 空白区：无操作，仅拦截 */ } }
    ) {
        // ================= 内容区：顶红条以下全部可上滑（内容从红条下方穿过被盖住）=================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(0.dp))

            // ================= 头部数据区 =================
            // selK：K线被选中的柱子下标（null=未选中，显示最新一天）
            DetailHeadData(detail, selK)

            // ================= 盘后固定价格交易（全 A 股）=================
            // 全市场均适用；仅当该股当日有盘后成交时显示。默认收起，点击展开分时图+分钟明细。
            AfterHoursBar(detail)

                // ================= 标签栏（跟随上滑）=================
                PeriodTabs(period, onPick = { period = it; selK = null; cursorMin = CURSOR_NONE }, onGear = { showMaSetting = true })
                // 分时信息条：仅分时/五日视图显示（跟随上滑）
                if (period == "minute" || period == "fiveday") {
                    val infoTrends = cleanMinuteTrends(detail?.trends ?: emptyList())
                    MinuteInfoBar(
                        trends = infoTrends,
                        preClose = detail?.base?.preClose ?: 0.0,
                        cursorMin = cursorMin
                    )
                }
                // 均线图例（跟随上滑）
                if (period == "day" || period == "week" || period == "month") MaLegend(maPeriods, showChip = showChip, onToggleChip = { showChip = !showChip })
                // ---- 图表区（固定高度，下方预留其它功能模块）----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_AREA_HEIGHT)
                ) {
                    when (period) {
                        "minute" -> MinuteChart(
                            d = detail,
                            showAuction = showAuction,
                            symmetric = symmetricAxis,
                            cursorMin = cursorMin,
                            showAmount = showOrderAmount,
                            todayTrades = todayTrades,
                            onCursorChange = { cursorMin = it },
                            onPickPrice = { orderPickPrice = it },
                            canSub = perm.has("sd_kline_sub"),
                            onLockedSub = { gateTitle = "K线副图指标" },
                            canBs = perm.has("sd_bs_marks")
                        )
                        "fiveday" -> FiveDayChart(detail)
                        else -> KLineChart(detail, selK, maPeriods, showChip = showChip, onMaPeriodsChange = { maPeriods = it }, onSelect = { selK = it }, canBs = perm.has("sd_bs_marks"), onLockedBs = { gateTitle = "买卖点标记" })
                    }
                    // 打开相应图表时判权：无权则提示一次会员引导（仅分时/K线有 BS 标记）
                    val bsViews = period == "minute" || period == "day" || period == "week" || period == "month"
                    var bsNotified by remember(current?.code) { mutableStateOf(false) }
                    LaunchedEffect(period, current?.code) {
                        if (bsViews && !perm.has("sd_bs_marks") && !bsNotified) {
                            bsNotified = true
                            gateTitle = "买卖点标记"
                        }
                    }
                    // ---- T/B/S 明细提示框：叠加在图表区左上角（均线按钮那行正下方）----
                    if (period == "day" || period == "week" || period == "month") {
                        val selMark = remember(selK, detail?.marks) {
                            val ln = detail?.line
                            val ms = detail?.marks
                            val sk = selK
                            if (sk != null && ln != null && sk in ln.indices && !ms.isNullOrEmpty()) {
                                val key = normDateKey(ln[sk].date)
                                ms.firstOrNull { normDateKey(it.date) == key }
                            } else null
                        }
                        if (selMark != null) {
                            MarkDetailOverlay(selMark)
                        }
                    }
                    if (loading && detail == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = TEXT_SUB, fontSize = 14.sp)
                        }
                    }
                }
                // ================= 下方内容切换栏（看点/资讯/盘口/资金/社区/简况/财务）=================
                // 属于滚动内容，跟随上滑；7 项横排，屏幕一次显示前 6 项，可左右横滑露出第 7 项
                DetailSectionTabs(
                    selected = sectionTab,
                    onPick = { i ->
                        guard(sectionFeat.getOrElse(i) { "" }, sectionName.getOrElse(i) { "该功能" }) {
                            sectionTab = i
                        }
                    }
                )

                // ---- 底部栏占位（防止滚动内容被铉底的底部栏遮挡）----
            Spacer(Modifier.height(DETAIL_BAR_HEIGHT))
            // TODO: 后续功能模块追加在此处下方（自动跟随上滑）
        }
        if (showMaSetting) {
            MaSettingDialog(
                initial = maPeriods,
                onDismiss = { showMaSetting = false },
                onConfirm = { maPeriods = it; showMaSetting = false },
                auction = showAuction,
                onAuctionChange = { v ->
                    showAuction = v
                    ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
                        .edit().putBoolean("minute_auction", v).apply()
                },
                symmetric = symmetricAxis,
                onSymmetricChange = { v ->
                    symmetricAxis = v
                    ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
                        .edit().putBoolean("minute_symmetric", v).apply()
                },
                showOrderAmount = showOrderAmount,
                onShowOrderAmountChange = { v ->
                    showOrderAmount = v
                    ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE)
                        .edit().putBoolean("orderbook_show_amount", v).apply()
                }
            )
        }
        // ================= 顶部红条（浮层，不跟随上滑）=================
        if (showOwnHeader) {
            Box(Modifier.align(Alignment.TopCenter).zIndex(2f)) {
                DetailHeaderBar(
                    name = detail?.name?.ifEmpty { current?.name ?: "" } ?: "",
                    code = current?.code ?: "",
                    tags = current?.tags ?: emptyList(),
                    canPrev = idx > 0,
                    canNext = idx < list.size - 1,
                    onBack = onClose,
                    onPrev = { setIdx(idx - 1) },
                    onNext = { setIdx(idx + 1) },
                    onOpenSearch = onOpenSearch
                )
            }
        }
        // ---- 底部功能栏：钉在屏幕最底，不跟随上滑 ----
        DetailBottomBar(
                concept = concept,
                watched = watched,
                onOrder = {
                    if (!perm.has("sd_order")) { gateTitle = "下单"; return@DetailBottomBar }
                    showOrderSheet = true
                    // 拉取券商账户列表（默认选中第一个）
                    if (orderBrokers.isEmpty()) {
                        scope.launch {
                            val ls = withContext(Dispatchers.IO) { Api.brokerList(token) }
                            orderBrokers = ls
                            if (orderBroker == null) orderBroker = AccountStore.pick(sdCtx, ls) { it.id }
                        }
                    }
                },
                onDiagnose = { guard("sd_diagnose", "诊股") { /* TODO 诊股 */ } },
                onToggleWatch = {
                    if (!perm.has("sd_add_watch")) { gateTitle = "加自选"; return@DetailBottomBar }
                    val code = current?.code ?: return@DetailBottomBar
                    scope.launch {
                        val isW = watched == true
                        val r = withContext(Dispatchers.IO) {
                            if (isW) Api.delWatch(token, code, 0) else Api.addWatch(token, code, 0)
                        }
                        if (r.ok) watched = !isW
                    }
                },
                onMore = { guard("sd_more", "功能") { /* TODO 功能菜单 */ } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
            )
        // ---- 下单弹窗：屏幕下方上弹 ----
        if (showOrderSheet) {
            TradeOrderSheet(
                brokers = orderBrokers,
                current = orderBroker,
                side = orderSide,
                code = current?.code ?: "",
                name = current?.name ?: "",
                token = token,
                brokerAccountId = orderBroker?.id ?: 0,
                availableCash = orderBroker?.available ?: 0.0,
                sellableQty = orderSellableQty,
                livePrice = livePrice,
                limitUp = orderBook?.limitUp ?: 0.0,
                limitDown = orderBook?.limitDown ?: 0.0,
                asks = orderBook?.asks ?: emptyList(),
                bids = orderBook?.bids ?: emptyList(),
                onPickPrice = { p -> orderPickPrice = p },
                pickPrice = orderPickPrice,
                onBack = { orderSide = "" },
                onSwitch = { orderBroker = it; AccountStore.set(sdCtx, it.id) },
                onBuy = { orderSide = "buy" },
                onSell = { orderSide = "sell" },
                onCancelOrder = { /* TODO 撤 */ },
                onDismiss = { showOrderSheet = false; orderSide = "" }
            )
        }
        // 会员专享引导弹窗
        if (gateTitle != null) {
            MemberGateDialog(
                title = gateTitle!!,
                onDismiss = { gateTitle = null },
                onOpen = { gateTitle = null; onOpenVipPage() }
            )
        }
    }
    }

// ============================================================
// 顶部红条：[←] ◀ 名称/代码 ▶ [放大镜][机器人]
// ============================================================
private val D_BAR_HEIGHT = 74.dp
private val D_ICON_BIG = 25.dp
private val D_ICON_SMALL = 24.dp

@Composable
private fun DetailHeaderBar(
    name: String,
    code: String,
    tags: List<String> = emptyList(),
    canPrev: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
                )
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().height(D_BAR_HEIGHT),
        contentAlignment = Alignment.Center
      ) {
        // 左侧：返回箭头（H10：命中区放大到 48dp；H11：去掉点击水波纹，无任何特效）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "←",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 中间：◀ 名称/代码 ▶
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ◀ 上一只
            Text(
                text = "◀",
                color = if (canPrev) Color.White else Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = canPrev) { onPrev() }
                    .padding(horizontal = 6.dp)
            )
            // 名称（大字，上）+ 代码（小字，下）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = code,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = DSpNumFont,
                        maxLines = 1
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.widthIn(max = 70.dp)) {
                            DetailTagRow(tags = tags, maxShow = 2, fontSize = 9f)
                        }
                    }
                }
            }
            // ▶ 下一只
            Text(
                text = "▶",
                color = if (canNext) Color.White else Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = canNext) { onNext() }
                    .padding(horizontal = 6.dp)
            )
        }

        // 右侧：放大镜 + 机器人（与自选页位置一致）
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_watch_mid),
                contentDescription = null,
                modifier = Modifier.height(D_ICON_SMALL),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(R.drawable.ic_watch_right),
                contentDescription = "搜索",
                modifier = Modifier
                    .size(D_ICON_BIG)
                    .clickable { onOpenSearch() },
                contentScale = ContentScale.FillBounds
            )
        }
    }
}
}

// ============================================================
// 头部数据区：左大字当前价 + 涨跌；右侧三竖列
//   列1：最高 / 最低 / 开盘
//   列2：市值 / 流通 / 市盈
//   列3：量比 / 换 / 额
// ============================================================
@Composable
private fun DetailHeadData(d: StockDetail?, selK: Int? = null) {
    val b = d?.base
    val line = d?.line ?: emptyList()
    // 选中的那根柱子（越界则回退为未选中）
    val kp: KLinePoint? = selK?.takeIf { it in line.indices }?.let { line[it] }

    if (kp != null) {
        DetailHeadSelected(d, line, selK!!, kp)
    } else {
        DetailHeadLatest(b)
    }
}

/** 未选中：显示今天快照（原逻辑，3 行 × 3 列） */
@Composable
private fun DetailHeadLatest(b: StockBase?) {
    val price = b?.price ?: 0.0
    val chg = b?.change ?: 0.0
    val pct = b?.changePct ?: 0.0
    val color = when {
        chg > 0 -> RED_UP
        chg < 0 -> GREEN_TEXT
        else -> GRAY_FLAT
    }

    // 昨收基准线：高/低/开 与它比 —— 线上红、线下绿、相等黑
    val preClose = b?.preClose ?: 0.0
    fun baseColor(v: Double): Color = when {
        preClose <= 0.0 -> TEXT_MAIN
        v > preClose -> RED_UP
        v < preClose -> GREEN_TEXT
        else -> TEXT_MAIN
    }
    // 量比：> 1.0 红，< 1.0 绿，= 1.0 黑
    val volRatio = b?.volRatio ?: 0.0
    val vrColor = when {
        volRatio > 1.0 -> RED_UP
        volRatio < 1.0 -> GREEN_TEXT
        else -> TEXT_MAIN
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：当前价（大字，按字符数自适应防溢出） + 涨跌额/幅
        Column(modifier = Modifier.width(104.dp)) {
            val priceStr = fmtNum(price)
            val priceFont = when {
                priceStr.length <= 5 -> 30.sp
                priceStr.length <= 6 -> 27.sp
                priceStr.length <= 7 -> 24.sp
                priceStr.length <= 8 -> 21.sp
                else -> 19.sp
            }
            Text(
                text = priceStr,
                color = color,
                fontSize = priceFont,
                fontWeight = FontWeight.Bold,
                fontFamily = DSpNumFont,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${fmtSignedPct(chg)}  ${fmtSignedPct(pct)}%",
                color = color,
                fontSize = 13.sp,
                fontFamily = DSpNumFont
            )
        }

        // 右：三竖列
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 列1：高/低/开 —— 与昨收比（线上红、线下绿、相等黑）
            DetailColC(
                Modifier.width(70.dp),
                listOf(
                    Triple("高", fmtNum(b?.high), baseColor(b?.high ?: 0.0)),
                    Triple("低", fmtNum(b?.low), baseColor(b?.low ?: 0.0)),
                    Triple("开", fmtNum(b?.open), baseColor(b?.open ?: 0.0))
                )
            )
            // 列2：统一黑色（内容长，多给权重）
            DetailColC(
                Modifier.width(88.dp),
                listOf(
                    Triple("市值", fmtCap(b?.mktCap), TEXT_MAIN),
                    Triple("流通", fmtCap(b?.floatCap), TEXT_MAIN),
                    Triple("市盈@TTM", fmtPeLabel(b?.pe), TEXT_MAIN)
                )
            )
            // 列3：量比单独判断（>1 红 / <1 绿 / =1 黑），其余黑
            DetailColC(
                Modifier.width(70.dp),
                listOf(
                    Triple("量比", fmtNum2(b?.volRatio), vrColor),
                    Triple("换", fmtPct(b?.turnover), TEXT_MAIN),
                    Triple("额", fmtAmt(b?.amount), TEXT_MAIN)
                )
            )
        }
    }
}

/** 选中某根柱子：左大字=那天收盘/涨跌；右侧 2 行 × 3 列（高·低 / 开·换 / 量·额）；下方全屏标签条 */
@Composable
private fun DetailHeadSelected(d: StockDetail?, line: List<KLinePoint>, idx: Int, kp: KLinePoint) {
    // 那天涨跌：前收 = 上一根收盘（第一根用自身开盘近似）
    val prevClose = if (idx > 0) line[idx - 1].close else kp.open
    val chg = kp.close - prevClose
    val pct = if (prevClose > 0) chg / prevClose * 100.0 else 0.0
    val color = when {
        chg > 0 -> RED_UP
        chg < 0 -> GREEN_TEXT
        else -> GRAY_FLAT
    }

    // 换手率：优先取东财 f58 真值(kp.turnover)；无则估算(流通股 = 今日流通市值(亿)*1e8 / 今日价；换手% = vol(手)*100 / 流通股 * 100)
    val b = d?.base
    val floatShares = if (b != null && b.price > 0 && b.floatCap > 0) b.floatCap * 1e8 / b.price else 0.0
    val turnoverEst = if (kp.turnover > 0.0) kp.turnover else if (floatShares > 0) kp.vol * 100.0 / floatShares * 100.0 else Double.NaN
    // 成交额(万)：优先用东财真值 kp.amount（万元）；无则回退估算 vol(手) * 100 * 均价(≈(开+收+高+低)/4)
    val avgP = (kp.open + kp.close + kp.high + kp.low) / 4.0
    val amountEst = if (kp.amount > 0.0) kp.amount else kp.vol * 100.0 * avgP / 10000.0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：那天收盘价（大字） + 那天涨跌额/幅
            Column(modifier = Modifier.width(110.dp)) {
                Text(
                    text = fmtNum(kp.close),
                    color = color,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DSpNumFont
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${fmtSigned(chg)}  ${fmtSigned(pct)}%",
                    color = color,
                    fontSize = 13.sp,
                    fontFamily = DSpNumFont
                )
            }

            // 右：2 行 × 3 列（列1 高/低、列2 开/换、列3 量/额）
            Row(modifier = Modifier.weight(1f)) {
                DetailCol(
                    Modifier.weight(1f),
                    listOf(
                        "高" to fmtNum(kp.high),
                        "低" to fmtNum(kp.low)
                    ),
                    color
                )
                DetailCol(
                    Modifier.weight(1f),
                    listOf(
                        "开" to fmtNum(kp.open),
                        "换" to (if (turnoverEst.isNaN()) "--" else fmtPct(turnoverEst))
                    ),
                    color
                )
                DetailCol(
                    Modifier.weight(1f),
                    listOf(
                        "量" to fmtVolWan(kp.vol),
                        "额" to fmtAmt(amountEst)
                    ),
                    color
                )
            }
        }

        // ===== 全屏标签条：2024/10/22  至今涨幅 113.83%  473周期 =====
        val lastClose = line.lastOrNull()?.close ?: kp.close
        val sincePct = if (kp.close > 0) (lastClose - kp.close) / kp.close * 100.0 else 0.0
        val nPeriod = line.lastIndex - idx
        val sinceColor = when {
            sincePct > 0 -> RED_UP
            sincePct < 0 -> GREEN_TEXT
            else -> GRAY_FLAT
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .background(Color(0xFFF2F3F5), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(kp.date, color = TEXT_MAIN, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "至今涨幅 ",
                color = TEXT_SUB,
                fontSize = 12.sp
            )
            Text(
                "${if (sincePct > 0) "+" else ""}${String.format(java.util.Locale.CHINA, "%.2f", sincePct)}%",
                color = sinceColor,
                fontSize = 12.sp,
                fontFamily = DSpNumFont
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "$nPeriod 周期",
                color = TEXT_SUB,
                fontSize = 12.sp,
                fontFamily = DSpNumFont
            )
        }
    }
}

/** 成交量（手）：<1万显示 X手；>=1万显示 X.XX万手 */
private fun fmtVolWan(volHand: Double): String {
    if (volHand <= 0) return "--"
    return if (volHand >= 10000) {
        String.format(java.util.Locale.CHINA, "%.2f万", volHand / 10000.0)
    } else {
        String.format(java.util.Locale.CHINA, "%.0f", volHand)
    }
}

/** 列标签：支持 "主@上标" 语法，@ 后面的字做成小号上标（如 市盈@TTM） */
@Composable
private fun colLabel(raw: String): androidx.compose.ui.text.AnnotatedString {
    val i = raw.indexOf('@')
    if (i < 0) return androidx.compose.ui.text.AnnotatedString(raw)
    val main = raw.substring(0, i)
    val sup = raw.substring(i + 1)
    return buildAnnotatedString {
        append(main)
        withStyle(
            SpanStyle(
                fontSize = 9.sp,
                baselineShift = BaselineShift(0.30f)
            )
        ) { append(sup) }
    }
}
/** 一竖列：3 行「标签 值」，标签灰、值随涨跌色 */
@Composable
private fun DetailCol(modifier: Modifier, rows: List<Pair<String, String>>, valueColor: Color) {
    Column(modifier = modifier) {
        rows.forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
                Text(
                    text = colLabel(label),
                    color = TEXT_COL_LABEL,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(1.dp))
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    color = valueColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DSpNumFont,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }
            if (i != rows.size - 1) Spacer(Modifier.height(6.dp))
        }
    }
}

/** 逐行指定值颜色的版本（标签保持灰色，值与颜色一一对应） */
@Composable
private fun DetailColC(modifier: Modifier, rows: List<Triple<String, String, Color>>) {
    Column(modifier = modifier) {
        rows.forEachIndexed { i, (label, value, vc) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
                Text(
                    text = colLabel(label),
                    color = TEXT_COL_LABEL,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(1.dp))
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    color = vc,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DSpNumFont,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.End
                )
            }
            if (i != rows.size - 1) Spacer(Modifier.height(6.dp))
        }
    }
}
// ============================================================
// 标签栏：分时 | 日K | 周K | 月K
// ============================================================
/** 均线图例：一行窄间距小字，每条用其自身颜色（M5 黑 / M10 橙 …） */
/**
 * T/B/S 明细提示框：显示在 K 线图左上角。
 *  - T：显示总买入股数与总卖出股数（多笔按方向汇总）
 *  - B：只显示买入总股数
 *  - S：只显示卖出总股数
 * 背景 #FED9BC（稍透明）+ 黑字。
 */
@Composable
private fun MarkDetailOverlay(mark: TradeMark) {
    var buyQty = 0.0
    var sellQty = 0.0
    for (t in mark.trades) {
        when (t.dir) {
            "B" -> buyQty += t.qty
            "S" -> sellQty += t.qty
        }
    }
    val text = when (mark.kind) {
        "T" -> "买 " + fmtQty(buyQty) + " 股   卖 " + fmtQty(sellQty) + " 股"
        "B" -> "买 " + fmtQty(buyQty) + " 股"
        "S" -> "卖 " + fmtQty(sellQty) + " 股"
        else -> ""
    }
    if (text.isEmpty()) return
    Box(
        modifier = Modifier
            .padding(start = 6.dp, top = 2.dp)
            .background(Color(0xB3FED9BC))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = Color.Black, fontSize = 11.sp)
    }
}

/** 股数格式化：去掉多余小数（整百股直接显示整数）。 */
private fun fmtQty(v: Double): String {
    return if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v)
}

@Composable
private fun MaLegend(
    periods: List<Int>,
    showChip: Boolean = false,
    onToggleChip: () -> Unit = {}
) {
    if (periods.isEmpty()) return
    // 均线标签仅展示，无点击功能；图例固定显示
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧均线标签（浅灰，纯展示）
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(MA_BTN_BG)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("均线", fontSize = 10.sp, color = MA_BTN_FG, lineHeight = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        periods.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.width(8.dp))
            Text(
                text = "M$p",
                color = maColorOf(p),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 12.sp
            )
        }
        // 最右端：前复权说明 + 筹码按钮（长方形空心，关闭黑灰 / 打开红色）
        Spacer(Modifier.weight(1f))
        Text(
            text = "前复权",
            fontSize = 10.sp,
            color = TEXT_SUB,
            lineHeight = 12.sp
        )
        Spacer(Modifier.width(8.dp))
        val chipColor = if (showChip) CHIP_BTN_ON else CHIP_BTN_FG
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, chipColor, RoundedCornerShape(2.dp))
                .clickable { onToggleChip() }
                .padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "筹码",
                fontSize = 10.sp,
                color = chipColor,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun PeriodTabs(current: String, onPick: (String) -> Unit, onGear: (() -> Unit)? = null) {
    val tabs = listOf(
        "minute" to "分时",
        "fiveday" to "五日",
        "day" to "日K",
        "week" to "周K",
        "month" to "月K"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (key, label) ->
            val active = key == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPick(key) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (active) TEXT_MAIN else TEXT_SUB,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (active) 26.dp else 0.dp)
                        .background(if (active) Color(Config.COLOR_PRIMARY) else Color.Transparent)
                )
            }
        }
        // 右侧齿轮：进入均线设置
        if (onGear != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onGear() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.aen),
                    contentDescription = "均线设置",
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
}

/**
 * 分时信息条：横向一排，位于分时标签栏（分时|日K|周K|月K）下方、分时价图上方。
 * 内容：时间(时:分) · 均价 · 涨跌额 · 涨跌幅。
 * 与十字光标联动：cursorMin >= CURSOR_ACTIVE_MIN 时显示光标所在分钟的时点值；否则显示最新值。
 *
 * 【数值来源说明 —— 重要，勿误当"计算值"】
 *   - price（白线点位，如 5.17）：交易所行情原生推送的该分钟瞬时成交价，直接取自
 *     detail.php 的 trends.points[i].price，非本页计算所得。光标停在 13:36 时拿到的
 *     就是这个分钟快照的 price，即分时白线在该处的高度。左右移动光标即切换所取的
 *     分钟点，故数值随光标变化；抬手(光标关闭)回落到 trends.last() 最新价。
 *   - avg（均价）：计算值 = amount / (vol * 100)，累计额 ÷ 累计量(手)。
 *   - chg（涨跌额）：计算值 = price - preClose。
 *   - pct（涨跌幅）：计算值 = chg / preClose * 100%。
 *
 * 内容规则（同一排，紧挨显示，仅用小间距分隔，不做左右两端分开）：
 *   - 光标未激活（显示最新）：均价、最新、涨跌额、涨跌幅；涨跌额/涨跌幅不带标签。
 *   - 光标激活（显示该分钟时点值）：时间、均价、最新、涨跌额、涨跌幅；时间前加 "时间:" 标签、最新价前加 "最新:" 标签，涨跌额/涨跌幅不带标签。
 *     示例："时间13:07 均价:4.96 最新:4.93 -0.17 -3.33%"
 *   - 光标关闭：显示 均价、最新、涨跌额、涨跌幅（无 "时间:" 前缀），"最新" 取当日最新一条。
 *
 * 颜色规则：
 *   - 均价：始终与分时价图均价线同色(0xFFFF8C00)。
 *   - 最新价：始终黑色(TEXT_MAIN)。
 *   - 涨跌额/涨跌幅：始终红涨绿跌(RED_UP / GREEN_TEXT / GRAY_FLAT)。
 */
@Composable
private fun MinuteInfoBar(
    trends: List<DetailTrend>,
    preClose: Double,
    cursorMin: Float
) {
    if (trends.isEmpty()) return
    val hasCursor = cursorMin >= CURSOR_ACTIVE_MIN
    // 光标所在分钟（吸附到最近的已有分时点），否则取最新点
    val point: DetailTrend? = if (hasCursor) {
        val target = cursorMin.toInt()
        // 优先精确匹配；否则取 <= target 的最近点
        trends.firstOrNull { it.min == target }
            ?: trends.lastOrNull { it.min <= target }
            ?: trends.firstOrNull()
    } else {
        trends.lastOrNull()
    }
    val p = point ?: return
    val price = p.price
    val chg = if (preClose > 0) price - preClose else 0.0
    val pct = if (preClose > 0) chg / preClose * 100.0 else 0.0
    // 均价：始终与分时价图均价线同色；最新价：始终黑色；涨跌额/涨跌幅：始终红涨绿跌。
    val upDownColor = when {
        chg > 0 -> RED_UP
        chg < 0 -> GREEN_TEXT
        else -> GRAY_FLAT
    }
    val avgColor = Color(0xFFFF8C00)  // 与分时价图均价线一致
    val priceColor = TEXT_MAIN        // 最新价/时点价：始终黑色
    // 时间：min=0 → 09:30；min<=120 → 上午；min>120 跳过午休 → 13:00 + (min-120)
    val label = minuteToClock(p.min)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 时间标签：仅光标激活时显示（"时间:13:36"，始终黑色）
        if (hasCursor) {
            Text(
                text = "时间:" + label,
                color = TEXT_MAIN,
                fontSize = 11.sp,
                fontFamily = DSpNumFont
            )
            Spacer(Modifier.width(8.dp))
        }
        // 均价：始终与均价线同色
        Text(
            text = "均价:" + fmtNum2(p.avg),
            color = avgColor,
            fontSize = 11.sp,
            fontFamily = DSpNumFont
        )
        Spacer(Modifier.width(8.dp))
        // 最新价（光标关闭=当日最新；光标激活=该时点价）：始终黑色，标签「最新」
        Text(
            text = "最新:" + fmtNum2(price),
            color = priceColor,
            fontSize = 11.sp,
            fontFamily = DSpNumFont
        )
        Spacer(Modifier.width(8.dp))
        // 涨跌额（无标签）：始终红涨绿跌
        Text(
            text = fmtSigned(chg),
            color = upDownColor,
            fontSize = 11.sp,
            fontFamily = DSpNumFont,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        // 涨跌幅（无标签）：始终红涨绿跌
        Text(
            text = fmtPct(pct),
            color = upDownColor,
            fontSize = 11.sp,
            fontFamily = DSpNumFont,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 分钟序号 → 时:分（A股：min=0→09:30；0..120 上午；>120 跳过午休从 13:00 起） */
private fun minuteToClock(min: Int): String {
    val m = min.coerceIn(0, 240)
    val total = if (m <= 120) 9 * 60 + 30 + m else 13 * 60 + (m - 120)
    val hh = total / 60
    val mm = total % 60
    return String.format(java.util.Locale.CHINA, "%02d:%02d", hh, mm)
}

// ============================================================
// 分时图 + 右侧盘口栏（对标同花顺）
//   左：上=价格曲线（含昨收基准线 + 均价线），下=分时量
//   右：上=五档盘口（卖5→卖1 / 买1→买5），下=逐笔成交明细
// ============================================================
/**
 * 清洗分时点：
 *  - 后端 detail.php 的 trends.points 恒返回 267 点，尾部 min>240 为冻结重复的"僵尸点"
 *    （价量卡在收盘值不断重复），必须剔除，否则曲线右端会拖出一条水平假线、
 *    且横轴被撑长导致与真实交易时间错位。
 *  - 仅保留 min ∈ [0,240] 的点（A股分时共 241 分钟），按 min 去重升序，
 *    保证 X 轴与 TimeAxis(9:30/10:30/11:30/13:00/14:00/15:00) 刻度严格对齐。
 */
private fun cleanMinuteTrends(src: List<DetailTrend>): List<DetailTrend> {
    if (src.isEmpty()) return src
    val seen = HashMap<Int, DetailTrend>()
    for (t in src) {
        if (t.min < 0 || t.min > 240) continue
        if (!seen.containsKey(t.min)) seen[t.min] = t
    }
    if (seen.isEmpty()) return src
    return seen.values.sortedBy { it.min }
}

/**
 * 分时图「逐笔买卖打点」覆盖层（方案：当日标记的 trades 用 'HH:mm' → 分钟序号定位）。
 *
 * 坐标完全复用 MinutePriceCanvas：
 *  - X：xMinuteAt(min, w, showAuction)
 *  - Y：yOf(p) = h - ((p - minP)/(maxP-minP))*h
 * 每个买/卖点画一个小圆点（买=红、卖=蓝，取该笔 dir），点大小随画布宽度自适应。
 * 只画「当日」标记（mark.date 与 detail 最新一根 line 的日期一致）。
 */
@Composable
private fun MinuteTickOverlay(
    marks: List<TradeMark>,
    trends: List<DetailTrend>,
    maxP: Double,
    minP: Double,
    showAuction: Boolean,
    modifier: Modifier = Modifier
) {
    // ===== 分时图买卖标（本轮需求）=====
    // 规则（用户定稿）：分时图只出 B / S；T 不显示。
    //   B + redB  → 红色 B 字母徽标
    //   S + blueS → 蓝色 S 字母徽标
    //   B + greyB / S + greyS → 灰点（退化，不出字母）
    val span = (maxP - minP).coerceAtLeast(1e-6)
    // 展平为 (分钟序号, kind, color, price)；time "HH:mm" → 分钟序号。
    val ticks = remember(marks) {
        val out = ArrayList<MarkTick>()
        for (m in marks) for (t in m.trades) {
            val mins = hhmmToMin(t.time) ?: continue
            val kind = m.kind
            if (kind != "B" && kind != "S") continue   // T 及未知一律不画
            out.add(MarkTick(mins, kind, m.color, t.price))
        }
        out
    }
    if (ticks.isEmpty()) return
    val markLabel = remember { tradeMarkPaint() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        fun yOf(p: Double): Float = (h - ((p - minP) / span) * h).toFloat()

        // 走势线（分时白线）在某个分钟处的 y：用于把买卖标短线真正连到走势线上的成交点。
        // trends 已按分钟升序；命中同分钟直接用，否则在相邻两点间线性插值。
        fun trendYAt(minute: Int): Float? {
            if (trends.isEmpty()) return null
            var lo: DetailTrend? = null
            var hi: DetailTrend? = null
            for (t in trends) {
                if (t.price <= 0.0) continue
                if (t.min <= minute) lo = t
                if (t.min >= minute) { hi = t; break }
            }
            val a = lo ?: hi ?: return null
            val b = hi ?: lo ?: return null
            if (a.min == b.min) return yOf(a.price)
            val r = (minute - a.min).toDouble() / (b.min - a.min).toDouble()
            val p = a.price + (b.price - a.price) * r.coerceIn(0.0, 1.0)
            return yOf(p)
        }

        // 徽标方框尺寸：与日K一致（边长 2.5 根牙签 = 15dp）
        val side = 2.5f * TOOTHPICK.toPx()
        val half = side / 2f
        val corner = side * 0.28f
        val dotR = 3.2f                      // 灰点半径（比原先 2.6f 略大，更易见）
        val wireW = 1.4.dp.toPx()

        for (tk in ticks) {
            val x = xMinuteAt(tk.minute.toFloat(), w, showAuction)
            val y = yOf(tk.price)                         // 成交价位置（画成交点小圆）
            val yT = trendYAt(tk.minute) ?: y             // 走势线位置（连线终点，真实锚点）
            if (yT < -side || yT > h + side) continue
            val c = tradeMarkColor(tk.color)
            val letter = when (tk.kind) { "B" -> "B"; "S" -> "S"; else -> "" }
            // 灰色系 → 点；红B / 蓝S → 字母徽标
            val showLetter = letter.isNotEmpty() &&
                ((tk.kind == "B" && tk.color == "redB") || (tk.kind == "S" && tk.color == "blueS"))
            if (!showLetter) {
                // 点按买卖方向着色：B → 红、S → 蓝（用户定稿「红蓝点」）
                val dotC = if (tk.kind == "B") RED_UP else BST_BLUE_S
                drawCircle(color = dotC, radius = dotR, center = Offset(x, y))
                drawCircle(color = Color.White, radius = dotR * 0.42f, center = Offset(x, y))
                continue
            }
            // ===== 字母徽标：置于【走势线成交点】上方/下方，短线连回走势线 =====
            val gap = 9.dp.toPx()
            // 上下各需要的半高（含间隙）
            val needAbove = half + gap        // 放上方时：从 yT 到框顶需要的高度
            val needBelow = half + gap        // 放下方时：从 yT 到框底需要的高度
            // 优先放上方；上方空间不足则放下方；两侧都不足时选空间更大的一侧
            val roomAbove = yT                // 走势线点上方可用高度
            val roomBelow = h - yT            // 走势线点下方可用高度
            val above = if (roomAbove >= needAbove) true
                        else if (roomBelow >= needBelow) false
                        else (roomAbove >= roomBelow)
            // 框中心：保证框整体在画布内（留 1px 边），且与走势线点保持净间距
            val boxCy = if (above) {
                (yT - half - gap).coerceAtLeast(1f + half)
            } else {
                (yT + half + gap).coerceAtMost(h - 1f - half)
            }
            // 框朝向走势线点的那条边
            val boxEdgeY = if (above) (boxCy + half) else (boxCy - half)
            // 连接短线：走势线点(yT) ↔ 方框边，覆盖整段净间距（密虚线）
            val yTop = min(yT, boxEdgeY)
            val yBot = max(yT, boxEdgeY)
            val sp = yBot - yTop
            if (sp > 1f) {
                val dashLen = 4f
                val dashGap = 3f
                var yy = yTop
                while (yy < yBot) {
                    val ye = min(yy + dashLen, yBot)
                    drawLine(c, Offset(x, yy), Offset(x, ye), wireW)
                    yy += dashLen + dashGap
                }
            }
            // 成交价小圆（保留成交语义）——若与走势线点差异明显，再从成交价补一小段实线到走势线
            drawCircle(color = c, radius = side * 0.11f, center = Offset(x, yT))
            drawCircle(color = c, radius = side * 0.11f, center = Offset(x, boxEdgeY))
            if (kotlin.math.abs(y - yT) > 1.5f) {
                drawLine(c, Offset(x, min(y, yT)), Offset(x, max(y, yT)), wireW * 0.7f)
            }
            // 白描边打底 + 彩色方框
            val tl = Offset(x - half, boxCy - half)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(tl.x - 1.5f, tl.y - 1.5f),
                size = Size(side + 3f, side + 3f),
                cornerRadius = CornerRadius(corner + 1.5f, corner + 1.5f)
            )
            drawRoundRect(
                color = c,
                topLeft = tl,
                size = Size(side, side),
                cornerRadius = CornerRadius(corner, corner)
            )
            markLabel.textSize = side * 0.62f
            markLabel.isFakeBoldText = true
            drawContext.canvas.nativeCanvas.drawText(
                letter, x, boxCy + markLabel.textSize * 0.36f, markLabel
            )
        }
    }
}
/** 分时买卖标一条（展平后）。 */
private class MarkTick(
    val minute: Int,
    val kind: String,
    val color: String,
    val price: Double
)
/** "HH:mm" → 分钟序号（09:30=0；11:30=120；13:00=121；15:00=240）。非法返回 null。 */
private fun hhmmToMin(t: String): Int? {
    val s = t.trim()
    val i = s.indexOf(':')
    if (i <= 0) return null
    val hh = s.substring(0, i).toIntOrNull() ?: return null
    val mm = s.substring(i + 1).toIntOrNull() ?: return null
    val total = hh * 60 + mm
    // 上午 09:30-11:30 → 0..120
    val amOpen = 9 * 60 + 30
    val amClose = 11 * 60 + 30
    val pmOpen = 13 * 60
    val pmClose = 15 * 60
    return when {
        total in amOpen..amClose -> total - amOpen
        total in pmOpen..pmClose -> 120 + (total - pmOpen)   // 13:00 → 121
        else -> null
    }
}

@Composable
private fun MinuteChart(
    d: StockDetail?,
    showAuction: Boolean = false,
    symmetric: Boolean = true,
    // 盘口五档末列是否显示委托金额（万/亿）而非手数（父层开关透传）
    showAmount: Boolean = false,
    // 当日真实成交流水（trades.php，含盘中今日）：用于分时图 B/S 标（marks 今日不产出）
    todayTrades: List<TradeRecord> = emptyList(),
    // 十字光标分钟（-1 = 无）：由父层持有并传入，便于顶部「分时信息条」与光标联动
    cursorMin: Float = CURSOR_NONE,
    onCursorChange: (Float) -> Unit = {},
    // 第十轮需求⑤：点右侧五档价格 → 回填下单弹窗价格（由 StockDetailPage 注入）
    onPickPrice: (Double) -> Unit = {},
    // 副图指标权限（sd_kline_sub）：成交量/成交额免费，MACD/KDJ/RSI/量MA5 需会员
    canSub: Boolean = true,
    onLockedSub: () -> Unit = {},
    // BS/T 买卖点权限（sd_bs_marks）：无权则不画逐笔打点
    canBs: Boolean = true
) {
    // 清洗分时点：后端恒返回 267 点，尾部 min>240 为冻结僵尸点，需剔除；
    // 并按 min(0..240) 去重排序，保证 X 轴与真实交易分钟严格对齐。
    // X 轴缩放（方案丙，价图/量图/TimeAxis 三者同源）：
    //   开竞价：四段布局 09:15->0 / 09:30->22% / 11:30->57.2% / 15:00->100%，
    //           早盘竞价(22%)明显宽于尾盘竞价(14%)，两个竞价网格清晰可辨。
    //   关竞价：09:30-15:00(240) 等比铺满全宽，11:30 天然居中。
    val trends = cleanMinuteTrends(d?.trends ?: emptyList())
    val preClose = d?.base?.preClose ?: 0.0

    // ===== 分时量副图：指标切换 + 十字光标 =====
    // cursorMin: 十字光标锁定的分钟（-1 = 无光标），由父层持有（见函数参数），
    // 价图与量图共享同一份状态，并驱动顶部「分时信息条」联动。
    // cursorPriceY: 价图横线归一化 y（0..1；-1 = 无），仅价区横线使用
    var cursorPriceY by remember { mutableStateOf(-1f) }
    // cursorVolY: 量图横线归一化 y（0..1；-1 = 无），仅量区横线使用
    var cursorVolY by remember { mutableStateOf(-1f) }
    // cursorZone: 十字光标当前激活区（0=无/1=价区/2=量区），保证横线与数值框仅在手指所在区出现
    var cursorZone by remember { mutableStateOf(0) }
    // 指标：0=成交量（手） 1=成交额（万元）
    var volMode by remember { mutableStateOf(0) }
    var showVolPicker by remember { mutableStateOf(false) }

    // ===== 五档盘口 + 成交明细（3 秒轮询）=====
    val code = d?.code ?: ""
    var ob by remember { mutableStateOf<OrderBook?>(null) }
    LaunchedEffect(code) {
        if (code.isEmpty()) return@LaunchedEffect
        while (isActive) {
            try {
                val r = withContext(Dispatchers.IO) { Api.orderbook(code, 1, 20) }
                if (r != null) ob = r
            } catch (_: Exception) {
                // 忽略单次失败，保持上一次数据
            }
            delay(3000)
        }
    }

    if (trends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无分时数据", color = TEXT_SUB, fontSize = 14.sp)
        }
        return
    }

    // 外层分栏：可选[早盘竞价] + 分时主轴 + 可选[收盘竞价] + 盘口栏
    Column(modifier = Modifier.fillMaxSize().padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
        // ===== 分时区（竞价栏 + 主轴）：按时间刻度 10:240:5 分宽，盘口栏不参与 =====
        // 结构：列 = [ 上部（图+量，含竞价栏三栏） + 底部贯穿整行的时间轴 ]
        // 时间轴用与网格同一套 10:240:5 权重贯穿整行，保证「09:30 落左竞价右边界、
        // 15:00 落右竞价左边界、11:30/13:00 落正中」，左右宽窄从刻度一眼可辨。
        Column(
            modifier = Modifier
                .weight(1f - ORDERBOOK_RATIO)
                .fillMaxHeight()
        ) {
            // ---------- 上部：单一分时主轴（含集合竞价，真实时间比例：09:15-11:30 + 13:00-15:00 = 255 分钟）----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 分时主轴（255 分钟，含竞价）—— 价图（70%）+ 时间轴 + 量图（30%）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 上部价格图：高度按实测复刻（905px÷3.5≈259dp）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(259f)
                    ) {
                        val range = minuteRange(trends, preClose, d?.base?.high ?: 0.0, d?.base?.low ?: 0.0, symmetric)
                        MinutePriceCanvas(
                            trends = trends,
                            preClose = preClose,
                            maxP = range.first,
                            minP = range.second,
                            showAuction = showAuction,
                            cursorMin = cursorMin,
                            cursorY = cursorPriceY,
                            cursorZone = cursorZone,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 逐笔买卖打点（仅当日）：覆盖在价格曲线上，与分时同坐标系
                        // 数据源优先级：① 当日真实成交流水 todayTrades（含盘中今日，首选）
                        //              ② 后端 marks 当日条目（收盘后 / 历史回看时兜底）
                        val todayMarks = remember(todayTrades, d?.marks, d?.line) {
                            if (todayTrades.isNotEmpty()) {
                                // 真实成交流水 → marks 形态：每笔一条 mark（kind 由 direction 决定）
                                val ticks = ArrayList<TradeMark>()
                                for (t in todayTrades) {
                                    val d1 = t.direction
                                    if (d1 != 1 && d1 != 2) continue
                                    val kind = if (d1 == 1) "B" else "S"
                                    val color = if (kind == "B") "redB" else "blueS"
                                    val hm = t.time.trim().split(" ").lastOrNull() ?: continue
                                    if (hm.length < 5) continue
                                    ticks.add(
                                        TradeMark(
                                            date = "",
                                            kind = kind,
                                            color = color,
                                            above = false,
                                            avgPrice = t.price,
                                            dayClose = 0.0,
                                            trades = listOf(
                                                MarkTrade(
                                                    time = hm.substring(0, 5),
                                                    dir = kind,
                                                    price = t.price,
                                                    qty = t.quantity.toDouble()
                                                )
                                            )
                                        )
                                    )
                                }
                                ticks
                            } else {
                                val ls = d?.line ?: emptyList()
                                // 归一化后再比较：K线日期（同花顺"20260924"）vs marks日期（后端"2026-09-24"）
                                val lastDate = normDateKey(ls.lastOrNull()?.date ?: "")
                                (d?.marks ?: emptyList()).filter {
                                    normDateKey(it.date) == lastDate && it.trades.isNotEmpty()
                                }
                            }
                        }
                        if (canBs) MinuteTickOverlay(
                            marks = todayMarks,
                            trends = trends,
                            maxP = range.first,
                            minP = range.second,
                            showAuction = showAuction,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 十字光标手势层（价图）：拖动吸附分钟 + 横线自由上下、松手保留、轻点清除
                        MinuteCursorGesture(
                            trends = trends,
                            showAuction = showAuction,
                            cursorMin = cursorMin,
                            cursorY = cursorPriceY,
                            hasCursor = cursorMin >= CURSOR_ACTIVE_MIN,
                            onCursor = { onCursorChange(it) },
                            onCursorY = { cursorPriceY = it; cursorZone = 1 },
                            onClearZone = { cursorZone = 0 },
                            modifier = Modifier.fillMaxSize()
                        )
                        // 数值框置顶层：覆盖在曲线/网格之上，保证不被遮挡
                        CursorValueOverlay(
                            trends = trends,
                            preClose = preClose,
                            maxP = range.first,
                            minP = range.second,
                            showAuction = showAuction,
                            cursorMin = cursorMin,
                            cursorY = cursorPriceY,
                            cursorZone = cursorZone,
                            volMode = volMode,
                            mode = 1
                        )
                    }
                    // 中间：时间轴（价图与量图之间）——刻度含集合竞价
                    TimeAxis(showAuction, Modifier.fillMaxWidth().height(TIME_BAR_HEIGHT))
                    // 下部量图：高度按实测复刻（352px÷3.5≈101dp）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(101f)
                    ) {
                        MinuteVolumeCanvas(trends, showAuction, preClose, cursorMin, cursorVolY, volMode, cursorZone)
                        // 十字光标手势层（量图）：与价图共享同一 cursorMin
                        MinuteCursorGesture(
                            trends = trends,
                            showAuction = showAuction,
                            cursorMin = cursorMin,
                            cursorY = cursorVolY,
                            hasCursor = cursorMin >= CURSOR_ACTIVE_MIN,
                            onCursor = { onCursorChange(it) },
                            onCursorY = { cursorVolY = it; cursorZone = 2 },
                            onClearZone = { cursorZone = 0 },
                            modifier = Modifier.fillMaxSize()
                        )
                        // ===== 量图区内浮动工具栏（方案C）：【分时量▼】+ 数值，浮在量图左上角，不占高度 =====
                        MinuteVolToolbar(
                            detail = d,
                            trends = trends,
                            cursorMin = cursorMin,
                            volMode = volMode,
                            onOpenPicker = { showVolPicker = true },
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                        // 指标切换：向下展开的内联菜单，贴在「分时指标▼」下方
                        if (showVolPicker) {
                            MinuteVolPicker(
                                current = volMode,
                                onPick = { volMode = it; showVolPicker = false },
                                canSub = canSub,
                                onLocked = onLockedSub,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 1.dp, top = 18.dp)
                            )
                        }
                        // 数值框置顶层：覆盖在曲线/工具栏/菜单之上，保证数值框始终最上层
                        CursorValueOverlay(
                            trends = trends,
                            preClose = preClose,
                            showAuction = showAuction,
                            cursorMin = cursorMin,
                            cursorY = cursorVolY,
                            cursorZone = cursorZone,
                            volMode = volMode,
                            mode = 2
                        )
                        // 全屏定位按钮（量区网格右下角）：样式复制日K右下角全屏按钮，暂未接功能
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp, bottom = 2.dp)
                        ) {
                            KLineButton(
                                text = "",
                                onClick = {},
                                content = { ChevronIcon(tipLeft = false, boxSize = 30.dp, double = true, rotate45 = true, scaleMult = 0.55f, gapMult = 3.4f) }
                            )
                        }
                    }
                }
            }
            } // end 分时区 Column
        // ================= 右侧：五档盘口 + 成交明细（宽度固定占比，不被竞价栏挤压）=================
        OrderBookPanel(
            ob = ob,
            preClose = preClose,
            showAmount = showAmount,
            modifier = Modifier
                .weight(ORDERBOOK_RATIO)
                .fillMaxHeight()
                .padding(start = 4.dp, end = 4.dp),
            onPickPrice = { p -> onPickPrice(p) }
        )
        } // end 内层 Row
    } // end 外层 Column
}

// ============================================================
// 分时量副图：顶部工具栏 + 指标弹层 + 十字光标手势
// ============================================================

/** 十字光标手势层：按下/拖动 → 吸附最近分钟；松手 → 保留；轻点 → 清除 */
@Composable
private fun MinuteCursorGesture(
    trends: List<DetailTrend>,
    showAuction: Boolean,
    cursorMin: Float,
    cursorY: Float,
    hasCursor: Boolean,
    onCursor: (Float) -> Unit,
    onCursorY: (Float) -> Unit,
    onClearZone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val hgt = constraints.maxHeight.toFloat()
        // 与画布同款 x 映射（仅用于反推分钟）
        fun xMinute(min: Float): Float {
            return if (showAuction) {
                when {
                    min < 0f -> ((min + 15f) / 10f) * 0.20f * w
                    min <= 237f -> 0.20f * w + (min / 237f) * 0.7333f * w
                    else -> 0.9333f * w + ((min - 237f) / 3f) * 0.0667f * w
                }
            } else {
                (min.coerceIn(0f, 240f) / 240f) * w
            }
        }
        // 横坐标 -> 最近的数据分钟
        // 注意：找不到任何有效点时返回 -1f（无效哨兵），绝不返回 0，
        // 否则松手会被误判为 09:30（0 分钟 = 集合竞价点，trends[0]）。
        fun minAtX(px: Float): Float {
            val c = px.coerceIn(0f, w)
            var best = -1f
            var bestD = Float.MAX_VALUE
            for (t in trends) {
                if (t.min < 0 || t.min > 240) continue
                val x = xMinute(t.min.toFloat())
                val d = kotlin.math.abs(x - c)
                if (d < bestD) { bestD = d; best = t.min.toFloat() }
            }
            return best
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 单手势层（同一 pointerInput 内自定义手势）：
                //   长按 → 激活光标 + 拖动跟随；
                //   轻点（未达长按就抬起）→ 关闭光标。
                // 之前在「同一个 pointerInput 里串 detectTapGestures + detectDragGesturesAfterLongPress」
                // 或「分成两个 pointerInput」都不行：
                //   - 两个 pointerInput：detectTapGestures 会抢占长按，detectDragGesturesAfterLongPress
                //     永远等不到长按 → 光标拖不动；
                //   - 同一 pointerInput 内串两个 detect*：它们是挂起函数会顺序阻塞，后面的永远不执行。
                // 因此用一个 awaitEachGesture 自己分流：长按走拖动循环，轻点走关闭。
                // 注意：key 里绝对不能放 hasCursor！长按激活会把 hasCursor 由 false 翻成 true，
                // 导致 pointerInput 协程被取消重启，拖动 while 循环刚进就被杀掉 → 光标拖不动。
                // cursorMin 只在轻点分支里读一次快照，不作为 key。
                .pointerInput(trends, showAuction, hgt) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 等待长按超时：返回非 null = 长按成立；为 null = 在超时前抬起（轻点）
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            // —— 轻点：关闭光标（必须写入 CURSOR_NONE(-99f)，不能用 -1f，
                            //    因为各层用 `cursorMin >= CURSOR_ACTIVE_MIN` 判定，-1f 仍在有效区间内会被误判为有光标）
                            // 这里不去读 cursorMin（否则在 pointerInput 协程里会捕获旧值），
                            // 关闭动作本身是幂等的：已关闭时再关一次无副作用。
                            onCursor(CURSOR_NONE)
                            onCursorY(CURSOR_NONE)
                            onClearZone()
                        } else {
                            // —— 长按成立：先按落点激活光标
                            val m0 = minAtX(longPress.position.x)
                            if (m0 >= 0f) {
                                onCursor(m0)
                                if (hgt > 0f) onCursorY((longPress.position.y / hgt).coerceIn(0f, 1f))
                            }
                            // 进入拖动循环：跟随手指实时吸附分钟，松手停在最后位置
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (ch.changedToUpIgnoreConsumed()) break
                                if (ch.positionChanged()) {
                                    val m = minAtX(ch.position.x)
                                    if (m >= 0f) {
                                        onCursor(m)
                                        if (hgt > 0f) onCursorY((ch.position.y / hgt).coerceIn(0f, 1f))
                                    }
                                    ch.consume()
                                }
                            }
                        }
                    }
                }
        )
    }
}

/**
 * 十字光标数值框置顶层：在所有图层/工具栏之上重绘数值框，保证不被遮挡。
 * mode: 1=价区, 2=量区
 */
@Composable
private fun CursorValueOverlay(
    trends: List<DetailTrend>,
    preClose: Double,
    maxP: Double = 0.0,
    minP: Double = 0.0,
    showAuction: Boolean,
    cursorMin: Float,
    cursorY: Float,
    cursorZone: Int,
    volMode: Int,
    mode: Int
) {
    if (cursorY !in 0f..1f || cursorZone != mode) return
    val tm = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cy = (cursorY * h).coerceIn(0f, h)
        if (mode == 1) {
            val span = maxP - minP
            val priceAtCy = minP + (1.0 - cursorY.toDouble()) * span
            val txt = fmtNum2(priceAtCy)
            val lay = tm.measure(txt, TextStyle(color = CURSOR_BOX_TX, fontSize = 7.5.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold))
            val boxW = lay.size.width + 4f
            val boxH = lay.size.height + 0.5f
            val cyOnline = (cursorMin < -15f) || (xMinuteAt(cursorMin, w, showAuction) >= w * 0.5f)
            if (cyOnline) {
                drawRect(color = CURSOR_BOX_BG, topLeft = Offset(0f, cy - boxH / 2f), size = Size(boxW, boxH))
                drawText(lay, topLeft = Offset(3f, cy - lay.size.height / 2f))
            } else {
                drawRect(color = CURSOR_BOX_BG, topLeft = Offset(w - boxW, cy - boxH / 2f), size = Size(boxW, boxH))
                drawText(lay, topLeft = Offset(w - boxW + 3f, cy - lay.size.height / 2f))
            }
        } else {
            // 量区：复算 maxV 同 MinuteVolumeCanvas
            val perMin = ArrayList<Float>(trends.size)
            val perAmt = ArrayList<Float>(trends.size)
            var prevCum = 0.0
            var prevAmt = 0.0
            for (t in trends) {
                val d = if (t.min <= 0) t.vol else (t.vol - prevCum)
                perMin.add(d.coerceAtLeast(0.0).toFloat())
                prevCum = t.vol
                val a = if (t.min <= 0) t.amount else (t.amount - prevAmt)
                perAmt.add((a.coerceAtLeast(0.0) / 10000.0).toFloat())
                prevAmt = t.amount
            }
            val vals = if (volMode == 1) perAmt else perMin
            val maxV = (vals.maxOrNull() ?: 1f).coerceAtLeast(1e-6f)
            val txt = if (volMode >= 2) {
                val price = DoubleArray(trends.size) { trends[it].price }
                when (volMode) {
                    2 -> {
                        val (dif, dea, hist) = macdSeries(price)
                        var mx = 1e-6
                        for (i in price.indices) mx = maxOf(mx, kotlin.math.abs(dif[i]), kotlin.math.abs(dea[i]), kotlin.math.abs(hist[i]))
                        fmtNum2(mx - cursorY.toDouble() * (2.0 * mx))
                    }
                    3 -> {
                        val (k, d, j) = kdjSeries(price, 9)
                        val idx = trends.indexOfFirst { kotlin.math.abs(it.min.toFloat() - cursorMin) < 0.5f }
                        val ii = if (idx >= 0) idx else trends.size - 1
                        "K:" + fmtNum2(k[ii]) + " D:" + fmtNum2(d[ii]) + " J:" + fmtNum2(j[ii])
                    }
                    4 -> {
                        val ii = trends.indexOfFirst { kotlin.math.abs(it.min.toFloat() - cursorMin) < 0.5f }.let { if (it >= 0) it else trends.size - 1 }
                        "RSI:" + fmtNum2(100.0 - cursorY.toDouble() * 100.0)
                    }
                    else -> {
                        val pv = perMinuteVol(trends)
                        val ma = maSeries(pv, 5)
                        val mx = (ma.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
                        fmtVol(mx - cursorY.toDouble() * mx)
                    }
                }
            } else {
                val vAt = (1.0 - cursorY.toDouble()) * maxV
                if (volMode == 1) fmtAmt(vAt) else fmtVol(vAt)
            }
            val lay = tm.measure(txt, TextStyle(color = CURSOR_BOX_TX, fontSize = 7.5.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold))
            val boxW = lay.size.width + 4f
            val boxH = lay.size.height + 0.5f
            drawRect(color = CURSOR_BOX_BG, topLeft = Offset(0f, cy - boxH / 2f), size = Size(boxW, boxH))
            drawText(lay, topLeft = Offset(3f, cy - lay.size.height / 2f))
            drawRect(color = CURSOR_BOX_BG, topLeft = Offset(w - boxW, cy - boxH / 2f), size = Size(boxW, boxH))
            drawText(lay, topLeft = Offset(w - boxW + 3f, cy - lay.size.height / 2f))
        }
    }
}

/** 顶部工具栏：【指标名▼】 + 数值（十字光标时=该分钟值，否则=最新值） */
@Composable
private fun MinuteVolToolbar(
    detail: StockDetail?,
    trends: List<DetailTrend>,
    cursorMin: Float,
    volMode: Int,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 定位当前分钟下标：有光标取该分钟，否则取最后一根
    val curIdx: Int = remember(trends, cursorMin) {
        if (trends.isEmpty()) -1
        else if (cursorMin < -15f) trends.size - 1
        else {
            val t = trends.minByOrNull { kotlin.math.abs(it.min.toFloat() - cursorMin) }
            if (t == null) trends.size - 1 else trends.indexOf(t)
        }
    }
    // 每分钟量/额（差分）
    val minuteVol: Double = remember(trends, curIdx) {
        if (curIdx < 0) 0.0 else {
            val t = trends[curIdx]
            if (t.min <= 0) t.vol else (t.vol - trends[curIdx - 1].vol).coerceAtLeast(0.0)
        }
    }
    val minuteAmt: Double = remember(trends, curIdx) {
        if (curIdx < 0) 0.0 else {
            val t = trends[curIdx]
            val a = if (t.min <= 0) t.amount else (t.amount - trends[curIdx - 1].amount)
            a.coerceAtLeast(0.0) / 10000.0
        }
    }
    // 当前分钟的逐笔聚合（detail.php 的 deals；无数据时为 null）
    val minuteDeal: MinuteDeal? = remember(detail, trends, curIdx) {
        if (curIdx < 0) null
        else detail?.minuteDeals?.get(trends.getOrNull(curIdx)?.min ?: -1)
    }
    // 「量」颜色：主买量 > 主卖量 → 红；< → 绿；其余 → 黑
    val volTextColor: Color = remember(minuteDeal) {
        val md = minuteDeal
        when {
            md == null -> VOL_LABEL_COLOR
            md.buy > md.sell -> RED_UP
            md.buy < md.sell -> GREEN_TEXT
            else -> VOL_LABEL_COLOR
        }
    }
    // 「现手」颜色：B 主买 → 红；S 主卖 → 绿；M 中性 → 黑
    val lastDealColor: Color = remember(minuteDeal) {
        when (minuteDeal?.lastDir) {
            "B" -> RED_UP
            "S" -> GREEN_TEXT
            else -> VOL_LABEL_COLOR
        }
    }
    // 指标模式下的当前值串（光标分钟 / 最新分钟）
    val indicatorText: String = remember(trends, curIdx, volMode) {
        if (curIdx < 0 || volMode < 2) "" else {
            val price = DoubleArray(trends.size) { trends[it].price }
            when (volMode) {
                VOLOR_MODE_MACD -> {
                    val (dif, dea, hist) = macdSeries(price)
                    "DIF:" + fmtNum2(dif[curIdx]) + " DEA:" + fmtNum2(dea[curIdx]) + " MACD:" + fmtNum2(hist[curIdx])
                }
                VOLOR_MODE_KDJ -> {
                    val (k, d, j) = kdjSeries(price, 9)
                    "K:" + fmtNum2(k[curIdx]) + " D:" + fmtNum2(d[curIdx]) + " J:" + fmtNum2(j[curIdx])
                }
                VOLOR_MODE_RSI -> {
                    val r = rsiSeries(price, 6)
                    "RSI:" + fmtNum2(r[curIdx])
                }
                VOLOR_MODE_VOLMA -> {
                    val pv = perMinuteVol(trends)
                    val ma = maSeries(pv, 5)
                    "MA5:" + fmtVol(ma[curIdx])
                }
                else -> ""
            }
        }
    }
    // 方案C：紧凑浮动胶囊，浮在量图左上角，不占高度、不挤压柱子
    Row(
        modifier = modifier
            .padding(start = 1.dp, top = 1.dp)
            .background(Color(0xE6FFFFFF), shape = RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 【指标名▼】点击展开菜单
        Row(
            modifier = Modifier.clickable { onOpenPicker() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                MINUTE_INDICATOR_NAMES.getOrElse(volMode) { "指标" },
                color = TEXT_MAIN,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "▼",
                color = TEXT_MAIN,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 1.dp)
            )
        }
        // 数值区：按指标类型切换
        if (volMode == VOLOR_MODE_MINUTE_AMT) {
            // 额：优先用逐笔聚合的真实成交额（与量区同源），无数据回退差分值；保持黑色
            val _amtTxt = minuteDeal?.amt?.let { fmtAmt(it / 10000.0) } ?: fmtAmt(minuteAmt)
            Text(
                "  额:" + _amtTxt,
                color = VOL_LABEL_COLOR,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (volMode == VOLOR_MODE_MINUTE_VOL) {
            // 三项同一行：额（黑）+ 量（主买/主卖着色）+ 现手（末笔方向着色）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "  量:" + fmtVol(minuteDeal?.hand ?: minuteVol),
                    color = if (minuteDeal != null) volTextColor else VOL_LABEL_COLOR,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  现手:" + fmtVol(minuteDeal?.lastHand ?: minuteVol),
                    color = if (minuteDeal != null) lastDealColor else VOL_LABEL_COLOR,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  额:" + (minuteDeal?.amt?.let { fmtAmt(it / 10000.0) } ?: fmtAmt(minuteAmt)),
                    color = VOL_LABEL_COLOR,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                "  " + indicatorText,
                color = VOL_LABEL_COLOR,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
/** 分时指标切换：向下展开的内联菜单（贴在标签下方，不遮盖全屏） */
@Composable
private fun MinuteVolPicker(
    current: Int,
    onPick: (Int) -> Unit,
    canSub: Boolean = true,
    onLocked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(96.dp)
            .background(Color.White, shape = RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(3.dp))
            .padding(vertical = 2.dp)
    ) {
        MINUTE_INDICATOR_NAMES.forEachIndexed { i, name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 成交量(0)/成交额(1) 免费；MACD/KDJ/RSI/量MA5 属副图指标(会员)
                        if (i >= 2 && !canSub) onLocked() else onPick(i)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    color = if (i == current) RED_UP else TEXT_MAIN,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (i == current) {
                    Text("\u2713", color = RED_UP, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// 布局比例常量（便于微调）
// ============================================================
private val CHART_AREA_HEIGHT = 380.dp          // 图表区固定总高（价图905+时间轴60+量352≈1317px÷3.5≈376dp，右侧1030+318=1348px≈385dp，取中380dp两边齐平）
private val DETAIL_BAR_HEIGHT = 100.dp         // 底部功能栏占位高度（内容66dp + 底部导航区预留，防止滚动内容被钉底栏遮挡）
private const val ORDERBOOK_RATIO = 0.30f      // 右侧盘口栏宽度占比（分时图自动占剩余）
private val AUCTION_COL_WIDTH = 56.dp          // 集合竞价栏宽度（保留兼容，不再用于布局）
private val TIME_BAR_HEIGHT = 17.dp            // 分时时间栏高度（实测 60px÷3.5≈17dp）
// ===== 分时区（左侧）实测尺寸复刻 =====
private val PRICE_CANVAS_HEIGHT = 259.dp       // 259dp
private val VOLUME_CANVAS_HEIGHT = 101.dp      // 101dp
// ===== 盘口栏（右侧）实测尺寸复刻 =====
private val ORDERBOOK_TOP_HEIGHT = 294.dp      // 294dp
private val ORDERBOOK_FONT = 13.sp             // 五档标签字号（缩小，防大价格挤压）
private val ORDERBOOK_FONT_VAL = 10.sp          // 五档价格字号（缩小，保证 1263.00 完整显示）
private val LIMIT_FONT_SMALL = 10.sp        // 五档价格/手数 & 涨跌停面板专用字号（缩小，保证标签完整显示）
private val ORDERBOOK_FONT_DEAL = 11.sp        // 明细数字字号（缩小加粗）
private val OB_TEXT_GRAY = Color(0xFF555555)   // 五档字体黑灰色
private val OB_LIMIT_BLUE = Color(0xFF3A7BD5)   // 涨跌停数据面板「点击查看」蓝色
private val OB_LIMIT_LABEL = Color(0xFF555555)  // 涨跌停数据面板标签黑偏灰
private val OB_LIMIT_NUM = Color(0xFF000000)    // 涨跌停数据面板百分比数值纯黑
private val OB_LIMIT_TITLE = Color(0xFF555555)  // 涨跌停数据面板标题黑偏灰
private val OB_LIMIT_TITLE_BG = Color(0xFFF0F0F0)   // 涨跌停面板标题行浅灰背景
private val ORDERBOOK_ROW_MIN = 13.dp          // 五档行高下限（防挤压）
private val ORDERBOOK_ROW_MAX = 23.dp          // 五档行高上限（对齐参考截图≈23dp行距，避免铺满后过疏）

// ============================================================
// 右侧盘口栏：上=五档（卖5→卖1，买1→买5），下=逐笔成交明细
// ============================================================
@Composable
private fun OrderBookPanel(ob: OrderBook?, preClose: Double, showAmount: Boolean = false, modifier: Modifier = Modifier, onPickPrice: ((Double) -> Unit)? = null) {
    // 明细是否展开：展开时上面五档收缩为「卖1 + 买1」两行，把空间让给明细
    var dealsExpanded by remember { mutableStateOf(false) }
    // ===== 全天逐笔（方案 A2）：独立分页状态，默认加载最近 5 页，往上滚加载更早页 =====
    // dealPages: 页号 -> 该页逐笔（页号越大越新；p=1 为当天最早页）
    val dealPages = remember(ob?.code) { mutableStateMapOf<Int, List<DealItem>>() }
    var lastDealPage by remember(ob?.code) { mutableStateOf(0) }   // 当天最新页号（0=未探测）
    var dealBaseReady by remember(ob?.code) { mutableStateOf(false) }
    // 上滑加载更早页（方案 A2）：滚到顶部时按需加载 minPage-1
    var dealLoadingEarlier by remember(ob?.code) { mutableStateOf(false) }
    // 首拉：探测最后一页，加载最近 5 页（[last-4, last]）
    LaunchedEffect(ob?.code) {
        val code = ob?.code ?: return@LaunchedEffect
        while (true) {
            try {
                if (!dealBaseReady) {
                    val lp = withContext(Dispatchers.IO) { Api.orderbookDealsLastPage(code) }
                    if (lp > 0) {
                        lastDealPage = lp
                        val from = (lp - 4).coerceAtLeast(1)
                        for (p in from..lp) {
                            val ds = withContext(Dispatchers.IO) { Api.orderbookDeals(code, p) }
                            if (ds.isNotEmpty()) dealPages[p] = ds
                        }
                        dealBaseReady = true
                    }
                } else {
                    // 增量：只拉 latest 与 latest+1，把新成交补进来
                    for (p in intArrayOf(lastDealPage, lastDealPage + 1)) {
                        val ds = withContext(Dispatchers.IO) { Api.orderbookDeals(code, p) }
                        if (ds.isNotEmpty()) {
                            dealPages[p] = ds
                            if (p > lastDealPage) lastDealPage = p
                        }
                    }
                }
            } catch (_: Exception) {
                // 忽略单次失败，保持已有数据
            }
            delay(3000)
        }
    }
    // 展平已加载页 -> 倒序（最新在上）。每页内部原始顺序为「早->晚」，故整体按页号降序拼接
    // 直接读 SnapshotStateMap 的 keys/values：同页增量更新（较新页追加新成交）也会触发重组。
    // 用 run{} 包住读取以显式建立对 dealPages 的快照读取依赖（不用 remember 缓存）。
    val allDealsLoaded: List<DealItem> = run {
        val ps = dealPages.keys.sortedDescending()
        val out = ArrayList<DealItem>()
        for (p in ps) {
            val lst = dealPages[p] ?: continue
            for (i in lst.indices.reversed()) out.add(lst[i])
        }
        out
    }
    Column(modifier = modifier) {
        // ---------- 五档盘口（自适应铺满：行高 = 可用高/10 并封顶，避免行距过疏） ----------
        // 收起态：卖5→卖1 + 买1→买5（10 行）；展开态：仅卖1 + 买1（2 行）
        // 收起态：卖5→卖1 + 买1→买5（10 行 + 1 个分界间隙）；展开态：仅卖1 + 间隙 + 买1
        // 五档区高度：收起态占盘口栏的固定权重（与左侧价图等高）；展开态按内容高度（不撑满，避免上方留白）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (dealsExpanded) Modifier.wrapContentHeight() else Modifier.weight(294f))
        ) {
            // 卖出/买入分界间隙：与一行同高，让买1与卖1之间有明显豁口
            val gapH = 12.dp
            // 大单判定：用「最新页」逐笔（此前误用 ob.deals = p=1 开盘页，判定依据错误）
            val allDeals = dealPages[lastDealPage] ?: ob?.deals ?: emptyList()
            val bigAsk = pickBigOrder(allDeals, true)
            val bigBid = pickBigOrder(allDeals, false)
            // 涨停 / 跌停判定：用后端 limitUp/limitDown 容差比较（0.001 防浮点误差）
            val isLimitUp0 = ob != null && ob.limitUp > 0 && ob.price >= ob.limitUp - 0.001
            val isLimitDown0 = ob != null && ob.limitDown > 0 && ob.price <= ob.limitDown + 0.001
            // 涨跌停数据面板展开/收起（可反复展开/收起）：收起时面板只留标题行，被顶替的五档恢复
            var limitPanelExpanded by remember(ob?.code) { mutableStateOf(true) }
            // 该股是否处于涨/跌停且面板已收起
            val limitPanelCollapsed = (!dealsExpanded) && ((isLimitUp0 || isLimitDown0)) && !limitPanelExpanded
            // 涨停展开时卖五大单被面板一并盖住（不再单独占行）；跌停展开时买五大单同理
            val bigAskShown = bigAsk != null && !(isLimitUp0 && limitPanelExpanded && !dealsExpanded)
            val bigBidShown = bigBid != null && !(isLimitDown0 && limitPanelExpanded && !dealsExpanded)
            val bigRows = (if (bigAskShown) 1 else 0) + (if (bigBidShown) 1 else 0)
            // 自适应行高：可用高度 / (行数 + 大单行)。收起态面板只占 1 行（标题行）+ 五档 5 行恢复
            val limitCollapsedExtraRow = if (limitPanelCollapsed) 1 else 0
            val obRowHeight = (maxHeight / ((if (dealsExpanded) 3 else 11) + bigRows + limitCollapsedExtraRow)).coerceIn(ORDERBOOK_ROW_MIN, ORDERBOOK_ROW_MAX)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
            val asks = ob?.asks ?: emptyList()
            val bids = ob?.bids ?: emptyList()
            val pc = if ((ob?.prevClose ?: 0.0) > 0) ob!!.prevClose else preClose
            val isLimitUp = isLimitUp0
            val isLimitDown = isLimitDown0
            // 面板占据的整块高度 = 5 行（涨停占卖盘位、跌停占买盘位），内容在该高度内上下居中
            // 面板高度：涨停/跌停侧 5 行；若对侧大单行被一并盖住则再加 1 行
            val panelBlockH = if (!limitPanelExpanded) obRowHeight * 1
                else obRowHeight * (5 + (if ((isLimitUp && bigAsk != null && !bigAskShown) || (isLimitDown && bigBid != null && !bigBidShown)) 1 else 0))
            if (dealsExpanded) {
                // 仅显示卖1 + 间隙 + 买1
                bigAsk?.takeIf { bigAskShown }?.let { BigOrderRow(it, obRowHeight) }
                OrderLevelRow(
                    label = "卖1",
                    lv = asks.getOrNull(0),
                    preClose = pc,
                    isAsk = true,
                    showAmount = showAmount,
                    onPick = onPickPrice,
                )
                // 卖买分界：块内竖直居中画一根细白线，保证线在豁口正中间
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gapH)
                        .drawBehind {
                            val y = size.height / 2f
                            drawRect(color = Color(0xFFEEEEEE), topLeft = Offset(0f, y), size = Size(size.width, 0.6.dp.toPx()))
                        }
                )
                OrderLevelRow(
                    label = "买1",
                    lv = bids.getOrNull(0),
                    preClose = pc,
                    isAsk = false,
                    showAmount = showAmount,
                    onPick = onPickPrice,
                )
                bigBid?.takeIf { bigBidShown }?.let { BigOrderRow(it, obRowHeight) }
            } else {
                // 卖五上方：卖侧大单（涨停展开时被面板一并盖住，故此处不再渲染）
                bigAsk?.takeIf { bigAskShown }?.let { BigOrderRow(it, obRowHeight) }
                if (isLimitUp) {
                    // 涨停：面板常驻。展开时顶替「卖盘 5 行」；收起时仅留标题行，卖五档恢复
                    LimitDataPanel(isUp = true, ob = ob, blockHeight = panelBlockH,
                        expanded = limitPanelExpanded, onToggle = { limitPanelExpanded = !limitPanelExpanded })
                    if (!limitPanelExpanded) {
                        for (i in 4 downTo 0) {
                            OrderLevelRow(label = "卖" + (i + 1), lv = asks.getOrNull(i), preClose = pc, isAsk = true, showAmount = showAmount, rowHeight = obRowHeight, onPick = onPickPrice)
                        }
                    }
                } else {
                    for (i in 4 downTo 0) {
                        OrderLevelRow(label = "卖" + (i + 1), lv = asks.getOrNull(i), preClose = pc, isAsk = true, showAmount = showAmount, rowHeight = obRowHeight, onPick = onPickPrice)
                    }
                }
                // 卖买分界：块内竖直居中画一根细白线，保证线在豁口正中间
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gapH)
                        .drawBehind {
                            val y = size.height / 2f
                            drawRect(color = Color(0xFFEEEEEE), topLeft = Offset(0f, y), size = Size(size.width, 0.6.dp.toPx()))
                        }
                )
                if (isLimitDown) {
                    // 跌停：面板常驻。展开时顶替「买盘 5 行」；收起时仅留标题行，买五档恢复
                    LimitDataPanel(isUp = false, ob = ob, blockHeight = panelBlockH,
                        expanded = limitPanelExpanded, onToggle = { limitPanelExpanded = !limitPanelExpanded })
                    if (!limitPanelExpanded) {
                        for (i in 0 until 5) {
                            OrderLevelRow(label = "买" + (i + 1), lv = bids.getOrNull(i), preClose = pc, isAsk = false, showAmount = showAmount, rowHeight = obRowHeight, onPick = onPickPrice)
                        }
                    }
                } else {
                    for (i in 0 until 5) {
                        OrderLevelRow(label = "买" + (i + 1), lv = bids.getOrNull(i), preClose = pc, isAsk = false, showAmount = showAmount, rowHeight = obRowHeight, onPick = onPickPrice)
                    }
                }
                // 买五下方：买侧大单（跌停展开时被面板一并盖住，故此处不再渲染）
                bigBid?.takeIf { bigBidShown }?.let { BigOrderRow(it, obRowHeight) }
            }
            }
        }
        // 分隔线
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
        // ---------- 逐笔成交明细（可折叠：默认收起只显示 4 条，点击标题行展开/收起） ----------
        // 明细数据：全天已加载逐笔（最新在上）；若分页尚未就绪，退回 ob.deals 兜底
        val deals = if (allDealsLoaded.isNotEmpty()) allDealsLoaded else (ob?.deals ?: emptyList())
        val pcD = if ((ob?.prevClose ?: 0.0) > 0) ob!!.prevClose else preClose
        // 标题行：「明细 + 三角」。收起时三角朝上(▲)，展开时朝下(▼)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dealsExpanded = !dealsExpanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("明细", color = TEXT_SUB, fontSize = 9.sp)
            // 三角：收起朝上、展开朝下
            Canvas(modifier = Modifier.padding(start = 3.dp).size(7.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    if (dealsExpanded) {
                        // 朝下 ▼
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, h); close()
                    } else {
                        // 朝上 ▲
                        moveTo(w / 2f, 0f); lineTo(w, h); lineTo(0f, h); close()
                    }
                }
                drawPath(path, color = TEXT_SUB)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
        // 明细列表：收起=占面板下 30%（与左侧量图 weight(0.3f) 同权，行高自适应）；
        //           展开=撑满剩余空间可滚动
        val dealScroll = rememberScrollState()
        // 上滑加载更早页：接近顶部且存在更早页时加载 minPage-1
        LaunchedEffect(dealScroll, dealBaseReady, ob?.code) {
            val code = ob?.code ?: return@LaunchedEffect
            snapshotFlow { dealScroll.value }
                .collect { v ->
                    if (v <= 40 && dealBaseReady && !dealLoadingEarlier) {
                        val minP = dealPages.keys.minOrNull() ?: return@collect
                        if (minP > 1) {
                            dealLoadingEarlier = true
                            try {
                                val p = minP - 1
                                val ds = withContext(Dispatchers.IO) { Api.orderbookDeals(code, p) }
                                if (ds.isNotEmpty()) dealPages[p] = ds
                            } catch (_: Exception) {
                            } finally {
                                dealLoadingEarlier = false
                            }
                        }
                    }
                }
        }
        if (dealsExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(dealScroll)
            ) {
                deals.forEach { d -> DealRow(d, pc = pcD, rowHeight = ORDERBOOK_ROW_MAX) }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(91f)
            ) {
                // 收起态固定 4 行：行高 = 本区可用高 / 4，用上下限收敛（对齐参考图 ≈21dp 行距）
                val dealRowH = (maxHeight / 4).coerceIn(ORDERBOOK_ROW_MIN, ORDERBOOK_ROW_MAX)
                Column(modifier = Modifier.fillMaxWidth()) {
                    deals.take(4).forEach { d -> DealRow(d, pc = pcD, rowHeight = dealRowH) }
                }
            }
        }
    }
}

// ============================================================
// 大单判定：从“逐笔成交明细 deals”里挑大单
// 1) 只看最新一页成交，按【分钟】聚合同一方向的成交量
// 2) 取最大的一组，超过阈值即视为大单
// 3) 卖侧(主动卖 S) -> 卖五上方；买侧(主动买 B) -> 买五下方
// ============================================================
private data class BigOrder(val price: Double, val vol: Double, val isAsk: Boolean)

/** 手数打星：两位数原样；三位数只留最后 1 位(前面补*)；三位以上留最后 2 位 */
private fun fmtBigVol(v: Double): String {
    if (v.isNaN() || v <= 0) return "--"
    // 过万：与五档一致用“万”单位
    if (v >= 10000) return String.format("%.2f万", v / 10000.0)
    val n = v.toLong().toString()
    return when {
        n.length <= 2 -> n
        n.length == 3 -> "**" + n.substring(n.length - 1)
        else -> "**" + n.substring(n.length - 2)
    }
}

/**
 * 从逐笔成交里挑大单：按分钟聚合同方向的量，返回最大的一组。
 * @param isAsk true=卖侧(主动卖 S) / false=买侧(主动买 B)
 */
private fun pickBigOrder(deals: List<DealItem>, isAsk: Boolean): BigOrder? {
    if (deals.isEmpty()) return null
    val wantDir = if (isAsk) "S" else "B"
    // 按分钟分组：key=HH:mm，value=(累加手数, 最后一笔价格)
    val agg = LinkedHashMap<String, DoubleArray>() // [vol, price]
    for (d in deals) {
        val dir = d.dir
        // 卖侧统计主动卖(S)，买侧统计主动买(B)；中性单不参与(跟同花顺口径近似)
        if (dir != wantDir) continue
        val mm = d.time.substringBeforeLast(':') // HH:mm
        val e = agg[mm]
        if (e == null) {
            agg[mm] = doubleArrayOf(d.vol, d.price)
        } else {
            e[0] += d.vol
            e[1] = d.price // 记最后一笔价作为该分钟代表价
        }
    }
    if (agg.isEmpty()) return null
    // 取该分钟聚合量最大的一组
    var bestVol = 0.0
    var bestPrice = 0.0
    for ((_, e) in agg) {
        if (e[0] > bestVol) { bestVol = e[0]; bestPrice = e[1] }
    }
    // 阀值：绝对门槛 300 手（后续可调）
    if (bestVol < 300.0) return null
    return BigOrder(bestPrice, bestVol, isAsk)
}

/** 大单提示行：大单  价格  打星手数 */
@Composable
private fun BigOrderRow(b: BigOrder, rowHeight: Dp) {
    // 只有“大单”标签分方向色；价格/手数统一黑灰
    val c = if (b.isAsk) GREEN_TEXT else RED_UP
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "大单",
            color = c, fontSize = ORDERBOOK_FONT, fontWeight = FontWeight.Medium,
            fontFamily = DSpNumFont, maxLines = 1, softWrap = false,
            modifier = Modifier.width(26.dp), textAlign = TextAlign.Start
        )
        Text(
            fmtNum2(b.price),
            color = OB_TEXT_GRAY, fontSize = ORDERBOOK_FONT_VAL, fontWeight = FontWeight.SemiBold,
            fontFamily = DSpNumFont, maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 6.dp), textAlign = TextAlign.Start
        )
        Text(
            fmtBigVol(b.vol),
            color = OB_TEXT_GRAY, fontSize = ORDERBOOK_FONT_VAL, fontWeight = FontWeight.SemiBold,
            fontFamily = DSpNumFont, maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}


/** 五档单行：左标签 + 中价格 + 右手数 */
@Composable
private fun OrderLevelRow(label: String, lv: OrderLevel?, preClose: Double, isAsk: Boolean, showAmount: Boolean = false, rowHeight: Dp = ORDERBOOK_TOP_HEIGHT / 10, onPick: ((Double) -> Unit)? = null) {
    val price = lv?.price ?: 0.0
    val vol = lv?.vol ?: 0.0
    val priceColor = when {
        price <= 0 -> TEXT_SUB
        price > preClose -> RED_UP
        price < preClose -> GREEN_TEXT
        else -> TEXT_MAIN
    }
    val labelColor = OB_TEXT_GRAY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .then(if (onPick != null && price > 0.0) Modifier.clickable { onPick?.invoke(price) } else Modifier)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = labelColor, fontSize = ORDERBOOK_FONT,
            fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false,
            modifier = Modifier.width(22.dp), textAlign = TextAlign.Start
        )
        Text(
            if (price > 0) fmtNum2(price) else "--",
            color = priceColor, fontSize = ORDERBOOK_FONT_VAL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1.15f).padding(start = 3.dp), textAlign = TextAlign.Start
        )
        Text(
            if (vol > 0) { if (showAmount) fmtOrderAmount(price * vol * 100.0) else fmtVol(vol) } else "--",
            color = OB_TEXT_GRAY, fontSize = if (showAmount) 9.sp else ORDERBOOK_FONT_VAL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

/**
 * 涨跌停数据面板：涨停时顶替卖盘 5 行、跌停时顶替买盘 5 行。
 * 结构：标题行（贴顶第一行，不参与居中，带小三角可收起）+ 数据块（在标题行以下剩余空间里整体上下居中）。
 * 数据块首行「几天几板」整行水平居中；其余行左标签/右数值横向排。
 * 颜色：标题/标签黑偏灰；百分比数值纯黑；金额数值涨停红/跌停绿；「点击查看」蓝色。
 */
@Composable
private fun LimitDataPanel(isUp: Boolean, ob: OrderBook?, blockHeight: Dp,
                           expanded: Boolean, onToggle: () -> Unit) {
    val title = if (isUp) "涨停数据" else "跌停数据"
    val amountColor = if (isUp) RED_UP else GREEN_TEXT
    // 封单占成交 = 封单额 / 成交额 * 100（能算则算，否则 --）
    val sealRatio: String = run {
        val sealAmt = sealAmount(ob, isUp)
        val dealAmt = dealAmount(ob)
        if (sealAmt > 0 && dealAmt > 0) String.format("%.1f%%", sealAmt / dealAmt * 100.0) else "--"
    }
    val sealAmtStr = fmtOrderAmount(sealAmount(ob, isUp))
    val dealAmtStr = fmtOrderAmount(dealAmount(ob))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(blockHeight)
            .padding(horizontal = 1.dp)
    ) {
        // ===== 标题行：贴顶第一行，不参与上下居中（仅标题行自身水平居中） =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OB_LIMIT_TITLE_BG)
                .clickable { onToggle() }
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, color = OB_LIMIT_TITLE, fontSize = LIMIT_FONT_SMALL,
                fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
                maxLines = 1, softWrap = false
            )
            Canvas(modifier = Modifier.padding(start = 3.dp).size(7.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    if (expanded) {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, h); close()   // ▼
                    } else {
                        moveTo(w / 2f, 0f); lineTo(w, h); lineTo(0f, h); close()    // ▲
                    }
                }
                drawPath(path, color = OB_LIMIT_TITLE)
            }
        }
        // ===== 数据块：在标题行以下的剩余空间里整体上下居中 =====
        if (expanded) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isUp) {
                        LimitRowCenter("--")                         // 几天几板（整行居中）
                        LimitRow("封单占成交", sealRatio, OB_LIMIT_NUM)
                        LimitRow("最高封单额", if ((ob?.maxSealAmount ?: 0.0) > 0) fmtOrderAmount(ob!!.maxSealAmount) else "--", amountColor)
                        LimitRow("涨停成交额", dealAmtStr, amountColor)
                        LimitRowBlue("明日涨停率", "点击查看")
                    } else {
                        // 跌停：无「几天几板」与「明日涨停率」，仅 3 行横向排
                        LimitRow("封单占成交", sealRatio, OB_LIMIT_NUM)
                        LimitRow("最高封单额", if ((ob?.maxSealAmount ?: 0.0) > 0) fmtOrderAmount(ob!!.maxSealAmount) else "--", amountColor)
                        LimitRow("跌停成交额", dealAmtStr, amountColor)
                    }
                }
            }
        }
    }
}

/** 面板居中单行（如「7天6板」）：整行水平居中 */
@Composable
private fun LimitRowCenter(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, color = OB_LIMIT_NUM, fontSize = LIMIT_FONT_SMALL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center
        )
    }
}

/** 面板单行：左标签（黑偏灰）+ 右数值（指定色） */
@Composable
private fun LimitRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = OB_LIMIT_LABEL, fontSize = LIMIT_FONT_SMALL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentWidth(), textAlign = TextAlign.Start
        )
        Text(
            value, color = valueColor, fontSize = LIMIT_FONT_SMALL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End
        )
    }
}

/** 面板单行：左标签 + 右侧蓝色「点击查看」（纯展示，不可点） */
@Composable
private fun LimitRowBlue(label: String, blueText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = OB_LIMIT_LABEL, fontSize = LIMIT_FONT_SMALL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentWidth(), textAlign = TextAlign.Start
        )
        Text(
            blueText, color = OB_LIMIT_BLUE, fontSize = LIMIT_FONT_SMALL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End
        )
    }
}

/** 封单额（元）：涨停取买一价*买一量*100；跌停取卖一价*卖一量*100。取不到返回 0 */
private fun sealAmount(ob: OrderBook?, isUp: Boolean): Double {
    ob ?: return 0.0
    val lv = (if (isUp) ob.bids else ob.asks).getOrNull(0) ?: return 0.0
    if (lv.price <= 0 || lv.vol <= 0) return 0.0
    return lv.price * lv.vol * 100.0
}

/** 成交额（元）：兜底用「外盘+内盘」手数 * 当前价 * 100 估算；取不到返回 0 */
private fun dealAmount(ob: OrderBook?): Double {
    ob ?: return 0.0
    if (ob.price <= 0) return 0.0
    val hands = ob.outer + ob.inner
    if (hands <= 0) return 0.0
    return ob.price * hands * 100.0
}

/** 逐笔成交单行：时间 / 价格 / 手数 */
@Composable
private fun DealRow(d: DealItem, pc: Double, rowHeight: Dp = 15.dp) {
    // 主动买/卖方向色？（B=主动买红 S=主动卖绿 M=中性灰）
    val dirColor = when (d.dir) {
        "B" -> RED_UP
        "S" -> GREEN_TEXT
        else -> GRAY_FLAT
    }
    val color = when {
        d.price > pc -> RED_UP
        d.price < pc -> GREEN_TEXT
        else -> GRAY_FLAT
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            // 明细只显示到分钟（去掉秒）
            d.time.substringBeforeLast(':'),
            color = TEXT_SUB, fontSize = ORDERBOOK_FONT_DEAL, fontWeight = FontWeight.SemiBold,
            fontFamily = DSpNumFont,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
            modifier = Modifier.weight(0.85f), textAlign = TextAlign.Start
        )
        Text(
            fmtNum2(d.price), color = color, fontSize = ORDERBOOK_FONT_DEAL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1.15f), textAlign = TextAlign.End
        )
        Text(
            fmtVol(d.vol), color = dirColor, fontSize = ORDERBOOK_FONT_DEAL,
            fontFamily = DSpNumFont, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

/** 手数格式化：>=1万 显示为 x.xx万 */
private fun fmtVol(v: Double): String {
    if (v.isNaN()) return "--"
    return if (v >= 10000) String.format("%.2f万", v / 10000.0) else v.toLong().toString()
}
/** 委托金额格式化（万/亿）：>=1亿 -> 保留两位小数用「亿」；>=1万 -> 取整用「万」；否则原值 */
private fun fmtOrderAmount(v: Double): String {
    if (v.isNaN() || v <= 0) return "--"
    return when {
        v >= 100000000.0 -> String.format("%.2f亿", v / 100000000.0)
        v >= 10000.0 -> String.format("%.0f万", v / 10000.0)
        else -> v.toLong().toString()
    }
}

/** 分钟 -> 画布 X 坐标（与 MinutePriceCanvas / MinuteVolumeCanvas 内 xMinute 同源，供顶层 overlay 复用） */
private fun xMinuteAt(min: Float, w: Float, showAuction: Boolean): Float {
    return if (showAuction) {
        when {
            min < 0f -> ((min + 15f) / 10f) * 0.20f * w                     // 早盘竞价 09:15(-15)-09:25(-5) → [0,0.20w]
            min <= 237f -> 0.20f * w + (min / 237f) * 0.7333f * w          // 09:30-14:57 正常分时(73.33%)
            else -> 0.9333f * w + ((min - 237f) / 3f) * 0.0667f * w        // 14:57-15:00 尾盘竞价(6.67%)
        }
    } else {
        (min.coerceIn(0f, 240f) / 240f) * w
    }
}

/**
 * 分时价格区间（方案 A / 准确性优先）：
 * 优先用后端权威的当日最高/最低价（base.high / base.low）定上下边界，
 * 避免 trends 里的脏点污染缩放；若 high/low 无效则回退到 trends 极值。
 * 边界必须包含昨收（保证 0% 基准线落在图内），仅留 1% 薄边贴近同花顺观感。
 */
private fun minuteRange(
    trends: List<DetailTrend>,
    preClose: Double,
    dayHigh: Double = 0.0,
    dayLow: Double = 0.0,
    symmetric: Boolean = true
): Pair<Double, Double> {
    val pc = if (preClose > 0) preClose else 1.0
    // 权威高低价优先；无效则回退 trends 极值
    var hi = if (dayHigh > 0) dayHigh else (trends.maxOfOrNull { it.price } ?: pc)
    var lo = if (dayLow > 0) dayLow else (trends.minOfOrNull { it.price } ?: pc)
    if (trends.isEmpty() && dayHigh <= 0 && dayLow <= 0) {
        return Pair(pc * 1.01, pc * 0.99)
    }
    // 必须包含昨收，确保 0% 线在图内
    if (preClose > 0) {
        hi = max(hi, preClose)
        lo = min(lo, preClose)
    }
    if (hi - lo < 1e-6) {
        hi += 0.01
        lo -= 0.01
    }
    if (!symmetric) {
        // ===== 满占坐标：真实高低填满画布（0% 线位置动态，不居中）=====
        val pad = (hi - lo) * 0.01
        return Pair(hi + pad, lo - pad)
    }
    // ===== 百分比对称坐标（同花顺手机默认）=====
    // 昨收价(0%)锁死画布垂直中点：上下涨跌幅取对称幅度，
    // 因此 0% 虚线恒在 h/2，无论高开/低开都居中。
    val up = max(hi - pc, 0.0)
    val dn = max(pc - lo, 0.0)
    var dev = max(up, dn)                 // 上/下偏离取较大者 → 对称半幅
    if (dev < pc * 1e-4) dev = pc * 0.005 // 全天平盘兜底 0.5%
    dev *= 1.02                           // 2% 边距，线不贴顶底
    return Pair(pc + dev, pc - dev)       // 顶=昨收+dev, 底=昨收-dev（昨收正中）
}

@Composable
private fun MinutePriceCanvas(
    trends: List<DetailTrend>,
    preClose: Double,
    maxP: Double,
    minP: Double,
    showAuction: Boolean = false,
    cursorMin: Float = CURSOR_NONE,
    cursorY: Float = -1f,
    cursorZone: Int = 0,
    modifier: Modifier = Modifier
) {
    val tm = rememberTextMeasurer()
    val numStyle = TextStyle(color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold)
    val upStyle = TextStyle(color = RED_UP, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold)
    val dnStyle = TextStyle(color = GREEN_TEXT, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold)
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 3.dp.toPx() }
    val upPct = if (preClose > 0) (maxP - preClose) / preClose * 100 else 0.0
    val dnPct = if (preClose > 0) (minP - preClose) / preClose * 100 else 0.0
    fun fmtPct(v: Double) = (if (v > 0) "+" else "") + String.format("%.2f", v) + "%"
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (trends.size < 2) return@Canvas

        val span = maxP - minP

        fun yOf(p: Double): Float = (h - ((p - minP) / span) * h).toFloat()
        // x：时间轴映射（方案丙：竞价段放大，早盘竞价明显宽于尾盘竞价）。
        // 数据 min 以 09:30 为 0；轴内分钟 = min + 15（09:15 起）。
        //   开启竞价：四段布局（轴 = 09:15 -> 15:00 总宽 w）
        //     早盘竞价 09:15-09:25 占 [0,        0.22w]  宽 22%
        //     早盘     09:30-11:30 占 [0.22w,    0.572w] 宽 35.2%（09:25-09:30 停顿 0 宽）
        //     午后     13:00-15:00 占 [0.572w,   0.86w]  宽 28.8%（午休 0 宽）
        //     尾盘竞价 14:55-15:00 占 [0.86w,    1.00w]  宽 14%
        //   关闭竞价：09:30-15:00(240min) 等比铺满 [0,w]，午休不占宽。
        //   开启竞价：三段布局（竞价独立、正常分时独立）
        //     早盘竞价 09:15-09:25 占 [0,        0.20w]  宽 20%（10 分钟独立映射）
        //     正常分时 09:30-14:57 占 [0.20w,   0.9333w] 宽 73.33%（237 分钟：早盘 120 + 午后 117）
        //     尾盘竞价 14:57-15:00 占 [0.9333w, 1.00w]  宽 6.67%（3 分钟独立映射）
        // 统一主轴 = 数据 min（09:30=0 / 11:30=120 / 13:00=121…14:57=237 / 15:00=240）。
        // 早盘竞价 09:15-09:25 用 min ∈ [-15,-5] 表达；尾盘竞价 14:57-15:00 用 min ∈ [237,240]。
        fun xMinute(min: Float): Float {
            return if (showAuction) {
                when {
                    min < 0f -> ((min + 15f) / 10f) * 0.20f * w                     // 早盘竞价 09:15(min=-15)-09:25(-5) → [0,0.20w]
                    min <= 237f -> 0.20f * w + (min / 237f) * 0.7333f * w          // 09:30-14:57 正常分时(73.33%)
                    else -> 0.9333f * w + ((min - 237f) / 3f) * 0.0667f * w        // 14:57-15:00 尾盘竞价(6.67%)
                }
            } else {
                (min.coerceIn(0f, 240f) / 240f) * w
            }
        }
        // 曲线截止 14:57（min=237），不贯穿竞价栏。
        fun xOf(t: DetailTrend): Float = xMinute(t.min.coerceIn(0, 237).toFloat())

        // 横网格：3 条装饰横线（4 等分，落在 1/4、2/4、3/4）
        for (k in 1..3) {
            val y = h * k / 4f
            drawLine(LINE_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        // 竖网格：中间"正常分时"区域始终是 4 格（4 列），开启竞价时整块被两端竞价挤压变窄。
        // 关闭竞价：09:30-15:00(240) 等比铺满，竖线落在 10:30(60)/11:30(120)/13:30(180)。
        // 开启竞价：中间分时区域（09:30→11:30→13:00→15:00）在这段被压窄的区域内仍分 4 格，
        //          两端各画一个集合竞价栏（2 列 × 4 行）。
        if (showAuction) {
            // 中间正常分时区域 09:30-14:57 被压缩到 [0.20w, 0.9333w]（73.33%），
            // 竖网格必须与价格曲线 xMinute() 完全同源，否则右端会"没收缩"。
            // 竖线落在真实分钟刻度 60(10:30) / 120(11:30) / 180(14:00)。
            for (m in floatArrayOf(60f, 120f, 180f)) {
                val x = xMinute(m)
                drawLine(LINE_GRID, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            // ===== 两端集合竞价栏（价区部分）：2 列 × 4 行 =====
            val aucColor = Color(0xFF9E9E9E)
            val aucFill = Color(0x14000000)
            fun aucPrice(x0: Float, x1: Float) {
                drawRect(color = aucFill, topLeft = Offset(x0, 0f), size = Size(x1 - x0, h))
                // 2 列：中间 1 条竖线
                val xm = x0 + (x1 - x0) * 0.5f
                drawLine(aucColor, Offset(xm, 0f), Offset(xm, h), strokeWidth = 0.8f)
                // 4 行：3 条横线（4 等分）
                for (k in 1..3) {
                    val y = h * k / 4f
                    drawLine(aucColor, Offset(x0, y), Offset(x1, y), strokeWidth = 0.8f)
                }
                // 1.5f 边线（左右）
                drawLine(aucColor, Offset(x0, 0f), Offset(x0, h), strokeWidth = 1.5f)
                drawLine(aucColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = 1.5f)
            }
            // 早盘竞价 09:15-09:25
            aucPrice(xMinute(-15f), xMinute(-5f))
            // 尾盘竞价 14:57-15:00
            aucPrice(xMinute(237f), xMinute(240f))
        } else {
            // 关闭竞价：09:30-15:00(240) 等比铺满，竖线落在 10:30(60) / 11:30(120) / 13:30(180)
            val xs = floatArrayOf(60f, 120f, 180f)
            for (m in xs) {
                val x = xMinute(m)
                drawLine(LINE_GRID, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
        }

        // 昨收基准线：灰色虚线（开启竞价时仅画在正常分时压缩区内，不伸进竞价栏）
        if (preClose > 0) {
            val yc = yOf(preClose)
            val dash = 6f
            val gap = 5f
            val xStart = if (showAuction) 0.20f * w else 0f
            var xd = xStart
            while (xd < w) {
                drawLine(Color(0xFFBBBBBB), Offset(xd, yc), Offset(min(xd + dash, w), yc), strokeWidth = 1.5f)
                xd += dash + gap
            }
        }

        // 价格曲线：统一黑色（涨跌都用黑色，涨跌信息由顶部数字/五档表达）
        val priceColor = Color(0xFF111111)

        // 曲线路径
        val path = Path()
        var pathStarted = false
        trends.forEach { t ->
            val x = xOf(t)
            val y = yOf(t.price)
            if (!pathStarted) { path.moveTo(x, y); pathStarted = true } else path.lineTo(x, y)
        }
        drawPath(path, priceColor, style = Stroke(width = 3.0f))

        // ===== 现价水平虚线（本轮需求）=====
        // 取清洗后最后一个有效分时点的价格作为「最新价」，在其高度画一条水平虚线，右端止于走势线末端。
        // 颜色按现价相对昨收的涨跌上色（涨红/跌绿/平灰），样式沿用昨收基准线的短划虚线，保证视觉一致。
        run {
            val lastTrend = trends.lastOrNull { it.price > 0.0 }
            val lastPrice = lastTrend?.price
            if (lastTrend != null && lastPrice != null && lastPrice > 0.0) {
                val yNow = yOf(lastPrice)
                val nowColor = when {
                    preClose <= 0.0 -> GRAY_FLAT
                    lastPrice > preClose + 1e-9 -> RED_UP
                    lastPrice < preClose - 1e-9 -> GREEN_TEXT
                    else -> GRAY_FLAT
                }
                // 右端止于走势线末端（最后一个有效点的 X），不再画满全宽，
                // 否则盘中未到 15:00 时虚线会超出曲线末端、与走势线脱节。
                val xEnd = xOf(lastTrend).coerceIn(0f, w)
                val dash = 7f
                val gap = 5f
                var xd = 0f
                while (xd < xEnd) {
                    drawLine(nowColor, Offset(xd, yNow), Offset(min(xd + dash, xEnd), yNow), strokeWidth = 1.6f)
                    xd += dash + gap
                }
            }
        }


        // ===== 均价线（黄色，随累计均价）=====
        val avgPath = Path()
        var started = false
        trends.forEach { t ->
            val a = t.avg
            if (a <= 0) return@forEach
            val x = xOf(t)
            val y = yOf(a)
            if (!started) { avgPath.moveTo(x, y); started = true } else avgPath.lineTo(x, y)
        }
        if (started) {
            // 均价线加粗+提亮，确保在浅灰网格与价格线下清晰可见
            drawPath(avgPath, Color(0xFFFF8C00), style = Stroke(width = 2.6f))
        }
        // 淡灰边框
        // 十字光标（价图段）：竖线穿过整个价区；横线归一化 y，交点单端显示价格（蓝色半透明框）
        if (cursorMin >= CURSOR_ACTIVE_MIN) {
            val cx = xMinute(cursorMin).coerceIn(0f, w)
            drawLine(CURSOR_LINE_COLOR, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2.0f)
        }
        // 横线与数值框仅在「价区为激活区」时绘制，保证与量区互斥（只有一根横线）
        if (cursorY in 0f..1f && cursorZone == 1) {
            val cy = (cursorY * h).coerceIn(0f, h)
            val priceAtCy = minP + (1.0 - cursorY.toDouble()) * span
            val txt = fmtNum2(priceAtCy)
            val lay = tm.measure(txt, TextStyle(color = CURSOR_BOX_TX, fontSize = 7.5.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold))
            val boxW = lay.size.width + 4f
            val boxH = lay.size.height + 0.5f
            val cyOnline = (cursorMin < -15f) || (xMinute(cursorMin) >= w * 0.5f)
            if (cyOnline) {
                // 竖线在右侧（或无竖线）：数值框放左端，横线左端截断
                drawLine(CURSOR_LINE_COLOR, Offset(boxW, cy), Offset(w, cy), strokeWidth = 2.0f)
                drawRect(color = CURSOR_BOX_BG, topLeft = Offset(0f, cy - boxH / 2f), size = Size(boxW, boxH))
                drawText(lay, topLeft = Offset(3f, cy - lay.size.height / 2f))
            } else {
                // 竖线在左侧：数值框放右端，横线右端截断
                drawLine(CURSOR_LINE_COLOR, Offset(0f, cy), Offset(w - boxW, cy), strokeWidth = 2.0f)
                drawRect(color = CURSOR_BOX_BG, topLeft = Offset(w - boxW, cy - boxH / 2f), size = Size(boxW, boxH))
                drawText(lay, topLeft = Offset(w - boxW + 3f, cy - lay.size.height / 2f))
            }
        }
        drawRect(color = MINUTE_BORDER, style = Stroke(width = 1f))

        // ===== 网格内三组角标注 =====
        // 顶部横线（y≈0）：左=最高价，右=+x%
        // 昨收虚线（y=h/2）：左=昨收价，右=0.00%
        // 底部横线（y≈h）：左=最低价，右=-x%
        val rowH = tm.measure("0", numStyle).size.height.toFloat()
        // 四角标注跟随网格：开启竞价时左端贴正常分时压缩区左线(0.20w)，右端贴最右线(w)
        val leftX = if (showAuction) 0.20f * w + insetPx else insetPx
        // 右列角标必须跟随「正常分时区右端网格」，不能钉在画布最右边界：
        //   开竞价：右端网格 = 0.9333w（尾盘竞价栏起点）；关竞价：右端网格 = w
        val rightX = if (showAuction) 0.9333f * w else w
        // 左列：价格（Top/Center/Bottom）
        run {
            val l1 = tm.measure(fmtNum2(maxP), numStyle)
            drawText(l1, topLeft = Offset(leftX, insetPx))
            if (preClose > 0) {
                val l2 = tm.measure(fmtNum2(preClose), numStyle)
                drawText(l2, topLeft = Offset(leftX, h / 2 - rowH / 2))
            }
            val l3 = tm.measure(fmtNum2(minP), numStyle)
            drawText(l3, topLeft = Offset(leftX, h - rowH - insetPx))
        }
        // 右列：涨跌幅（Top/Center/Bottom，右对齐贴右边界）
        fun pctStyle(v: Double) = when { v > 0.0001 -> upStyle; v < -0.0001 -> dnStyle; else -> numStyle }
        run {
            val lu = tm.measure(fmtPct(upPct), pctStyle(upPct))
            drawText(lu, topLeft = Offset(rightX - insetPx - lu.size.width, insetPx))
            val lz = tm.measure("0.00%", numStyle)
            drawText(lz, topLeft = Offset(rightX - insetPx - lz.size.width, h / 2 - rowH / 2))
            val ld = tm.measure(fmtPct(dnPct), pctStyle(dnPct))
            drawText(ld, topLeft = Offset(rightX - insetPx - ld.size.width, h - rowH - insetPx))
        }
    }
}


// ============================================================
// 分时指标计算（纯前端自算）
// 后端 trends 仅提供 price/vol/amount（后两者为累计值），
// 指标均基于【每分钟收盘价序列】与【每分钟量序列】计算。
//   MACD(12,26,9)：基于价格序列的指数平均
//   KDJ(9,3,3)：基于价格滚动窗口的 RSV
//   RSI(6)：基于价格涨跌
//   VOL-MA5：基于每分钟量的 5 周期均线（量比参考）
// ============================================================
private val VOLOR_MODE_MINUTE_VOL = 0   // 成交量（手）
private val VOLOR_MODE_MINUTE_AMT = 1   // 成交额（万元）
private val VOLOR_MODE_MACD = 2         // MACD（12,26,9）
private val VOLOR_MODE_KDJ = 3          // KDJ（9,3,3）
private val VOLOR_MODE_RSI = 4          // RSI（6）
private val VOLOR_MODE_VOLMA = 5        // 量 MA5（量比参考）

/** 分时指标名称表（下拉菜单顺序） */
private val MINUTE_INDICATOR_NAMES = listOf(
    "成交量", "成交额", "MACD", "KDJ", "RSI(6)", "量MA5"
)

/** EMA（指数移动平均） */
private fun emaSeries(src: DoubleArray, n: Int): DoubleArray {
    val out = DoubleArray(src.size)
    if (src.isEmpty()) return out
    val k = 2.0 / (n + 1.0)
    var prev = src[0]
    out[0] = prev
    for (i in 1 until src.size) {
        prev = src[i] * k + prev * (1 - k)
        out[i] = prev
    }
    return out
}

/** MACD（返回 DIF / DEA / HIST）hist = (DIF-DEA)*2 */
private fun macdSeries(price: DoubleArray): Triple<DoubleArray, DoubleArray, DoubleArray> {
    if (price.isEmpty()) return Triple(DoubleArray(0), DoubleArray(0), DoubleArray(0))
    val e12 = emaSeries(price, 12)
    val e26 = emaSeries(price, 26)
    val dif = DoubleArray(price.size) { e12[it] - e26[it] }
    val dea = emaSeries(dif, 9)
    val hist = DoubleArray(price.size) { (dif[it] - dea[it]) * 2.0 }
    return Triple(dif, dea, hist)
}

/** KDJ（n=9, k=3, d=3）返回 K / D / J */
private fun kdjSeries(price: DoubleArray, n: Int = 9): Triple<DoubleArray, DoubleArray, DoubleArray> {
    val sz = price.size
    val K = DoubleArray(sz); val D = DoubleArray(sz); val J = DoubleArray(sz)
    var kPrev = 50.0; var dPrev = 50.0
    for (i in 0 until sz) {
        val lo = maxOf(0, i - n + 1)
        var hh = price[lo]; var ll = price[lo]
        for (j in lo..i) { if (price[j] > hh) hh = price[j]; if (price[j] < ll) ll = price[j] }
        val rsv = if (hh - ll > 1e-9) (price[i] - ll) / (hh - ll) * 100.0 else 50.0
        val k = (2.0 / 3.0) * kPrev + (1.0 / 3.0) * rsv
        val d = (2.0 / 3.0) * dPrev + (1.0 / 3.0) * k
        K[i] = k; D[i] = d; J[i] = 3 * k - 2 * d
        kPrev = k; dPrev = d
    }
    return Triple(K, D, J)
}

/** RSI（n=6） */
private fun rsiSeries(price: DoubleArray, n: Int = 6): DoubleArray {
    val sz = price.size
    val out = DoubleArray(sz)
    if (sz == 0) return out
    var gain = 0.0; var loss = 0.0
    for (i in 0 until sz) {
        if (i > 0) {
            val ch = price[i] - price[i - 1]
            if (i <= n) { if (ch > 0) gain += ch else loss -= ch }
        }
        when {
            i < n -> out[i] = 50.0
            i == n -> out[i] = if (gain + loss > 1e-9) gain / (gain + loss) * 100.0 else 50.0
            else -> {
                val ch = price[i] - price[i - 1]
                val up = if (ch > 0) ch else 0.0
                val dn = if (ch < 0) -ch else 0.0
                gain = (gain * (n - 1) + up) / n
                loss = (loss * (n - 1) + dn) / n
                out[i] = if (gain + loss > 1e-9) gain / (gain + loss) * 100.0 else 50.0
            }
        }
    }
    return out
}

/** 每分钟量（差分后） */
private fun perMinuteVol(trends: List<DetailTrend>): DoubleArray {
    val out = DoubleArray(trends.size)
    var prev = 0.0
    for (i in trends.indices) {
        val t = trends[i]
        out[i] = if (t.min <= 0) t.vol else (t.vol - prev).coerceAtLeast(0.0)
        prev = t.vol
    }
    return out
}

/** MA（简单移动平均） */
private fun maSeries(src: DoubleArray, n: Int): DoubleArray {
    val out = DoubleArray(src.size)
    var sum = 0.0
    for (i in src.indices) {
        sum += src[i]
        if (i >= n) sum -= src[i - n]
        out[i] = sum / minOf(i + 1, n)
    }
    return out
}

@Composable
private fun MinuteVolumeCanvas(
    trends: List<DetailTrend>,
    showAuction: Boolean = false,
    preClose: Double = 0.0,
    cursorMin: Float = CURSOR_NONE,
    cursorY: Float = -1f,
    volMode: Int = 0,
    cursorZone: Int = 0
) {
    val tm = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (trends.isEmpty()) return@Canvas
        // 后端 trends.vol/amount 是【累计值】，需差分还原每分钟值（首根=min0 自身累计）
        val perMin = ArrayList<Float>(trends.size)
        val perAmt = ArrayList<Float>(trends.size)
        var prevCum = 0.0
        var prevAmt = 0.0
        for (t in trends) {
            val d = if (t.min <= 0) t.vol else (t.vol - prevCum)
            perMin.add(d.coerceAtLeast(0.0).toFloat())
            prevCum = t.vol
            val a = if (t.min <= 0) t.amount else (t.amount - prevAmt)
            perAmt.add((a.coerceAtLeast(0.0) / 10000.0).toFloat())  // 元 -> 万元
            prevAmt = t.amount
        }
        // 指标切换：0=成交量（手） 1=成交额（万元）
        val vals = if (volMode == 1) perAmt else perMin
        val maxV = (vals.maxOrNull() ?: 1f).coerceAtLeast(1e-6f)
        // 每分钟柱宽与定位：与价图/时间轴同款映射（方案丙：竞价段放大）
        fun xMinute(min: Float): Float {
            return if (showAuction) {
                when {
                    min < 0f -> ((min + 15f) / 10f) * 0.20f * w                     // 早盘竞价 09:15(-15)-09:25(-5) → [0,0.20w]
                    min <= 237f -> 0.20f * w + (min / 237f) * 0.7333f * w          // 09:30-14:57 正常分时(73.33%)
                    else -> 0.9333f * w + ((min - 237f) / 3f) * 0.0667f * w        // 14:57-15:00 尾盘竞价(6.67%)
                }
            } else {
                (min.coerceIn(0f, 240f) / 240f) * w
            }
        }
        // 柱宽：相邻两根柱 X 坐标之差 × 0.7（留 30% 空隙，柱子不贴合）
        // 正常分时区 237 分钟对应 0.7333w（开竞价）/ 240 分钟对应 w（关竞价）
        val bw = if (showAuction) (0.7333f * w / 237f) * 0.7f else (w / 240f) * 0.7f
        // 竖网格：与价图同款——中间"正常分时"区域始终 4 格，开启竞价时两端各一个竞价栏
        // 注意：量图区域的竞价栏是 2 列 × 2 行（量区竖向比价区短，行数少一半）
        if (showAuction) {
            // 中间正常分时区域竖网格：与量柱 xMinute() / 价格曲线同源，
            // 落在真实分钟刻度 60(10:30) / 120(11:30) / 180(14:00)。
            for (m in floatArrayOf(60f, 120f, 180f)) {
                val x = xMinute(m)
                drawLine(LINE_GRID, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            // ===== 两端集合竞价栏（量区部分）：2 列 × 2 行 =====
            val aucColor = Color(0xFF9E9E9E)
            val aucFill = Color(0x14000000)
            fun aucVol(x0: Float, x1: Float) {
                drawRect(color = aucFill, topLeft = Offset(x0, 0f), size = Size(x1 - x0, h))
                // 2 列：中间 1 条竖线
                val xm = x0 + (x1 - x0) * 0.5f
                drawLine(aucColor, Offset(xm, 0f), Offset(xm, h), strokeWidth = 0.8f)
                // 2 行：中间 1 条横线
                val ym = h * 0.5f
                drawLine(aucColor, Offset(x0, ym), Offset(x1, ym), strokeWidth = 0.8f)
                // 1.5f 边线（左右）
                drawLine(aucColor, Offset(x0, 0f), Offset(x0, h), strokeWidth = 1.5f)
                drawLine(aucColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = 1.5f)
            }
            aucVol(xMinute(-15f), xMinute(-5f))    // 早盘竞价 09:15-09:25
            aucVol(xMinute(237f), xMinute(240f))  // 尾盘竞价 14:57-15:00
        } else {
            for (m in floatArrayOf(60f, 120f, 180f)) {
                val x = xMinute(m)
                drawLine(LINE_GRID, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
        }
        // 量柱颜色：本分钟收盘价 vs 上一分钟收盘价
        //   > 上分钟 → 红；< 上分钟 → 绿；相等/涨停/跌停/无前值 → 白(灰)
        // 持平柱用浅灰（纯白在白色背景看不见，与同花顺“白柱”观感一致）
        if (volMode == VOLOR_MODE_MINUTE_VOL || volMode == VOLOR_MODE_MINUTE_AMT) {
            // ===== 量柱模式（成交量 / 成交额）=====
            // 量柱颜色：本分钟收盘价 vs 上一分钟收盘价
            //   > 上分钟 → 红；< 上分钟 → 绿；相等/涨停/跌停/无前值 → 白(灰)
            val FLAT_BAR = Color(0xFFBDBDBD)
            trends.forEachIndexed { idx, t ->
                val vh = (vals[idx] / maxV * h).toFloat()
                val x = xMinute(t.min.coerceIn(0, 237).toFloat())
                val prevPrice = if (idx > 0) trends[idx - 1].price else t.price
                val barColor = when {
                    t.price > prevPrice -> RED_UP
                    t.price < prevPrice -> GREEN_DOWN
                    else -> FLAT_BAR
                }
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, h - vh),
                    size = Size(bw.coerceAtLeast(0.8f), vh)
                )
            }
        } else {
            // ===== 指标线模式（MACD / KDJ / RSI / 量MA5）=====
            drawMinuteIndicator(w, h, trends, volMode) { m -> xMinute(m) }
        }
        // 十字光标竖线（量图段）：纯黑，仅在量图区域内画，时间轴不画
        if (cursorMin >= CURSOR_ACTIVE_MIN) {
            val cx = xMinute(cursorMin).coerceIn(0f, w)
            drawLine(CURSOR_LINE_COLOR, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2.0f)
        }
        // 十字光标横线（量图段）：两端各显示横线高度对应的量值，蓝底白字
        // 横线与数值框仅在「量区为激活区」时绘制，保证与价区互斥（只有一根横线）
        if (cursorY in 0f..1f && cursorZone == 2) {
            val cy = (cursorY * h).coerceIn(0f, h)
            val txt = if (volMode >= 2) {
                // 指标模式：横线高度按该指标的纵轴范围反算数值
                val price = DoubleArray(trends.size) { trends[it].price }
                when (volMode) {
                    2 -> { // MACD: lo=-mx, hi=mx
                        val (dif, dea, hist) = macdSeries(price)
                        var mx = 1e-6
                        for (i in price.indices) mx = maxOf(mx, kotlin.math.abs(dif[i]), kotlin.math.abs(dea[i]), kotlin.math.abs(hist[i]))
                        fmtNum2(mx - cursorY.toDouble() * (2.0 * mx))
                    }
                    3 -> { // KDJ: 0-100
                        val (k, d, j) = kdjSeries(price, 9)
                        val idx = trends.indexOfFirst { kotlin.math.abs(it.min.toFloat() - cursorMin) < 0.5f }
                        val ii = if (idx >= 0) idx else trends.size - 1
                        "K:" + fmtNum2(k[ii]) + " D:" + fmtNum2(d[ii]) + " J:" + fmtNum2(j[ii])
                    }
                    4 -> { // RSI: 0-100
                        val ii = trends.indexOfFirst { kotlin.math.abs(it.min.toFloat() - cursorMin) < 0.5f }.let { if (it >= 0) it else trends.size - 1 }
                        "RSI:" + fmtNum2(100.0 - cursorY.toDouble() * 100.0)
                    }
                    else -> { // 量MA5: 0-maMax
                        val pv = perMinuteVol(trends)
                        val ma = maSeries(pv, 5)
                        val mx = (ma.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
                        fmtVol(mx - cursorY.toDouble() * mx)
                    }
                }
            } else {
                val vAt = (1.0 - cursorY.toDouble()) * maxV
                if (volMode == 1) fmtAmt(vAt) else fmtVol(vAt)
            }
            val lay = tm.measure(txt, TextStyle(color = CURSOR_BOX_TX, fontSize = 7.5.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold))
            val boxW = lay.size.width + 4f
            val boxH = lay.size.height + 0.5f
            // 两端各截断 boxW，中间画横线；左右端各放一个蓝框写同数值
            drawLine(CURSOR_LINE_COLOR, Offset(boxW, cy), Offset(w - boxW, cy), strokeWidth = 2.0f)
            drawRect(color = CURSOR_BOX_BG, topLeft = Offset(0f, cy - boxH / 2f), size = Size(boxW, boxH))
            drawText(lay, topLeft = Offset(3f, cy - lay.size.height / 2f))
            drawRect(color = CURSOR_BOX_BG, topLeft = Offset(w - boxW, cy - boxH / 2f), size = Size(boxW, boxH))
            drawText(lay, topLeft = Offset(w - boxW + 3f, cy - lay.size.height / 2f))
        }
        // 淡灰边框
        drawRect(color = MINUTE_BORDER, style = Stroke(width = 1f))
    }
}
/**
 * 分时指标线绘制（量区副图，volMode >= 2 时调用）
 *   MACD(2)：DIF/DEA 双线 + HIST 红绿柱，0 轴居中
 *   KDJ(3)：K/D/J 三线，固定 0-100
 *   RSI(4)：单线，固定 0-100
 *   量MA5(5)：单线，量纲同量柱
 * xOf：分钟 -> 画布 X 坐标（与量柱同源映射）
 */
private fun DrawScope.drawMinuteIndicator(
    w: Float,
    h: Float,
    trends: List<DetailTrend>,
    volMode: Int,
    xOf: (Float) -> Float
) {
    if (trends.isEmpty()) return
    val n = trends.size
    val price = DoubleArray(n) { trends[it].price }
    // 每分钟分钟序号（用于 xOf）
    fun xAt(i: Int): Float = xOf(trends[i].min.coerceIn(0, 240).toFloat())

    // 辅助：把一串值按 [lo, hi] 映射到画布 Y（上=hi，下=lo）
    fun yOf(v: Double, lo: Double, hi: Double): Float {
        if (hi - lo < 1e-9) return h * 0.5f
        val r = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        return (h - r * h).toFloat()
    }
    // 画一条折线
    fun polyline(vals: DoubleArray, lo: Double, hi: Double, color: Color, sw: Float) {
        if (vals.isEmpty()) return
        val p = Path()
        var started = false
        for (i in 0 until minOf(vals.size, n)) {
            val x = xAt(i)
            val y = yOf(vals[i], lo, hi)
            if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
    // 水平参考线
    fun hline(v: Double, lo: Double, hi: Double, color: Color) {
        val y = yOf(v, lo, hi)
        drawLine(color, Offset(0f, y), Offset(w, y), strokeWidth = 0.8f)
    }

    val CLR_DIF = Color(0xFFFF8C00)   // DIF 橙
    val CLR_DEA = Color(0xFF1565C0)   // DEA 蓝
    val CLR_K = Color(0xFFFF8C00)     // K 橙
    val CLR_D = Color(0xFF1565C0)     // D 蓝
    val CLR_J = Color(0xFF8E24AA)     // J 紫
    val CLR_RSI = Color(0xFF8E24AA)   // RSI 紫
    val CLR_MA = Color(0xFFFF8C00)    // 量MA 橙

    when (volMode) {
        VOLOR_MODE_MACD -> {
            val (dif, dea, hist) = macdSeries(price)
            var mx = 1e-6
            for (i in 0 until n) {
                mx = maxOf(mx, kotlin.math.abs(dif[i]), kotlin.math.abs(dea[i]), kotlin.math.abs(hist[i]))
            }
            val lo = -mx; val hi = mx
            // 0 轴
            hline(0.0, lo, hi, LINE_GRID)
            // HIST 柱：正红负绿
            val bwid = (w / (n.coerceAtLeast(1)).toFloat()) * 0.55f
            for (i in 0 until n) {
                val x = xAt(i)
                val y0 = yOf(0.0, lo, hi)
                val y1 = yOf(hist[i], lo, hi)
                val top = minOf(y0, y1); val bot = maxOf(y0, y1)
                val c = if (hist[i] >= 0) RED_UP else GREEN_DOWN
                drawRect(color = c, topLeft = Offset(x - bwid / 2f, top), size = Size(bwid.coerceAtLeast(0.6f), (bot - top).coerceAtLeast(0.6f)))
            }
            polyline(dif, lo, hi, CLR_DIF, 1.6f)
            polyline(dea, lo, hi, CLR_DEA, 1.6f)
        }
        VOLOR_MODE_KDJ -> {
            val (k, d, j) = kdjSeries(price, 9)
            val lo = 0.0; val hi = 100.0
            hline(20.0, lo, hi, LINE_GRID)
            hline(50.0, lo, hi, LINE_GRID)
            hline(80.0, lo, hi, LINE_GRID)
            polyline(k, lo, hi, CLR_K, 1.6f)
            polyline(d, lo, hi, CLR_D, 1.6f)
            polyline(j, lo, hi, CLR_J, 1.6f)
        }
        VOLOR_MODE_RSI -> {
            val r = rsiSeries(price, 6)
            val lo = 0.0; val hi = 100.0
            hline(20.0, lo, hi, LINE_GRID)
            hline(50.0, lo, hi, LINE_GRID)
            hline(80.0, lo, hi, LINE_GRID)
            polyline(r, lo, hi, CLR_RSI, 1.8f)
        }
        VOLOR_MODE_VOLMA -> {
            val pv = perMinuteVol(trends)
            val ma = maSeries(pv, 5)
            val mx = (ma.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
            polyline(ma, 0.0, mx, CLR_MA, 1.8f)
        }
    }
}

/** 右侧价格轴（3 个刻度） */
@Composable
private fun PriceAxis(maxV: Double, minV: Double, midV: Double, modifier: Modifier) {
    Column(
        modifier = modifier.padding(start = 2.dp, end = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        Text(fmtNum2(maxV), color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont)
        Text(fmtNum2(if (midV > 0) midV else (maxV + minV) / 2), color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont)
        Text(fmtNum2(minV), color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont)
    }
}

/**
 * 时间轴：位于价图与量图之间，刻度与分时曲线严格对齐。
 * 开启集合竞价（四刻度）：09:15 / 09:30 / 11:30 / 15:00
 *   方案丙：四段布局（早盘竞价明显宽于尾盘竞价），
 *     09:15->0 / 09:30->22% / 11:30->57.2% / 15:00->100%。
 * 关闭集合竞价（三刻度）：09:30 / 11:30 / 15:00，09:30-15:00(240min) 等比铺满，11:30 天然在正中。
 */
@Composable
private fun TimeAxis(showAuction: Boolean = false, modifier: Modifier = Modifier) {
    // 时间刻度锚点 = 分时价画布的"网格格位"（不是物理时间）：
    //   开启竞价：中间正常分时区被两端竞价栏压缩到 [0.20w, 0.9333w]（73.33%），
    //             其内部固定 4 格等分。三个时间标签跟随该压缩区网格移动：
    //             09:30 -> 压缩区左端(0.20w) / 11:30 -> 压缩区中格(约 0.5667w) / 15:00 -> 右端(1.00w)。
    //             另在早盘竞价栏左端补一个 "09:15"。
    //   关闭竞价：09:30 / 11:30 / 15:00 按 240 分钟等比铺满 [0,w]。
    //   数字加粗、在时间轴上垂居中。
    data class Tick(val label: String, val min: Float, val first: Boolean, val last: Boolean)
    val ticks = if (showAuction) {
        listOf(
            Tick("09:15", -15f, true, false),   // 早盘竞价栏左端
            Tick("09:30", 0f, false, false),
            Tick("11:30", 120f, false, false),
            Tick("15:00", 240f, false, true)
        )
    } else {
        listOf(
            Tick("09:30", 0f, true, false),
            Tick("11:30", 120f, false, false),
            Tick("15:00", 240f, false, true)
        )
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val w = constraints.maxWidth.toFloat()
        fun xOf(min: Float): Float {
            return if (showAuction) {
                when {
                    min < 0f -> 0f                                               // 09:15 → 早盘竞价栏左端
                    min < 237f -> 0.20f * w + (min / 237f) * 0.7333f * w        // 09:30-14:57 压缩区(73.33%)，与价格曲线同源
                    else -> 0.20f * w + 0.7333f * w                            // 15:00(min=240) → 网格最右线 0.9333w
                }
            } else {
                (min.coerceIn(0f, 240f) / 240f) * w
            }
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val labelPx = with(density) { 30.dp.toPx() }
        // 标签与网格线的内侧间距：不压线，落在线内侧一点点
        val labelInset = with(density) { 2.dp.toPx() }
        ticks.forEach { tk ->
            val x = xOf(tk.min)
            // 标签在时间轴上按刻度【线】对齐：左端标签左对齐贴线、右端标签右对齐贴线、中间标签水平居中压线
            // 标签贴线方式（落在网格线「内侧」，不压线）：
            //   首端标签（min<=0，即 09:15 / 09:30）→ 标签左缘 = 网格线右侧内侧
            //   尾端标签（min>=240，即 15:00）   → 标签右缘 = 网格线左侧内侧
            //   中间标签（11:30）                → 绕网格线水平居中
            // 关键：09:30 用 xOf(0)=(0.20w 开竞价 / 0 关竞价)、15:00 用 xOf(240)=w，
            //       开启集合竞价后两端标签跟随收缩后的网格。
            val isHead = tk.min <= 0f
            val isTail = tk.min >= 240f
            val leftPx = when {
                isHead -> (x + labelInset).coerceAtLeast(0f)                        // 首端：左缘落在网格线内侧
                isTail -> (x - labelInset - labelPx).coerceAtLeast(0f)               // 尾端：右缘落在网格线内侧
                else -> (x - labelPx / 2f).coerceIn(0f, (w - labelPx).coerceAtLeast(0f))  // 中间居中
            }
            Text(
                tk.label,
                color = TEXT_SUB,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset(x = with(density) { leftPx.toDp() })
                    .width(with(density) { labelPx.toDp() })
                    .wrapContentWidth(if (isHead) Alignment.Start else if (isTail) Alignment.End else Alignment.CenterHorizontally)
            )
        }
    }
}
// ============================================================
// 五日分时图：5 天分时横向拼接，上=价格曲线（含均价线），下=量柱，底部=日期
// ============================================================
@Composable
private fun FiveDayChart(d: StockDetail?) {
    val days = d?.trends5 ?: emptyList()
    val preClose = d?.base?.preClose ?: 0.0
    if (days.isEmpty() || days.all { it.points.isEmpty() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无五日分时数据", color = TEXT_SUB, fontSize = 14.sp)
        }
        return
    }
    // 全周价格范围（所有天的所有点）
    val allPts = days.flatMap { it.points }
    val maxP = allPts.maxOf { it.price }
    val minP = allPts.minOf { it.price }
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 6.dp)
    ) {
        // 上部价格图（占 70%）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .padding(end = 46.dp)
        ) {
            FiveDayPriceCanvas(days, maxP, minP)
            PriceAxis(
                maxV = maxP,
                minV = minP,
                midV = if (preClose > 0) preClose else (maxP + minP) / 2,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(46.dp)
                    .fillMaxHeight()
            )
        }
        // 下部量图（占 30%）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
                .padding(end = 46.dp, top = 4.dp)
        ) {
            FiveDayVolumeCanvas(days)
        }
        // 日期轴
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                Text(shortDate(day.date), color = TEXT_SUB, fontSize = 10.sp)
            }
        }
    }
}

/** 把 20260923 转成 09-23 */
private fun shortDate(s: String): String {
    if (s.length >= 8) return s.substring(4, 6) + "-" + s.substring(6, 8)
    return s
}

@Composable
private fun FiveDayPriceCanvas(days: List<DetailDay>, maxP: Double, minP: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (days.isEmpty()) return@Canvas
        var hi = maxP
        var lo = minP
        if (hi - lo < 1e-6) {
            hi += 0.01
            lo -= 0.01
        }
        val range = hi - lo
        hi += range * 0.06
        lo -= range * 0.06
        val span = hi - lo
        fun yOf(p: Double): Float = (h - ((p - lo) / span) * h).toFloat()
        // 网格横线
        for (k in 1..3) {
            val y = h * k / 4f
            drawLine(LINE_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        val dayW = w / days.size
        // 每天单独画分段，日与日之间画分隔竖线
        days.forEachIndexed { di, day ->
            val pts = day.points
            val x0 = di * dayW
            if (di > 0) {
                drawLine(LINE_GRID, Offset(x0, 0f), Offset(x0, h), strokeWidth = 1f)
            }
            if (pts.size < 2) return@forEachIndexed
            val n = max(pts.size - 1, 1)
            fun xOf(i: Int): Float = x0 + (i.toFloat() / n) * dayW
            // 相对每天首点判断涨跌着色
            val first = pts.first().price
            val last = pts.last().price
            val lineColor = when {
                last > first -> RED_UP
                last < first -> GREEN_TEXT
                else -> GRAY_FLAT
            }
            val path = Path()
            pts.forEachIndexed { i, t ->
                val x = xOf(i)
                val y = yOf(t.price)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.0f))
            // 均价线（黄色）
            val avgPath = Path()
            var started = false
            pts.forEachIndexed { i, t ->
                val a = t.avg
                if (a <= 0) return@forEachIndexed
                val x = xOf(i)
                val y = yOf(a)
                if (!started) { avgPath.moveTo(x, y); started = true } else avgPath.lineTo(x, y)
            }
            if (started) {
                drawPath(avgPath, Color(0xFFFFB300), style = Stroke(width = 1.5f))
            }
        }
    }
}

@Composable
private fun FiveDayVolumeCanvas(days: List<DetailDay>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxV = days.flatMap { it.points }.maxOfOrNull { it.vol }?.coerceAtLeast(1e-6) ?: 1.0
        val dayW = w / days.size.coerceAtLeast(1)
        days.forEachIndexed { di, day ->
            val pts = day.points
            val x0 = di * dayW
            val n = pts.size.coerceAtLeast(1)
            val bw = dayW / n
            pts.forEachIndexed { i, t ->
                val vh = (t.vol / maxV * h).toFloat()
                drawRect(
                    color = Color(0xFF9AA0A6),
                    topLeft = Offset(x0 + i * bw, h - vh),
                    size = Size(max(bw - 0.6f, 0.6f), vh)
                )
            }
        }
    }
}

// ============================================================
// K线图：上=蜡烛，下=量柱
//
// ★ 交互对齐同花顺（2026-09-23 抓取实测）：
//   - 可视根数范围：27（放大到最大）~ 106（缩到最小），约 4 倍
//   - 双指捏合：连续缩放，一步到位
//   - 拖动：平移回看历史（同花顺需全屏才可拖，我们直接支持更顺手）
//   - 底部按钮栏：[－] 缩小  [＋] 放大  [←] 往前 1 天  [→] 往后 1 天
//   - 柱实体宽 ≈ slot * 0.82（同花顺实测 37/45 ≈ 0.82）
// ============================================================

/** 可视根数上下限：放大最大 27 根/屏；缩到最小 ≈320 根/屏（柱子几乎连成一条线，同花顺观感） */
private const val K_MIN_VISIBLE = 27
private const val K_MAX_VISIBLE = 1500
private const val K_DEFAULT_VISIBLE = 60
/** 点击 ＋/－ 按钮时按比例跳档 */
private const val K_BTN_ZOOM_STEP = 1.25f

/** 竖线颜色（深灰） */
private val K_CROSS_COLOR = Color(0xFF666666)

/**
 * 均线（MA）默认参数与配色。
 * 每条参数对应一条均线，颜色按顺序循环分配。
 * 后期可由用户在「设置」弹窗中自定义参数（如 5,10,20,60）。
 */
private val MA_DEFAULT_PERIODS = listOf(5, 10, 20, 60)
private val MA_COLORS = listOf(
    Color(0xFF000000), // 黑     MA5（固定）
    Color(0xFFF5A623), // 橙     MA10
    Color(0xFFB44DD8), // 紫     MA20
    Color(0xFF2E8BE6), // 蓝     MA30
    Color(0xFF306612), // 墨绿   MA60
    Color(0xFFE91E63), // 玫红   MA120
    Color(0xFF8D6E63)  // 棕     兜底
)

/** 参数值 -> 固定颜色。同一个参数（如 5）无论排在第几位，颜色永远一致。 */
private val MA_PARAM_COLOR: Map<Int, Color> = mapOf(
    5 to Color(0xFF000000),   // M5  黑（固定）
    10 to Color(0xFFF5A623),  // M10 橙
    20 to Color(0xFFB44DD8),  // M20 紫
    30 to Color(0xFF2E8BE6),  // M30 蓝
    60 to Color(0xFF306612),  // M60 墨绿
    120 to Color(0xFFE91E63)  // M120 玫红
)

private val MA_FALLBACK_COLORS = listOf(
    Color(0xFF8D6E63), Color(0xFFE91E63), Color(0xFF00838F), Color(0xFF6D4C41)
)

/** 取均线颜色：优先按参数精确匹配；未收录的参数按参数大小稳定取兜底色。 */
internal fun maColorOf(period: Int): Color = MA_PARAM_COLOR[period]
    ?: MA_FALLBACK_COLORS[(period / 10) % MA_FALLBACK_COLORS.size]

/**
 * 计算均线：返回与 line 等长的数组，第 i 项为「最近 period 根收盘均值」。
 * 数据不足 period 的位置返回 Double.NaN（该点不画）。
 */
private fun computeMA(line: List<KLinePoint>, period: Int): DoubleArray {
    val out = DoubleArray(line.size) { Double.NaN }
    if (period <= 0 || line.isEmpty()) return out
    var sum = 0.0
    for (i in line.indices) {
        sum += line[i].close
        if (i >= period) sum -= line[i - period].close
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}

/**
 * 计算「量均线」：与 computeMA 同口径，但对成交量 vol 做周期均值。
 * 数据不足 period 的位置返回 Double.NaN（该点不画）。
 */
private fun computeVolMA(line: List<KLinePoint>, period: Int): DoubleArray {
    val out = DoubleArray(line.size) { Double.NaN }
    if (period <= 0 || line.isEmpty()) return out
    var sum = 0.0
    for (i in line.indices) {
        sum += line[i].vol
        if (i >= period) sum -= line[i - period].vol
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}

/**
 * MACD 指标：EMA12 / EMA26 / DIF / DEA(9) / MACD 柱。
 * 口径与同花顺一致：
 *   EMA(n) 递归平滑（首值取首个收盘价）
 *   DIF  = EMA12 - EMA26
 *   DEA  = EMA(DIF, 9)
 *   MACD = (DIF - DEA) * 2
 * 注意：必须传入「全量」line 计算（EMA 递归与历史有关，
 *      只算可见窗口会导致值全错），绘制时再按窗口切片。
 */
private class MacdResult(
    val dif: DoubleArray,
    val dea: DoubleArray,
    val macd: DoubleArray
)

private fun ema(src: DoubleArray, n: Int): DoubleArray {
    val out = DoubleArray(src.size)
    if (src.isEmpty() || n <= 0) return out
    val k = 2.0 / (n + 1)
    out[0] = src[0]
    for (i in 1 until src.size) out[i] = src[i] * k + out[i - 1] * (1 - k)
    return out
}

private fun computeMACD(line: List<KLinePoint>): MacdResult {
    val n = line.size
    if (n == 0) return MacdResult(DoubleArray(0), DoubleArray(0), DoubleArray(0))
    val close = DoubleArray(n) { line[it].close }
    val e12 = ema(close, 12)
    val e26 = ema(close, 26)
    val dif = DoubleArray(n) { e12[it] - e26[it] }
    val dea = ema(dif, 9)
    val macd = DoubleArray(n) { (dif[it] - dea[it]) * 2.0 }
    return MacdResult(dif, dea, macd)
}


// ============================================================
// 副图指标算法（全部基于「全量」line，递归/平滑与历史一致；绘制时再切片）
// ============================================================

/** 简单移动平均（对任意 DoubleArray 窗口），返回等长数组，不足周期为 NaN。 */
private fun smaOf(src: DoubleArray, period: Int): DoubleArray {
    val out = DoubleArray(src.size) { Double.NaN }
    if (period <= 0 || src.isEmpty()) return out
    var sum = 0.0
    for (i in src.indices) {
        sum += src[i]
        if (i >= period) sum -= src[i - period]
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}

/** 滑动窗口标准差（总体），不足周期为 NaN。 */
private fun stdOf(src: DoubleArray, period: Int): DoubleArray {
    val out = DoubleArray(src.size) { Double.NaN }
    if (period <= 0 || src.isEmpty()) return out
    val ma = smaOf(src, period)
    for (i in src.indices) {
        if (ma[i].isNaN()) continue
        var acc = 0.0
        for (j in i - period + 1..i) {
            val d = src[j] - ma[i]
            acc += d * d
        }
        out[i] = kotlin.math.sqrt(acc / period)
    }
    return out
}

/** 更高周期 EMA 的“前置值”：同花顺口径 SMA 用；此处直接用 ema 递归。 */
private fun emaOf(src: DoubleArray, n: Int): DoubleArray = ema(src, n)

/** KDJ(9,3,3)：返回 K/D/J 三条，等长，不足周期为 NaN。 */
private class KdjResult(val k: DoubleArray, val d: DoubleArray, val j: DoubleArray)

private fun computeKDJ(line: List<KLinePoint>, n: Int = 9, m1: Int = 3, m2: Int = 3): KdjResult {
    val sz = line.size
    val K = DoubleArray(sz) { Double.NaN }
    val D = DoubleArray(sz) { Double.NaN }
    val J = DoubleArray(sz) { Double.NaN }
    if (sz == 0) return KdjResult(K, D, J)
    var prevK = 50.0
    var prevD = 50.0
    for (i in 0 until sz) {
        val lo = (i - (n - 1)).coerceAtLeast(0)
        var hh = Double.NEGATIVE_INFINITY
        var ll = Double.POSITIVE_INFINITY
        for (j in lo..i) {
            if (line[j].high > hh) hh = line[j].high
            if (line[j].low < ll) ll = line[j].low
        }
        val rsv = if (hh - ll < 1e-12) 50.0 else (line[i].close - ll) / (hh - ll) * 100.0
        // 同花顺：K = (2/3)*prevK + (1/3)*RSV；D = (2/3)*prevD + (1/3)*K；J = 3K - 2D
        val kk = (2.0 / 3.0) * prevK + (1.0 / 3.0) * rsv
        val dd = (2.0 / 3.0) * prevD + (1.0 / 3.0) * kk
        prevK = kk
        prevD = dd
        K[i] = kk
        D[i] = dd
        J[i] = 3 * kk - 2 * dd
    }
    return KdjResult(K, D, J)
}

/** RSI(6,12,24)：经典 Wilder 口径的简化（用简单涨跌幅均值），返回三条。 */
private class RsiResult(val r6: DoubleArray, val r12: DoubleArray, val r24: DoubleArray)

private fun rsiOf(close: DoubleArray, period: Int): DoubleArray {
    val sz = close.size
    val out = DoubleArray(sz) { Double.NaN }
    if (period <= 0 || sz == 0) return out
    var upSum = 0.0
    var dnSum = 0.0
    for (i in 1 until sz) {
        val chg = close[i] - close[i - 1]
        val up = if (chg > 0) chg else 0.0
        val dn = if (chg < 0) -chg else 0.0
        upSum += up
        dnSum += dn
        if (i > period) {
            val old = close[i - period] - close[i - period - 1]
            upSum -= if (old > 0) old else 0.0
            dnSum -= if (old < 0) -old else 0.0
        }
        if (i >= period) {
            out[i] = if (upSum + dnSum < 1e-12) 50.0 else upSum / (upSum + dnSum) * 100.0
        }
    }
    return out
}

private fun computeRSI(line: List<KLinePoint>): RsiResult {
    val c = DoubleArray(line.size) { line[it].close }
    return RsiResult(rsiOf(c, 6), rsiOf(c, 12), rsiOf(c, 24))
}

/** WR(14)：威廉指标，0~100，数值=超买小/超卖大（同花顺口径取负号归一）。 */
private fun computeWR(line: List<KLinePoint>, n: Int = 14): DoubleArray {
    val sz = line.size
    val out = DoubleArray(sz) { Double.NaN }
    for (i in 0 until sz) {
        val lo = (i - (n - 1)).coerceAtLeast(0)
        var hh = Double.NEGATIVE_INFINITY
        var ll = Double.POSITIVE_INFINITY
        for (j in lo..i) {
            if (line[j].high > hh) hh = line[j].high
            if (line[j].low < ll) ll = line[j].low
        }
        if (i >= n - 1) {
            out[i] = if (hh - ll < 1e-12) 0.0 else (hh - line[i].close) / (hh - ll) * 100.0
        }
    }
    return out
}

/** CCI(14)：典型价 TP，CCI = (TP-MA(TP)) / (0.015*MD)。 */
private fun computeCCI(line: List<KLinePoint>, n: Int = 14): DoubleArray {
    val sz = line.size
    val out = DoubleArray(sz) { Double.NaN }
    val tp = DoubleArray(sz) { (line[it].high + line[it].low + line[it].close) / 3.0 }
    val matp = smaOf(tp, n)
    for (i in 0 until sz) {
        if (matp[i].isNaN()) continue
        var md = 0.0
        for (j in i - n + 1..i) md += kotlin.math.abs(tp[j] - matp[i])
        md /= n
        out[i] = if (md < 1e-12) 0.0 else (tp[i] - matp[i]) / (0.015 * md)
    }
    return out
}

/** BIAS(6,12,24)：乖离率 %，返回三条。 */
private class BiasResult(val b6: DoubleArray, val b12: DoubleArray, val b24: DoubleArray)

private fun biasOf(close: DoubleArray, period: Int): DoubleArray {
    val ma = smaOf(close, period)
    return DoubleArray(close.size) { i ->
        if (ma[i].isNaN() || kotlin.math.abs(ma[i]) < 1e-12) Double.NaN
        else (close[i] - ma[i]) / ma[i] * 100.0
    }
}

private fun computeBIAS(line: List<KLinePoint>): BiasResult {
    val c = DoubleArray(line.size) { line[it].close }
    return BiasResult(biasOf(c, 6), biasOf(c, 12), biasOf(c, 24))
}

/** PSY(12)：心理线 %，统计近 n 根上涨根占比×100。 */
private fun computePSY(line: List<KLinePoint>, n: Int = 12): DoubleArray {
    val sz = line.size
    val out = DoubleArray(sz) { Double.NaN }
    for (i in 0 until sz) {
        if (i < n) continue
        var up = 0
        for (j in i - n + 1..i) if (line[j].close > line[j - 1].close) up++
        out[i] = up.toDouble() / n * 100.0
    }
    val up0 = if (sz > 0) { var c = 0; for (j in 1..0) c++; c } else 0
    return out
}

/** OBV：能量潮，收涨则累加当日量、收跌则累减，平盘不变。 */
private fun computeOBV(line: List<KLinePoint>): DoubleArray {
    val sz = line.size
    val out = DoubleArray(sz)
    if (sz == 0) return out
    out[0] = line[0].vol
    for (i in 1 until sz) {
        out[i] = when {
            line[i].close > line[i - 1].close -> out[i - 1] + line[i].vol
            line[i].close < line[i - 1].close -> out[i - 1] - line[i].vol
            else -> out[i - 1]
        }
    }
    return out
}

/** DMI(14,6)：返回 +DI / -DI / ADX 三条。 */
private class DmiResult(val pdi: DoubleArray, val mdi: DoubleArray, val adx: DoubleArray)

private fun computeDMI(line: List<KLinePoint>, n: Int = 14, m: Int = 6): DmiResult {
    val sz = line.size
    val pdi = DoubleArray(sz) { Double.NaN }
    val mdi = DoubleArray(sz) { Double.NaN }
    val adx = DoubleArray(sz) { Double.NaN }
    if (sz == 0) return DmiResult(pdi, mdi, adx)
    val tr = DoubleArray(sz)
    val pdm = DoubleArray(sz)
    val mdm = DoubleArray(sz)
    for (i in 1 until sz) {
        val h = line[i].high
        val l = line[i].low
        val pc = line[i - 1].close
        val ph = line[i - 1].high
        val pl = line[i - 1].low
        tr[i] = maxOf(h - l, kotlin.math.abs(h - pc), kotlin.math.abs(l - pc))
        val upMove = h - ph
        val dnMove = pl - l
        pdm[i] = if (upMove > dnMove && upMove > 0) upMove else 0.0
        mdm[i] = if (dnMove > upMove && dnMove > 0) dnMove else 0.0
    }
    // 用累加法平滑（近似 Wilder）
    val trSum = DoubleArray(sz)
    val pSum = DoubleArray(sz)
    val mSum = DoubleArray(sz)
    for (i in 1 until sz) {
        if (i <= n) {
            trSum[i] = trSum[i - 1] + tr[i]
            pSum[i] = pSum[i - 1] + pdm[i]
            mSum[i] = mSum[i - 1] + mdm[i]
        } else {
            trSum[i] = trSum[i - 1] - trSum[i - 1] / n + tr[i]
            pSum[i] = pSum[i - 1] - pSum[i - 1] / n + pdm[i]
            mSum[i] = mSum[i - 1] - mSum[i - 1] / n + mdm[i]
        }
        if (i >= n) {
            val p = if (trSum[i] < 1e-12) 0.0 else pSum[i] / trSum[i] * 100.0
            val mm = if (trSum[i] < 1e-12) 0.0 else mSum[i] / trSum[i] * 100.0
            pdi[i] = p
            mdi[i] = mm
        }
    }
    // ADX = MA(|+DI - -DI| / (+DI + -DI) * 100, m)
    val dx = DoubleArray(sz) { Double.NaN }
    for (i in 0 until sz) {
        if (!pdi[i].isNaN() && !mdi[i].isNaN()) {
            val sum = pdi[i] + mdi[i]
            dx[i] = if (sum < 1e-12) 0.0 else kotlin.math.abs(pdi[i] - mdi[i]) / sum * 100.0
        }
    }
    val adxTmp = smaOf(dx.map { if (it.isNaN()) 0.0 else it }.toDoubleArray(), m)
    for (i in 0 until sz) if (!dx[i].isNaN()) adx[i] = adxTmp[i]
    return DmiResult(pdi, mdi, adx)
}

/** BOLL(20,2)：返回上/中/下轨。 */
private class BollResult(val up: DoubleArray, val mid: DoubleArray, val low: DoubleArray)

private fun computeBOLL(line: List<KLinePoint>, n: Int = 20, k: Double = 2.0): BollResult {
    val c = DoubleArray(line.size) { line[it].close }
    val mid = smaOf(c, n)
    val sd = stdOf(c, n)
    val up = DoubleArray(c.size) { i -> if (mid[i].isNaN()) Double.NaN else mid[i] + k * sd[i] }
    val low = DoubleArray(c.size) { i -> if (mid[i].isNaN()) Double.NaN else mid[i] - k * sd[i] }
    return BollResult(up, mid, low)
}

/** 已知窗口右端下标 right、窗口根数 visible 时，窗口左端下标 */
private fun startOf(right: Int, visible: Int, size: Int): Int =
    (right - visible + 1).coerceAtLeast(0).coerceAtMost(size - 1)

@Composable
private fun KLineChart(
    d: StockDetail?,
    selIdx: Int? = null,
    maPeriods: List<Int> = MA_DEFAULT_PERIODS,
    showChip: Boolean = false,
    onMaPeriodsChange: (List<Int>) -> Unit = {},
    onSelect: (Int?) -> Unit = {},
    // BS/T 买卖点标记权限（sd_bs_marks）：无权则不画徽标 + 打开时弹引导
    canBs: Boolean = true,
    onLockedBs: () -> Unit = {}
) {
    val line = d?.line ?: emptyList()
    if (line.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无K线数据", color = TEXT_SUB, fontSize = 14.sp)
        }
        return
    }

    // ===== 状态化可视窗口 =====
    // visible：一屏显示多少根（27~106）
    // rightIndex：窗口右端在 line 中的下标（指向最新一根则为 line.lastIndex）
    var visible by remember(d?.code) { mutableIntStateOf(K_DEFAULT_VISIBLE.coerceIn(K_MIN_VISIBLE, line.size.coerceAtLeast(1))) }
    var rightIndex by remember(d?.code) { mutableIntStateOf(line.lastIndex) }
    // 日K十字光标：当前光标所在窗口内的局部下标，-1 表示未激活。
    var kdayCursorLocal by remember(d?.code) { mutableIntStateOf(-1) }

    // 数据变化（切股票/换周期）时重置窗口
    LaunchedEffect(d?.code, line.size) {
        visible = K_DEFAULT_VISIBLE.coerceIn(K_MIN_VISIBLE, line.size.coerceAtLeast(1))
        rightIndex = line.lastIndex
    }


    // ★ 当 marks 变化（一键模拟生成后）时，把视窗右端滚到「最新一条 marks」所在的那根 K 线，
    //   解决「生成的标记在更右侧、视窗没滚过去就看不到」的问题。
    LaunchedEffect(d?.code, d?.marks) {
        val ms = d?.marks
        if (!ms.isNullOrEmpty()) {
            val latest = ms.maxByOrNull { normDateKey(it.date) }
            if (latest != null) {
                val key = normDateKey(latest.date)
                val idx = line.indexOfLast { normDateKey(it.date) == key }
                if (idx >= 0) {
                    val vis = K_DEFAULT_VISIBLE.coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE).coerceAtMost(line.size)
                    // 把最新标记放在窗口偏右位置（留 3 根余量），保证可见
                    rightIndex = (idx + 3).coerceIn((vis - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                }
            }
        }
    }
    // 取当前窗口数据
    val n = visible.coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE).coerceAtMost(line.size)
    val end = rightIndex.coerceIn((n - 1).coerceAtMost(line.lastIndex), line.lastIndex)
    val start = (end - n + 1).coerceAtLeast(0)
    val show = line.subList(start, end + 1)

    // BST 标记：窗口内可见日期集合，仅把落在当前可视窗口的标记传给画布（避免无用绘制）
    // 注意：必须用 normDateKey 归一化后再比较——K线日期来自同花顺（"20260924"），
    // marks 日期来自后端（"2026-09-24"），原始字符串直接比较会全量失配、徽标恒为空。
    val showDateSet = remember(show) { show.mapTo(HashSet()) { normDateKey(it.date) } }
    val showMarks = remember(d?.marks, showDateSet) {
        (d?.marks ?: emptyList()).filter { normDateKey(it.date) in showDateSet }
    }

    // ===== 筹码峰：数据固定按最近120根推演一次（方案A：绘制时随价格轴缩放）=====
    val chip = remember(d?.code, line.size) { computeChipDist(line, d?.base) }
    val chipW = if (showChip && chip != null) 72.dp else 0.dp

    // ===== MACD：必须用「全量」line 计算（EMA 递归依赖历史），再按可见窗口切片 =====
    val macdFull = remember(d?.code, line.size) { computeMACD(line) }
    // 当前副图指标（点副图空白循环切换 / 点标签框弹层选择）——两幅独立
    var currentSub by remember(d?.code) { mutableStateOf(SubIndicator.VOL) }
    var subPickerOpen by remember { mutableStateOf(false) }
    var currentSub2 by remember(d?.code) { mutableStateOf(SubIndicator.MACD) }
    var subPickerOpen2 by remember { mutableStateOf(false) }
    val macdShow = remember(macdFull, start, end) {
        MacdResult(
            macdFull.dif.copyOfRange(start, end + 1),
            macdFull.dea.copyOfRange(start, end + 1),
            macdFull.macd.copyOfRange(start, end + 1)
        )
    }

    // 副图指标选择弹层（点哪个换哪个）
    if (subPickerOpen) {
        SubIndicatorPicker(
            current = currentSub,
            onPick = { currentSub = it; subPickerOpen = false },
            onDismiss = { subPickerOpen = false }
        )
    }
    if (subPickerOpen2) {
        SubIndicatorPicker(
            current = currentSub2,
            onPick = { currentSub2 = it; subPickerOpen2 = false },
            onDismiss = { subPickerOpen2 = false }
        )
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .padding(start = 2.dp, end = 2.dp)
        ) {
            KLineCanvas(
                line = show,
                marks = showMarks,
                canBs = canBs,
                maList = maPeriods.map { p -> computeMA(line, p).copyOfRange(start, end + 1) }.toTypedArray(),
                maPeriods = maPeriods,
                selLocal = null, // fix3: 去掉点击竖线，只保留滑动光标线
                cursorLocal = if (kdayCursorLocal in show.indices) kdayCursorLocal else -1,
                onCursorDrag = { local ->
                    kdayCursorLocal = local
                    // fix3: 拖动光标同步选中，让头部价格与 T/B/S 明细实时跟随
                    if (local in show.indices) {
                        val g = start + local
                        if (selIdx != g) onSelect(g)
                    }
                },
                onPick = { local ->
                    // 点同一根 → 取消选中；否则选中该根（映射回全局下标）
                    val g = start + local
                    onSelect(if (selIdx == g) null else g)
                },
                onZoom = { factor ->
                    // factor > 1 放大（根数变少），< 1 缩小（根数变多）
                    val nv = (visible / factor).toInt()
                        .coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE)
                        .coerceAtMost(line.size.coerceAtLeast(K_MIN_VISIBLE))
                    if (nv != visible) {
                        visible = nv
                        // 保持右端锚定（下界不得超过上界，防空 range 崩溃）
                        val lo = (visible - 1).coerceAtMost(line.lastIndex)
                        rightIndex = rightIndex.coerceIn(lo, line.lastIndex)
                    }
                },
                onPan = { moved ->
                    // moved > 0 表示向右拖（看更早的历史 → 窗口左移）
                    val lo = (visible - 1).coerceAtMost(line.lastIndex)
                    val ni = (rightIndex - moved).coerceIn(lo, line.lastIndex)
                    rightIndex = ni
                },
                // 底部留出时间轴条带高度（绘图不越界到分割线下边）；右侧让出筹码峰宽度
                modifier = Modifier.fillMaxSize().padding(bottom = TA_H, end = chipW)
            )
            PriceAxis(
                maxV = show.maxOf { it.high },
                minV = show.minOf { it.low },
                midV = (show.maxOf { it.high } + show.minOf { it.low }) / 2,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(top = 2.dp, end = chipW, bottom = TA_H)
            )
            // ===== 右侧纵向筹码峰（方案A：与K线同高、同价格轴 → 联动缩放）=====
            if (showChip && chip != null) {
                ChipCanvas(
                    chip = chip,
                    topV = show.maxOf { it.high } * 1.05,
                    botV = show.minOf { it.low } * 0.95,
                    curPrice = d?.base?.price ?: show.last().close,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(72.dp)
                        .fillMaxHeight()
                        .padding(bottom = TA_H)
                )
            }
            // ===== 底部时间轴条带：宽度随K线区收缩（右侧让出 chipW）=====
            KLineTimeAxis(
                show = show,
                globalStart = start,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = chipW, start = 2.dp)
            )
            // ===== 缩放 / 平移圆形按钮：浮在主图（分时/K线主图）底部 =====
            // 6 个按钮：仅展示(尖朝左) / ＋ / − / 左移(尖朝左) / 右移(尖朝右) / 仅展示(双尖角 45°)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 抬到「时间轴条带」正上方：预留 TA_H + 间距，避免压住刻度线
                    .padding(start = 2.dp, end = chipW, bottom = TA_H + 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1) 最左：手绘尖角，尖朝左 —— 仅展示
                KLineButton(
                    text = "",
                    onClick = {},
                    content = { ChevronIcon(tipLeft = true, boxSize = 30.dp, twin = true, scaleMult = 0.78f) }
                )
                // 2) ＋ 放大（手绘十字，与折线同一套画笔，尺寸统一用 scaleMult 调）
                KLineButton(
                    text = "",
                    onClick = {
                        visible = (visible / K_BTN_ZOOM_STEP).toInt()
                            .coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE)
                            .coerceAtMost(line.size.coerceAtLeast(K_MIN_VISIBLE))
                        rightIndex = rightIndex.coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                    },
                    content = { PlusMinusIcon(minus = false, boxSize = 30.dp, scaleMult = 0.8f) }
                )
                // 3) − 缩小（手绘横线）
                KLineButton(
                    text = "",
                    onClick = {
                        visible = (visible * 1f * K_BTN_ZOOM_STEP).toInt()
                            .coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE)
                            .coerceAtMost(line.size.coerceAtLeast(K_MIN_VISIBLE))
                        rightIndex = rightIndex.coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                    },
                    content = { PlusMinusIcon(minus = true, boxSize = 30.dp, scaleMult = 0.8f) }
                )
                // 4) 左移一格：尖朝左
                KLineButton(
                    text = "",
                    onClick = {
                        val nr = (rightIndex - 1).coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                        rightIndex = nr
                        // 若已选中，跟随移动选中框
                        selIdx?.let { onSelect(it.coerceIn(startOf(nr, visible, line.size), nr)) }
                    },
                    content = { ChevronIcon(tipLeft = true, boxSize = 30.dp, scaleMult = 0.8f) }
                )
                // 5) 右移一格：尖朝右
                KLineButton(
                    text = "",
                    onClick = {
                        val nr = (rightIndex + 1).coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                        rightIndex = nr
                        selIdx?.let { onSelect(it.coerceIn(startOf(nr, visible, line.size), nr)) }
                    },
                    content = { ChevronIcon(tipLeft = false, boxSize = 30.dp, scaleMult = 0.8f) }
                )
                // 6) 最右：全屏 —— 双尖角背靠背 + 中间留缝 + 整体顺时针 45°，仅展示
                KLineButton(
                    text = "",
                    onClick = {},
                    content = { ChevronIcon(tipLeft = false, boxSize = 30.dp, double = true, rotate45 = true, scaleMult = 0.55f, gapMult = 3.4f) }
                )
            }
        }
        // ===== 副图1（默认成交量；点标签框弹层选 / 点空白循环切下一个）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .padding(start = 2.dp, end = 2.dp, top = 3.dp)
                .clipToBounds()
                .pointerInput(currentSub, show.size, selIdx) {
                    detectTapGestures { currentSub = currentSub.next() }
                }
        ) {
            val subLocal = selIdx?.let { it - start }?.takeIf { it in show.indices }
            when (currentSub) {
                SubIndicator.VOL -> KLineVolumeCanvas(line = show, selLocal = subLocal)
                SubIndicator.MACD -> KLineMacdCanvas(
                    dif = macdShow.dif, dea = macdShow.dea, macd = macdShow.macd, selLocal = subLocal
                )
                else -> SubGenericCanvas(idx = currentSub, line = show, globalStart = start, selLocal = subLocal)
            }
            // ===== 首行：指标名标签框 + 紧跟其后的数值（不重叠）=====
            SubLabelRow(
                main = if (currentSub == SubIndicator.MACD) currentSub.tag else currentSub.label,
                showMacd = currentSub == SubIndicator.MACD,
                labIdx = subLocal,
                macdShow = macdShow,
                volLine = if (currentSub == SubIndicator.VOL) show else null,
                onTag = { subPickerOpen = true },
                subIdx = currentSub,
                subShow = show
            )
        }
        // ===== 副图2（默认 MACD；独立可切）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .padding(start = 2.dp, end = 2.dp, top = 3.dp)
                .clipToBounds()
                .pointerInput(currentSub2, show.size, selIdx) {
                    detectTapGestures { currentSub2 = currentSub2.next() }
                }
        ) {
            val subLocal2 = selIdx?.let { it - start }?.takeIf { it in show.indices }
            when (currentSub2) {
                SubIndicator.VOL -> KLineVolumeCanvas(line = show, selLocal = subLocal2)
                SubIndicator.MACD -> KLineMacdCanvas(
                    dif = macdShow.dif, dea = macdShow.dea, macd = macdShow.macd, selLocal = subLocal2
                )
                else -> SubGenericCanvas(idx = currentSub2, line = show, globalStart = start, selLocal = subLocal2)
            }
            SubLabelRow(
                main = if (currentSub2 == SubIndicator.MACD) currentSub2.tag else currentSub2.label,
                showMacd = currentSub2 == SubIndicator.MACD,
                labIdx = subLocal2,
                macdShow = macdShow,
                volLine = if (currentSub2 == SubIndicator.VOL) show else null,
                onTag = { subPickerOpen2 = true },
                subIdx = currentSub2,
                subShow = show
            )
        }
    }
}

/**
 * K线缩放/平移圆形小按钮（同花顺风格）。
 * 外观：圆形、灰色半透明底、居中符号。
 * 短按 = 执行 1 档；长按 = 每 70ms 连续执行。
 */
@Composable
private fun KLineButton(
    text: String,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // 用 rememberUpdatedState 保证 pointerInput 内始终调用最新 lambda（避免闭包捕获旧状态）
    val curOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(30.dp)
            // 圆形浅灰半透明底（不压过行情线）
            .background(KBTN_BG, shape = CircleShape)
            .pointerInput(text, content != null) {
                detectTapGestures(
                    onPress = {
                        // 先立即执行一次
                        curOnClick()
                        val job = scope.launch {
                            delay(320) // 长按判定延迟
                            while (isActive) {
                                curOnClick()
                                delay(70)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else {
            Text(text, color = KBTN_FG, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 手绘加减图标：与 ChevronIcon 共用同一套画笔 / 线宽 / 尺寸倍率。
 *  - minus = true  → 只画一条横线「−」
 *  - minus = false → 画一横 + 一竖交叉成「＋」
 *
 * @param boxSize   图标外框尺寸（应与按钮圆底一致）
 * @param scaleMult 尺寸倍率（1f = 基准占 32%，与折角线同一基准）
 * @param strokeW   线条粗细
 */
@Composable
private fun PlusMinusIcon(
    minus: Boolean,
    boxSize: androidx.compose.ui.unit.Dp,
    scaleMult: Float = 1f,
    strokeW: androidx.compose.ui.unit.Dp = 1.6.dp
) {
    val color = KBTN_FG
    Canvas(modifier = Modifier.size(boxSize)) {
        val w = size.width
        val h = size.height
        val sw = strokeW.toPx()
        // 与 ChevronIcon 同基准：0.32 * scaleMult（保证加减号与折线看起来一样大）
        val scale = 0.32f * scaleMult
        val armX = w * scale * 0.5f
        val armY = h * scale * 0.5f
        val cx = w / 2f
        val cy = h / 2f
        // 横线
        val hPath = Path().apply {
            moveTo(cx - armX, cy)
            lineTo(cx + armX, cy)
        }
        drawPath(hPath, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        if (!minus) {
            // 竖线（＋）
            val vPath = Path().apply {
                moveTo(cx, cy - armY)
                lineTo(cx, cy + armY)
            }
            drawPath(vPath, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

/**
 * 手绘尖角图标（同花顺风格）。
 *
 * 用「一竖 + 一横」两条线画出 90° 直角折线，拐点即「尖角」：
 *  - tipLeft  = true  → 拐点朝左（竖臂在左，横臂从顶端向右伸出）
 *  - tipLeft  = false → 拐点朝右（竖臂在右，横臂从顶端向左伸出）
 *  - double   = true  → 双尖角背靠背（中间留缝）：❮❯
 *  - rotate45 = true  → 整体顺时针旋转 45°（配合 double 得到「斜着的双箭头」）
 *
 * @param size     图标外框尺寸（应与按钮圆底一致，内部按比例留边距）
 * @param strokeW  线条粗细
 */
@Composable
private fun ChevronIcon(
    tipLeft: Boolean,
    boxSize: androidx.compose.ui.unit.Dp,
    double: Boolean = false,
    rotate45: Boolean = false,
    strokeW: androidx.compose.ui.unit.Dp = 1.6.dp,
    // 折线整体大小倍率（1f=基准 0.32），调小则折角线更短
    scaleMult: Float = 1f,
    // 双折线中间缝隙倍率（1f=基准 0.06w），调大则两条分得更开
    gapMult: Float = 1f,
    // 是否画两条并排同向折线（最左按钮用）
    twin: Boolean = false
) {
    // 符号色 = KBTN_FG
    val color = KBTN_FG
    Canvas(modifier = Modifier.size(boxSize)) {
        val w = size.width
        val h = size.height
        val sw = strokeW.toPx()
        // 几何：一竖 + 一横两条线，拐角处严格垂直（90°）；armW=armH 保证两臂等长
        // scale 控制整体大小（占圆底比例），越小越精致
        val scale = 0.32f * scaleMult
        val armW = w * scale * 0.5f   // 水平半臂长（tip 到 back 的水平距离）
        val armH = h * scale * 0.5f   // 垂直半臂长（= armW → 保证 90°）
        val gap  = w * 0.06f * gapMult // 双尖角：中间缝隙（gapMult 调大可分开两条线）

        // 画一个 90° 直角折线：基准 = 拐点在角落的正直角（一竖一横），整体绕 (cx,cy) 旋转。
        // 朝左(tipLeftLocal=true)：绕 (cx,cy) 逆时针 -45°；朝右(tipLeftLocal=false)：顺时针 +45°。
        fun chevron(cx: Float, cy: Float, tipLeftLocal: Boolean) {
            // 严格按确认图算法：基准是「一横一竖的正 90° 直角」，拐点在角落，
            // 三个顶点为 竖臂两端 + 横臂外端；再整体绕 (cx,cy) 旋转。
            val ax = if (tipLeftLocal) cx - armW else cx + armW  // 竖臂的 x（拐点在那一侧）
            val bx = if (tipLeftLocal) cx + armW else cx - armW  // 横臂的外端 x
            val topY = cy - armH
            val botY = cy + armH
            val path = Path().apply {
                moveTo(ax, botY)   // 竖臂下端
                lineTo(ax, topY)   // 拐点(角)
                lineTo(bx, topY)   // 横臂外端
            }
            val deg = if (tipLeftLocal) -45f else 45f
            rotate(degrees = deg, pivot = Offset(cx, cy)) {
                drawPath(path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }

        val cx = w / 2f
        val cy = h / 2f
        if (twin) {
            // 两条并排、同朝向的折线（最左按钮）：都朝左 -45°
            val tx = w * 0.16f
            chevron(cx = cx - tx, cy = cy, tipLeftLocal = true)
            chevron(cx = cx + tx, cy = cy, tipLeftLocal = true)
        } else if (double) {
            // 双折线背离：左折线朝左(-45) + 右折线朝右(+45)，整体再绕中心顺时针 +45°。
            val leftCx = cx - gap
            val rightCx = cx + gap
            val both: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                chevron(cx = leftCx, cy = cy, tipLeftLocal = true)
                chevron(cx = rightCx, cy = cy, tipLeftLocal = false)
            }
            if (rotate45) rotate(45f, Offset(cx, cy)) { both() } else both()
        } else {
            chevron(cx = cx, cy = cy, tipLeftLocal = tipLeft)
        }
    }
}

/**
 * 均线设置弹窗：用户填写均线参数（逗号分隔），如 5,10,20,60。
 * 每个参数对应一条均线，颜色按 MA_COLORS 顺序自动分配。
 */
@Composable
private fun MaSettingDialog(
    initial: List<Int>,
    onDismiss: () -> Unit,
    auction: Boolean = false,
    onAuctionChange: (Boolean) -> Unit = {},
    symmetric: Boolean = true,
    onSymmetricChange: (Boolean) -> Unit = {},
    showOrderAmount: Boolean = false,
    onShowOrderAmountChange: (Boolean) -> Unit = {},
    onConfirm: (List<Int>) -> Unit
) {
    // 每行一个输入框：默认 4 个（沿用初始参数），可点“＋增加一条”追加
    val rows = remember {
        mutableStateListOf<String>().apply {
            val base = initial.ifEmpty { MA_DEFAULT_PERIODS }
            base.forEach { add(it.toString()) }
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("均线设置", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                rows.indices.forEach { i ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = rows[i],
                            onValueChange = { v ->
                                // 只允许数字
                                rows[i] = v.filter { it.isDigit() }.take(4)
                            },
                            singleLine = true,
                            placeholder = { androidx.compose.material3.Text("如 5", fontSize = 13.sp) },
                            modifier = Modifier.width(96.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("日线", fontSize = 14.sp, color = TEXT_MAIN)
                        Spacer(Modifier.weight(1f))
                        if (rows.size > 1) {
                            androidx.compose.material3.Text(
                                "删除", fontSize = 13.sp, color = GRAY_FLAT,
                                modifier = Modifier.clickable { rows.removeAt(i) }.padding(6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (rows.size < 8) rows.add("") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text("＋ 增加一条", fontSize = 14.sp, color = Color(Config.COLOR_PRIMARY))
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(color = LINE_GRID)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        androidx.compose.material3.Text("开启集合竞价", fontSize = 14.sp, color = TEXT_MAIN)
                        androidx.compose.material3.Text("显示 9:15-9:25 与 14:55-15:00 竞价栏", fontSize = 10.sp, color = TEXT_SUB)
                    }
                    androidx.compose.material3.Switch(
                        checked = auction,
                        onCheckedChange = { onAuctionChange(it) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text("分时百分比对称坐标", fontSize = 14.sp, color = TEXT_MAIN)
                        androidx.compose.material3.Text("昨收0%线居中(同花顺默认)；关闭则按真实高低填满满占坐标", fontSize = 10.sp, color = TEXT_SUB)
                    }
                    androidx.compose.material3.Switch(
                        checked = symmetric,
                        onCheckedChange = { onSymmetricChange(it) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text("买卖盘委托单显示金额", fontSize = 14.sp, color = TEXT_MAIN)
                        androidx.compose.material3.Text("开启后盘口最后一栏改显示委托金额(万/亿)，关闭显示手数", fontSize = 10.sp, color = TEXT_SUB)
                    }
                    androidx.compose.material3.Switch(
                        checked = showOrderAmount,
                        onCheckedChange = { onShowOrderAmountChange(it) }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val list = rows.mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                    .distinct()
                    .take(8)
                onConfirm(if (list.isEmpty()) MA_DEFAULT_PERIODS else list)
            }) { androidx.compose.material3.Text("确定") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("取消")
            }
        }
    )
}
@Composable
private fun KLineCanvas(
    line: List<KLinePoint>,
    marks: List<TradeMark> = emptyList(),
    // BS/T 买卖点标记权限（sd_bs_marks）：无权则不画徽标
    canBs: Boolean = true,
    maList: Array<DoubleArray> = emptyArray(),
    maPeriods: List<Int> = MA_DEFAULT_PERIODS,
    selLocal: Int? = null,
    cursorLocal: Int = -1,
    onPick: (Int) -> Unit = {},
    onCursorDrag: (Int) -> Unit = {},
    onZoom: (Float) -> Unit = {},
    onPan: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // date -> BST 标记（每交易日至多一条）；按日期对齐到具体柱子。
    // 注意：key 必须用 normDateKey 归一化——marks 日期来自后端（"2026-09-24"）。
    val markByDate = remember(marks) { marks.associateBy { normDateKey(it.date) } }
    val markLabel = remember(marks) { tradeMarkPaint() }
    var lastSlot by remember { mutableFloatStateOf(0f) }
    val curPick by rememberUpdatedState(onPick)
    val curCursor by rememberUpdatedState(onCursorDrag)
    val curZoom by rememberUpdatedState(onZoom)
    val curPan by rememberUpdatedState(onPan)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, _ ->
                        if (lastSlot > 0f) {
                            // 手指向右拖(pan.x>0) → K线右移 → 看到更早历史 → rightIndex 变小
                            val moved = (pan.x / lastSlot).toInt()
                            if (moved != 0) curPan(moved)
                        }
                        if (zoom != 1f) curZoom(zoom)
                    }
                )
            }
            .pointerInput(line.size) {
                // 日K光标：长按按住 0.5s 激活十字光标，随后左右拖动连续移动；
                // 轻点（未达长按）仍然保持原有“选中该根 K 线”的行为。
                // 关键点：不使用 awaitLongPressOrCancellation —— 它在手指位移超过 touch slop 时
                // 会直接判为“取消/轻点”，导致长按后根本无法拖动。这里手写计时：
                //   500ms 内若没松手（无论是否移动）→ 判定长按成立并进入拖动跟随；
                //   500ms 内松手 → 判定轻点选柱。
                // 注意：key 只放 line.size，绝不能放光标状态，否则长按激活时会重启协程、拖动循环被杀。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastX = down.position.x
                    var upBeforeLong = false
                    withTimeoutOrNull(500L) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                upBeforeLong = true
                                break
                            }
                            lastX = ch.position.x
                        }
                    }
                    if (upBeforeLong) {
                        // 轻点：选柱
                        if (lastSlot > 0f && line.isNotEmpty()) {
                            val i = (down.position.x / lastSlot).toInt().coerceIn(0, line.size - 1)
                            curPick(i)
                        }
                    } else {
                        // 长按成立：激活光标并进入拖动跟随；期间 consume 事件，阻止外层 pan/zoom 抢占。
                        if (lastSlot > 0f && line.isNotEmpty()) {
                            val i0 = (lastX / lastSlot).toInt().coerceIn(0, line.size - 1)
                            curCursor(i0)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    ch.consume()
                                    break
                                }
                                if (ch.positionChanged()) {
                                    val i = (ch.position.x / lastSlot).toInt().coerceIn(0, line.size - 1)
                                    curCursor(i)
                                }
                                ch.consume()
                            }
                        } else {
                            // 不足一屏柱宽时也要把手势消费掉，避免误触外层平移
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        if (line.isEmpty()) return@Canvas
        // 将主图所有绘制裁剪在 K 线显示区内，任何越界内容（如均线）都不会跑出屏幕
        clipRect(0f, 0f, w, h) {
        val maxP = line.maxOf { it.high }
        val minP = line.minOf { it.low }
        val span = (maxP - minP).coerceAtLeast(1e-6)
        val pad = span * 0.05
        val top = maxP + pad
        val bot = minP - pad
        val sp = top - bot

        fun yOf(p: Double): Float = (h - ((p - bot) / sp) * h).toFloat()

        // 网格线
        for (k in 1..3) {
            val y = h * k / 4f
            drawLine(LINE_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        // K线显示区 完整边框（上/下/左/右）
        val bd = LINE_AXIS
        drawLine(bd, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
        drawLine(bd, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        drawLine(bd, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
        drawLine(bd, Offset(w, 0f), Offset(w, h), strokeWidth = 1f)

        val n = line.size
        val slot = w / n
        lastSlot = slot
        // 缩到很小时保证柱宽至少 1px，视觉上连成一条线（同花顺观感）
        val bw = max(slot * 0.92f, 1f)
        // 缩放到最小（一屏根数达到上限）时：不画均线、不逐根画蜡烛影线，
        // 改为只连收盘价画一条平滑黑色波浪线（方案A，同花顺观感）。
        val tooNarrow = n >= K_NARROW_VISIBLE
        if (tooNarrow) {
            val pathClose = Path()
            var startedClose = false
            line.forEachIndexed { i, k ->
                val cx = i * slot + slot / 2
                val y = yOf(k.close)
                if (!startedClose) { pathClose.moveTo(cx, y); startedClose = true } else pathClose.lineTo(cx, y)
            }
            if (startedClose) drawPath(pathClose, K_NARROW_COLOR, style = Stroke(width = max(slot * 0.9f, 3.8f)))
        } else {
            line.forEachIndexed { i, k ->
                val cx = i * slot + slot / 2
                val up = k.close >= k.open
                val c = if (up) RED_UP else GREEN_DOWN
                val yHigh = yOf(k.high)
                val yLow = yOf(k.low)
                val yO = yOf(k.open)
                val yC = yOf(k.close)
                val yTop = min(yO, yC)
                val yBot = max(yO, yC)
                val bh = max(abs(yC - yO), 1.2f)
                val sw = 3.2f
                val barSw = 4.6f
                if (yHigh < yTop) drawLine(c, Offset(cx, yHigh), Offset(cx, yTop), strokeWidth = sw)
                if (yLow > yBot) drawLine(c, Offset(cx, yBot), Offset(cx, yLow), strokeWidth = sw)
                drawRect(
                    color = c,
                    topLeft = Offset(cx - bw / 2, yTop),
                    size = Size(bw, bh),
                    style = if (up) Stroke(width = barSw) else Fill
                )
            }
        }

        // 均线（MA）：缩到最小时不画，避免叠在黑色波浪上显得杂乱。
        if (!tooNarrow) maList.forEachIndexed { mi, ma ->
            if (ma.isEmpty()) return@forEachIndexed
            val color = maColorOf(maPeriods.getOrElse(mi) { MA_DEFAULT_PERIODS.getOrElse(mi) { 5 } })
            val pathMA = Path()
            var started = false
            ma.forEachIndexed { i, v ->
                if (v.isNaN()) return@forEachIndexed
                val x = i * slot + slot / 2
                // 固定压缩到显示区域内：均线值超出可视价格区间时，夹到上下边界，避免跑出分时图
                val y = yOf(v).coerceIn(0f, h)
                if (!started) { pathMA.moveTo(x, y); started = true } else pathMA.lineTo(x, y)
            }
            if (started) drawPath(pathMA, color, style = Stroke(width = 2.2f))
        }

        // ===== BST 买卖标记徽标：每根有交易的柱子画上/下偏移的小徽标 =====
        // 规则（后端 _marks.php 已定稿）：每交易日至多一条；above=true 画柱上方、false 画下方；
        // color: redB/greyB/blueS/greyS/T → 前端映射；字母取 kind（B/S/T）。
        if (!tooNarrow && markByDate.isNotEmpty() && canBs) {
            // 标记方框：边长固定 = 2.5 根牙签（TOOTHPICK=6dp）→ 15dp；不随 K 线缩放变化（Q1：允许与相邻柱重叠）。
            // 缩到最小（n>=K_NARROW_VISIBLE=1500，即"变成一条线"）时整段隐藏（见上方 tooNarrow 门控）
            // （Q2：沿用之前设定的"缩成一条线"阈值）。
            val side = 2.5f * TOOTHPICK.toPx()     // 方框边长固定 = 2.5 根牙签（固定）
            val half = side / 2f
            val corner = side * 0.28f              // 圆角："半个圆点"观感
            val dotR = side * 0.11f                // 虚线两端小圆点半径
            // 断线由每段手绘短划实现，无需 dashEffect
            val wireW = 1.4.dp.toPx()              // 虚线宽
            line.forEachIndexed { i, k ->
                // k.date 为 K 线源日期（同花顺 "20260924"），必须归一化后再查
                val m = markByDate[normDateKey(k.date)] ?: return@forEachIndexed
                val cx = i * slot + slot / 2
                val c = tradeMarkColor(m.color)
                // 影线尖端（连接终点）与方框中心
                val yHigh = yOf(k.high)            // 上影线尖端
                val yLow = yOf(k.low)              // 下影线尖端
                // 框与影线尖端之间留出可见间隙，供 3 段虚线连接（间隙过小则虚线无从画起）
                val gap = 9.dp.toPx()
                // 首层保底（对齐同花顺）：
                // 标记默认按后端给定的朝向（above=true 画柱上方 / false 画柱下方）放置；
                // 但若该朝向的空间不足（上标记顶出画布 / 下标记底部超出画布），
                // 则自动"翻面"到另一侧，使标记尽量留在可见区内。
                // 例：标记本在下方，把 K 线拖到最右、下方不够时就搬到上面去。
                // 同花顺式：按当前 K 线的高低价区间给定上下。
                // T 类（成交日）用买卖两笔均价；其余用 avgPrice，缺失回退 close。
                // high/low 取自 K 线；无法成出区间时，默认放下方。
                val refPx = if (m.avgPrice > 0.0) m.avgPrice else k.close
                val spanP = k.high - k.low
                val frac = if (spanP > 1e-9) ((refPx - k.low) / spanP) else 1.0
                var above = frac >= 0.5
                var boxCyRaw = if (above) (yHigh - half - gap) else (yLow + half + gap)
                val fits = if (above) (boxCyRaw - half >= 0f) else (boxCyRaw + half <= h)
                if (!fits) {
                    above = !above
                    boxCyRaw = if (above) (yHigh - half - gap) else (yLow + half + gap)
                }
                // 第二层保底（方案A）：翻面后仍越界（例如上下都极窄）时不丢弃，
                // 而是把框夹取回画布内边缘，保证极端高/低柱的标记也始终可见（贴边显示）。
                val loB = half + 1f
                val hiB = (h - half - 1f).coerceAtLeast(loB)   // 极端窄画布保护
                val boxCy = boxCyRaw.coerceIn(loB, hiB)
                // 方框朝向影线的那一边（上标记→框底；下标记→框顶）
                val boxEdgeY = if (above) (boxCy + half) else (boxCy - half)
                val wireEndY = if (above) yHigh else yLow   // 影线端
                // ===== 连接虚线：影线端↔方框边，三等分画 3 段竖直虚线（Q3：方案A） =====
                val y0 = min(wireEndY, boxEdgeY)
                val y1 = max(wireEndY, boxEdgeY)
                val span = y1 - y0
                if (span > 4f) {
                    val seg = span / 3f
                    // 每段内画 2 个小短划（不依赖 dashEffect 周期，保证短距离下仍可见）
                    for (s in 0 until 3) {
                        val segTop = y0 + s * seg
                        val dashLen = seg * 0.28f
                        drawLine(
                            color = c,
                            start = Offset(cx, segTop + seg * 0.16f),
                            end = Offset(cx, segTop + seg * 0.16f + dashLen),
                            strokeWidth = wireW
                        )
                        drawLine(
                            color = c,
                            start = Offset(cx, segTop + seg * 0.58f),
                            end = Offset(cx, segTop + seg * 0.58f + dashLen),
                            strokeWidth = wireW
                        )
                    }
                }
                // ===== 两端小圆点（颜色同方框） =====
                drawCircle(color = c, radius = dotR, center = Offset(cx, wireEndY))  // 连影线一端
                drawCircle(color = c, radius = dotR, center = Offset(cx, boxEdgeY))  // 连方框一端
                // ===== 标记方框（圆角方，白色描边打底 + 字母） =====
                val boxTopLeft = Offset(cx - half, boxCy - half)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(boxTopLeft.x - 1.5f, boxTopLeft.y - 1.5f),
                    size = Size(side + 3f, side + 3f),
                    cornerRadius = CornerRadius(corner + 1.5f, corner + 1.5f)
                )
                drawRoundRect(
                    color = c,
                    topLeft = boxTopLeft,
                    size = Size(side, side),
                    cornerRadius = CornerRadius(corner, corner)
                )
                val label = when (m.kind) { "B" -> "B"; "S" -> "S"; "T" -> "T"; else -> "" }
                if (label.isNotEmpty()) {
                    markLabel.textSize = side * 0.62f
                    markLabel.isFakeBoldText = true
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        cx,
                        boxCy + markLabel.textSize * 0.36f,
                        markLabel
                    )
                }
            }
        }

        // ===== 选中竖线（深灰、贯穿） =====
        val sel = selLocal
        if (sel != null && sel in line.indices) {
            val cx = sel * slot + slot / 2
            drawLine(
                color = K_CROSS_COLOR,
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 1.5f
            )
        }
        // ===== 长按拖动十字光标（贯穿竖线 + 顶部短横线）=====
        if (cursorLocal >= 0 && cursorLocal in line.indices) {
            val ccx = cursorLocal * slot + slot / 2
            drawLine(
                color = K_CROSS_COLOR,
                start = Offset(ccx, 0f),
                end = Offset(ccx, h),
                strokeWidth = 1.2f
            )
            drawLine(
                color = K_CROSS_COLOR,
                start = Offset(0f, 0f),
                end = Offset(w, 0f),
                strokeWidth = 1.2f
            )
        }
        } // end clipRect
    }
}

/**
 * 底部时间轴「尺子」：
 *  - 左侧固定区：显示当前窗口最左柱子的完整年月日（如 2025-07-30），不随滑动位移；
 *  - 右侧滑动区：刻度绑定在具体柱子上（按全局索引对齐），滑动时随柱子一起移动；
 *    每个小刻度对应 1 根柱子；每 LABEL_EVERY 根柱子一个「带日期」的长刻度，显示 MM-DD；
 *    无日期的刻度短、有日期的刻度长，刻度统一加粗；
 *  - 上方一条横线作为尺子框架；滑动内容进入左侧固定区即被遮住并渐隐。
 */
@Composable
private fun KLineTimeAxis(
    show: List<KLinePoint>,
    globalStart: Int,
    modifier: Modifier = Modifier
) {
    val n = show.size
    if (n <= 0) return
    // 左侧固定区宽度（与底部按钮行左侧预留对齐）
    val fixedW = 78.dp

    // ---- 日期归一化：复用全局 normDateKey（兼容 "2019-11-26" / "20191126" / "2019/11/26"）----
    // 统一成带横线的 "YYYY-MM-DD"；解析失败则原样返回（避免越界崩溃）。
    fun normDate(raw: String): String = normDateKey(raw)
    // 取 MM-DD（从归一化结果截取尾部 5 位）
    fun mdOf(raw: String): String {
        val nd = normDate(raw)
        return if (nd.length >= 5) nd.substring(nd.length - 5) else nd
    }
    // 刻度/日期间隔在下面按「屏幕可视宽度」动态计算：
    // 目标让屏幕（除左侧固定区外）恒定约 4 个日期标签；缩小时自动加大间隔、放大时自动缩小，
    // 并做兜底保证任何缩放级别都至少命中 1 个标签（不会消失）。

    Box(modifier = modifier.height(TA_H).background(TA_BG)) {
        // 上、下分割线：把时间轴框成一条独立条带
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TIME_AXIS_LINE)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TIME_AXIS_LINE)
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalW = maxWidth
            val slot = totalW / n  // 每根柱子的宽度（与主图同口径 w / show.size）

            // ===== 动态计算「日期标签间隔」与「刻度间隔」=====
            // 右侧滑动区可用宽度（左侧固定区不参与排布）
            val availW = (totalW - fixedW).coerceAtLeast(1.dp)
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            val slotPx = slot.value * density               // 每根柱子像素宽
            val availPx = availW.value * density            // 可用像素宽

            // 目标：屏幕右侧约 4 个标签 → 目标像素间距 = availPx / 4
            // 换算成「柱数」，向上取整，再夹到 [1, n]（保证一定能命中）
            val targetPx = (availPx / 4f).coerceAtLeast(1f)
            var labelEvery = kotlin.math.ceil((targetPx / slotPx).toDouble()).toInt()
            labelEvery = labelEvery.coerceIn(1, if (n < 1) 1 else n)

            // 刻度线间隔：跟随标签间隔，但保证刻度像素间距 >= 6dp，避免糊
            val tickMinPx = 6.dp.value * density
            var tickEvery = kotlin.math.ceil((tickMinPx / slotPx).toDouble()).toInt()
            tickEvery = tickEvery.coerceIn(1, if (n < 1) 1 else n)

            // 遍历「全局索引」：刻度线每 tickEvery 根一条；日期标签每 labelEvery 根一次。
            // 两者都绑定全局索引 → 滑动时随柱子移动（右滑新标签从右进、旧标签从右出）。
            val gStart = globalStart
            val gEnd = globalStart + n - 1

            // ---- 先画小刻度线（背景层） ----
            var gt = ((gStart + tickEvery - 1) / tickEvery) * tickEvery
            while (gt <= gEnd) {
                val local = gt - gStart
                if (local in 0 until n) {
                    val cx = slot * local + slot / 2
                    Box(
                        modifier = Modifier
                            .offset(x = cx - 1.dp, y = 1.dp)
                            .width(1.dp)
                            .height(6.dp)
                            .background(TIME_AXIS_LINE)
                    )
                }
                gt += tickEvery
            }

            // ---- 再画带日期的长刻度 + 文字 ----
            // 首个 >= gStart 且为 labelEvery 倍数的全局索引
            var firstLabelG = ((gStart + labelEvery - 1) / labelEvery) * labelEvery
            // ★ 兜底：若区间内没有任何 labelEvery 倍数命中，就用区间起点强制出一个标签，绝不消失
            if (firstLabelG > gEnd) firstLabelG = gStart

            var gl = firstLabelG
            // 用 while 但以 labelEvery 步进；若走了兜底（firstLabelG=gStart），步进依然按 labelEvery
            while (gl <= gEnd) {
                val local = gl - gStart
                if (local in 0 until n) {
                    val cx = slot * local + slot / 2
                    Box(
                        modifier = Modifier
                            .offset(x = cx - 24.dp, y = 1.dp)
                            .width(48.dp)
                            .height(TA_H - 2.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 长刻度竖线：从上分割线往下伸
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(7.dp)
                                    .background(TIME_AXIS_LINE)
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = mdOf(show[local].date),  // MM-DD（兼容多种原始格式）
                                color = TEXT_SUB,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                gl += labelEvery
            }
        }

        // ===== 左侧固定区：不透明浅灰遮罩（盖竖线+文字，上下各留1dp让分割线可见） =====
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 1.dp, bottom = 1.dp)
                .fillMaxHeight()
                .width(fixedW)
                .background(TA_BG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(4.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 固定区长刻度：从上分割线往下（与滑区视觉一致）
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(7.dp)
                        .background(TIME_AXIS_LINE)
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = normDate(show.first().date),   // 固定年月日：屏幕最左那根柱子（归一化为带横线）
                    color = TEXT_SUB,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        // 固定区右缘渐隐（浅灰->透明），让滑入内容「逐渐隐藏」
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 1.dp, bottom = 1.dp)
                .offset(x = fixedW)
                .width(12.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(TA_BG, TA_BG.copy(alpha = 0f))
                    )
                )
        )
    }
}

@Composable
private fun KLineVolumeCanvas(line: List<KLinePoint>, selLocal: Int? = null) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val hFull = size.height
        if (line.isEmpty()) return@Canvas
        // ===== 顶部限高：为栏顶数字标签预留高度，量柱最高不超过标签底部 =====
        val topReserve = 18.dp.toPx()
        val h = (hFull - topReserve).coerceAtLeast(1f)
        val baseY = hFull
        // ===== 量均线：对成交量做 MA5 / MA10 =====
        val volMa5 = computeVolMA(line, 5)
        val volMa10 = computeVolMA(line, 10)
        // 纵轴最大值统一纳入量柱与量均线，保证均线天然落在区域内
        var maxV = line.maxOf { it.vol }
        for (v in volMa5) if (!v.isNaN() && v > maxV) maxV = v
        for (v in volMa10) if (!v.isNaN() && v > maxV) maxV = v
        maxV = maxV.coerceAtLeast(1e-6)
        val n = line.size
        val slot = w / n
        val bw = max(slot * 0.92f, 1f)
        // 统一裁剪到成交量显示区（顶部让出标签高度），量柱/量均线/竖线都不会越界
        clipRect(0f, 0f, w, hFull) {
            line.forEachIndexed { i, k ->
                val cx = i * slot + slot / 2
                val up = k.close >= k.open
                val vh = (k.vol / maxV * h).toFloat()
                // 同花顺风格：涨=红空心（描边）、跌=绿实心（填充），与蜡烛配色一致；缩到最小时统一实心黑色
                drawRect(
                    color = if (up) RED_UP else GREEN_DOWN,
                    topLeft = Offset(cx - bw / 2, baseY - vh),
                    size = Size(bw, vh),
                    style = if (up) Stroke(width = 3.0f) else Fill
                )
            }
            // ===== 量均线 MA5（黑）/ MA10（橙），加粗 2.5f（同样按限高后的基准绘制）=====
            drawVolMA(volMa5, slot, w, baseY, h, maxV, maColorOf(5))
            drawVolMA(volMa10, slot, w, baseY, h, maxV, maColorOf(10))
            // ===== 选中竖线（与主图对齐） =====
            val sel = selLocal
            if (sel != null && sel in line.indices) {
                val cx = sel * slot + slot / 2
                drawLine(
                    color = K_CROSS_COLOR,
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1.5f
                )
            }
        } // end clipRect
    }
}

/** 在成交量副图绘制一条量均线（点值 clamp 到 [0,h]，双保险不越界）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVolMA(
    ma: DoubleArray,
    slot: Float,
    w: Float,
    baseY: Float,
    h: Float,
    maxV: Double,
    color: Color
) {
    val path = androidx.compose.ui.graphics.Path()
    var started = false
    ma.forEachIndexed { i, v ->
        if (v.isNaN()) return@forEachIndexed
        val cx = i * slot + slot / 2
        val y = (baseY - (v / maxV * h)).toFloat().coerceIn(baseY - h, baseY)
        if (!started) {
            path.moveTo(cx, y)
            started = true
        } else {
            path.lineTo(cx, y)
        }
    }
    if (started) {
        drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
    }
}

/**
 * 副图首行：指标名标签框 + 紧跟其后的数值（同一 Row，避免重叠）。
 * 成交量等其它指标只显示名称框（涨跌幅可选），MACD 在名称框右侧依次排 DIF/DEA/MACD 数值。
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SubLabelRow(
    main: String,
    showMacd: Boolean,
    labIdx: Int?,
    macdShow: MacdResult,
    volLine: List<KLinePoint>?,
    onTag: () -> Unit,
    subIdx: SubIndicator? = null,
    subShow: List<KLinePoint>? = null
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 2.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubIndicatorTag(text = main) { onTag() }
        // ===== 成交量副图：标签框后追加 量均线 MA5 / MA10 当前值（颜色与均线一致） =====
        if (volLine != null && volLine.isNotEmpty()) {
            val idx = labIdx?.takeIf { it in volLine.indices } ?: (volLine.size - 1)
            val ma5 = computeVolMA(volLine, 5)
            val ma10 = computeVolMA(volLine, 10)
            if (idx in volLine.indices) {
                val c5 = maColorOf(5)
                val c10 = maColorOf(10)
                // 当前成交量：带“量：”前缀，紧跟在标签框后面
                Spacer(Modifier.width(6.dp))
                Text("量：" + fmtVol(volLine[idx].vol), color = VOL_LABEL_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                // 与量均线数值拉开明显间隔，避免挤在一起
                Spacer(Modifier.width(16.dp))
                Text("M5", color = c5, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(fmtVol(ma5[idx]), color = c5, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("M10", color = c10, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(fmtVol(ma10[idx]), color = c10, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (showMacd) {
            val idx = labIdx?.takeIf { it in macdShow.dif.indices }
                ?: (macdShow.dif.size - 1).takeIf { it >= 0 }
            if (idx != null) {
                Spacer(Modifier.width(6.dp))
                Text("DIF", color = MACD_DIF_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(fmtMacdNum(macdShow.dif[idx]), color = MACD_DIF_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("DEA", color = MACD_DEA_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(fmtMacdNum(macdShow.dea[idx]), color = MACD_DEA_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("MACD", color = MACD_BAR_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(fmtMacdNum(macdShow.macd[idx]), color = MACD_BAR_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // ===== 其余指标：K/D/J、RSI、WR、CCI、BIAS、PSY、OBV、DMI、BOLL 数值 =====
        if (subIdx != null && subShow != null && subShow.isNotEmpty() && subIdx != SubIndicator.VOL && subIdx != SubIndicator.MACD) {
            val idx = labIdx?.takeIf { it in subShow.indices } ?: (subShow.size - 1)
            val vals = subIndicatorValues(subIdx, subShow, idx)
            Spacer(Modifier.width(6.dp))
            vals.forEachIndexed { i, v ->
                if (i > 0) Spacer(Modifier.width(8.dp))
                Text(v.first, color = v.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(v.third, color = v.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                // v: Triple<标签, 颜色, 数值>
            }
        }
    }
}

/**
 * 副图指标名标签框：长方形（无圆角），宽高自适应包住「指标名 + ▼」。
 */
@Composable
private fun SubIndicatorTag(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .background(SUB_TAG_BG)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = SUB_TAG_FG, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(text = "▼", color = SUB_TAG_FG, fontSize = 9.sp, maxLines = 1)
    }
}

/**
 * 副图指标选择弹层：列出全部指标，点哪个换哪个。
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun SubIndicatorPicker(
    current: SubIndicator,
    onPick: (SubIndicator) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD9D9D9))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "分时指标",
                color = TEXT_MAIN,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SubIndicator.entries.forEach { ind ->
                val on = ind == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ind) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ind.label,
                        color = if (on) Color(Config.COLOR_PRIMARY) else TEXT_MAIN,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.weight(1f))
                    if (on) Text("✓", color = Color(Config.COLOR_PRIMARY), fontSize = 14.sp)
                }
            }
        }
    }
}

/** 副图通用指标线定义：一组折线（含可选定色）。 */
private data class SubLine(val vals: DoubleArray, val color: androidx.compose.ui.graphics.Color, val width: Float = 1.8f)

/**
 * 副图通用指标画布：支持 KDJ / RSI / WR / CCI / BIAS / PSY / OBV / DMI / BOLL。
 * line 为「已按可见窗口切片」的数据；算法在画布内对该窗口重算（结果与全量一致：指标值只依赖到当前 bar）。
 * 统一策略：先算全部序列，再取窗口内的 min/max 做纵轴自适应；正负跨零的画零轴。
 */
@Composable
private fun SubGenericCanvas(
    idx: SubIndicator,
    line: List<KLinePoint>,
    globalStart: Int,
    selLocal: Int? = null
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val n = line.size
        if (n == 0) return@Canvas
        val slot = w / n

        // ===== 1) 产出该指标的各条线 =====
        val lines: List<SubLine> = when (idx) {
            SubIndicator.KDJ -> {
                val r = computeKDJ(line)
                listOf(
                    SubLine(r.k, Color(0xFF111111), 1.8f),
                    SubLine(r.d, Color(0xFFE8A33D), 1.8f),
                    SubLine(r.j, Color(0xFF8E44AD), 1.8f)
                )
            }
            SubIndicator.RSI -> {
                val r = computeRSI(line)
                listOf(
                    SubLine(r.r6, Color(0xFF111111), 1.8f),
                    SubLine(r.r12, Color(0xFFE8A33D), 1.8f),
                    SubLine(r.r24, Color(0xFF8E44AD), 1.8f)
                )
            }
            SubIndicator.WR -> listOf(SubLine(computeWR(line), Color(0xFF111111), 1.8f))
            SubIndicator.CCI -> listOf(SubLine(computeCCI(line), Color(0xFF111111), 1.8f))
            SubIndicator.BIAS -> {
                val r = computeBIAS(line)
                listOf(
                    SubLine(r.b6, Color(0xFF111111), 1.8f),
                    SubLine(r.b12, Color(0xFFE8A33D), 1.8f),
                    SubLine(r.b24, Color(0xFF8E44AD), 1.8f)
                )
            }
            SubIndicator.PSY -> listOf(SubLine(computePSY(line), Color(0xFF111111), 1.8f))
            SubIndicator.OBV -> listOf(SubLine(computeOBV(line), Color(0xFF111111), 1.8f))
            SubIndicator.DMI -> {
                val r = computeDMI(line)
                listOf(
                    SubLine(r.pdi, Color(0xFFD32F2F), 1.8f),
                    SubLine(r.mdi, Color(0xFF1976D2), 1.8f),
                    SubLine(r.adx, Color(0xFFE8A33D), 1.8f)
                )
            }
            SubIndicator.BOLL -> {
                val r = computeBOLL(line)
                listOf(
                    SubLine(r.up, Color(0xFF1976D2), 1.6f),
                    SubLine(r.mid, Color(0xFF111111), 1.8f),
                    SubLine(r.low, Color(0xFF1976D2), 1.6f)
                )
            }
            else -> emptyList()
        }
        if (lines.isEmpty()) return@Canvas

        // ===== 2) 纵轴自适应（跨零则零轴居中）=====
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (ln in lines) for (v in ln.vals) {
            if (v.isNaN()) continue
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        if (lo > hi) { lo = 0.0; hi = 1.0 }
        if (hi - lo < 1e-9) { hi += 1.0; lo -= 1.0 }
        val pad = (hi - lo) * 0.08
        lo -= pad; hi += pad
        val span = (hi - lo).coerceAtLeast(1e-9)
        fun yOf(v: Double): Float = (h - ((v - lo) / span * h)).toFloat()

        // 零轴（仅当跨零时画）
        if (lo < 0.0 && hi > 0.0) {
            val zy = yOf(0.0)
            drawLine(Color(0xFF9AA0A6), Offset(0f, zy), Offset(w, zy), strokeWidth = 0.8f)
        }

        // ===== 3) 逐条折线（端点内缩半个线宽，避免线宽/极值越出副图框）=====
        val padX = 1.5f
        val innerW = (w - padX * 2f).coerceAtLeast(1f)
        val step = if (n > 1) innerW / (n - 1) else 0f
        for (ln in lines) {
            val path = androidx.compose.ui.graphics.Path()
            var started = false
            for (i in 0 until n) {
                val v = ln.vals[i]
                if (v.isNaN()) { started = false; continue }
                val cx = if (n > 1) padX + i * step else w / 2f
                val cy = yOf(v).coerceIn(1f, h - 1f)
                if (!started) {
                    path.moveTo(cx, cy)
                    started = true
                } else {
                    path.lineTo(cx, cy)
                }
            }
            drawPath(path, color = ln.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = ln.width))
        }

        // ===== 4) 选中竖线 =====
        val sel = selLocal
        if (sel != null && sel in 0 until n) {
            val cx = sel * slot + slot / 2
            drawLine(K_CROSS_COLOR, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f)
        }
    }
}

/** 取通用指标在 idx 处的「标签/颜色/值」三元组，用于栏顶数值显示。 */
private fun subIndicatorValues(
    idx: SubIndicator,
    line: List<KLinePoint>,
    i: Int
): List<Triple<String, androidx.compose.ui.graphics.Color, String>> {
    if (line.isEmpty() || i !in line.indices) return emptyList()
    fun f(v: Double): String = String.format("%.2f", v)
    return when (idx) {
        SubIndicator.KDJ -> {
            val r = computeKDJ(line)
            listOf(
                Triple("K", Color(0xFF111111), f(r.k[i])),
                Triple("D", Color(0xFFE8A33D), f(r.d[i])),
                Triple("J", Color(0xFF8E44AD), f(r.j[i]))
            )
        }
        SubIndicator.RSI -> {
            val r = computeRSI(line)
            listOf(
                Triple("RSI6", Color(0xFF111111), f(r.r6[i])),
                Triple("RSI12", Color(0xFFE8A33D), f(r.r12[i])),
                Triple("RSI24", Color(0xFF8E44AD), f(r.r24[i]))
            )
        }
        SubIndicator.WR -> listOf(Triple("WR14", Color(0xFF111111), f(computeWR(line)[i])))
        SubIndicator.CCI -> listOf(Triple("CCI14", Color(0xFF111111), f(computeCCI(line)[i])))
        SubIndicator.BIAS -> {
            val r = computeBIAS(line)
            listOf(
                Triple("BIAS6", Color(0xFF111111), f(r.b6[i])),
                Triple("BIAS12", Color(0xFFE8A33D), f(r.b12[i])),
                Triple("BIAS24", Color(0xFF8E44AD), f(r.b24[i]))
            )
        }
        SubIndicator.PSY -> listOf(Triple("PSY12", Color(0xFF111111), f(computePSY(line)[i])))
        SubIndicator.OBV -> listOf(Triple("OBV", Color(0xFF111111), fmtVol(computeOBV(line)[i])))
        SubIndicator.DMI -> {
            val r = computeDMI(line)
            listOf(
                Triple("PDI", Color(0xFFD32F2F), f(r.pdi[i])),
                Triple("MDI", Color(0xFF1976D2), f(r.mdi[i])),
                Triple("ADX", Color(0xFFE8A33D), f(r.adx[i]))
            )
        }
        SubIndicator.BOLL -> {
            val r = computeBOLL(line)
            listOf(
                Triple("UP", Color(0xFF1976D2), f(r.up[i])),
                Triple("MID", Color(0xFF111111), f(r.mid[i])),
                Triple("LOW", Color(0xFF1976D2), f(r.low[i]))
            )
        }
        else -> emptyList()
    }
}

/** 尚未接入的副图指标占位画布（后续逐个替换为真实计算+绘制）。 */
@androidx.compose.runtime.Composable
private fun SubPlaceholderCanvas(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$text 指标接入中…", color = TEXT_SUB, fontSize = 12.sp)
    }
}

/**
 * MACD 副图（同花顺风格）。
 * dif/dea/macd 由 computeMACD 对「全量」数据预计算后按可见窗口切片传入，
 * 保证 EMA 递归与历史一致。
 * 绘制：MACD（蓝线，原柱改线）+ DEA 线（红）+ DIF 线（黑）；零轴居中。
 */
@Composable
private fun KLineMacdCanvas(
    dif: DoubleArray,
    dea: DoubleArray,
    macd: DoubleArray,
    selLocal: Int? = null
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val n = dif.size
        if (n == 0) return@Canvas
        val slot = w / n
        var maxAbs = 1e-9
        for (i in 0 until n) {
            maxAbs = maxOf(maxAbs, kotlin.math.abs(dif[i]), kotlin.math.abs(dea[i]), kotlin.math.abs(macd[i]))
        }
        val midY = h / 2f
        val scale = (h / 2f * 0.92f) / maxAbs.toFloat()
      // 统一裁剪到副图区，确保任何极值/线宽都不越出框
      clipRect(0f, 0f, w, h) {
        // 零轴
        drawLine(Color(0xFF444444), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.0f)
        // MACD：柱改折线（与 DIF/DEA 同一套 poly，单色蓝）
        // DIF 黑线（快线=上攻线）、DEA 红线（慢线）；线加粗、左右端点拉满整幅（到底到边）
        fun poly(vals: DoubleArray, c: Color, sw: Float) {
            var prev: Offset? = null
            // 端点对齐：i=0 -> x=0，i=n-1 -> x=w，线贯穿整个副图宽度，不留左右空隙
            val step = if (n > 1) w / (n - 1) else 0f
            for (i in 0 until n) {
                val cx = if (n > 1) i * step else w / 2f
                // 不做 coerceIn 到 [0,h]，仅夹到允许的上下边界（含极值贴边），保证"到底"贯穿
                val cy = (midY - (vals[i] * scale).toFloat())
                val cur = Offset(cx, cy)
                prev?.let { drawLine(c, it, cur, strokeWidth = sw) }
                prev = cur
            }
        }
        // MACD：柱状（每根细竖线，零轴->值），正红负绿
        val barW = maxOf(1.0f, slot * 0.45f)
        for (i in 0 until n) {
            val cx = i * slot + slot / 2f
            val cy = (midY - (macd[i] * scale).toFloat())
            val cc = if (macd[i] >= 0.0) RED_UP else GREEN_DOWN
            drawLine(cc, Offset(cx, midY), Offset(cx, cy), strokeWidth = barW)
        }
        poly(dea, MACD_DEA_COLOR, 2.2f)      // 红线（慢线 DEA）
        poly(dif, MACD_DIF_COLOR, 2.2f)      // 黑线（快线 DIF）
        // 选中竖线
        val sel = selLocal
        if (sel != null && sel in 0 until n) {
            val cx = sel * slot + slot / 2
            drawLine(K_CROSS_COLOR, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f)
        }
      } // end clipRect
    }
}

// ============================================================
// 数字格式化工具
// ============================================================
// ============================================================
// 副图指标枚举（可切换）
// ============================================================
/** 副图可切换指标；顺序即「点副图空白循环切换」的顺序。 */
internal enum class SubIndicator(val label: String, val tag: String) {
    VOL("成交量", "成交量"),
    MACD("MACD", "MACD(12,26,9)"),
    KDJ("KDJ", "KDJ(9,3,3)"),
    RSI("RSI", "RSI(6,12,24)"),
    WR("WR", "WR(14)"),
    CCI("CCI", "CCI(14)"),
    BIAS("BIAS", "BIAS(6,12,24)"),
    PSY("PSY", "PSY(12)"),
    OBV("OBV", "OBV"),
    DMI("DMI", "DMI(14,6)"),
    BOLL("BOLL", "BOLL(20,2)");

    fun next(): SubIndicator = entries[(ordinal + 1) % entries.size]
}

/** MACD 指标值：+0.123 / -0.456（3 位小数，正数带 +） */
private fun fmtMacdNum(v: Double): String {
    val s = String.format(java.util.Locale.CHINA, "%.3f", v)
    return if (v > 0) "+$s" else s
}

/** 普通价格：2.18 */
private fun fmtNum(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 2 位小数（量比/量价值） */
private fun fmtNum2(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 带符号：+0.06 / -0.06 */
private fun fmtSigned(v: Double?): String {
    if (v == null) return "--"
    val s = String.format(java.util.Locale.CHINA, "%.2f", v)
    return if (v > 0) "+$s" else s
}

/** 顶栏用：涨不带 +，跌带 -（仅去掉正号） */
private fun fmtSignedPct(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 市盈标签值：正数显示数字，负数显示“亏损” */
private fun fmtPeLabel(v: Double?): String {
    if (v == null) return "--"
    if (v < 0) return "亏损"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 百分比：7.50% */
private fun fmtPct(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f%%", v)
}

/** 市值/流通（亿）：109.93 */
private fun fmtCap(v: Double?): String {
    if (v == null) return "--"
    // 单位：亿；>=10000 亿（即 >=1 万亿）时改用“万亿”
    return if (v >= 10000.0) String.format(java.util.Locale.CHINA, "%.2f万亿", v / 10000.0)
    else String.format(java.util.Locale.CHINA, "%.2f亿", v)
}

/** 市盈：-79.11，正数不显示 + 号 */
private fun fmtPe(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 成交额（万）：744.5 → 显示 744.5万；大于 1 亿显示 X.XX亿 */
private fun fmtAmt(v: Double?): String {
    if (v == null) return "--"
    return if (v >= 10000) {
        String.format(java.util.Locale.CHINA, "%.2f亿", v / 10000)
    } else {
        String.format(java.util.Locale.CHINA, "%.1f万", v)
    }
}
// ============================================================
// 底部功能栏：概念涨跌 + 下单 + 诊股 + 自选 + 功能
// ============================================================
@Composable
private fun DetailBottomBar(
    concept: Api.Concept?,
    watched: Boolean?,
    onOrder: () -> Unit,
    onDiagnose: () -> Unit,
    onToggleWatch: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 外层黑底容器：高度 = 内容高度 + 系统按键区，使黑色铺到屏幕最底部
    // 有虚拟按键 → 底部留出黑底 Spacer（按键区）；无按键 → inset=0，内容自然整块居中
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1) 概念按钮（动态：概念名 + 涨跌幅）
        val cName = concept?.name ?: "概念"
        val pct = concept?.pct
        val pctColor = when {
            pct == null -> Color(0xFFBBBBBB)
            pct > 0 -> Color(0xFFE83030)
            pct < 0 -> Color(0xFF009900)
            else -> Color(0xFFBBBBBB)
        }
        val pctTxt = when {
            pct == null -> "--"
            else -> (if (pct > 0) "+" else "") + String.format("%.2f", pct) + "%"
        }
        Column(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .clickable { onMore() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_dbar_concept),
                contentDescription = null,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = cName,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pctTxt,
                color = pctColor,
                fontSize = 11.sp,
                fontFamily = DSpNumFont,
                maxLines = 1
            )
        }
        // 2) 下单
        BottomAction("下单", R.drawable.ic_dbar_order, 20.dp, Modifier.weight(1f), onOrder)
        // 3) 诊股
        BottomAction("诊股", R.drawable.ic_dbar_diagnose, 20.dp, Modifier.weight(1f), onDiagnose)
        // 4) 自选 / 删自选（图标随是否已加自选切换）
        val wTxt = when (watched) {
            true -> "删自选"
            false -> "加自选"
            else -> "自选"
        }
        val wIconRes = if (watched == true) R.drawable.ic_dbar_watch_on else R.drawable.ic_dbar_watch_off
        BottomAction(wTxt, wIconRes, 20.dp, Modifier.weight(1f), onToggleWatch)
        // 5) 功能
        BottomAction("功能", R.drawable.ic_dbar_more, 20.dp, Modifier.weight(1f), onMore)
    }
    // 底部系统虚拟按键区（纯黑填充）
    Spacer(Modifier.height(navInset))
    }
}

@Composable
private fun BottomAction(text: String, iconRes: Int, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.height(6.dp))
        Text(text = text, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

// ===================== 筹码分布（筹码峰）算法 =====================
// 无公开接口，按东财口径本地推演：日K线(OHLC) + 估算换手率。
// 回看 CHIP_LOOKBACK 根，价格按三角分布铺开，历史筹码按换手率衰减。
// 产出：150 档价格 + 占比（从低到高），便于画横向筹码柱。
private const val CHIP_LOOKBACK = 120      // 回看K线根数（东财默认120）
private const val CHIP_BINS = 150          // 价格档位

private class ChipDist(
    val prices: List<Double>,   // 每档价格（从低到高）
    val ratios: List<Double>,   // 每档筹码占比（总和≈1）
    val maxRatio: Double,       // 最大档占比（用于横向柱长归一化）
    val avgCost: Double,        // 平均成本（累计50%处）
    val profitRatio: Double     // 获利比例（现价之下筹码占比）
)

/**
 * 计算筹码分布。
 * @param line  日K线（含 OHLC + vol + amount）
 * @param base  今日行情（用于估算流通股 / 换手率）
 */
private fun computeChipDist(line: List<KLinePoint>, base: StockBase?): ChipDist? {
    if (line.size < 2) return null
    val b = base ?: StockBase()
    // 流通股（股）：仅在需要估算换手率时使用；同花顺真值可用时可为 0
    val floatShares = if (b.price > 0 && b.floatCap > 0) b.floatCap * 1e8 / b.price else 0.0

    // 取最近 CHIP_LOOKBACK 根
    val start = maxOf(0, line.size - CHIP_LOOKBACK)
    val seg = line.subList(start, line.size)

    // 确定价格范围（稍留边）
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (kp in seg) {
        if (kp.low > 0 && kp.low < lo) lo = kp.low
        if (kp.high > hi) hi = kp.high
    }
    if (hi <= lo || lo <= 0) return null
    val span = hi - lo
    val pad = span * 0.02
    lo -= pad; hi += pad
    val step = (hi - lo) / CHIP_BINS
    if (step <= 0) return null

    // 筹码数组（每档权重）
    val hist = DoubleArray(CHIP_BINS)

    // 按日期从旧到新推演
    val turnoverRate = DoubleArray(seg.size)
    for (i in seg.indices) {
        val kp = seg[i]
        // 换手率%：优先同花顺真值(kp.turnover)，缺失时才用估算兜底
        var tr = if (kp.turnover > 0.0) kp.turnover
                 else if (floatShares > 0.0) kp.vol * 100.0 / floatShares * 100.0
                 else 0.0
        if (tr.isNaN() || tr < 0) tr = 0.0
        tr = tr.coerceIn(0.0, 100.0) / 100.0
        turnoverRate[i] = tr
    }

    // 三角分布铺开当日成交：以当日均价为峰，[low,high] 为底
    fun spread(dayIdx: Int, weight: Double) {
        val kp = seg[dayIdx]
        val dl = ((kp.low - lo) / step).toInt().coerceIn(0, CHIP_BINS - 1)
        val dh = ((kp.high - lo) / step).toInt().coerceIn(0, CHIP_BINS - 1)
        val mid = ((kp.open + kp.close + kp.high + kp.low) / 4.0 - lo) / step
        for (k in dl..dh) {
            // 三角分布：离峰越远权重越低
            val d = kotlin.math.abs(k - mid)
            val half = maxOf(0.5, kotlin.math.abs(dh - mid) + 0.5, kotlin.math.abs(mid - dl) + 0.5)
            val w = (1.0 - d / (half + 1e-9)).coerceAtLeast(0.05)
            hist[k] += weight * w
        }
    }

    for (i in seg.indices) {
        val tr = turnoverRate[i]
        if (tr <= 0.0) {
            // 无成交量：仅轻微衰减
            for (k in 0 until CHIP_BINS) hist[k] *= 0.999
            continue
        }
        // 历史筹码按换手率衰减
        for (k in 0 until CHIP_BINS) hist[k] *= (1.0 - tr)
        // 当日成交按换手率加入
        val dayWeight = tr * seg[i].vol.coerceAtLeast(0.0)
        if (dayWeight > 0) spread(i, dayWeight)
    }

    // 归一化
    var total = 0.0
    for (v in hist) total += v
    if (total <= 0) return null
    val ratios = List(CHIP_BINS) { hist[it] / total }
    val prices = List(CHIP_BINS) { lo + step * (it + 0.5) }
    val maxR = ratios.maxOrNull() ?: 0.0

    // 平均成本：从低到高累计到 50% 处的价格
    var acc = 0.0
    var avgCost = prices.last()
    for (k in 0 until CHIP_BINS) {
        acc += ratios[k]
        if (acc >= 0.5) { avgCost = prices[k]; break }
    }
    // 获利比例：现价之下的筹码占比
    val cur = if (b.price > 0) b.price else seg.last().close
    var profit = 0.0
    for (k in 0 until CHIP_BINS) if (prices[k] <= cur) profit += ratios[k]

    return ChipDist(prices, ratios, maxR, avgCost, profit)
}

/**
 * 右侧纵向筹码峰（同花顺风格）。
 * 方案A：不使用自己的纵坐标，而是复用 K 线主图的价格轴 topV/botV，
 * 因此 K 线缩放时（窗口 max/min 变化）筹码峰高度同步缩放。
 * 现价上方 = 套牢盘（蓝 CHIP_TRAPPED），下方 = 获利盘（红 CHIP_PROFIT）。
 */
@Composable
private fun ChipCanvas(
    chip: ChipDist,
    topV: Double,
    botV: Double,
    curPrice: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (h <= 0f || chip.prices.isEmpty()) return@Canvas
        val sp = (topV - botV).coerceAtLeast(1e-6)
        // 价格 -> y（与 KLineCanvas 完全同口径，保证联动缩放）
        fun yOf(p: Double): Float = (h - ((p - botV) / sp) * h).toFloat()
        // 每档中心 y 对应的柱子高度
        val n = chip.prices.size
        val barH = (h / n).coerceAtLeast(0.5f)
        val maxBarW = w * 0.92f
        // 从低到高绘制：柱左端贴 K 线主图（方案B），长度按占比归一化向右生长
        chip.prices.forEachIndexed { i, p ->
            val r = chip.ratios[i]
            if (r <= 0.0) return@forEachIndexed
            val len = (r / chip.maxRatio * maxBarW).toFloat().coerceAtLeast(0.6f)
            val cy = yOf(p)
            if (cy < -barH || cy > h + barH) return@forEachIndexed
            val color = if (p > curPrice) CHIP_TRAPPED else CHIP_PROFIT
            drawRect(
                color = color,
                topLeft = Offset(0f, cy - barH / 2f),
                size = Size(len, barH)
            )
        }
        // 现价水平基准线（贯穿筹码区域）
        val yCur = yOf(curPrice)
        if (yCur in 0f..h) {
            drawLine(
                color = Color(0xCC666666),
                start = Offset(0f, yCur),
                end = Offset(w, yCur),
                strokeWidth = 1.5f
            )
        }
    }
}

// ============================================================================
// 盘后固定价格交易模块（科创板 688/689、创业板 300/301，15:05–15:30）
// 数据来源：Api.afterHours()（腾讯 minute/query，已对累计量额做过差分）
// 收起态：一行横条「盘后固定价格交易  价 xxx  量 xxx手  额 x.x万」
// 展开态：分时价图（固定价水平线）+ 时间轴(15:06..15:30) + 分时量柱 + 十字光标 + 分钟明细
// ============================================================================

/** 盘后模块内部固定尺寸 */
private val AH_PRICE_H = 150.dp   // 展开态价图高度
private val AH_TIME_H = 17.dp     // 时间轴高度
private val AH_VOL_H = 70.dp      // 展开态量图高度
/** 盘后每分钟一个槽位（15:06..15:30 共 25 分钟） */
private val AH_SLOTS = 25

/**
 * 盘后固定价格交易栏。
 * @param detail 当前个股详情（用于取 code / market / 收盘价做兜底价）
 */
@Composable
private fun AfterHoursBar(detail: StockDetail?) {
    // ---- 时间窗：仅 15:00-24:00 显示盘后固定价格交易整条内容 ----
    val ahHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    if (ahHour < 15) return
    val code = detail?.code ?: ""
    val market = detail?.market ?: ""
    // 盘后数据：随股票切换重新拉取
    var ah by remember(code) { mutableStateOf<AfterHours?>(null) }
    var loading by remember(code) { mutableStateOf(true) }
    LaunchedEffect(code) {
        if (code.isEmpty()) return@LaunchedEffect
        loading = true
        ah = withContext(Dispatchers.IO) {
            try { Api.afterHours(code, market) } catch (_: Exception) { null }
        }
        loading = false
    }
    val d = ah
    // 不支持 / 无盘后成交 / 尚未就绪 → 整块不渲染
    if (d == null || !d.supported || d.minutes.isEmpty()) return

    // 盘口（买一/卖一）：标题条收起态也要显示，独立拉取
    var quote by remember(code) { mutableStateOf<OrderBook?>(null) }
    LaunchedEffect(code) {
        if (code.isEmpty()) return@LaunchedEffect
        quote = withContext(Dispatchers.IO) {
            try { Api.orderbook(code) } catch (_: Exception) { null }
        }
    }
    val preClose = detail?.base?.preClose ?: d.price
    val bid1 = quote?.bids?.firstOrNull()
    val ask1 = quote?.asks?.firstOrNull()
    // 买一/卖一显示色：标签与数值同色，按各自报价对比收盘价红涨绿跌
    val bid1Color = when {
        bid1 == null -> TEXT_SUB
        bid1.price > preClose -> RED_UP
        bid1.price < preClose -> GREEN_TEXT
        else -> GRAY_FLAT
    }
    val ask1Color = when {
        ask1 == null -> TEXT_SUB
        ask1.price > preClose -> RED_UP
        ask1.price < preClose -> GREEN_TEXT
        else -> GRAY_FLAT
    }

    var expanded by remember(code) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 与分时页 MinuteChart 左右边界一致（start=4 / end=4），保证盘后分时区与分时页左右齐。
            .padding(start = 4.dp, end = 4.dp)
    ) {
        // ---------------- 标题横条 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "盘后固定价格交易",
                color = TEXT_MAIN,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(8.dp))
            // 收盘价（固定价）：颜色同收盘价红涨绿跌
            Text(
                fmtAhPrice(d.price),
                color = when {
                    preClose <= 0.0 -> GRAY_FLAT
                    d.price > preClose -> RED_UP
                    d.price < preClose -> GREEN_TEXT
                    else -> GRAY_FLAT
                },
                fontSize = 12.sp,
                fontFamily = DSpNumFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(8.dp))
            // 量：带标签
            Text(
                "量 ${fmtAhVol(d.totalVol)}",
                color = TEXT_SUB,
                fontSize = 10.sp,
                fontFamily = DSpNumFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(8.dp))
            // 买一：带标签，标签与数值同色
            Text(
                if (bid1 != null) "买一 ${fmtAhVol(bid1.vol)}" else "买一 --",
                color = bid1Color,
                fontSize = 10.sp,
                fontFamily = DSpNumFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(8.dp))
            // 卖一：带标签，标签与数值同色
            Text(
                if (ask1 != null) "卖一 ${fmtAhVol(ask1.vol)}" else "卖一 --",
                color = ask1Color,
                fontSize = 10.sp,
                fontFamily = DSpNumFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "收起 ▲" else "展开 ▼",
                color = TEXT_SUB,
                fontSize = 11.sp
            )
        }

        // ---------------- 展开态：左=盘后分时（价/轴/量），右=交易明细 ----------------
        // 布局规格对标分时页 MinuteChart：左侧区域 weight=1-ORDERBOOK_RATIO（与分时页左右齐），
        // 右侧区域 weight=ORDERBOOK_RATIO；左右 padding 与分时页一致(start=4,end=4)。
        if (expanded) {
            AhExpanded(d, preClose = detail?.base?.preClose ?: d.price, code = code, market = market)
            Spacer(Modifier.height(6.dp))
        }
        HorDottedLine()
    }
}

/** 盘后：价格格式化（2 位小数） */
private fun fmtAhPrice(v: Double): String = String.format("%.2f", v)
/** 盘后：量格式化（手，天然 1 位小数；整数则不带小数） */
private fun fmtAhVol(v: Double): String =
    if (v >= 100000000.0) String.format("%.2f亿", v / 100000000.0)
    else if (v >= 10000.0) String.format("%.2f万", v / 10000.0)
    else if (abs(v - Math.round(v).toDouble()) < 0.05) Math.round(v).toString()
    else String.format("%.1f", v)
/** 盘后：额格式化（元 → 万/亿） */
private fun fmtAhAmt(v: Double): String =
    if (v >= 100000000.0) String.format("%.2f亿", v / 100000000.0)
    else String.format("%.1f万", v / 10000.0)

/** 分隔虚线 */
@Composable
private fun HorDottedLine() {
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        val dash = 4.dp.toPx(); val gap = 3.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(LINE_AXIS, Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), strokeWidth = 1f)
            x += dash + gap
        }
    }
}

// ---- 盘后时间槽："HHmm" 归一为 0..24（15:06 -> 0 ... 15:30 -> 24）----
private fun ahSlotOf(hhmm: String): Int {
    val h = hhmm.substring(0, 2).toIntOrNull() ?: 15
    val m = hhmm.substring(2).toIntOrNull() ?: 0
    return (h * 60 + m) - (15 * 60 + 6)
}

// ---- 盘后分时：价图（自绘，X 连续光标）----
@Composable
private fun AhPriceChart(
    minutes: List<AfterMinute>,
    preClose: Double,
    cursorX: Float,
    modifier: Modifier = Modifier,
) {
    val flatColor = GRAY_FLAT
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val slots = AH_SLOTS.toFloat()
        val gapTop = 6.dp.toPx()
        val gapBottom = 6.dp.toPx()
        val ch = h - gapTop - gapBottom
        if (ch <= 0f) return@Canvas

        for (i in 0..3) {
            val y = gapTop + ch * i / 3f
            drawLine(color = LINE_GRID, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
        }

        val pc = preClose.toFloat()
        var maxDev = 0f
        for (m in minutes) {
            val d = abs(m.price.toFloat() - pc)
            if (d > maxDev) maxDev = d
        }
        if (maxDev <= 0f) maxDev = pc * 0.01f
        if (maxDev <= 0f) maxDev = 1f

        val cx = gapTop + ch / 2f
        val half = ch / 2f * 0.92f

        drawLine(color = TIME_AXIS_LINE, start = Offset(0f, cx), end = Offset(w, cx), strokeWidth = 1f)

        val pts = ArrayList<Offset>(minutes.size)
        for (m in minutes) {
            val s = ahSlotOf(m.time)
            if (s < 0) continue
            val x = (s + 0.5f) / slots * w
            val y = cx - (m.price.toFloat() - pc) / maxDev * half
            pts.add(Offset(x, y))
        }
        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            drawPath(path, color = flatColor, style = Stroke(width = 1.4.dp.toPx()))
        }
        for (p in pts) drawCircle(color = flatColor, radius = 1.6.dp.toPx(), center = p)

        if (cursorX >= 0f) {
            drawLine(color = CURSOR_LINE_COLOR, start = Offset(cursorX * w, 0f), end = Offset(cursorX * w, h), strokeWidth = 1f)
        }
    }
}

// ---- 盘后分时：量图（每槽一根细柱，宽度对标分时量区）----
@Composable
private fun AhVolChart(
    minutes: List<AfterMinute>,
    preClose: Double,
    cursorX: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val slots = AH_SLOTS.toFloat()
        val gapTop = 4.dp.toPx()
        val gapBottom = 4.dp.toPx()
        val ch = h - gapTop - gapBottom
        if (ch <= 0f) return@Canvas

        var maxVol = 0.0
        for (m in minutes) if (m.vol > maxVol) maxVol = m.vol
        if (maxVol <= 0.0) maxVol = 1.0

        val bw = (w / slots) * 0.18f
        val pc = preClose.toFloat()

        for (m in minutes) {
            val s = ahSlotOf(m.time)
            if (s < 0) continue
            val cxp = (s + 0.5f) / slots * w
            val bh = (m.vol / maxVol).toFloat() * ch
            val color = when {
                m.price > pc -> RED_UP
                m.price < pc -> GREEN_DOWN
                else -> GRAY_FLAT
            }
            drawRect(color = color, topLeft = Offset(cxp - bw / 2f, gapTop + ch - bh), size = Size(bw, bh))
        }

        if (cursorX >= 0f) {
            drawLine(color = CURSOR_LINE_COLOR, start = Offset(cursorX * w, 0f), end = Offset(cursorX * w, h), strokeWidth = 1f)
        }
    }
}

// ---- 盘后分时：时间轴（与分时页同：无背景，仅文字）----
@Composable
private fun AhTimeAxis(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // 只显示两端时间刻度：左 15:05、右 15:30（中间刻度不显示）
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text = "15:05", color = TEXT_SUB, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(text = "15:30", color = TEXT_SUB, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---- 盘后：X 连续坐标找最近有成交分钟 ----
private fun ahNearestIdx(minutes: List<AfterMinute>, x01: Float): Int {
    if (minutes.isEmpty()) return -1
    var best = -1
    var bestD = Float.MAX_VALUE
    minutes.forEachIndexed { i, m ->
        val s = ahSlotOf(m.time)
        if (s < 0) return@forEachIndexed
        val slotX = (s + 0.5f) / AH_SLOTS.toFloat()
        val dd = abs(slotX - x01)
        if (dd < bestD) { bestD = dd; best = i }
    }
    return best
}

// ---- 盘后展开态：左=盘后分时（价/轴/量），右=卖一/买一 + 交易明细 ----
// 布局对标分时页 MinuteChart：左 weight=1-ORDERBOOK_RATIO，右 weight=ORDERBOOK_RATIO 且高度与左侧价+量两区平齐。
@Composable
private fun AhExpanded(d: AfterHours, preClose: Double, code: String, market: String) {
    val minutes = d.minutes
    // 光标用连续归一化 X（-1 表示无）
    var cursorX by remember(d) { mutableStateOf(-1f) }
    val cursorIdx = if (cursorX >= 0f) ahNearestIdx(minutes, cursorX) else -1

    var quote by remember(code) { mutableStateOf<OrderBook?>(null) }
    LaunchedEffect(code) {
        if (code.isEmpty()) return@LaunchedEffect
        quote = withContext(Dispatchers.IO) {
            try { Api.orderbook(code) } catch (_: Exception) { null }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 左侧：盘后分时（价图 + 时间轴 + 量图）
        // 注意：此处不能依赖 weight 撑高——父 Column 高度是 wrap_content，
        // weight 会失效并让 Canvas.fillMaxSize 撑到无限高，把底部固定价交易按钮顶出屏幕。
        // 因此左列整体给定固定高 = 价图 + 时间轴 + 量图（AH_PRICE_H + AH_TIME_H + AH_VOL_H），
        // 与右侧 AhRightPanel 高度一致，保证左右两列上下对齐、一样高。
        // 时间轴夹在价图与量图之间。
        Column(modifier = Modifier.weight(1f - ORDERBOOK_RATIO)) {
            Box(modifier = Modifier.fillMaxWidth().height(AH_PRICE_H + AH_TIME_H + AH_VOL_H)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AhPriceChart(minutes, preClose, cursorX, modifier = Modifier.fillMaxWidth().height(AH_PRICE_H))
                    AhTimeAxis(modifier = Modifier.fillMaxWidth().height(AH_TIME_H))
                    AhVolChart(minutes, preClose, cursorX, modifier = Modifier.fillMaxWidth().height(AH_VOL_H))
                }
                // 分时价区左上角：委托趋势（标签 买一:手数 卖一:手数；标签与数值同色，红涨绿跌）
                val trendAsk1 = quote?.asks?.firstOrNull()
                val trendBid1 = quote?.bids?.firstOrNull()
                val trendAskColor = when {
                    trendAsk1 == null -> TEXT_SUB
                    trendAsk1.price > preClose -> RED_UP
                    trendAsk1.price < preClose -> GREEN_TEXT
                    else -> GRAY_FLAT
                }
                val trendBidColor = when {
                    trendBid1 == null -> TEXT_SUB
                    trendBid1.price > preClose -> RED_UP
                    trendBid1.price < preClose -> GREEN_TEXT
                    else -> GRAY_FLAT
                }
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("委托趋势", color = TEXT_SUB, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(4.dp))
                    Text("买一:", color = trendBidColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(
                        if (trendBid1 != null) fmtAhVol(trendBid1.vol) else "--",
                        color = trendBidColor, fontSize = 9.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("卖一:", color = trendAskColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(
                        if (trendAsk1 != null) fmtAhVol(trendAsk1.vol) else "--",
                        color = trendAskColor, fontSize = 9.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                    )
                }
                // 手势层：X 连续拖动，轻点清除（覆盖价图/时间轴/量图整体，不拦截右侧明细的滑动）
                AhCursorGesture(
                    onCursor = { cursorX = it },
                    onClear = { cursorX = -1f },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        // 右侧：卖一/买一 + 交易明细，高度与左侧价+量+时间轴平齐，明细区可上下滑动
        AhRightPanel(
            quote = quote,
            minutes = minutes,
            selectedIdx = cursorIdx,
            modifier = Modifier.weight(ORDERBOOK_RATIO).height(AH_PRICE_H + AH_VOL_H + AH_TIME_H).padding(start = 4.dp),
        )
    }
    // 光标数值行：放在左右两列下方，不参与左右两列的高度对齐
    val hover = if (cursorIdx in minutes.indices) minutes[cursorIdx] else null
    Text(
        if (hover != null) fmtAhTime(hover.time) + "   价 " + fmtAhPrice(hover.price) + "   量 " + fmtAhVol(hover.vol) + "手"
        else "拖动查看盘后每分钟成交",
        color = if (hover != null) TEXT_MAIN else TEXT_SUB,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = DSpNumFont,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

// ---- 盘后分时手势：X 连续坐标，轻点清除 ----
@Composable
private fun AhCursorGesture(
    onCursor: (Float) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // 与分时页 MinuteCursorGesture 同款长按逻辑：
            //   长按 ~0.5s 成立 → 激活光标 + 拖动跟随；
            //   未达长按就抬手/短滑 → 关闭光标且【不消费事件】，让事件穿透给外层垂直滚动。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        // 短滑/轻点：清除光标，不消费事件（滚动交还给外层）
                        onClear()
                    } else {
                        // 长按成立：按落点激活光标
                        val w = size.width.toFloat()
                        if (w > 0f) onCursor((longPress.position.x / w).coerceIn(0f, 1f))
                        // 进入拖动循环：跟随手指实时更新，松手停在最后位置
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUpIgnoreConsumed()) break
                            if (ch.positionChanged()) {
                                val ww = size.width.toFloat()
                                if (ww > 0f) onCursor((ch.position.x / ww).coerceIn(0f, 1f))
                                ch.consume()
                            }
                        }
                    }
                }
            },
    )
}

/** 盘后："HHmm" → "HH:mm" */
private fun fmtAhTime(hhmm: String): String =
    if (hhmm.length >= 4) hhmm.substring(0, 2) + ":" + hhmm.substring(2) else hhmm

// ---- 右侧面板：卖一 / 买一（带标签） + 交易明细 ----
@Composable
private fun AhRightPanel(
    quote: OrderBook?,
    minutes: List<AfterMinute>,
    selectedIdx: Int,
    modifier: Modifier = Modifier,
) {
    val pc = quote?.prevClose ?: 0.0
    Column(modifier = modifier) {
        val ask1 = quote?.asks?.firstOrNull()
        val bid1 = quote?.bids?.firstOrNull()
        // ---- 卖一 / 买一：标签 | 价格（右对齐，单行不换行）| 手数（加粗）----
        // 价格用固定宽度 + maxLines(1) + softWrap(false)，避免价格串太长把行撑成两行。
        Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("卖一", color = GREEN_TEXT, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
            Text(
                if (ask1 != null) fmtAhPrice(ask1.price) else "--",
                color = when {
                    ask1 == null -> TEXT_SUB
                    ask1.price > pc -> RED_UP
                    ask1.price < pc -> GREEN_TEXT
                    else -> GRAY_FLAT
                },
                fontSize = 11.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (ask1 != null) fmtAhVol(ask1.vol) else "--",
                color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start, maxLines = 1, softWrap = false, modifier = Modifier.width(54.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("买一", color = RED_UP, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
            Text(
                if (bid1 != null) fmtAhPrice(bid1.price) else "--",
                color = when {
                    bid1 == null -> TEXT_SUB
                    bid1.price > pc -> RED_UP
                    bid1.price < pc -> GREEN_TEXT
                    else -> GRAY_FLAT
                },
                fontSize = 11.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (bid1 != null) fmtAhVol(bid1.vol) else "--",
                color = TEXT_SUB, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start, maxLines = 1, softWrap = false, modifier = Modifier.width(54.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.HorizontalDivider(color = LINE_GRID, thickness = 1.dp)
        AhDealList(minutes = minutes, selectedIdx = selectedIdx, preClose = pc, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

// ---- 交易明细：无表头，可上下滑动 ----
// 数字整体加粗；中列价格红涨绿跌（对比 preClose，与头部股价一致）；
// 末列手数颜色由「主动买卖」决定：本分钟价 > 上一有成交分钟价 → 主动买(红)，
// < → 主动卖(绿)，相等 → 中性(灰)。（腾讯盘后分时无逐笔方向，用相邻分钟价升/降近似）
@Composable
private fun AhDealList(
    minutes: List<AfterMinute>,
    selectedIdx: Int,
    preClose: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        for (i in minutes.indices.reversed()) {
            val m = minutes[i]
            val sel = i == selectedIdx
            // 中列价格：对比收盘价 preClose 红涨绿跌
            val priceColor = when {
                preClose <= 0.0 -> GRAY_FLAT
                m.price > preClose -> RED_UP
                m.price < preClose -> GREEN_TEXT
                else -> GRAY_FLAT
            }
            // 末列手数：主动买/卖（对比上一有成交分钟价）
            val prevPrice = if (i - 1 in minutes.indices) minutes[i - 1].price else m.price
            val volColor = when {
                m.price > prevPrice -> RED_UP      // 主动买
                m.price < prevPrice -> GREEN_TEXT  // 主动卖
                else -> TEXT_SUB                   // 平
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(if (sel) TA_BG else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmtAhTime(m.time), color = TEXT_SUB, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(fmtAhPrice(m.price), color = priceColor, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(fmtAhVol(m.vol), color = volColor, fontSize = 10.sp, fontFamily = DSpNumFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}


// ================= 下方内容切换栏（看点/资讯/盘口/资金/社区/简况/财务）=================
// - 前 6 项等宽铺满一屏，第 7 项（财务）藏在屏幕右侧外，横滑才露出
// - 选中：纯黑文字 + 下方粗红线指示器；未选中：灰黑文字
private val SECTION_TABS = listOf("看点", "资讯", "盘口", "资金", "社区", "简况(F10)", "财务")
private val SEC_TAB_ACTIVE = Color(0xFF000000)   // 选中：纯黑
private val SEC_TAB_INACTIVE = Color(0x99000000) // 未选中：灰黑（可区分）
private val SEC_TAB_INDICATOR = Color(0xFFE60012) // 指示红线：醒目红

@Composable
private fun DetailSectionTabs(
    selected: Int,
    onPick: (Int) -> Unit,
) {
    // 文字宽度自适应：每个按钮宽度 = 文字本身宽度 + 左右内边距，
    // 字符多的（如“简况(F10)”）自然占更宽；整排可左右横滑，
    // 第 7 项（财务）落在屏幕右侧外，横滑才露出。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp), // 左右各留一点点边距
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SECTION_TABS.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                modifier = Modifier
                    .clickable { onPick(i) }
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = label,
                    color = if (on) SEC_TAB_ACTIVE else SEC_TAB_INACTIVE,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(5.dp))
                // 指示红线：一个汉字宽（15dp）、粗 3dp；未选中用透明占位保持高度一致
                Box(
                    modifier = Modifier
                        .width(15.dp)
                        .height(3.dp)
                        .background(if (on) SEC_TAB_INDICATOR else Color.Transparent)
                )
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}
// S16_END

// ============================================================
// 下单弹窗（从屏幕下方上弹，约 1/4 屏高）
// 右上 X 关闭 / 左上 券商名 + 资金账户 + 切换 / 下方并排 买(红) 卖(蓝) 撤(灰) 三实心圆
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeOrderSheet(
    brokers: List<MyBroker>,
    current: MyBroker?,
    onSwitch: (MyBroker) -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onCancelOrder: () -> Unit,
    onDismiss: () -> Unit,
    side: String = "",
    code: String = "",
    name: String = "",
    token: String = "",
    brokerAccountId: Int = 0,
    availableCash: Double = 0.0,
    sellableQty: Int = 0,
    livePrice: Double = 0.0,
    limitUp: Double = 0.0,
    limitDown: Double = 0.0,
    asks: List<OrderLevel> = emptyList(),
    bids: List<OrderLevel> = emptyList(),
    onPickPrice: (Double) -> Unit = {},
    pickPrice: Double = 0.0,
    onBack: () -> Unit = {},
) {
    // 第十轮需求④：只能靠右上角 ✕ 关闭（禁掉下拉/点背景关闭）
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )
    var showSwitch by remember { mutableStateOf(false) }
    // 第十轮需求(5)：上方要透出五档买卖盘并可点选，弹窗高度相应增大（约半屏）
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    // H1 修复：0.62 过高（超出个股详情页下单弹窗原高度），回调至 0.52；表单已可滚动，按钮仍可达
    // H2：弹窗底边贴屏幕最底、盖住底部固定栏（zIndex 10 > BottomBar 2），关窗后 BottomBar 自然还原
    // H4：两个弹窗高度统一到【矮的那个】(买卖撤入口原高 0.36)，即把表单态从 0.52 压到 0.36
    val sheetH = screenH * 0.36f
    // H4：系统导航栏（虚拟按键）高度——标签行要坐在它上方，避免被虚拟按键挡住
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 第十轮需求⑤(A2)：改为自定义底部覆盖层——上方完全不绘制，可点穿到分时页五档
    // H2：zIndex(10) 高于底部固定栏(2)，保证弹窗盖住底部那排固定按钮；关窗后 BottomBar 自然还原
    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(sheetH)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // ---------- 第一行：券商名 + 资金账户 + 切换（左）  ✕（右，同一水平线）----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第十轮需求⑧：证券名称前加返回图标（返回上一页）
                Image(
                    painter = painterResource(R.drawable.ic_order_back),
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onBack() },
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(6.dp))
                // 券商名
                Text(
                    text = current?.broker ?: "证券账户",
                    color = TEXT_MAIN,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 一根牙签的间距
                Spacer(Modifier.width(8.dp))
                // 资金账户（与券商名同色）
                Text(
                    text = current?.account?.ifEmpty { "未添加账户" } ?: "未添加账户",
                    color = TEXT_MAIN,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 一根牙签的间距
                Spacer(Modifier.width(8.dp))
                // 切换：只有蓝色文字，无背景无特效
                Text(
                    text = "切换",
                    color = Color(0xFF2A7BFF),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { if (brokers.isNotEmpty()) showSwitch = true }
                )
                // 余下空间把 ✕ 顶到最右
                Spacer(Modifier.weight(1f))
                // ✕ 关闭：与账户行同水平线，无特效
                Text(
                    text = "✕",
                    color = TEXT_MAIN,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            if (side == "buy" || side == "sell") {
                Column(modifier = Modifier.weight(1f)) {
                    OrderFormBlock(
                        side = side,
                        code = code,
                        name = name,
                        brokerAccountId = brokerAccountId,
                        token = token,
                        availableCash = availableCash,
                        sellableQty = sellableQty,
                        livePrice = livePrice,
                        limitUp = limitUp,
                        limitDown = limitDown,
                        pickPrice = pickPrice,
                        onPickPrice = onPickPrice,
                        onBack = onBack,
                        onDone = { r -> onBack() }
                    )
                }
            } else {
            Spacer(Modifier.weight(1f))
            // ---------- 并排：买 / 卖 / 撤 —— 图片圆按钮 + 下方浅灰标签，均匀分布 ----------
            // H3：整体上提（底部留白），让"买/卖/撤"标签完整可见、不再被藏到屏幕下边
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TOOTHPICK, end = TOOTHPICK, bottom = navInset + 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrderColumn("买", Color(0xFFFA0000), "去交易下单", Modifier.weight(1f)) { onBuy() }
                Spacer(Modifier.width(TOOTHPICK))
                OrderColumn("卖", Color(0xFF008AFF), "双向下单", Modifier.weight(1f)) { onSell() }
                Spacer(Modifier.width(TOOTHPICK))
                OrderColumn("撤", Color(0xFF5A607A), "条件单", Modifier.weight(1f)) { onCancelOrder() }
            }
            }
        }
    }
    }
    // ---------- 切换证券账户弹层 ----------
    if (showSwitch) {
        ModalBottomSheet(
            onDismissRequest = { showSwitch = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "选择证券账户",
                    color = TEXT_MAIN,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color(0xFFEEEEEE))
                )
                brokers.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSwitch(b)
                                showSwitch = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = b.broker,
                                color = TEXT_MAIN,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(text = b.account, color = TEXT_SUB, fontSize = 13.sp)
                        }
                        if (current != null && b.id == current.id) {
                            Text(
                                text = "✓",
                                color = Color(Config.COLOR_PRIMARY),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFFEEEEEE))
                    )
                }
            }
        }
    }
}


// 一根牙签的间距基准值
private val TOOTHPICK = 6.dp

/**
 * 下单列：上=实心圆按钮（圆内白字），下=浅灰底黑字标签；整体在列内水平居中。
 * 圆在标签正中间上方，列宽由外层 weight(1f) 均匀分配。
 */
@Composable
private fun OrderColumn(
    circleText: String,
    circleColor: Color,
    labelText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // H5：回退为代码绘制的实心圆（圆内白字），不再用图片
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(circleColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = circleText,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // 圆圈与标签之间：四根牙签
        Spacer(Modifier.height(TOOTHPICK * 4))
        // 浅灰底 + 黑色文字标签（固定高度：比首版再高一半）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOOTHPICK * 3 + 21.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF2F3F5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                color = Color(0xFF000000),
                fontSize = 15.sp,
                maxLines = 1
            )
        }
    }
}
// ============================================================
// 下单表单块（买/卖共用）—— 本轮第九轮修正版
//   顶部：仅保留「券商名 + 账户 + 切换 + ✕」由外层 TradeOrderSheet 负责，本块不再重复
//   两个连体编辑框（价格 / 手数）：直角、淡红填充更淡、按下瞬红松手恢复
//   跌停/涨停/可买多少股/按金额买入：仅「可买多少股」无边框，其余浅灰边框白底
//   快捷仓位：全仓 1/2仓 1/3仓 1/4仓 2/3仓，浅灰边框
//   底部：齿轮（浅灰正方形边框，与红按钮等高）在红按钮外侧；红按钮空值禁用
// ============================================================
// ============================================================
// 下单表单块（买/卖共用）—— 第十轮修正版
//   顶部：仅保留「返回图标 + 券商名 + 账户 + 切换 + ✕」由外层 TradeOrderSheet 负责
//   两个连体编辑框（价格 / 手数）：直角、淡红填充更淡、减号右/加号左加竖线、整体正方形
//   跌停/涨停后面接「可买多少股」；行最右「按金额买入」与第二个编辑框加号右对齐
//   快捷仓位：全仓 1/2仓 1/3仓 1/4仓 2/3仓，浅灰边框
//   底部：齿轮（浅灰正方形边框，与红按钮等高）在红按钮外侧；红按钮空值禁用
// ============================================================
@Composable
private fun OrderFormBlock(
    side: String,
    code: String,
    name: String,
    brokerAccountId: Int,
    token: String,
    availableCash: Double,
    sellableQty: Int,
    livePrice: Double,
    limitUp: Double = 0.0,
    limitDown: Double = 0.0,
    pickPrice: Double = 0.0,
    onPickPrice: (Double) -> Unit = {},
    onBack: () -> Unit,
    onDone: (TradeResult) -> Unit
) {
    val isBuy = side == "buy"
    val sideColor = if (isBuy) Color(0xFFFA0000) else Color(0xFF008AFF)
    val sideText = if (isBuy) "买入" else "卖出"
    // H11：加减按钮底色按买/卖动态取色（买=淡红 / 卖=淡蓝）
    val redFill = if (isBuy) Color(0x0DFA0000) else Color(0x0D008AFF)
    // H11：主色（按下瞬时反馈底色 + 步进器外框/竖线）——买=#FA0000 / 卖=#008AFF
    val redSolid = if (isBuy) Color(0xFFFA0000) else Color(0xFF008AFF)
    // 浅灰边框（齿轮等）
    val grayBorder = Color(0xFFDDDDDD)
    // H7：标签外框细线（涨跌停 / 全仓等）——更细、更灰
    val thinBorder = Color(0xFFC8C8C8)
    val greenText = Color(0xFF009900)
    // 数字黄偏橙
    val amberText = Color(0xFFE8A33D)
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    // 第十轮需求(5)：点击上方买卖盘价格后，回填到价格编辑框
    LaunchedEffect(pickPrice) {
        if (pickPrice > 0.0) {
            priceText = String.format(java.util.Locale.CHINA, "%.2f", pickPrice)
        }
    }
    var mode by remember { mutableStateOf("quick") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val qty = qtyText.trim().toIntOrNull() ?: 0
    val price = priceText.trim().toDoubleOrNull() ?: 0.0
    // 两个编辑框都有内容才可点击（且数量合法）
    val canSubmit = qtyText.trim().isNotEmpty() and priceText.trim().isNotEmpty() and (qty > 0) and (price > 0.0)
    // 第十轮需求⑦：未输入价格时，默认按左上方实时股价计算「可买多少股」
    val calcPrice = if (price > 0.0) price else livePrice
    val canBuyQty = if (calcPrice > 0.0) ((availableCash / calcPrice / 100.0).toInt() * 100) else 0
    // H11：卖出态「可卖多少股」= 账户当前该股票持仓（持仓多少股 = 可卖多少股），向下取整到 100 的整数倍
    val sellQty = (sellableQty / 100) * 100
    // H11：当前态展示股数（买=按资金算 / 卖=按持仓算）与标签文案
    val showQty = if (isBuy) canBuyQty else sellQty
    val showQtyLabel = if (isBuy) "可买" else "可卖"
    // H7 需求③：加载窗口时，默认按当前股价 + 可用资金算出「可买多少股」并回填数量框。
    // 仅当数量框为空、且算出的可买股数 > 0 时回填一次，避免覆盖用户已输入的内容。
    // H8 修复①：可买股数「智能跟随」。
    // 记录上一次由本逻辑自动写入到数量框的值；只要框为空、或框内内容仍等于该自动值，
    // 就允许跟随 canBuyQty 实时刷新（解决「时算时不算」「点价不重算」）；
    // 一旦用户在框里手输过（内容 != 上次自动值），则永久不再覆盖用户输入。
    var autoQty by remember { mutableStateOf("") }
    // H11：买入跟随「可买股数」、卖出跟随「可卖股数（持仓）」
    LaunchedEffect(showQty, pickPrice) {
        val cur = qtyText.trim()
        val userEdited = cur.isNotEmpty() && cur != autoQty
        if (!userEdited && showQty > 0) {
            val nv = showQty.toString()
            if (nv != cur) {
                qtyText = nv
                autoQty = nv
            }
        }
    }
    fun submit() {
        if (busy) return
        if (!canSubmit) return
        if (qty % 100 != 0) { msg = "数量需为 100 的整数倍"; return }
        // H11：卖出时数量不得超过当前持仓（持仓多少股 = 可卖多少股）
        if (!isBuy && qty > sellableQty) { msg = "卖出数量超过可卖持仓"; return }
        busy = true
        msg = ""
        val acctId = brokerAccountId
        val c = code
        val nm = name
        kotlinx.coroutines.MainScope().launch {
            // R55fix-A：统一下单数据层，线程调度/异常兜底由 Api.placeOrder 负责
            val r = Api.placeOrder(token, acctId, isBuy, c, nm, qty, mode, price)
            busy = false
            onDone(r)
        }
    }
    // A2-D 修复：表单主体可滚动，避免在小屏上底部齿轮/买入按钮被裁掉看不到
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(10.dp))
        // 两个连体编辑框：左价格，右手数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectStepper(
                value = priceText,
                placeholder = "价格",
                isDecimal = true,
                onValueChange = { priceText = it },
                onMinus = {
                    val v = priceText.trim().toDoubleOrNull() ?: 0.0
                    val nv = v - 0.01
                    priceText = if (nv <= 0.0) "" else String.format(java.util.Locale.CHINA, "%.2f", nv)
                },
                onPlus = {
                    val v = priceText.trim().toDoubleOrNull() ?: 0.0
                    priceText = String.format(java.util.Locale.CHINA, "%.2f", v + 0.01)
                },
                redFill = redFill,
                redSolid = redSolid,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            ConnectStepper(
                value = qtyText,
                placeholder = "手数",
                isDecimal = false,
                onValueChange = { qtyText = it },
                onMinus = {
                    val v = qtyText.trim().toIntOrNull() ?: 0
                    val nv = v - 100
                    qtyText = if (nv <= 0) "" else nv.toString()
                },
                onPlus = {
                    val v = qtyText.trim().toIntOrNull() ?: 0
                    qtyText = (v + 100).toString()
                },
                redFill = redFill,
                redSolid = redSolid,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        // 第十轮需求① ②：跌停 / 涨停 / 可买多少股 一行；行最右「按金额买入」与第二个编辑框加号右对齐
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 跌停：标签黑字，价格绿字；浅灰边框白底
            // H8 修复②：可点击，点击后把跌停价填入价格编辑框（无有效价时不响应）
            Box(
                modifier = Modifier
                    .clip(RectangleShape)
                    .background(Color.White)
                    .border(0.5.dp, thinBorder, RectangleShape)
                    .clickable {
                        if (limitDown > 0.0) {
                            priceText = String.format(java.util.Locale.CHINA, "%.2f", limitDown)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("跌停", color = TEXT_MAIN, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (limitDown > 0.0) String.format(java.util.Locale.CHINA, "%.2f", limitDown) else "--",
                        color = greenText, fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 涨停：标签黑字，价格红字；浅灰边框白底
            // H8 修复②：可点击，点击后把涨停价填入价格编辑框（无有效价时不响应）
            Box(
                modifier = Modifier
                    .clip(RectangleShape)
                    .background(Color.White)
                    .border(0.5.dp, thinBorder, RectangleShape)
                    .clickable {
                        if (limitUp > 0.0) {
                            priceText = String.format(java.util.Locale.CHINA, "%.2f", limitUp)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("涨停", color = TEXT_MAIN, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (limitUp > 0.0) String.format(java.util.Locale.CHINA, "%.2f", limitUp) else "--",
                        // H11-fix：涨停价固定红（涨红跌绿，与买卖方向无关；卖出页此前误用 sideColor 变成蓝色）
                        color = Color(0xFFFA0000), fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 可买/可卖多少股：无边框、无背景；标签/单位灰字，数字黄橙（跟在涨跌停后面）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(showQtyLabel, color = TEXT_SUB, fontSize = 12.sp)
                Text(showQty.toString(), color = amberText, fontSize = 12.sp)
                Text("股", color = TEXT_SUB, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            // 按金额买入 / 按净额卖：主色边框 + 主色字 + 白底（行最右，与第二个编辑框加号右对齐）
            Box(
                modifier = Modifier
                    .clip(RectangleShape)
                    .background(Color.White)
                    .border(1.dp, sideColor, RectangleShape)
                    .clickable {
                        // H11：买入=按可用资金估最大可买；卖出=按当前持仓最大可卖
                        if (isBuy) {
                            val p = priceText.trim().toDoubleOrNull() ?: 0.0
                            val p2 = if (p > 0.0) p else livePrice
                            if (p2 > 0.0) {
                                val maxQty = (availableCash / p2 / 100.0).toInt() * 100
                                if (maxQty > 0) qtyText = maxQty.toString()
                            }
                        } else {
                            if (sellQty > 0) qtyText = sellQty.toString()
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(if (isBuy) "按金额买入" else "按净额卖", color = sideColor, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 快捷仓位：全仓 + 1/2仓 1/3仓 1/4仓 2/3仓；浅灰边框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val posList = listOf("全仓", "1/2仓", "1/3仓", "1/4仓", "2/3仓")
            posList.forEachIndexed { idx, p ->
                QuickPosButton(label = p, thinBorder = thinBorder) {
                    val frac = when (p) {
                        "全仓" -> 1.0
                        "1/2仓" -> 0.5
                        "1/3仓" -> 1.0 / 3.0
                        "1/4仓" -> 0.25
                        "2/3仓" -> 2.0 / 3.0
                        else -> 0.0
                    }
                    val p0 = priceText.trim().toDoubleOrNull() ?: 0.0
                    val pp = if (p0 > 0.0) p0 else livePrice
                    if (frac > 0.0) {
                        // H11-fix2：买=按可用资金算（资金×仓位比例÷价格）；卖=按持仓算（持仓×仓位比例）
                        val maxQty = if (isBuy) {
                            if (pp > 0.0) (availableCash * frac / pp / 100.0).toInt() * 100 else 0
                        } else {
                            (sellableQty * frac / 100.0).toInt() * 100
                        }
                        if (maxQty > 0) qtyText = maxQty.toString()
                    }
                }
                if (idx != posList.lastIndex) Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        if (msg.isNotEmpty()) {
            Text(
                text = msg,
                color = Color(0xFFE23B3B),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        // A2-D 修复：去掉 weight(1f) 顶底，避免高度不足时按钮被挤出；按钮行正常跟随内容
        // 底部：齿轮在红按钮外侧（浅灰正方形边框，与红按钮等高）+ 红色买入按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 齿轮：浅灰正方形边框，高度与红按钮一致
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RectangleShape)
                    .background(Color.White)
                    .border(1.dp, grayBorder, RectangleShape)
                    .clickable {
                        // 下单设置（暂未接）
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.aen),
                    contentDescription = "下单设置",
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(10.dp))
            // H5：回退为原纯色按钮（买/卖共用同一布局，仅靠 sideColor/sideText 区分）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RectangleShape)
                    .background(if (busy) sideColor.copy(alpha = 0.45f) else if (canSubmit) sideColor else sideColor.copy(alpha = 0.45f))
                    .clickable(enabled = canSubmit && !busy) { submit() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (busy) "提交中..." else sideText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
/**
 * 连体步进框：减号方块 + 中间编辑区 + 加号方块，三者连成一体。
 * 直角（无圆角）、边框红色；加减方块淡红填充、符号红色；
 * 减号右 / 加号左各一条竖线，整体呈正方形包围加减号；
 * 按下瞬红、松手立刻恢复（pointerInput 监听松手复位）。
 */
@Composable
private fun ConnectStepper(
    value: String,
    placeholder: String,
    isDecimal: Boolean,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    redFill: Color,
    redSolid: Color,
    modifier: Modifier = Modifier
) {
    val borderColor = redSolid
    // H7：竖线颜色与外框保持一致（用户要求：减号右 / 加号左两条竖线跟外框同色，
    // 这样左右两块看起来像两个正方形固定在编辑框两端）
    val dividerColor = borderColor
    var minusPressed by remember { mutableStateOf(false) }
    var plusPressed by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RectangleShape)
            .background(Color.White)
            .border(1.dp, borderColor, RectangleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 减号方块：按下瞬红，松手恢复；外沿宽 = 高（40dp），保证正方形
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(if (minusPressed) redSolid else redFill)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            minusPressed = true
                            tryAwaitRelease()
                            minusPressed = false
                        },
                        onTap = { onMinus() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = if (minusPressed) Color.White else redSolid,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // 竖线（减号右）
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )
        // 中间编辑区（背景保持原样）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = { raw ->
                    if (isDecimal) onValueChange(raw.filter { ch -> ch.isDigit() || ch == '.' })
                    else onValueChange(raw.filter { ch -> ch.isDigit() })
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TEXT_MAIN,
                    fontSize = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (isDecimal) androidx.compose.ui.text.input.KeyboardType.Decimal
                    else androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value.isEmpty()) Text(placeholder, color = TEXT_SUB, fontSize = 14.sp)
                        inner()
                    }
                }
            )
        }
        // 竖线（加号左）
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )
        // 加号方块：按下瞬红，松手恢复；外沿宽 = 高（40dp），保证正方形
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(if (plusPressed) redSolid else redFill)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            plusPressed = true
                            tryAwaitRelease()
                            plusPressed = false
                        },
                        onTap = { onPlus() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (plusPressed) Color.White else redSolid,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
/** 快捷仓位小按钮：白底 + 浅灰边框 + 灰字/黑字，按下短暂变红填充 */
@Composable
private fun QuickPosButton(
    label: String,
    thinBorder: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(Color.White)
            .border(0.5.dp, thinBorder, RectangleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TEXT_MAIN,
            fontSize = 13.sp
        )
    }
}

/** 详情页头部标签（红底头部专用：实底半透明白 + 白字；普通标签白字无边） */
@Composable
private fun DetailTagRow(tags: List<String>, maxShow: Int = 2, fontSize: Float = 9f) {
    // 详情页：过滤掉「场内」；L1 必须始终显示
    val show = tags.filter { it != "场内" }
    if (show.isEmpty()) return
    val ordered = show.filter { it != "L1" } + show.filter { it == "L1" }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ordered.forEach { t ->
            Box(
                modifier = Modifier
                    .background(Color(Config.COLOR_TAG_BG))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t,
                    color = Color(Config.COLOR_TAG_FG),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(3.dp))
        }
    }
}

