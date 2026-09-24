package com.chaogujia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ===== 字体（与搜索页一致：同花顺数字字体） =====
private val DSpNumFont = FontFamily(Font(R.font.ths_regular))

// ===== 颜色 =====
// 全站涨跌配色单一源头：改这两处，全软件（K线/分时/盘口/涨跌幅/列表）同步生效
internal val RED_UP = Color(0xFFE64A3B)       // 涨：红
internal val GREEN_DOWN = Color(0xFF306612)   // 跌：绿（图表柱体/影线/量柱，墨绿 #306612，同花顺实测）
internal val GREEN_TEXT = Color(0xFF00B42A)   // 跌：绿（涨跌文字，亮绿）
private val GRAY_FLAT = Color(0xFF888888)    // 平：灰
private val K_NARROW_COLOR = Color(0xFF111111)  // 缩放最小时柱子统一黑色（全历史黑波浪）
// 缩放到最小（一屏约1400+根）时：蜡烛/量柱/均线全部转黑，整体成一条黑波浪线
private const val K_NARROW_VISIBLE = 1200
private val TEXT_MAIN = Color(0xFF222222)
private val TEXT_SUB = Color(0xFF888888)
private val LINE_GRID = Color(0xFFEEEEEE)
private val LINE_AXIS = Color(0xFFF0F0F0)
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
    list: List<StockItem>,
    index: Int,
    showOwnHeader: Boolean = true,
    onIndexChange: (Int) -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onClose: () -> Unit,
    onOpenSearch: () -> Unit = {}
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
        // 筹码峰展开开关（父层持有，MaLegend 按钮与 KLineChart 共用）
        var showChip by remember { mutableStateOf(false) }
    // 所属概念（含实时涨跌幅）
    var concept by remember { mutableStateOf<Api.Concept?>(null) }
    // 是否已加自选（用于底部“自选”按钮）
    var watched by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
    LaunchedEffect(current?.code, period) {
        val code = current?.code ?: return@LaunchedEffect
        loading = true
        // 分时用 day 数据（同一份 trends）；五日分时 → k=fiveday；其余照原周期
        val k = if (period == "minute") "day" else period
        val d = withContext(Dispatchers.IO) { Api.detail(code, k, 320) }
        if (d != null) detail = d
        selK = null   // 换股票/换周期 → 取消 K 线选中
        loading = false
    }

    // 名称/代码变化 → 回吐给外部（红条显示用）
    LaunchedEffect(current?.code, detail?.name) {
        val nm = (detail?.name?.ifEmpty { null }) ?: current?.name ?: ""
        if (nm.isNotEmpty()) onNameChange(nm)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // detail-overlay-swallow：吞掉自身空白区点击，阻止穿透到下层 IndexBar 触发「编辑表头」
            .pointerInput(Unit) { detectTapGestures { /* 空白区：无操作，仅拦截 */ } }
    ) {
        // ================= 顶部红条（仅独立使用时自绘） =================
        // 被 App 级红条托管时（showOwnHeader=false）跳过，避免出现第二条红条 / 图标重复。
        if (showOwnHeader) {
            DetailHeaderBar(
                name = detail?.name?.ifEmpty { current?.name ?: "" } ?: "",
                code = current?.code ?: "",
                canPrev = idx > 0,
                canNext = idx < list.size - 1,
                onBack = onClose,
                onPrev = { setIdx(idx - 1) },
                onNext = { setIdx(idx + 1) },
                onOpenSearch = onOpenSearch
            )
        }

        // ================= 头部数据区 =================
        // selK：K线被选中的柱子下标（null=未选中，显示最新一天）
        DetailHeadData(detail, selK)

        // ================= 标签栏 =================
        PeriodTabs(period, onPick = { period = it; selK = null }, onGear = { showMaSetting = true })
        // 均线参数（父层持有，供 KLineChart 与图例共享）
        if (period == "day" || period == "week" || period == "month") MaLegend(maPeriods, showChip = showChip, onToggleChip = { showChip = !showChip })
        if (showMaSetting) {
            MaSettingDialog(
                initial = maPeriods,
                onDismiss = { showMaSetting = false },
                onConfirm = { maPeriods = it; showMaSetting = false }
            )
        }

        // ================= 图表区（占满剩余） =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (period) {
                "minute" -> MinuteChart(detail)
                "fiveday" -> FiveDayChart(detail)
                else -> KLineChart(detail, selK, maPeriods, showChip = showChip, onMaPeriodsChange = { maPeriods = it }, onSelect = { selK = it })
            }
            if (loading && detail == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TEXT_SUB, fontSize = 14.sp)
                }
            }
        }
        // ================= 底部功能栏 =================
        DetailBottomBar(
            concept = concept,
            watched = watched,
            onOrder = { /* TODO 下单 */ },
            onDiagnose = { /* TODO 诊股 */ },
            onToggleWatch = {
                val code = current?.code ?: return@DetailBottomBar
                scope.launch {
                    val isW = watched == true
                    val r = withContext(Dispatchers.IO) {
                        if (isW) Api.delWatch(token, code, 0) else Api.addWatch(token, code, 0)
                    }
                    if (r.ok) watched = !isW
                }
            },
            onMore = { /* TODO 功能菜单 */ }
        )
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
            .height(D_BAR_HEIGHT)
            .background(
                Brush.verticalGradient(
                    listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
                )
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // 左侧：返回箭头
        Text(
            text = "←",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .clickable { onBack() }
        )

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
                Text(
                    text = code,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = DSpNumFont,
                    maxLines = 1
                )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：当前价（大字） + 涨跌额/幅
        Column(modifier = Modifier.width(110.dp)) {
            Text(
                text = fmtNum(price),
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

        // 右：三竖列
        Row(modifier = Modifier.weight(1f)) {
            // 列1
            DetailCol(
                Modifier.weight(1f),
                listOf(
                    "最高" to fmtNum(b?.high),
                    "最低" to fmtNum(b?.low),
                    "开盘" to fmtNum(b?.open)
                ),
                color
            )
            // 列2
            DetailCol(
                Modifier.weight(1f),
                listOf(
                    "市值" to fmtCap(b?.mktCap),
                    "流通" to fmtCap(b?.floatCap),
                    "市盈TTM" to fmtPe(b?.pe)
                ),
                color
            )
            // 列3
            DetailCol(
                Modifier.weight(1f),
                listOf(
                    "量比" to fmtNum2(b?.volRatio),
                    "换" to fmtPct(b?.turnover),
                    "额" to fmtAmt(b?.amount)
                ),
                color
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

/** 一竖列：3 行「标签 值」，标签灰、值随涨跌色 */
@Composable
private fun DetailCol(modifier: Modifier, rows: List<Pair<String, String>>, valueColor: Color) {
    Column(modifier = modifier) {
        rows.forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TEXT_SUB, fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    value,
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = DSpNumFont,
                    maxLines = 1
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
                    color = if (active) Color(Config.COLOR_PRIMARY) else TEXT_MAIN,
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

// ============================================================
// 分时图 + 右侧盘口栏（对标同花顺）
//   左：上=价格曲线（含昨收基准线 + 均价线），下=分时量
//   右：上=五档盘口（卖5→卖1 / 买1→买5），下=逐笔成交明细
// ============================================================
@Composable
private fun MinuteChart(d: StockDetail?) {
    val trends = d?.trends ?: emptyList()
    val preClose = d?.base?.preClose ?: 0.0

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

    // 外层左右分栏：左侧分时图（62），右侧盘口栏（38）
    Row(
        modifier = Modifier.fillMaxSize().padding(top = 6.dp)
    ) {
        // ================= 左侧：分时图 =================
        Column(
            modifier = Modifier
                .weight(MINUTE_CHART_RATIO)
                .fillMaxHeight()
        ) {
            // 上部价格图（占 70%）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .padding(end = 40.dp)
            ) {
                MinutePriceCanvas(trends, preClose)
                // 右侧价格刻度（最高/中/最低）
                PriceAxis(
                    maxV = trends.maxOf { it.price },
                    minV = trends.minOf { it.price },
                    midV = preClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(40.dp)
                        .fillMaxHeight()
                )
            }
            // 下部量图（占 30%）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .padding(end = 40.dp, top = 4.dp)
            ) {
                MinuteVolumeCanvas(trends)
            }
            // 时间轴
            TimeAxis()
        }

        // ================= 右侧：五档盘口 + 成交明细 =================
        OrderBookPanel(
            ob = ob,
            preClose = preClose,
            modifier = Modifier
                .weight(1f - MINUTE_CHART_RATIO)
                .fillMaxHeight()
                .padding(start = 4.dp, end = 2.dp)
        )
    }
}

// ============================================================
// 布局比例常量（便于微调）
// ============================================================
private const val MINUTE_CHART_RATIO = 0.62f   // 左侧分时图宽度占比
private val ORDERBOOK_TOP_HEIGHT = 168.dp      // 右侧五档区高度
private val ORDERBOOK_FONT = 11.sp             // 五档/明细字号

// ============================================================
// 右侧盘口栏：上=五档（卖5→卖1，买1→买5），下=逐笔成交明细
// ============================================================
@Composable
private fun OrderBookPanel(ob: OrderBook?, preClose: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // ---------- 五档盘口 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ORDERBOOK_TOP_HEIGHT)
        ) {
            val asks = ob?.asks ?: emptyList()
            val bids = ob?.bids ?: emptyList()
            val pc = if ((ob?.prevClose ?: 0.0) > 0) ob!!.prevClose else preClose
            // 卖5 → 卖1（列表下标 4 → 0，倒序）
            for (i in 4 downTo 0) {
                OrderLevelRow(
                    label = "卖" + (i + 1),
                    lv = asks.getOrNull(i),
                    preClose = pc,
                    isAsk = true
                )
            }
            // 买1 → 买5（正序）
            for (i in 0 until 5) {
                OrderLevelRow(
                    label = "买" + (i + 1),
                    lv = bids.getOrNull(i),
                    preClose = pc,
                    isAsk = false
                )
            }
        }
        // 分隔线
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
        // 明细表头
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("时间", color = TEXT_SUB, fontSize = 9.sp,
                modifier = Modifier.weight(1.15f), textAlign = TextAlign.Start)
            Text("价格", color = TEXT_SUB, fontSize = 9.sp,
                modifier = Modifier.weight(0.85f), textAlign = TextAlign.End)
            Text("手数", color = TEXT_SUB, fontSize = 9.sp,
                modifier = Modifier.weight(0.85f), textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
        // ---------- 逐笔成交明细（最新在上，可滚动） ----------
        val deals = ob?.deals ?: emptyList()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            deals.forEach { d ->
                DealRow(d, pc = if ((ob?.prevClose ?: 0.0) > 0) ob!!.prevClose else preClose)
            }
        }
    }
}

/** 五档单行：左标签 + 中价格 + 右手数 */
@Composable
private fun OrderLevelRow(label: String, lv: OrderLevel?, preClose: Double, isAsk: Boolean) {
    val price = lv?.price ?: 0.0
    val vol = lv?.vol ?: 0.0
    val priceColor = when {
        price <= 0 -> TEXT_SUB
        price > preClose -> RED_UP
        price < preClose -> GREEN_TEXT
        else -> TEXT_MAIN
    }
    val labelColor = if (isAsk) GREEN_TEXT else RED_UP
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ORDERBOOK_TOP_HEIGHT / 10)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = labelColor, fontSize = ORDERBOOK_FONT,
            fontFamily = DSpNumFont,
            modifier = Modifier.width(26.dp), textAlign = TextAlign.Start
        )
        Text(
            if (price > 0) fmtNum2(price) else "--",
            color = priceColor, fontSize = ORDERBOOK_FONT,
            fontFamily = DSpNumFont, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
        Text(
            if (vol > 0) fmtVol(vol) else "--",
            color = TEXT_MAIN, fontSize = ORDERBOOK_FONT,
            fontFamily = DSpNumFont,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

/** 逐笔成交单行：时间 / 价格 / 手数 */
@Composable
private fun DealRow(d: DealItem, pc: Double) {
    val color = when {
        d.price > pc -> RED_UP
        d.price < pc -> GREEN_TEXT
        else -> GRAY_FLAT
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            d.time, color = TEXT_SUB, fontSize = 10.sp,
            fontFamily = DSpNumFont,
            modifier = Modifier.weight(1.15f), textAlign = TextAlign.Start
        )
        Text(
            fmtNum2(d.price), color = color, fontSize = 10.sp,
            fontFamily = DSpNumFont,
            modifier = Modifier.weight(0.85f), textAlign = TextAlign.End
        )
        Text(
            fmtVol(d.vol), color = color, fontSize = 10.sp,
            fontFamily = DSpNumFont,
            modifier = Modifier.weight(0.85f), textAlign = TextAlign.End
        )
    }
}

/** 手数格式化：>=1万 显示为 x.xx万 */
private fun fmtVol(v: Double): String {
    return if (v >= 10000) String.format("%.2f万", v / 10000.0) else v.toLong().toString()
}

@Composable
private fun MinutePriceCanvas(trends: List<DetailTrend>, preClose: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (trends.size < 2) return@Canvas

        // 价格范围（含昨收，保证基准线在中间）
        var maxP = trends.maxOf { it.price }
        var minP = trends.minOf { it.price }
        if (preClose > 0) {
            maxP = max(maxP, preClose)
            minP = min(minP, preClose)
        }
        if (maxP - minP < 1e-6) {
            maxP += 0.01
            minP -= 0.01
        }
        // 上下各留 6% 空间
        val range = maxP - minP
        maxP += range * 0.06
        minP -= range * 0.06
        val span = maxP - minP

        fun yOf(p: Double): Float = (h - ((p - minP) / span) * h).toFloat()
        // x：按 241 个点均分（A股分时固定 241 点），保证时间轴对齐
        val n = max(trends.size - 1, 1)
        fun xOf(i: Int): Float = (i.toFloat() / n) * w

        // 网格横线（3 条）
        for (k in 1..3) {
            val y = h * k / 4f
            drawLine(LINE_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        // 中轴竖线
        drawLine(LINE_GRID, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1f)

        // 昨收基准线（灰虚线效果：实线灰）
        if (preClose > 0) {
            val yc = yOf(preClose)
            drawLine(Color(0xFFCCCCCC), Offset(0f, yc), Offset(w, yc), strokeWidth = 1.5f)
        }

        // 价格曲线颜色：相对昨收
        val last = trends.last().price
        val lineColor = when {
            preClose <= 0 -> RED_UP
            last > preClose -> RED_UP
            last < preClose -> GREEN_TEXT
            else -> GRAY_FLAT
        }

        // 曲线路径
        val path = Path()
        trends.forEachIndexed { i, t ->
            val x = xOf(i)
            val y = yOf(t.price)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.2f))

        // 曲线下方淡色渐变填充
        val fill = Path().apply {
            moveTo(0f, h)
            trends.forEachIndexed { i, t -> lineTo(xOf(i), yOf(t.price)) }
            lineTo(xOf(trends.size - 1), h)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0.02f))
            )
        )

        // ===== 均价线（黄色，随累计均价）=====
        val avgPath = Path()
        var started = false
        trends.forEachIndexed { i, t ->
            val a = t.avg
            if (a <= 0) return@forEachIndexed
            val x = xOf(i)
            val y = yOf(a)
            if (!started) { avgPath.moveTo(x, y); started = true } else avgPath.lineTo(x, y)
        }
        if (started) {
            drawPath(avgPath, Color(0xFFFFB300), style = Stroke(width = 1.8f))
        }
    }
}

