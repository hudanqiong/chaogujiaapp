package com.chaogujia.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.withContext
import java.util.Locale

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
/** 盈亏金额：带正负号，盈利 + ，亏损 - */
private fun tpSigned(v: Double): String =
    String.format(Locale.CHINA, "%+,.2f", v)
private fun tpPct(v: Double): String =
    String.format(Locale.CHINA, "%+.2f%%", v)
/** 盈亏颜色：盈利红、亏损蓝、零灰 */
private fun tpPnColor(v: Double): Color = when {
    v > 1e-9 -> TP_RED
    v < -1e-9 -> TP_BLUE
    else -> TP_GRAY
}

@Composable
fun TradePage(
    token: String,
    subTab: String = "A股",
    onSubTabChange: (String) -> Unit = {},
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ===== 子页内容（账户类型切换按钮已移入顶部红条 MemberHeaderBar） =====
        when (subTab) {
            "A股" -> ASharePage(token = token, onOpenDetail = onOpenDetail)
            else -> PlaceholderPage(subTab)
        }
    }
}

/** A股子页 */
@Composable
private fun ASharePage(
    token: String,
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf(AccountInfo()) }
    var myBrokers by remember { mutableStateOf<List<MyBroker>>(emptyList()) }
    var currentBroker by remember { mutableStateOf<MyBroker?>(null) }
    suspend fun load() {
        loading = true
        try {
            val acc = withContext(Dispatchers.IO) { Api.account(token) }
            account = acc
        } catch (e: Exception) {
            // 保持上次数据
        } finally {
            loading = false
        }
    }
    LaunchedEffect(token) {
        load()
        try {
            val bs = withContext(Dispatchers.IO) { Api.brokerList(token) }
            myBrokers = bs
            if (currentBroker == null) currentBroker = bs.firstOrNull()
        } catch (e: Exception) {
            // 无账户时保持空列表
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== ② 证券账户栏（可切换） =====
        AccountBar(
            brokers = myBrokers,
            current = currentBroker,
            onSwitch = { currentBroker = it }
        )
        tpDivider()

        // ===== ③ 两行两列统计 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
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
                    value = "—",
                    valueColor = TP_BLACK,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        tpDivider()

        // ===== ④ 一排操作按钮（本轮文字占位） =====
        ActionBarRow()

        // 下方留白（持仓列表将在「持仓」入口内展示，本轮不铺开）
        Box(modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** ③ 统计单元格：标签灰色在上，数值在下 */
@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(Config.COLOR_TEXT_MAIN)
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TP_GRAY,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = TPNumFont,
            maxLines = 1
        )
    }
}

/** ④ 操作按钮排：买入 / 卖出 / 撤单 / 持仓 / 查询（图标 + 文字） */
@Composable
private fun ActionBarRow() {
    val actions = listOf(
        "买入" to R.drawable.ico_trade_buy,
        "卖出" to R.drawable.ico_trade_sell,
        "撤单" to R.drawable.ico_trade_cancel,
        "持仓" to R.drawable.ico_trade_position,
        "查询" to R.drawable.ico_trade_query
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { (label, icon) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // TODO: 本轮占位，点击暂不处理
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


/** ② 证券账户栏：左图标 +（上）券商名（下）资金账号 + 右 ∨；点击弹底部半屏列表 */
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
