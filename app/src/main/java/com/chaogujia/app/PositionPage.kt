package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 独立持仓页（PositionPage）—— 第 34 轮新增
 *
 * 入口：交易页（TradePage）「持仓」按钮上抛 onOpenPosition → MainActivity 打开本页。
 * 不挂在底部导航上；返回由顶部红条的 [<] 按钮负责（MainActivity.BackHandler 亦可）。
 *
 * 页面结构（自上而下）：
 *   ① 顶部红条：由 MainActivity 统一渲染 MemberHeaderBar(current = "position")，
 *      内含 [<返回] + 两行文字(证券名/资金账户) + ▼ + 方框箭头 + 放大镜 + 刷新。
 *      本页不画红条。
 *   ② 5 个切换按钮：买入 / 卖出 / 撤单 / 持仓 / 查询（固定不滚动，5 等分）。
 *      未选中深灰、选中红 + 红色下划线（宽度 = 按钮宽度）；默认选中「持仓」。
 *   ③ 内容区：本轮先空占位（列表数据 / 统计数字留待后续步骤）。
 */
private val PP_RED = Color(0xFFE23B3B)      // 选中色 / 下划线（与红条同族）
private val PP_GRAY = Color(0xFF555555)     // 未选中文字
private val PP_BG = Color(0xFFF5F5F5)       // 页面灰底

// ★ 第 34 轮新增：中栏 / 6 格统计用色（对齐交易页 TradePage 的色系）
private val PP_BLUE = Color(0xFF2A7BFF)     // 跌/亏损（蓝）
private val PP_BLACK = Color(0xFF222222)    // 数值色（深）
private val PP_LABEL = Color(0xFF888888)    // 标签色（浅灰）：总资产/浮动盈亏/总市值/可用/可取
private val PP_LINE = Color(0xFFF0F0F0)     // 上下分割细线（第35轮：更细更浅）
private val PP_TRACK = Color(0xFFFFFFFF)    // 仓位进度条底槽（第35轮：与背景一致=白）
private val PP_FILL = Color(0xFFEEEEEE)     // 仓位进度条/后缀方框 填充色（第35轮：#EEEEEE）
private val PP_ORANGE = Color(0xFFFF671B)   // 可用/可取后缀方框 文字+三角（第35轮：#FF671B）
private val PP_BOXBG = Color(0xFFEEEEEE)    // 后缀小方框底（第35轮：#EEEEEE，不填充）

/** 数值字体：与交易页/详情页一致（同花顺金融体） */
private val PPNumFont = FontFamily(Font(R.font.ths_regular))
private fun ppMoney(v: Double): String = String.format(Locale.CHINA, "%,.2f", v)
/** 盈亏金额：带正负号（盈利 +、亏损 -）；为 0 不带符号 */
private fun ppSigned(v: Double): String =
    if (kotlin.math.abs(v) < 5e-3) String.format(Locale.CHINA, "%,.2f", 0.0)
    else String.format(Locale.CHINA, "%+,.2f", v)
/** 盈亏颜色：红涨、蓝跌、平黑 */
private fun ppPnColor(v: Double): Color = when {
    v > 1e-9 -> PP_RED
    v < -1e-9 -> PP_BLUE
    else -> PP_BLACK
}

/** 5 个切换按钮（顺序固定） */
private val POS_TABS = listOf("买入", "卖出", "撤单", "持仓", "查询")

