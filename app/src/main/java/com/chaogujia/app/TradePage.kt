package com.chaogujia.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField

/**
 * 交易页（持仓）—— 第二十一轮
 * 入口：底部「交易」Tab（无 token 时由底部导航统一拦截并弹登录）。
 *
 * 页面结构（自上而下）：
 *   ① 账户类型切换栏：A股 / 基金 / 期货 / 黄金 / 模拟（红底白字，选中纯白/未选中暗白）
 *   ② 证券账户行（居中，含图标，行高稍高）
 *   ③ 分割线 → 两行两列统计（总资产/浮动盈亏/总市值/当日参考盈亏，数值在文字下方）
 *   ④ 分割线 → 一排操作按钮（买入/卖出/撤单/持仓查询）—— 本轮文字占位
 *
 * 本轮只实现 A股 子页；基金/期货/黄金/模拟 先占位。
 */
private val TP_RED = Color(0xFFE23B3B)     // 涨/盈利（红）
private val TP_BLUE = Color(0xFF2A7BFF)    // 跌/亏损（蓝）
private val TP_GRAY = Color(0xFF888888)
private val TP_BLACK = Color(0xFF222222)   // 总资产/总市值 数值色
/** 数值字体：与股价/详情页一致（同花顺金融体） */
private val TPNumFont = FontFamily(Font(R.font.ths_regular))
private fun tpMoney(v: Double): String =
    String.format(Locale.CHINA, "%,.2f", v)
/** 盈亏金额：带正负号，盈利 + ，亏损 -；为 0 时不带符号 */
private fun tpSigned(v: Double): String =
    if (kotlin.math.abs(v) < 5e-3) String.format(Locale.CHINA, "%,.2f", 0.0)
    else String.format(Locale.CHINA, "%+,.2f", v)
/** 百分比：带正负号（涨 +、跌 -）；为 0 时不带符号，显示 0.00% */
private fun tpPct(v: Double): String =
    if (kotlin.math.abs(v) < 5e-3) String.format(Locale.CHINA, "%.2f%%", 0.0)
    else String.format(Locale.CHINA, "%+.2f%%", v)
/** 盈亏文字颜色：红涨、蓝跌、平黑（对齐同花顺"平盘黑"惯例） */
private fun tpPnColor(v: Double): Color = when {
    v > 1e-9 -> TP_RED
    v < -1e-9 -> TP_BLUE
    else -> TP_BLACK
}
/** 走势图/收益率颜色：红涨蓝跌，零值归红（同花顺 0.00% 显示为红色） */
private fun tpChartColor(v: Double): Color = when {
    v < -1e-9 -> TP_BLUE
    else -> TP_RED
}

@Composable
fun TradePage(
    token: String,
    subTab: String = "A股",
    onSubTabChange: (String) -> Unit = {},
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit = { _, _ -> },
    onOpenMemberCenter: () -> Unit = {},
    onOpenVipPage: () -> Unit = {},
    onLogout: () -> Unit = {},
    perm: PermissionInfo = PermissionInfo(),
    // ★ 第 34 轮：「持仓」按钮 → 打开独立持仓页（上抛给 MainActivity）
    onOpenPosition: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ===== 子页内容（账户类型切换按钮已移入顶部红条 MemberHeaderBar） =====
        when (subTab) {
            "A股" -> ASharePage(
                token = token,
                onOpenDetail = onOpenDetail,
                onOpenMemberCenter = onOpenMemberCenter,
                onOpenVipPage = onOpenVipPage,
                onLogout = onLogout,
                perm = perm,
                onOpenPosition = onOpenPosition
            )
            else -> PlaceholderPage(subTab)
        }
    }
}