@Composable
private fun MinuteVolumeCanvas(trends: List<DetailTrend>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (trends.isEmpty()) return@Canvas
        val maxV = trends.maxOf { it.vol }.coerceAtLeast(1e-6)
        val n = trends.size
        val bw = w / n
        trends.forEachIndexed { i, t ->
            val vh = (t.vol / maxV * h).toFloat()
            drawRect(
                color = Color(0xFF9AA0A6),
                topLeft = Offset(i * bw, h - vh),
                size = Size(max(bw - 0.6f, 0.6f), vh)
            )
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

/** 时间轴：09:30 / 11:30 / 15:00 */
@Composable
private fun TimeAxis() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 46.dp, top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("09:30", color = TEXT_SUB, fontSize = 10.sp)
        Text("11:30", color = TEXT_SUB, fontSize = 10.sp)
        Text("15:00", color = TEXT_SUB, fontSize = 10.sp)
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
    onSelect: (Int?) -> Unit = {}
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

    // 数据变化（切股票/换周期）时重置窗口
    LaunchedEffect(d?.code, line.size) {
        visible = K_DEFAULT_VISIBLE.coerceIn(K_MIN_VISIBLE, line.size.coerceAtLeast(1))
        rightIndex = line.lastIndex
    }


    // 取当前窗口数据
    val n = visible.coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE).coerceAtMost(line.size)
    val end = rightIndex.coerceIn((n - 1).coerceAtMost(line.lastIndex), line.lastIndex)
    val start = (end - n + 1).coerceAtLeast(0)
    val show = line.subList(start, end + 1)

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
                maList = maPeriods.map { p -> computeMA(line, p).copyOfRange(start, end + 1) }.toTypedArray(),
                maPeriods = maPeriods,
                selLocal = selIdx?.let { it - start }?.takeIf { it in show.indices },
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
                // 底部留出叠层按钮行的高度，避免蜡烛图被半透明条压住；右侧让出筹码峰宽度
                modifier = Modifier.fillMaxSize().padding(bottom = 26.dp, end = chipW)
            )
            PriceAxis(
                maxV = show.maxOf { it.high },
                minV = show.minOf { it.low },
                midV = (show.maxOf { it.high } + show.minOf { it.low }) / 2,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(top = 2.dp, end = chipW)
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
                        .padding(bottom = 26.dp)
                )
            }
            // ===== 缩放 / 平移圆形按钮：浮在主图（分时/K线主图）底部 =====
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(end = 2.dp, bottom = 4.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(show.first().date, color = TEXT_SUB, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                KLineButton(text = "−") {
                    visible = (visible * 1f * K_BTN_ZOOM_STEP).toInt()
                        .coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE)
                        .coerceAtMost(line.size.coerceAtLeast(K_MIN_VISIBLE))
                    rightIndex = rightIndex.coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                }
                KLineButton(text = "＋") {
                    visible = (visible / K_BTN_ZOOM_STEP).toInt()
                        .coerceIn(K_MIN_VISIBLE, K_MAX_VISIBLE)
                        .coerceAtMost(line.size.coerceAtLeast(K_MIN_VISIBLE))
                    rightIndex = rightIndex.coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                }
                KLineButton(text = "＜") {
                    val nr = (rightIndex - 1).coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                    rightIndex = nr
                    // 若已选中，跟随移动选中框
                    selIdx?.let { onSelect(it.coerceIn(startOf(nr, visible, line.size), nr)) }
                }
                KLineButton(text = "＞") {
                    val nr = (rightIndex + 1).coerceIn((visible - 1).coerceAtMost(line.lastIndex), line.lastIndex)
                    rightIndex = nr
                    selIdx?.let { onSelect(it.coerceIn(startOf(nr, visible, line.size), nr)) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    selIdx?.takeIf { it in line.indices }?.let { line[it].date } ?: show.last().date,
                    color = if (selIdx != null) Color(Config.COLOR_PRIMARY) else TEXT_SUB,
                    fontSize = 10.sp
                )
            }
        }
        // ===== 副图1（默认成交量；点标签框弹层选 / 点空白循环切下一个）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .padding(start = 2.dp, end = 2.dp, top = 3.dp)
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
                else -> SubPlaceholderCanvas(text = currentSub.label)
            }
            // ===== 首行：指标名标签框 + 紧跟其后的数值（不重叠）=====
            SubLabelRow(
                main = if (currentSub == SubIndicator.MACD) currentSub.tag else currentSub.label,
                showMacd = currentSub == SubIndicator.MACD,
                labIdx = subLocal,
                macdShow = macdShow,
                onTag = { subPickerOpen = true }
            )
        }
        // ===== 副图2（默认 MACD；独立可切）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .padding(start = 2.dp, end = 2.dp, top = 3.dp)
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
                else -> SubPlaceholderCanvas(text = currentSub2.label)
            }
            SubLabelRow(
                main = if (currentSub2 == SubIndicator.MACD) currentSub2.tag else currentSub2.label,
                showMacd = currentSub2 == SubIndicator.MACD,
                labIdx = subLocal2,
                macdShow = macdShow,
                onTag = { subPickerOpen2 = true }
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
private fun KLineButton(text: String, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 用 rememberUpdatedState 保证 pointerInput 内始终调用最新 lambda（避免闭包捕获旧状态）
    val curOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(30.dp)
            // 圆形灰底半透明：0x66111111 ≈ 40% 深灰
            .background(Color(0x66111111), shape = CircleShape)
            .pointerInput(text) {
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
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
    maList: Array<DoubleArray> = emptyArray(),
    maPeriods: List<Int> = MA_DEFAULT_PERIODS,
    selLocal: Int? = null,
    onPick: (Int) -> Unit = {},
    onZoom: (Float) -> Unit = {},
    onPan: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var lastSlot by remember { mutableFloatStateOf(0f) }
    val curPick by rememberUpdatedState(onPick)
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
            .pointerInput(Unit) {
                // 点选柱子（单击）
                detectTapGestures(
                    onTap = { off ->
                        if (lastSlot > 0f && line.isNotEmpty()) {
                            val i = (off.x / lastSlot).toInt().coerceIn(0, line.size - 1)
                            curPick(i)
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        if (line.isEmpty()) return@Canvas
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
        // 缩放到最小（一屏根数达到约1400+，红绿已无法分辨）时，蜡烛+均线统一黑色，
        // 使全历史数据整体看起来像一条黑色波浪线（同花顺观感）。
        val tooNarrow = n >= K_NARROW_VISIBLE

        line.forEachIndexed { i, k ->
            val cx = i * slot + slot / 2
            val up = k.close >= k.open
            val c = if (tooNarrow) K_NARROW_COLOR else if (up) RED_UP else GREEN_DOWN
            val yHigh = yOf(k.high)
            val yLow = yOf(k.low)
            val yO = yOf(k.open)
            val yC = yOf(k.close)
            val yTop = min(yO, yC)
            val yBot = max(yO, yC)
            val bh = max(abs(yC - yO), 1.2f)
            val sw = 3.2f   // 影线线宽
            val barSw = 4.6f // 涨柱（空心）描边线宽（加粗，同花顺观感）
            // 影线：只画上下两段，不穿过实体中心（同花顺观感）
            if (yHigh < yTop) drawLine(c, Offset(cx, yHigh), Offset(cx, yTop), strokeWidth = sw)
            if (yLow > yBot) drawLine(c, Offset(cx, yBot), Offset(cx, yLow), strokeWidth = sw)
            // 实体：涨=空心（描边）、跌=实心（同花顺风格）；缩到最小时统一实心黑色
            drawRect(
                color = c,
                topLeft = Offset(cx - bw / 2, yTop),
                size = Size(bw, bh),
                style = if (up && !tooNarrow) Stroke(width = barSw) else Fill
            )
        }

        // ===== 均线（MA）：叠在蜡烛之上，每条一种颜色 =====
        maList.forEachIndexed { mi, ma ->
            if (ma.isEmpty()) return@forEachIndexed
            val color = if (tooNarrow) K_NARROW_COLOR
                else maColorOf(maPeriods.getOrElse(mi) { MA_DEFAULT_PERIODS.getOrElse(mi) { 5 } })
            val pathMA = Path()
            var started = false
            ma.forEachIndexed { i, v ->
                if (v.isNaN()) return@forEachIndexed
                val x = i * slot + slot / 2
                val y = yOf(v)
                if (!started) { pathMA.moveTo(x, y); started = true } else pathMA.lineTo(x, y)
            }
            if (started) drawPath(pathMA, color, style = Stroke(width = 2.2f))
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
    }
}

@Composable
private fun KLineVolumeCanvas(line: List<KLinePoint>, selLocal: Int? = null) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (line.isEmpty()) return@Canvas
        val maxV = line.maxOf { it.vol }.coerceAtLeast(1e-6)
        val n = line.size
        val slot = w / n
        val bw = max(slot * 0.92f, 1f)
        // 缩放到最小（一屏约1400+根）时量柱统一改黑，和主图黑波浪保持一致。
        val tooNarrow = n >= K_NARROW_VISIBLE
        line.forEachIndexed { i, k ->
            val cx = i * slot + slot / 2
            val up = k.close >= k.open
            val vh = (k.vol / maxV * h).toFloat()
            // 同花顺风格：涨=红空心（描边）、跌=绿实心（填充），与蜡烛配色一致；缩到最小时统一实心黑色
            drawRect(
                color = if (tooNarrow) K_NARROW_COLOR else if (up) RED_UP else GREEN_DOWN,
                topLeft = Offset(cx - bw / 2, h - vh),
                size = Size(bw, vh),
                style = if (up && !tooNarrow) Stroke(width = 3.0f) else Fill
            )
        }
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
    onTag: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 2.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubIndicatorTag(text = main) { onTag() }
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

/** 百分比：7.50% */
private fun fmtPct(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f%%", v)
}

/** 市值/流通（亿）：109.93 */
private fun fmtCap(v: Double?): String {
    if (v == null) return "--"
    return String.format(java.util.Locale.CHINA, "%.2f", v)
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
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1) 概念按钮（动态：概念名 + 涨跌幅）
        val cName = concept?.name ?: "概念"
        val pct = concept?.pct
        val pctColor = when {
            pct == null -> GRAY_FLAT
            pct > 0 -> RED_UP
            pct < 0 -> GREEN_TEXT
            else -> GRAY_FLAT
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
            Text(
                text = cName,
                color = TEXT_MAIN,
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
        BottomAction("下单", Modifier.weight(1f), onOrder)
        // 3) 诊股
        BottomAction("诊股", Modifier.weight(1f), onDiagnose)
        // 4) 自选 / 删自选
        val wTxt = when (watched) {
            true -> "删自选"
            false -> "加自选"
            else -> "自选"
        }
        BottomAction(wTxt, Modifier.weight(1f), onToggleWatch)
        // 5) 功能
        BottomAction("功能", Modifier.weight(1f), onMore)
    }
}

@Composable
private fun BottomAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TEXT_MAIN, fontSize = 13.sp, maxLines = 1)
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