@Composable
fun PositionPage(
    token: String = "",
    /** 默认选中项：按用户要求默认「持仓」 */
    defaultTab: String = "持仓",
    /** 回吐当前资金账户（上行=券商名，下行=账号），供红条两行文字显示 */
    onAccountChange: (String, String) -> Unit = { _, _ -> }
) {
    var curTab by remember { mutableStateOf(defaultTab) }

    // ★ 第 34 轮新增：中栏与 6 格统计所需数据
    var account by remember { mutableStateOf(AccountInfo()) }
    var cashAccount by remember { mutableStateOf("") }   // 资金账号（可空）
    var brokerId by remember { mutableStateOf(0) }

    // ★ 进页时拉一次「我的券商账户」+「账户资金」：
    //   - 券商账户：取第一个，回吐红条两行文字，同时拿到资金账户 id
    //   - 账户资金：拿 total/marketValue/available/floatPn 填 6 格统计；仓位% = 总资产/总持仓
    //   失败静默忽略（红条文字留空），不影响页面其它部分。
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            try {
                // 网络请求必须在 IO 线程（与 TradePage/HomeScreen 等保持一致），
                // 否则主线程抛 NetworkOnMainThreadException 被静默吞掉 → 红条两行文字空白。
                val list = withContext(Dispatchers.IO) { Api.brokerList(token) }
                val first = list.firstOrNull()
                if (first != null) {
                    onAccountChange(first.broker, first.account)
                    brokerId = first.id
                    cashAccount = first.account
                    // 账户资金详情
                    val acc = withContext(Dispatchers.IO) { Api.account(token, first.id) }
                    account = acc
                }
            } catch (_: Throwable) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PP_BG)
    ) {
        // ===== ② 5 个切换按钮（固定不滚动）=====
        PosTabBar(cur = curTab, onSelect = { curTab = it })

        // ===== ③ 账户信息栏（左：国旗+账户类型；右：仓位百分比框）=====
        //   上下两条浅灰细线分割，高度≈上方切换按钮栏（44dp）。
        Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(PP_LINE))
        PosAccountBar(account = account)
        Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(PP_LINE))

        // ===== ④ 6 格统计（两行三列）：总资产/浮动盈亏/当日参考盈亏 · 总市值/可用/可取 =====
        PosStatGrid(account = account)

        // ===== ⑤ 内容区：本轮先空占位（持仓列表留待后续步骤）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // 持仓列表数据留待后续步骤；此处仅占位（保持空，不误导）
        }
    }
}

/**
 * ③ 账户信息栏（持仓按钮下方一整栏）：
 *   - 左：小国旗图标（占位：红底 + 黄星，后续可替换为真图） + 「人民币账户 A股」
 *         文字色 = 上方切换按钮「未点亮」色（PP_GRAY）
 *   - 最右：仓位百分比框 —— 长方形，宽≈切换按钮红线条宽；浅灰按百分比填充；
 *         框内居中显示「仓位 xx%」
 *   - 高度≈上方切换按钮栏（44dp）
 */