/** A股子页 */
@Composable
private fun ASharePage(
    token: String,
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit,
    onOpenMemberCenter: () -> Unit = {},
    onOpenVipPage: () -> Unit = {},
    onLogout: () -> Unit = {},
    perm: PermissionInfo = PermissionInfo(),
    // ★ 第 34 轮：「持仓」按钮 → 打开独立持仓页（上抛给 MainActivity）
    onOpenPosition: () -> Unit = {}
) {
    // ===== 交易页会员权限门禁（第18轮） =====
    // 无权限时弹出的引导标题（null=不弹）；非空即展示 MemberGateDialog
    var gateTitle by remember { mutableStateOf<String?>(null) }
    // 「用户帮助与反馈」弹窗开关（该栏不纳入权限，直接弹出意见反馈）
    var showFeedback by remember { mutableStateOf(false) }
    // 受限功能统一入口：有权限执行 action，无权限弹会员引导
    fun guard(feat: String, title: String, action: () -> Unit) {
        if (perm.has(feat)) action() else gateTitle = title
    }
    val tpCtx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf(AccountInfo()) }
    var myBrokers by remember { mutableStateOf<List<MyBroker>>(emptyList()) }
    var currentBroker by remember { mutableStateOf<MyBroker?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    // 当前选中的账户 id（无账户时为 0）
    val brokerAccountId = currentBroker?.id ?: 0

    // 账户统计（随切换账户 / 手动刷新重载；第29轮：改为每 5 秒自动刷新一次资产卡片）
    LaunchedEffect(brokerAccountId, reloadTick) {
        loading = true
        while (true) {
            try {
                account = withContext(Dispatchers.IO) { Api.account(token, brokerAccountId) }
            } catch (e: Exception) {
                // 保持上次数据
            } finally {
                loading = false
            }
            kotlinx.coroutines.delay(5000)
        }
    }
    // 券商账户列表（只拉一次）
    LaunchedEffect(token) {
        try {
            val bs = withContext(Dispatchers.IO) { Api.brokerList(token) }
            myBrokers = bs
            // 第48轮：账户联动——优先恢复全局保存的账户，而非固定取第一个
            if (currentBroker == null) currentBroker = AccountStore.pick(tpCtx, bs) { it.id }
        } catch (e: Exception) {
            // 无账户时保持空列表
        }
    }

    // 面板开关：null=无 / "buy"=买入 / "sell"=卖出 / "order"=撤单(挂单列表) / "pos"=持仓
    var panel by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ===== ② 证券账户栏（可切换）=====
        AccountBar(
            brokers = myBrokers,
            current = currentBroker,
            onSwitch = { guard("tp_account_switch", "账户切换") { currentBroker = it; AccountStore.set(tpCtx, it.id) } }
        )
        tpDivider()

        // ===== ③ 两行两列统计（会员功能 tp_assets） =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .then(
                    if (perm.has("tp_assets")) Modifier
                    else Modifier.clickable { gateTitle = "资产统计" }
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = "总资产",
                    value = if (loading) "加载中…" else tpMoney(account.total),
                    valueColor = TP_BLACK,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "浮动盈亏",
                    value = tpSigned(account.floatPn),
                    valueColor = tpPnColor(account.floatPn),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = "总市值",
                    value = tpMoney(account.marketValue),
                    valueColor = TP_BLACK,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "当日参考盈亏",
                    // R50c: 后端已提供 dayPnl 字段（= Σ 持仓量 ×(现价 - 昨收)），改用真实当日盈亏。
                    value = tpSigned(account.dayPn),
                    valueColor = tpPnColor(account.dayPn),
                    // R55h-22: 当日参考盈亏% 分母统一为月基准 monthBase(=初始资金+上月末累计盈亏)，
                    // 与「收益率」month_pct 同口径（本月第一天两者一致）。monthBase 缺失/为0时回退旧口径 marketValue。
                    pctText = if (account.monthBase > 0.0) tpPct(account.dayPn / account.monthBase * 100.0)
                              else if (account.marketValue > 0.0) tpPct(account.dayPn / account.marketValue * 100.0) else null,
                    pctColor = tpPnColor(account.dayPn),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        tpDivider()

        // ===== ④ 操作按钮排：买入 / 卖出 / 持仓 / 撤单 / 刷新 =====
        ActionBarRow(onAction = { key ->
            val feat = when (key) {
                "buy" -> "tp_buy" to "买入"
                "sell" -> "tp_sell" to "卖出"
                "pos" -> "tp_position" to "持仓"
                "order" -> "tp_cancel" to "撤单"
                "refresh" -> "tp_refresh" to "刷新"
                else -> "tp_buy" to "交易"
            }
            // ★ 第 34 轮：「持仓」不再开内嵌面板，改为上抛 → 打开独立持仓页（PositionPage）
            if (key == "pos") {
                guard(feat.first, feat.second) { onOpenPosition() }
            } else {
                guard(feat.first, feat.second) { panel = key }
            }
        })

        // 灰色间隔：露出页面灰底，把「月度参考盈亏」栏整体下移
        Spacer(Modifier.fillMaxWidth().height(8.dp))
        // ===== ④.5 月度参考盈亏（新栏，会员功能 tp_pnl） =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (perm.has("tp_pnl")) Modifier else Modifier.clickable { gateTitle = "月度参考盈亏" })
        ) {
            MonthlyPnlBar(token = token, brokerAccountId = brokerAccountId, reloadTick = reloadTick, totalAsset = account.total)
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp))
        // ===== ④.6 功能入口宫格（新栏，月度盈亏下方） =====
        MenuGrid(brokerName = currentBroker?.broker ?: "光大证券", onCell = { guard("tp_menu_grid", "功能入口") { } })
        Spacer(Modifier.fillMaxWidth().height(8.dp))
        // ===== ④.7 功能列表（6 栏 + 退出按钮，第九轮新增） =====
        MenuListSection(
            onOpenMemberCenter = onOpenMemberCenter,
            onLogout = onLogout,
            guard = { f, t, a -> guard(f, t, a) },
            onFeedback = { showFeedback = true }
        )
        Spacer(Modifier.fillMaxWidth().height(8.dp))

        // ===== ⑤ 面板区 =====
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            when (panel) {
                "buy" -> OrderPanel(
                    token = token,
                    brokerAccountId = brokerAccountId,
                    side = "buy",
                    onDone = { reloadTick++ },
                    onClose = { panel = null }
                )
                "sell" -> OrderPanel(
                    token = token,
                    brokerAccountId = brokerAccountId,
                    side = "sell",
                    onDone = { reloadTick++ },
                    onClose = { panel = null }
                )
                "order" -> OrderListPanel(
                    token = token,
                    brokerAccountId = brokerAccountId,
                    onChanged = { reloadTick++ }
                )
                "pos" -> PositionPanel(
                    token = token,
                    brokerAccountId = brokerAccountId,
                    onOpenDetail = onOpenDetail
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                }
            }
        }
    }
    // ===== 会员引导弹窗（受权限限制的功能统一在此提示） =====
    val gt = gateTitle
    if (gt != null) {
        MemberGateDialog(
            title = gt,
            onDismiss = { gateTitle = null },
            onOpen = { gateTitle = null; onOpenVipPage() }
        )
    }
    // ===== 意见反馈弹窗（「用户帮助与反馈」栏触发，复用首页意见反馈表单） =====
    if (showFeedback) {
        FeedbackDialog(token = token, onDismiss = { showFeedback = false })
    }

}

