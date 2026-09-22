package com.chaogujia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ===== 字体（与搜索页一致：同花顺数字字体） =====
private val DSpNumFont = FontFamily(Font(R.font.ths_regular))

// ===== 颜色 =====
private val RED_UP = Color(0xFFE64A3B)       // 涨：红
private val GREEN_DOWN = Color(0xFF19A05A)   // 跌：绿
private val GRAY_FLAT = Color(0xFF888888)    // 平：灰
private val TEXT_MAIN = Color(0xFF222222)
private val TEXT_SUB = Color(0xFF888888)
private val LINE_GRID = Color(0xFFEEEEEE)
private val LINE_AXIS = Color(0xFFF0F0F0)
private val TICK_RED = Color(0x33E64A3B)     // 蜡烛红（半透明填充）
private val TICK_GREEN = Color(0x3319A05A)   // 蜡烛绿（半透明填充）

/**
 * 个股详情页
 *
 * 入口：搜索结果行点击进入（携带整条列表，支持左右切换上一只/下一只）。
 *
 * @param token    登录 token（用于右上角搜索页入口，可空）
 * @param list     当前列表（搜索结果/自选列表）
 * @param index    当前股票在 list 中的下标
 * @param onClose  返回回调
 * @param onOpenSearch 点右上角放大镜 → 打开搜索页
 */
@Composable
fun StockDetailPage(
    token: String,
    list: List<StockItem>,
    index: Int,
    onClose: () -> Unit,
    onOpenSearch: () -> Unit = {}
) {
    // 当前下标（可左右切换）
    var idx by remember { mutableStateOf(index.coerceIn(0, max(0, list.size - 1))) }
    val current: StockItem? = list.getOrNull(idx)

    // 当前周期：minute / day / week / month
    var period by remember { mutableStateOf("minute") }

    // 详情数据
    var detail by remember { mutableStateOf<StockDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 切换股票 或 切换周期 → 拉数据
    // 注意：分时是同一份数据（trends），K线按周期取
    LaunchedEffect(current?.code, period) {
        val code = current?.code ?: return@LaunchedEffect
        loading = true
        val k = if (period == "minute") "day" else period
        val d = withContext(Dispatchers.IO) { Api.detail(code, k, 320) }
        if (d != null) detail = d
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ================= 顶部红条 =================
        DetailHeaderBar(
            name = detail?.name?.ifEmpty { current?.name ?: "" } ?: "",
            code = current?.code ?: "",
            canPrev = idx > 0,
            canNext = idx < list.size - 1,
            onBack = onClose,
            onPrev = { if (idx > 0) idx-- },
            onNext = { if (idx < list.size - 1) idx++ },
            onOpenSearch = onOpenSearch
        )

        // ================= 头部数据区 =================
        DetailHeadData(detail)

        // ================= 标签栏 =================
        PeriodTabs(period) { period = it }

        // ================= 图表区（占满剩余） =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
        ) {
            when (period) {
                "minute" -> MinuteChart(detail)
                else -> KLineChart(detail)
            }
            if (loading && detail == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TEXT_SUB, fontSize = 14.sp)
                }
            }
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
private fun DetailHeadData(d: StockDetail?) {
    val b = d?.base
    val price = b?.price ?: 0.0
    val chg = b?.change ?: 0.0
    val pct = b?.changePct ?: 0.0
    val color = when {
        chg > 0 -> RED_UP
        chg < 0 -> GREEN_DOWN
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
                    "市盈" to fmtPe(b?.pe)
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
@Composable
private fun PeriodTabs(current: String, onPick: (String) -> Unit) {
    val tabs = listOf(
        "minute" to "分时",
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
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LINE_AXIS))
}

// ============================================================
// 分时图：上=价格曲线（含昨收基准线 + 均价线），下=分时量
// ============================================================
@Composable
private fun MinuteChart(d: StockDetail?) {
    val trends = d?.trends ?: emptyList()
    val preClose = d?.base?.preClose ?: 0.0

    if (trends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无分时数据", color = TEXT_SUB, fontSize = 14.sp)
        }
        return
    }

    // 屏幕右侧留 46dp 给价格轴
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
            MinutePriceCanvas(trends, preClose)
            // 右侧价格刻度（最高/中/最低）
            PriceAxis(
                maxV = trends.maxOf { it.price },
                minV = trends.minOf { it.price },
                midV = preClose,
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
            MinuteVolumeCanvas(trends)
        }
        // 时间轴
        TimeAxis()
    }
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
            last < preClose -> GREEN_DOWN
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
        modifier = modifier.padding(start = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
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
// K线图：上=蜡烛，下=量柱
// ============================================================
@Composable
private fun KLineChart(d: StockDetail?) {
    val line = d?.line ?: emptyList()
    if (line.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无K线数据", color = TEXT_SUB, fontSize = 14.sp)
        }
        return
    }
    // 只显示最近 90 根，保证蜡烛不太挤
    val show = if (line.size > 90) line.takeLast(90) else line

    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.72f)
                .padding(end = 46.dp)
        ) {
            KLineCanvas(show)
            PriceAxis(
                maxV = show.maxOf { it.high },
                minV = show.minOf { it.low },
                midV = (show.maxOf { it.high } + show.minOf { it.low }) / 2,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(46.dp)
                    .fillMaxHeight()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f)
                .padding(end = 46.dp, top = 4.dp)
        ) {
            KLineVolumeCanvas(show)
        }
        // 底部显示最早/最新日期
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(show.first().date, color = TEXT_SUB, fontSize = 10.sp)
            Text(show.last().date, color = TEXT_SUB, fontSize = 10.sp)
        }
    }
}

@Composable
private fun KLineCanvas(line: List<KLinePoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
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

        val n = line.size
        val slot = w / n
        val bw = min(slot * 0.7f, 12f)

        line.forEachIndexed { i, k ->
            val cx = i * slot + slot / 2
            val up = k.close >= k.open
            val c = if (up) RED_UP else GREEN_DOWN
            // 影线
            val yHigh = yOf(k.high)
            val yLow = yOf(k.low)
            drawLine(c, Offset(cx, yHigh), Offset(cx, yLow), strokeWidth = 1.4f)
            // 实体
            val yO = yOf(k.open)
            val yC = yOf(k.close)
            val yTop = min(yO, yC)
            val bh = max(abs(yC - yO), 1.2f)
            drawRect(
                color = c,
                topLeft = Offset(cx - bw / 2, yTop),
                size = Size(bw, bh)
            )
        }
    }
}

@Composable
private fun KLineVolumeCanvas(line: List<KLinePoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (line.isEmpty()) return@Canvas
        val maxV = line.maxOf { it.vol }.coerceAtLeast(1e-6)
        val n = line.size
        val slot = w / n
        val bw = min(slot * 0.7f, 12f)
        line.forEachIndexed { i, k ->
            val cx = i * slot + slot / 2
            val up = k.close >= k.open
            val vh = (k.vol / maxV * h).toFloat()
            drawRect(
                color = if (up) RED_UP.copy(alpha = 0.55f) else GREEN_DOWN.copy(alpha = 0.55f),
                topLeft = Offset(cx - bw / 2, h - vh),
                size = Size(bw, vh)
            )
        }
    }
}

// ============================================================
// 数字格式化工具
// ============================================================
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