@Composable
private fun PosAccountBar(account: AccountInfo) {
    // 仓位% = 总市值 / 总资产（总资产为 0 时置 0），框取 0..1
    val posRatio: Float =
        if (account.total > 0.0) (account.marketValue / account.total).toFloat().coerceIn(0f, 1f)
        else 0f
    val posPct = (posRatio * 100f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- 左：国旗占位 + 账户类型 ----
        CnrFlagIcon(size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "人民币账户 A股",
            color = PP_GRAY,
            fontSize = 13.sp,
            maxLines = 1
        )

        Spacer(Modifier.weight(1f))

        // ---- 右：仓位百分比框（宽≈一条切换按钮红线宽；此处取按钮宽 = 屏宽/5）----
        //   宽度取 72dp（约等于 5 等分按钮宽度量级），高 22dp。
        //   第35轮：底槽=白（与背景一致），仅填充部分为浅灰 #EEEEEE。
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PP_TRACK)
        ) {
            // 浅灰按百分比填充（自左向右）
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(posRatio)
                    .background(PP_FILL)
            )
            // 框内居中文字
            Text(
                text = String.format(Locale.CHINA, "仓位 %.0f%%", posPct),
                color = PP_BLACK,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * ④ 6 格统计（两行三列）：
 *   行1：总资产 / 浮动盈亏 / 当日参考盈亏
 *   行2：总市值 / 可用(逆回购▶) / 可取(转账▶)
 *   标签（总资产/浮动盈亏/总市值/可用/可取）：浅灰 #888888、13sp Bold，在上
 *   数值：深色 #222222、11sp，在下；浮动盈亏/当日参考盈亏红涨蓝跌带加减号
 *   后缀小方框：挂在「可用」「可取」标签右侧（浅灰底 + #FF671B 字「逆回购▶」「转账▶」）
 */
@Composable
private fun PosStatGrid(account: AccountInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PosStatCell(
                label = "总资产",
                value = ppMoney(account.total),
                valueColor = PP_BLACK,
                suffixText = null,
                modifier = Modifier.weight(1f)
            )
            PosStatCell(
                label = "浮动盈亏",
                value = ppSigned(account.floatPn),
                valueColor = ppPnColor(account.floatPn),
                suffixText = null,
                modifier = Modifier.weight(1f)
            )
            PosStatCell(
                label = "当日参考盈亏",
                // 后端暂无独立「当日参考盈亏」字段，暂以浮动盈亏代入（模拟盘日盈亏口径一致），
                // 后续后端补 dayPnl 字段后替换此处即可。
                value = ppSigned(account.floatPn),
                valueColor = ppPnColor(account.floatPn),
                suffixText = null,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PosStatCell(
                label = "总市值",
                value = ppMoney(account.marketValue),
                valueColor = PP_BLACK,
                suffixText = null,
                modifier = Modifier.weight(1f)
            )
            PosStatCell(
                label = "可用",
                value = ppMoney(account.available),
                valueColor = PP_BLACK,
                suffixText = "逆回购",
                modifier = Modifier.weight(1f)
            )
            PosStatCell(
                label = "可取",
                // 后端无独立「可取」字段（模拟盘无 T+1），暂以可用资金代入。
                value = ppMoney(account.available),
                valueColor = PP_BLACK,
                suffixText = "转账",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个统计格：标题在上（灰？——按用户要求用交易页标题色/字号），数值在下。
 *   - 标题：Config.COLOR_TEXT_MAIN、15sp、Bold（与交易页面板标题一致）
 *   - 数值：17sp Medium，金融体
 *   - suffix：数值右侧的橙色小方框（如「逆回购▶」「转账▶」），为 null 时不显示
 */
@Composable
private fun PosStatCell(
    label: String,
    value: String,
    valueColor: Color,
    suffixText: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // ---- 标签行：固定文字（浅灰）+ 可选方框（逆回购▶/转账▶）----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = PP_LABEL,          // 标签=浅灰
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (suffixText != null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(PP_BOXBG)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    // 文字 + 独立三角（两段，确保 ▶ 不被裁切、且都在框内）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = suffixText,
                            color = PP_ORANGE,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "▶",
                            color = PP_ORANGE,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // ---- 数值行：深色数字 ----
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = PPNumFont,
            maxLines = 1
        )
    }
}

/**
 * 小国旗占位图标（红底 + 一颗黄星）。
 *   ★ 用户明确：先用小图标代替，后期再更换为真·五星红旗。
 *   用 Compose 直接绘制，避免新增资源文件带来的编译风险。
 */
@Composable
private fun CnrFlagIcon(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(1.dp))
            .background(PP_RED),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "★",
            color = Color(0xFFFFDE00),
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

/**
 * 顶部 5 切换按钮栏：
 *   - Row 内 5 等分（weight(1f)），固定不滚动
 *   - 未选中：深灰文字；选中：红色文字 + 红色下划线（宽度 = 按钮宽度）
 *   - 默认选中「持仓」
 */
@Composable
private fun PosTabBar(cur: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        POS_TABS.forEach { t ->
            val selected = t == cur
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(t) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = t,
                    color = if (selected) PP_RED else PP_GRAY,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(5.dp))
                // 下划线：仅选中项显示，宽度 = 按钮宽度（即 weight 分到的整格宽）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (selected) PP_RED else Color.Transparent)
                )
            }
        }
    }
}