/** ③ 统计单元格：标签灰色在上，数值在下 */
@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(Config.COLOR_TEXT_MAIN),
    // 第50轮：可选百分比副值（小 3-4 号、红蓝带符号、与金额下水平对齐）
    pctText: String? = null,
    pctColor: Color = Color(Config.COLOR_TEXT_MAIN)
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TP_GRAY,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        // 金额 + 其后紧跟的小号百分比（同一行，底对齐）
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TPNumFont,
                maxLines = 1
            )
            if (pctText != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = pctText,
                    color = pctColor,
                    fontSize = 13.sp,   // 比 17sp 小 4 号
                    fontWeight = FontWeight.Medium,
                    fontFamily = TPNumFont,
                    maxLines = 1
                )
            }
        }
    }
}

/** ④ 操作按钮排：买入 / 卖出 / 持仓 / 撤单 / 刷新（图标 + 文字） */
@Composable
private fun ActionBarRow(onAction: (String) -> Unit) {
    val actions = listOf(
        "买入" to (R.drawable.ico_trade_buy to "buy"),
        "卖出" to (R.drawable.ico_trade_sell to "sell"),
        "持仓" to (R.drawable.ico_trade_position to "pos"),
        "撤单" to (R.drawable.ico_trade_cancel to "order"),
        "刷新" to (R.drawable.ico_trade_query to "refresh")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { (label, pair) ->
            val (icon, key) = pair
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onAction(key)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    color = Color(Config.COLOR_TEXT_MAIN),
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** ④.5 月度参考盈亏栏：金额 + 曲线(逐点渐现) + 收益率（三行整体纵向居中） */
@Composable
private fun MonthlyPnlBar(token: String, brokerAccountId: Int, reloadTick: Int, totalAsset: Double) {
    var hist by remember { mutableStateOf(PnlHistory()) }
    // R55h-22b: 与资产卡片同步，每 5 秒轮询一次月度历史（收益率/跑赢上证随之刷新）。
    // 仅当内容真正变化时才赋值，避免曲线动画每 5 秒无谓重播。
    LaunchedEffect(brokerAccountId, reloadTick) {
        while (true) {
            try {
                val nh = withContext(Dispatchers.IO) { Api.pnlHistory(token, brokerAccountId) }
                if (nh != hist) hist = nh
            } catch (e: Exception) {
                // 保持上次数据
            }
            kotlinx.coroutines.delay(5000)
        }
    }
    val series: List<Double> = remember(hist) {
        val base = hist.points.map { it.monthPnl }
        val t = hist.today
        if (t != null) base + t.monthPnl else base
    }
    val last = hist.today ?: hist.points.lastOrNull()
    val monthPnl = last?.monthPnl ?: 0.0
    // 第29轮修正（口径C）：月度收益率 / 月度跑赢上证 直接取后端字段，
    // 不再用 totalAsset-monthPnl 做分母（该分母会被算得极小，导致百分比爆表）
    val monthPct = hist.monthPct
    val excess = hist.monthExcess
    val nowMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    // 三行文字各自独立、贯穿卡片；走势图作为右上角独立小图叠在上层，不参与挤压文字宽度
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = nowMonth.toString() + "月参考盈亏",
                color = TP_GRAY,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tpSigned(monthPnl),
                color = tpPnColor(monthPnl),
                fontSize = 18.sp,
                fontFamily = TPNumFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            // 第3行：收益率与跑赢上证指数同一行，满宽显示不被走势图挤压
            Text(
                text = "收益率 " + tpPct(monthPct) + "  跑赢上证指数 " + tpPct(excess),
                color = TP_GRAY,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
        // 右上角独立小图（绝对定位，不占文字宽度）；始终绘制：即使本月只有 0.00 一个点，
        // 也会画出一小段贴在零轴的实线 + 贯穿的灰色虚线，端点标 0.00%
        MiniPnlChart(
            data = series,
            centerLabel = tpPct(monthPct),
            centerLabelColor = tpChartColor(monthPct),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 42.dp) // 给右侧箭头图标让位，基准线右端不顶到最右（间距在14dp基础上再增两倍）
                .size(width = 108.dp, height = 40.dp)
        )
        Image(
            painter = painterResource(R.drawable.ic_pnl_arrow),
            contentDescription = "展开",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 12.dp, height = 19.dp)
        )
    }
}
/** 迷你盈亏走势图（对标同花顺）：
 *  ① 零轴水平虚线（基准）；
 *  ② 红涨蓝跌实线，从第一个数据点起逐点绘制；
 *  ③ 线下渐变从走势线往下渐隐；
 *  ④ 末端圆点落在走势线上，跟随走势线移动。
 *
 *  R55h-22e 横轴口径修正（关键）：
 *   后端 month_pnl 是「当月月内累计盈亏」（月初≈0，逐日累加）。
 *   之前把首点硬拉最左、末点硬拉最右，导致本月仅 2 个点时
 *   画成一条从左下直冲右上、横贯整个控件的斜直线。
 *   现在改为：横轴 = 当月自然月总天数（28~31）等分，
 *   第 i 个数据点落在「本月第 (i+1) 天」的横坐标上，
 *   曲线只延伸到最后一个数据点所在处，右侧按当月剩余天数自然留空。
 *   效果：月初的点靠左、随日期推进向右长，月末才接近右端，
 *   始终不会出现"一条线直接拉到最右边"。
 */
@Composable
private fun MiniPnlChart(
    data: List<Double>,
    centerLabel: String = "",
    centerLabelColor: Color = TP_GRAY,
    modifier: Modifier = Modifier
) {
    // 仅首帧播放一次展开动画；后续数据每 5 秒刷新时不再重播，避免"刷新就跳"
    var animated by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(0) }
    LaunchedEffect(data, animated) {
        if (!animated) {
            visible = 0
            if (data.isNotEmpty()) {
                for (i in 1..data.size) {
                    kotlinx.coroutines.delay(260)
                    visible = i
                }
            }
            animated = true
        } else {
            visible = data.size
        }
    }
    val lastVal = data.lastOrNull() ?: 0.0
    val lineColor = tpChartColor(lastVal)
    // 当月自然月总天数（28~31）：横轴按"天"分格的基准
    val daysInMonth = remember {
        val c = java.util.Calendar.getInstance()
        c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    }
    BoxWithConstraints(modifier = modifier) {
        val d = LocalDensity.current.density
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pad = 4f * d
        val midY = h / 2f
        val n = visible.coerceAtMost(data.size)
        val hasData = n >= 1 && data.isNotEmpty()
        // 纵向：按数据实际 min~max 映射，充分利用上下空间（对标同花顺）
        //   单点/全等值时退化为中线；否则最低值贴底、最高值贴顶
        val dMin = data.minOrNull() ?: 0.0
        val dMax = data.maxOrNull() ?: 0.0
        val span = (dMax - dMin)
        fun yOf(v: Double): Float {
            if (span < 1e-9) return midY
            val t = ((v - dMin) / span).toFloat()
            // t=0(最低) -> 靠底；t=1(最高) -> 靠顶
            return (h - pad - t * (h - 2f * pad)).coerceIn(pad, h - pad)
        }
        // 横向（R55h-22e）：一天一格。
        //   把整个控件宽度按「本月总天数 daysInMonth」等分（首日在最左，末日才到最右），
        //   第 i 个数据点固定落在「本月第 (i+1) 天」的位置；曲线只画到最后一个数据点。
        //   -> 少点时曲线短、靠左，右侧留白；只有到月末(点数=总天数)才铺满全宽。
        val slots = (daysInMonth - 1).coerceAtLeast(1).toFloat()  // 分格数 = 总天数-1
        val dayPx = w / slots                                      // 每天占的像素宽
        val xOf: (Int) -> Float = { i -> (i * dayPx).coerceIn(0f, w) }
        val lastX = xOf(n - 1)
        val firstX = xOf(0)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 零轴水平浅灰虚线
            drawLine(
                color = TP_GRAY.copy(alpha = 0.45f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            if (hasData) {
                // 走势线：从第一个数据点开始，逐点连接（不从零轴硬拽）
                val linePath = Path()
                for (i in 0 until n) {
                    val x = xOf(i)
                    val y = yOf(data[i])
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                // 面积：走势线下方 fill 到控件底部，渐变从"走势线最高点"往下渐隐
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(lastX, h)
                    lineTo(firstX, h)
                    close()
                }
                // 线的最高点（y 最小）——渐变的浓色起点，从走势线往下渲染
                var topLineY = Float.MAX_VALUE
                for (i in 0 until n) topLineY = minOf(topLineY, yOf(data[i]))
                val areaBrush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.30f),
                        lineColor.copy(alpha = 0.02f)
                    ),
                    startY = topLineY,
                    endY = h
                )
                drawPath(path = areaPath, brush = areaBrush)
                drawPath(path = linePath, color = lineColor, style = Stroke(width = 2f))
                // 末端圆点：落在走势线末端点上（跟随走势线）
                val dotX = lastX
                val dotY = yOf(data[n - 1])
                drawCircle(color = lineColor, radius = 3.2f, center = Offset(dotX, dotY))
            }
        }
        // 百分比文字：贴在末端圆点右上方（靠近右端时自动左移，避免超出控件）
        if (centerLabel.isNotEmpty() && hasData) {
            val dotX = lastX
            val dotY = yOf(data[n - 1])
            val labelRight = dotX + 6f * d
            val offX = if (labelRight > w - 34f * d) (dotX - 34f * d) else labelRight
            val offY = (dotY - 13f * d)
            Text(
                text = centerLabel,
                color = centerLabelColor,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            )
        }
    }
}
/** 其他账户类型占位页 */
@Composable
private fun PlaceholderPage(name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$name 敬请期待",
            color = TP_GRAY,
            fontSize = 14.sp
        )
    }
}

