package com.chaogujia.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
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

/** 涨跌颜色：>=0 红，<0 绿 */
private val COLOR_UP = Color(0xFFF5222D)    // 红（涨）——更鲜红
private val COLOR_DOWN = Color(0xFF00B050)  // 绿（跌）——翠绿（同花顺）
private val COLOR_FLAT = Color(0xFF000000)  // 平——纯黑
private val COLOR_TEXT = Color(0xFF222222)
private val COLOR_SUB = Color(0xFF888888)

private fun colorOf(pct: Double): Color =
    if (pct > 0.000001) COLOR_UP else if (pct < -0.000001) COLOR_DOWN else COLOR_FLAT

/** 行情数字字体开关（已定稿 = Lato）：
 *  - 选 Lato 的原因：6/9 尾巴长度最接近同花顺（经 4 批对比页实机挑选）
 *  - 想换别的：改成 FontFamily(Font(R.font.xxx)) 即可
 */
// 行情数字字体（同花顺 THS 金融体，从「趣股道」APK 扒出）
// 想换别的：改动下面的 R.font.xxx 即可，或换 ths_money / din_medium
private val NumFont = FontFamily(Font(R.font.ths_regular))

@Composable
fun IndexBar(token: String? = null, onOpenNews: () -> Unit = {}) {
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

    // ===== 自选分组标签 =====
    // 当前选中的分组 key：负数为内置组（-1 自选股 / -2 持仓股），>=0 为用户自建组 id
    var selectedGroup by remember { mutableStateOf(-1) }
    // 用户自建分组（来自后端 watch_group.php）
    var userGroups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    // 当前分组下的股票列表
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    // 列表是否加载中
    var loadingStocks by remember { mutableStateOf(false) }

    // ===== 股票列表「列配置」=====
    // 当前显示哪些统计列（默认：最新价 + 涨跌幅；以后可由用户自定义）
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var colKeys by remember { mutableStateOf(WatchColumnPrefs.load(ctx)) }
    val activeCols = remember(colKeys) { WatchColumnRegistry.resolve(colKeys) }

    // 点列表行进详情页：记录当前看的是第几只（null = 没进详情页）
    var detailIdx by remember { mutableStateOf<Int?>(null) }

    // 拉一次分组列表
    LaunchedEffect(token) {
        try {
            val g = withContext(Dispatchers.IO) { Api.watchGroups(token) }
            if (g.isNotEmpty()) userGroups = g.sortedBy { it.sort }
        } catch (e: Exception) { /* 忽略 */ }
    }
    // 切换分组时拉该组股票（自选股=-1 或自建组 id；持仓股=-2 后续接）
    LaunchedEffect(selectedGroup, token) {
        loadingStocks = true
        stocks = emptyList()
        try {
            val gid = if (selectedGroup >= 0) selectedGroup else -1
            if (selectedGroup != -2) {
                val l = withContext(Dispatchers.IO) { Api.stockList(token, gid) }
                stocks = l
            }
        } catch (e: Exception) { /* 忽略 */ }
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
// 顶部红条高度由内容自适应，面板紧贴其底部，无需硬编码
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {

        // ---------- 详情页分支：进了详情页就整屏显示，返回时退出 ----------
        val openIdx = detailIdx
        if (openIdx != null && openIdx in stocks.indices) {
            StockDetailPage(
                token = token.orEmpty(),
                list = stocks,
                index = openIdx,
                onClose = { detailIdx = null },
                onOpenSearch = { detailIdx = null }
            )
            return
        }


        // ---------- 主列：红条 + 分割线 +（展开时）面板 +（展开时）关闭遮罩 ----------
        Column(Modifier.fillMaxWidth()) {

            // ================= 顶部两排版工具条（红条）=================
            TopBar(
                quote = quote,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onOpenNews = onOpenNews
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
                // 表头：Box 叠放 —— 下层是可横滑的统计列（铺满整宽），上层是固定列（盖在最上面）
                // 往左滑时，统计列会从左往右依次钻到固定列底下被遮住
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color.White)
                ) {
                    // 下层：统计列（从固定列右缘起铺，随列表同步横滑；表头本身不响应手指拖动）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = WATCH_FIXED_W.dp)
                            .horizontalScroll(hScroll, enabled = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in activeCols) {
                            Text(
                                text = col.title,
                                color = COLOR_SUB,
                                fontSize = 12.sp,
                                modifier = Modifier.width(col.width.dp).padding(horizontal = 10.dp),
                                textAlign = if (col.key == "pctPrice") TextAlign.End else TextAlign.Center
                            )
                        }
                    }
                    // 中层：「多股同列」入口 —— 紧贴固定列右缘，占满第一列右边所有宽度（白底盖住统计列）
                    // 默认只看到它；下面列表左滑时统计列会从它底下钻出
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = WATCH_FIXED_W.dp)
                            .width(WATCH_COLS_ENTRY_W.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showColumns = true }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    // 「涨幅」列标题 —— 紧接「多股同列」右侧，独立成列，右对齐
                    Text(
                        text = "涨幅",
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(WATCH_PCT_COL_W.dp)
                            .padding(end = 10.dp)
                    )
                    // 上层：固定列「名称 / 代码」，带白底，盖住滑过来的统计列
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(WATCH_FIXED_W.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        Text(
                            text = "名称 / 代码",
                            color = COLOR_SUB,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                        )
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
                            StockRow(
                                s = s,
                                cols = activeCols,
                                hScroll = hScroll,
                                onOpen = {
                                    detailIdx = stocks.indexOfFirst { it.code == s.code }
                                }
                            )
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
    }
}

/* ---------------- 顶部两排版 ---------------- */

@Composable
private fun TopBar(quote: IndexQuote?, expanded: Boolean, onToggle: () -> Unit, onOpenNews: () -> Unit) {
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
        TopActionButton(R.drawable.btn_fund, "资金", enabled = false)
        TopActionButton(R.drawable.btn_news, "资讯", enabled = true) { onOpenNews() }
        TopActionButton(R.drawable.btn_asset, "资产", enabled = false)
        TopActionButton(R.drawable.btn_analysis, "分析", enabled = false)
    }
}