// ==========================================================================
//  下单面板 / 挂单列表 / 持仓列表 / 五档盘口
// ==========================================================================

/** 面板标题栏：左标题 + 右「✕ 收起」 */
@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(Config.COLOR_TEXT_MAIN),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "✕ 收起",
            color = TP_GRAY,
            fontSize = 13.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
        )
    }
    tpDivider()
}

/** 下单面板
 *  方式A（限价·即时成交）：手填价格，立即按该价成交（mode=quick）
 *  方式B（到价挂单）：点五档价格 → 自动填入价格并挂单，到价成交（mode=limit） */
@Composable
private fun OrderPanel(
    token: String,
    brokerAccountId: Int,
    side: String,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    val isBuy = side == "buy"
    val sideColor = if (isBuy) TP_RED else TP_BLUE
    val sideText = if (isBuy) "买入" else "卖出"

    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("quick") }   // quick=方式A / limit=方式B
    var ob by remember { mutableStateOf<OrderBook?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgColor by remember { mutableStateOf(TP_GRAY) }

    // 五档行情轮询（有 code 时每 3 秒刷新）
    LaunchedEffect(code) {
        val c = code.trim()
        if (c.isEmpty()) {
            ob = null
            return@LaunchedEffect
        }
        while (true) {
            try {
                val r = withContext(Dispatchers.IO) { Api.orderbook(c) }
                if (r != null) ob = r
            } catch (_: Exception) {
                // 保持上次数据
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    val qty = qtyText.trim().toIntOrNull() ?: 0
    val price = priceText.trim().toDoubleOrNull() ?: 0.0
    val amount = price * qty

    Column(modifier = Modifier.fillMaxWidth()) {
        PanelHeader(title = "$sideText 股票", onClose = onClose)

        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // ---- 代码 / 名称 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("代码", color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                BasicField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    placeholder = "如 600519 / sh600519",
                    modifier = Modifier.weight(1f)
                )
            }
            tpDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("名称", color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                BasicField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "选填",
                    modifier = Modifier.weight(1f)
                )
            }
            tpDivider()

            // ---- 方式A：手填价 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioDot(selected = mode == "quick", color = sideColor) {
                    mode = "quick"
                    msg = ""
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("方式A｜手填价 · 即时成交", color = TP_BLACK, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("按你填的价格立即成交", color = TP_GRAY, fontSize = 11.sp)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("价格", color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                BasicField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    placeholder = "委托价",
                    keyboardNumber = true,
                    modifier = Modifier.weight(1f)
                )
            }
            tpDivider()

            // ---- 方式B：点五档到价 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioDot(selected = mode == "limit", color = sideColor) {
                    mode = "limit"
                    msg = ""
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("方式B｜点五档价 · 到价挂单", color = TP_BLACK, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("点下方五档价格自动填入并挂单，到价成交", color = TP_GRAY, fontSize = 11.sp)
                }
            }
            // 五档盘口（点价填框）
            FiveLevelBoard(
                ob = ob,
                redDotSide = side,
                onPickPrice = { p ->
                    priceText = String.format(Locale.CHINA, "%.2f", p)
                    mode = "limit"
                    msg = ""
                }
            )
            tpDivider()

            // ---- 数量 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数量", color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                BasicField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    placeholder = "100 的整数倍",
                    keyboardNumber = true,
                    modifier = Modifier.weight(1f)
                )
            }
            tpDivider()

            // ---- 预估金额 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("预估金额", color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    text = tpMoney(amount),
                    color = sideColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TPNumFont
                )
            }
            tpDivider()

            if (msg.isNotEmpty()) {
                Text(
                    text = msg,
                    color = msgColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- 提交按钮 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(if (busy) TP_GRAY else sideColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (busy) return@clickable
                        if (brokerAccountId <= 0) {
                            msg = "请先添加/选择证券账户"; msgColor = TP_RED; return@clickable
                        }
                        val c = code.trim()
                        if (c.isEmpty()) { msg = "请填写股票代码"; msgColor = TP_RED; return@clickable }
                        if (qty <= 0 || qty % 100 != 0) {
                            msg = "A股数量须为 100 的整数倍"; msgColor = TP_RED; return@clickable
                        }
                        if (mode == "quick" && price <= 0) {
                            msg = "方式A请填写有效价格"; msgColor = TP_RED; return@clickable
                        }
                        busy = true
                        msg = "提交中…"; msgColor = TP_GRAY
                        // 异步提交
                        kotlinx.coroutines.MainScope().launch {
                            val r: TradeResult = withContext(Dispatchers.IO) {
                                try {
                                    if (isBuy) Api.buy(token, brokerAccountId, c, name.trim(), qty, mode, price)
                                    else Api.sell(token, brokerAccountId, c, qty, mode, price)
                                } catch (e: Exception) {
                                    TradeResult(false, e.message ?: "网络异常")
                                }
                            }
                            busy = false
                            if (r.ok) {
                                if (r.mode == "limit") {
                                    msg = "挂单成功：订单号 ${r.orderId}，到价自动成交"
                                } else {
                                    msg = "${sideText}成交：${r.quantity} 股 @ ${tpMoney(r.price)}"
                                }
                                msgColor = if (isBuy) TP_RED else TP_BLUE
                                priceText = ""
                                qtyText = ""
                                onDone()
                            } else {
                                msg = "失败：${r.msg}"
                                msgColor = TP_RED
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (busy) "提交中…" else "确认$sideText",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 单选圆点 */
@Composable
private fun RadioDot(selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = if (selected) color else TP_GRAY,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 简易输入框（带下划线） */
@Composable
private fun BasicField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardNumber: Boolean = false
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TP_BLACK,
            fontSize = 15.sp,
            fontFamily = TPNumFont
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (keyboardNumber) androidx.compose.ui.text.input.KeyboardType.Decimal
            else androidx.compose.ui.text.input.KeyboardType.Text
        ),
        modifier = modifier.padding(vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(text = placeholder, color = TP_GRAY, fontSize = 14.sp)
            }
            inner()
        }
    )
}

/** 五档盘口（卖5→卖1 / 买1→买5），点价回调；redDotSide 侧显示红点（本方报价） */
@Composable
private fun FiveLevelBoard(
    ob: OrderBook?,
    redDotSide: String,
    onPickPrice: (Double) -> Unit
) {
    val asks = ob?.asks ?: emptyList()
    val bids = ob?.bids ?: emptyList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 4.dp)
    ) {
        // 卖档：卖5 → 卖1
        for (i in 4 downTo 0) {
            FiveLevelRow(
                label = "卖${i + 1}",
                lv = asks.getOrNull(i),
                isAsk = true,
                redDot = redDotSide == "sell",
                onPickPrice = onPickPrice
            )
        }
        tpDivider()
        // 买档：买1 → 买5
        for (i in 0 until 5) {
            FiveLevelRow(
                label = "买${i + 1}",
                lv = bids.getOrNull(i),
                isAsk = false,
                redDot = redDotSide == "buy",
                onPickPrice = onPickPrice
            )
        }
    }
}

/** 五档单行：标签 | 价格（点选）| 数量  */
@Composable
private fun FiveLevelRow(
    label: String,
    lv: OrderLevel?,
    isAsk: Boolean,
    redDot: Boolean,
    onPickPrice: (Double) -> Unit
) {
    val price = lv?.price ?: 0.0
    val vol = lv?.vol ?: 0.0
    val priceColor = when {
        price <= 0 -> TP_GRAY
        isAsk -> TP_RED
        else -> TP_BLUE
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (price > 0) onPickPrice(price) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TP_GRAY, fontSize = 13.sp, modifier = Modifier.width(40.dp))
        Row(
            modifier = Modifier.width(110.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 红点：本方（买/卖）报价侧，画在价格标签左侧
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (redDot) TP_RED else Color.Transparent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (price > 0) String.format(Locale.CHINA, "%.2f", price) else "—",
                color = priceColor,
                fontSize = 15.sp,
                fontFamily = TPNumFont
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (vol > 0) String.format(Locale.CHINA, "%,.0f", vol) else "—",
            color = TP_BLACK,
            fontSize = 14.sp,
            fontFamily = TPNumFont
        )
    }
}

/** 挂单列表 / 撤单面板 */
@Composable
private fun OrderListPanel(
    token: String,
    brokerAccountId: Int,
    onChanged: () -> Unit
) {
    var orders by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tip by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(brokerAccountId, tick) {
        loading = true
        try {
            orders = withContext(Dispatchers.IO) {
                Api.orderList(token, brokerAccountId, status = "pending")
            }
        } catch (e: Exception) {
            orders = emptyList()
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前挂单",
                color = Color(Config.COLOR_TEXT_MAIN),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "刷新",
                color = Color(Config.COLOR_PRIMARY),
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { tick++ }
            )
        }
        tpDivider()
        if (tip.isNotEmpty()) {
            Text(
                text = tip,
                color = TP_GRAY,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text("加载中…", color = TP_GRAY, fontSize = 13.sp) }
        } else if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text("暂无挂单", color = TP_GRAY, fontSize = 13.sp) }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                orders.forEach { o ->
                    val isBuy = o.direction == 1 || o.type == "buy"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBuy) "买入" else "卖出",
                                color = if (isBuy) TP_RED else TP_BLUE,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = o.name.ifEmpty { o.code },
                                color = TP_BLACK,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(o.code, color = TP_GRAY, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "委托 ${tpMoney(o.price)} × ${o.quantity}",
                                color = TP_GRAY,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "撤单",
                                color = TP_RED,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .border(
                                        width = 1.dp,
                                        color = TP_RED,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        tip = "撤单中…"
                                        kotlinx.coroutines.MainScope().launch {
                                            val r = withContext(Dispatchers.IO) {
                                                try { Api.orderCancel(token, o.id) }
                                                catch (e: Exception) { Api.Res(false, e.message ?: "网络异常") }
                                            }
                                            tip = if (r.ok) "撤单成功" else "撤单失败：${r.msg}"
                                            tick++
                                            onChanged()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    tpDivider()
                }
            }
        }
    }
}