/** 顶部功能按钮：上图标 + 下文字。enabled=false 时暂不绑定功能（点了没反应） */
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
internal val WATCH_FIXED_W = 110

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
    onOpen: () -> Unit
) {
    val c = colorOf(s.changePct)
    Column(Modifier.fillMaxWidth()) {
        // Box 叠放：下层是可横滑的统计列（铺满整宽），上层是固定列（白底，盖在最上面）
        // 往左滑时，统计列会从左往右依次钻到固定列底下被遮住
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            // 下层：统计列（从固定列右缘起铺，与表头同步横滑）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = WATCH_FIXED_W.dp)
                    .horizontalScroll(hScroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in cols) {
                    val txt = col.value(s)
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
            // 上层：固定列「名称 + 代码」，带白底（点击进详情页），盖住滑过来的统计列
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(WATCH_FIXED_W.dp)
                    .fillMaxHeight()
                    .background(Color.White)
                    .clickable { onOpen() }
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = s.name,
                    color = COLOR_TEXT,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = s.code,
                    color = COLOR_SUB,
                    fontSize = 12.sp,
                    fontFamily = NumFont
                )
            }
            // 上层：最右「涨幅」固定列 —— 上行涨跌幅（长方形微圆角色块底+白字），下行最新价（灰），整体贴右、垂直居中
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(WATCH_PCT_COL_W.dp)
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
                            .background(colorOf(s.changePct)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            // 符号规则：涨（红）不显示「+」，跌（绿）显示「-」，平盘不带符号
                            text = if (s.changePct < -0.000001)
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
                        text = String.format(Locale.CHINA, "%.2f", s.price),
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        fontFamily = NumFont,
                        maxLines = 1
                    )
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

/** 成交额：>=1万亿 显示「X.XX万亿」，>=1亿 显示「X亿」，否则「X万」 */
private fun fmtAmount(v: Double): String {
    if (v <= 0.0) return "--"
    return when {
        v >= 1e12 -> String.format(Locale.CHINA, "%.2f万亿", v / 1e12)
        v >= 1e8 -> String.format(Locale.CHINA, "%.0f亿", v / 1e8)
        v >= 1e4 -> String.format(Locale.CHINA, "%.0f万", v / 1e4)
        else -> String.format(Locale.CHINA, "%.0f", v)
    }
}

/* ======================= 数字字体对比页（临时调试，挑完删除） ======================= */
/*
 * 来源：从「趣股道」APK 扒出的同花顺字体。
 *   ths_money   = THS Money font Medium（同花顺金额字体，纯数字+符号）
 *   ths_regular = THS（同花顺金融体）
 *   din_medium  = DIN
 * 数字重点看 0-9 的形态，尤其 6/9 的尾巴。
 */

private data class NumCand(val label: String, val font: FontFamily?)

private val NUM_CANDS: List<NumCand> = listOf(
    NumCand("系统默认（参考）", null),
    NumCand("THS金额体 ths_money", FontFamily(Font(R.font.ths_money))),
    NumCand("THS金融体 ths_regular", FontFamily(Font(R.font.ths_regular))),
    NumCand("THS金融体 ths_medium", FontFamily(Font(R.font.ths_medium))),
    NumCand("THS金融体 ths_bold", FontFamily(Font(R.font.ths_bold))),
    NumCand("DIN din_medium", FontFamily(Font(R.font.din_medium))),
)

/** 下跌绿候选：label + 颜色。挑一个最顺眼的，告诉我 label 前字母即可 */
private data class ColorCand(val label: String, val color: Color)
private val DOWN_CANDS = listOf(
    ColorCand("① 现状 2E7D32（偏黑）", Color(0xFF2E7D32)),
    ColorCand("② 00A05A（清新绿）", Color(0xFF00A05A)),
    ColorCand("③ 00B050（翠绿·同花顺）", Color(0xFF00B050)),
    ColorCand("④ 00C853（亮饱和绿）", Color(0xFF00C853)),
    ColorCand("⑤ 3CB371（柔和中绿）", Color(0xFF3CB371)),
    ColorCand("⑥ 1DB954（清新流行绿）", Color(0xFF1DB954)),
)

@Composable
fun FontComparePage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("数字字体对比（同花顺扒出）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("重点看 6 和 9 的尾巴方向。哪套最像同花顺，就选哪套",
                fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
        }
        items(NUM_CANDS) { c ->
            val fam = c.font ?: FontFamily.Default
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(3.dp))
                // 大号数字（看 6/9 形态）
                Text(
                    text = "6  9",
                    fontSize = 40.sp,
                    color = Color(0xFFF5222D),
                    fontFamily = fam
                )
                // 全部数字
                Text(
                    text = "0123456789",
                    fontSize = 26.sp,
                    color = Color(0xFF222222),
                    fontFamily = fam
                )
                // 实际行情串
                Text(
                    text = "3952.30  +0.35%",
                    fontSize = 22.sp,
                    color = Color(0xFFF5222D),
                    fontFamily = fam
                )
                HorizontalDivider(Modifier.padding(top = 6.dp), color = Color(0xFFEEEEEE))
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFE8E8E8))
            Spacer(Modifier.height(10.dp))
            Text("↓ 下跌绿色对比（挑一个告诉我编号）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("看哪个最像同花顺的跌绿、又不偏黑。用同花顺金融体渲染。",
                fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
        }
        items(DOWN_CANDS) { c ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 左侧色块
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(c.color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                }
                Spacer(Modifier.height(3.dp))
                // 用该颜色 + 金融字体渲染真实行情串（跌）
                Text(
                    text = "3963.73  -12.85  -0.32%",
                    fontSize = 24.sp,
                    color = c.color,
                    fontFamily = FontFamily(Font(R.font.ths_regular))
                )
                Text(
                    text = "深证成指  9876.54  -1.28%",
                    fontSize = 15.sp,
                    color = c.color,
                    fontFamily = FontFamily(Font(R.font.ths_regular))
                )
                HorizontalDivider(Modifier.padding(top = 6.dp), color = Color(0xFFEEEEEE))
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("说明：THS 字体是从「趣股道」APK 里扒出来的，字符集只有数字和符号（就是当金额字体用的）。",
                fontSize = 11.sp, color = Color(0xFF888888))
        }
    }
}
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
                // 拉取每只股票的分时走势（逐只请求，失败跳过）
                val m = HashMap<String, Pair<List<DetailTrend>, Double>>()
                for (s in list) {
                    try {
                        val d = withContext(Dispatchers.IO) { Api.detail(s.code) }
                        if (d != null && d.trends.isNotEmpty()) {
                            m[s.code] = d.trends to d.base.preClose
                        }
                    } catch (e: Exception) { }
                }
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
                    .background(Color(0xFFD32F2F)).padding(horizontal = 8.dp),
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
                                    if (on) Color(0xFFD32F2F) else Color(0xFFF2F2F2),
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
                                    if (on) Color(0xFFD32F2F) else Color(0xFFF2F2F2),
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
                            Text(s.name, color = Color(0xFF111111), fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(s.code, color = Color(0xFF888888), fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            // 分时走势：从左向右逐点延伸的折线（涨红跌绿 + 昨收基准线）
                            val tr = trendMap[s.code]
                            MiniMinuteChart(
                                trends = tr?.first ?: emptyList(),
                                preClose = tr?.second ?: 0.0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = String.format(java.util.Locale.CHINA, "%.2f", s.price),
                                color = colorOf(s.changePct), fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, fontFamily = NumFont
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = String.format(java.util.Locale.CHINA, "%+.2f  %+.2f%%", s.change, s.changePct),
                                color = colorOf(s.changePct), fontSize = 12.sp, fontFamily = NumFont
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        if (trends.size < 2) return@Canvas

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

        val total = 240f
        fun xOf(i: Int): Float = ((i.toFloat() / total).coerceIn(0f, 1f)) * w

        val last = trends.last().price
        // 线色：相对昨收 涨红 / 跌绿 / 平灰；竖向渐变——上端深、下端浅
        val lineBrush = when {
            last > preClose -> Brush.verticalGradient(
                colors = listOf(Color(0xFFE64A3B), Color(0x40E64A3B))
            )
            last < preClose -> Brush.verticalGradient(
                colors = listOf(Color(0xFF19A05A), Color(0x4019A05A))
            )
            else -> Brush.verticalGradient(
                colors = listOf(Color(0xFF9E9E9E), Color(0x409E9E9E))
            )
        }

        val path = Path()
        trends.forEachIndexed { i, p ->
            val x = xOf(i)
            val y = yOf(p.price)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, brush = lineBrush, style = Stroke(width = 1.6f))
    }
}