/** 持仓面板（占位）：按钮保留，内容后续重新设计填充 */
@Composable
private fun PositionPanel(
    token: String,
    brokerAccountId: Int,
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "持仓面板建设中",
            color = TP_GRAY,
            fontSize = 13.sp
        )
    }
}


/** ② 证券账户栏：左图标 +（上）券商名（下）资金账号 + 右 ∨；点击弹底部半屏列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountBar(
    brokers: List<MyBroker>,
    current: MyBroker?,
    onSwitch: (MyBroker) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val name = current?.broker ?: "证券账户"
    val no = current?.account ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (brokers.isNotEmpty()) showSheet = true
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrokerIcon(name, current?.key ?: "")
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color(Config.COLOR_TEXT_MAIN),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (no.isNotEmpty()) no else "未添加账户",
                color = TP_GRAY,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (brokers.isNotEmpty()) {
            Text("∨", color = TP_GRAY, fontSize = 16.sp)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "选择证券账户",
                    color = Color(Config.COLOR_TEXT_MAIN),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                tpDivider()
                brokers.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSwitch(b)
                                showSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrokerIcon(b.broker, b.key)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = b.broker,
                                color = Color(Config.COLOR_TEXT_MAIN),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(text = b.account, color = TP_GRAY, fontSize = 13.sp)
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
                    tpDivider()
                }
            }
        }
    }
}

/** 券商图标：有图读 broker_<key>.png（36dp 圆形），无图用「圆圈 + 首字」兜底 */
@Composable
private fun BrokerIcon(brokerName: String, key: String) {
    if (key.isNotEmpty()) {
        val ctx = LocalContext.current
        val id = ctx.resources.getIdentifier("broker_$key", "drawable", ctx.packageName)
        if (id != 0) {
            Image(
                painter = painterResource(id),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            return
        }
    }
    // 兜底：圆圈 + 券商名首字
    val ch = if (brokerName.isNotEmpty()) brokerName.substring(0, 1) else "券"
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fallbackColor(brokerName)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ch,
            color = Color.White,
            fontSize = 16.sp,
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

/** 浅灰分割线 */
@Composable
private fun tpDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(Config.COLOR_DIVIDER))
    )
}

// ================= ④.6 功能入口宫格（2列×5行） =================
/** 单个入口：图标 + 主标题(可带 NEW 角标) + 副标题 */
data class MenuItem(
    val icon: Int,
    val title: String,
    val sub: String,
    val isNew: Boolean = false,
    val subRed: Boolean = false
)

/** 功能入口宫格：一行两个、共五行。点击先留占位。 */
@Composable
private fun MenuGrid(brokerName: String, onCell: () -> Unit) {
    val ctx = LocalContext.current
    val items = listOf(
        MenuItem(R.drawable.ic_menu_01, "新股/债申购", "暂无新股和新债"),
        MenuItem(R.drawable.ic_menu_02, "通用逆回购回购", "14天期1.385%", subRed = true),
        MenuItem(R.drawable.ic_menu_03, "条件单", "监控条件 触发委托", isNew = true),
        MenuItem(R.drawable.ic_menu_04, "融资融券", "借钱买多 借券卖空"),
        MenuItem(R.drawable.ic_menu_05, "科创板", "科创板交易"),
        MenuItem(R.drawable.ic_menu_06, "我的笔记", "每日记录 心得积累"),
        MenuItem(R.drawable.ic_menu_07, "我的组合", "创建组合 批量买卖"),
        MenuItem(R.drawable.ic_menu_08, "行情全景", "产业板块 全球宏观"),
        MenuItem(R.drawable.ic_menu_09, brokerName + "业务办理", "权限开通 信息更新"),
        MenuItem(R.drawable.ic_menu_10, "港股ETF", "T+0交易 当天买卖"),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // 5 行
        for (r in 0 until 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val li = r * 2
                val ri = r * 2 + 1
                MenuCell(items[li], Modifier.weight(1f)) { onCell() }
                MenuCell(items[ri], Modifier.weight(1f)) { onCell() }
            }
        }
    }
}

// ================= ④.7 功能列表（6 栏 + 退出按钮，第九轮新增） =================
/** 功能列表区：6 栏（灰底分隔，单/双行高） + 3牙签空隙 + 红色退出按钮 */
@Composable
private fun MenuListSection(
    onOpenMemberCenter: () -> Unit,
    onLogout: () -> Unit,
    guard: (String, String, () -> Unit) -> Unit,
    onFeedback: () -> Unit
) {
    val ctx = LocalContext.current
    // 1 牙签 = 月度参考盈亏灰底间隔高 = 8dp；3 牙签 = 24dp；左右各 2 牙签 = 16dp
    val toothpick = 8.dp
    val rowDivider = Color(Config.COLOR_DIVIDER)

    val gap = toothpick  // 每栏之间的灰底间隔 = 1 牙签 = 8dp
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 第1栏：银证转账 → 整行点击跳会员中心（资金设置），右侧 › 图标
        ListRow(title = "银证转账") { guard("tp_transfer", "银证转账") { onOpenMemberCenter() } }
        Spacer(Modifier.fillMaxWidth().height(gap))

        // 第2栏：盘后固定价格交易
        ListRow(title = "盘后固定价格交易") { guard("tp_after_price", "盘后固定价格交易") { Toast.makeText(ctx, "功能建设中", Toast.LENGTH_SHORT).show() } }
        Spacer(Modifier.fillMaxWidth().height(gap))

        // 第3栏：融资融券预约开通（右灰小字“借钱借券”）
        ListRow(title = "融资融券预约开通", rightText = "借钱借券") { guard("tp_margin_reserve", "融资融券预约开通") { Toast.makeText(ctx, "功能建设中", Toast.LENGTH_SHORT).show() } }
        Spacer(Modifier.fillMaxWidth().height(gap))

        // 第4栏：两行式（同一白块内上下两行）
        //   行1 人脸/指纹,快捷登录  右“未开启”
        //   行2 风险评测            右“即将退出敬请期待”
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            ListRowInline(title = "人脸/指纹,快捷登录", rightText = "未开启") {
                guard("tp_quick_login", "快捷登录") { Toast.makeText(ctx, "功能建设中", Toast.LENGTH_SHORT).show() }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(0.5.dp)
                    .background(rowDivider)
            )
            ListRowInline(title = "风险评测", rightText = "即将退出敬请期待") {
                guard("tp_risk_eval", "风险评测") { Toast.makeText(ctx, "功能建设中", Toast.LENGTH_SHORT).show() }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(gap))

        // 第5栏：交易设置（右灰小字“买卖预设，交易提醒，修改密码”）
        ListRow(title = "交易设置", rightText = "买卖预设，交易提醒，修改密码") { guard("tp_trade_settings", "交易设置") { Toast.makeText(ctx, "功能建设中", Toast.LENGTH_SHORT).show() } }
        Spacer(Modifier.fillMaxWidth().height(gap))

        // 第6栏：用户帮助与反馈（不纳入权限，弹出意见反馈弹窗）
        ListRow(title = "用户帮助与反馈") { onFeedback() }
    }

    // 3 牙签高 = 24dp 空隙（露出灰底）
    Spacer(Modifier.fillMaxWidth().height(toothpick * 3))

    // 红色退出按钮：红底白字，左右各 2 牙签 = 16dp 边距
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = toothpick * 2)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(Color(0xFFE64A3B))
            .clickable { onLogout() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "退出当前账户",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 单行列表项（独立白块）：大标题左，灰色小字（可选）+ › 图标右 */
@Composable
private fun ListRow(
    title: String,
    rightText: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TP_BLACK,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (rightText.isNotEmpty()) {
            Text(
                text = rightText,
                color = TP_GRAY,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_pnl_arrow),
            contentDescription = "进入",
            modifier = Modifier.width(8.dp).height(13.dp)
        )
    }
}

/** 两行式中的行（无底部分割线，分割由外层控制） */
@Composable
private fun ListRowInline(
    title: String,
    rightText: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TP_BLACK,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (rightText.isNotEmpty()) {
            Text(
                text = rightText,
                color = TP_GRAY,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_pnl_arrow),
            contentDescription = "进入",
            modifier = Modifier.width(8.dp).height(13.dp)
        )
    }
}

/** 单个入口格：左图标 + （上）主标题 （下）副标题；isNew 时标题右上叠加 NEW 角标 */
@Composable
private fun MenuCell(item: MenuItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(item.icon),
            contentDescription = item.title,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 标题行（带可选 NEW 角标）
            Box {
                Text(
                    text = item.title,
                    color = TP_BLACK,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 26.dp, y = 0.dp)
                            .background(Color(0xFFFFECEC), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = TP_RED,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.sub,
                color = if (item.subRed) TP_RED else TP_GRAY,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ================= 意见反馈弹窗（交易页「用户帮助与反馈」栏触发，第18轮） =================
/**
 * 意见反馈弹窗：与首页会员中心「意见反馈」子页同源表单（文字必填 ≤500 字 + 最多 3 张图），
 * 复用 Api.uploadImage / Api.submitFeedback，提交与提示文案保持一致。
 */
@Composable
public fun FeedbackDialog(token: String?, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var txt by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val imgs = remember { mutableStateListOf<String>() }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (imgs.size >= 3) { msg = "最多上传 3 张图片"; return@rememberLauncherForActivityResult }
        busy = true
        msg = "图片上传中…"
        Thread {
            val saved = try {
                val tmp = java.io.File(ctx.cacheDir, "fb_${System.currentTimeMillis()}.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    tmp.outputStream().use { outs -> ins.copyTo(outs) }
                }
                val url = Api.uploadImage(token, tmp, "feedback")
                tmp.delete()
                url
            } catch (e: Exception) {
                null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                if (saved != null) {
                    imgs.add(saved)
                    msg = if (imgs.size >= 3) "已上传 3 张（上限）" else "已上传 ${imgs.size} 张"
                } else {
                    msg = "图片上传失败，请重试"
                }
            }
        }.start()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text("意见反馈", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("问题或建议", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = txt,
                onValueChange = { if (it.length <= 500) txt = it },
                placeholder = { Text("请描述你遇到的问题或想提的建议（必填）", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text("${txt.length}/500", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Text("截图（选填，最多 3 张）", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                imgs.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEEEEEE))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { imgs.removeAt(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("图${idx + 1}\n✕", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (imgs.size < 3) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F0))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (!busy) picker.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+ 添加", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                    }
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(msg, color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            BigBtn(if (busy) "请稍候…" else "提交反馈") {
                if (busy) return@BigBtn
                if (txt.trim().isEmpty()) { msg = "请填写反馈内容"; return@BigBtn }
                busy = true
                msg = "提交中…"
                Thread {
                    val r = Api.submitFeedback(token, txt.trim(), imgs.toList())
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        busy = false
                        if (r.ok) {
                            txt = ""
                            imgs.clear()
                            msg = "提交成功，感谢你的反馈！"
                        } else {
                            msg = "提交失败：${r.msg}"
                        }
                    }
                }.start()
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!busy) onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("取消", color = Color(Config.COLOR_TEXT_SUB), fontSize = 14.sp)
            }
        }
    }
}