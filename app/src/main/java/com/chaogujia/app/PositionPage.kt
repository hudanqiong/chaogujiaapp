package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
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
private val PP_BOXLINE = Color(0xFFDDDDDD)  // 第37轮：仓位框外轮廓线（浅灰细边）

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

    // ★ 第 39 轮新增：持仓股票列表（下方 9 栏表格的数据源）
    var positions by remember { mutableStateOf(listOf<PositionItem>()) }

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
                    // ★ 第 39 轮：持仓列表（下方 9 栏表格数据源），失败留空不影响其它部分
                    positions = withContext(Dispatchers.IO) { Api.positions(token, first.id) }
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
        //   上下两条浅灰细线分割，高度 36dp（第38轮：44→36，更紧凑）。
        //   第38轮：线高 0.8→0.4；上分割线紧贴切换栏底边（与选中红线在同一视觉带上）。
        Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
        PosAccountBar(account = account)
        Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))

        // ===== ④ 6 格统计（两行三列）：总资产/浮动盈亏/当日参考盈亏 · 总市值/可用/可取 =====
        PosStatGrid(account = account)

        // ===== ⑤ 灰底分隔条（第40轮）：视觉分割上下两块，高度较上轮翻倍 =====
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(PP_BG))

        // ===== ⑥ 持仓表格（第39轮）：表头(9栏) + 股票列表(每只两行 / 部分单行) =====
        //   左滑：第一栏(市值)固定，右侧 8 栏整体左滑钻到固定列下方（复用自选页 hScroll 模式）
        PosHoldTable(positions = positions)
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
            .height(44.dp)   // 第38轮：与切换栏等高（红线贴底后，上分割线即红线带）
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
        //   宽度取 72dp（约等于 5 等分按钮宽度量级），高 20dp。
        //   第35轮：底槽=白（与背景一致），仅填充部分为浅灰 #EEEEEE。
        //   第37轮：加长方形外轮廓线（PP_BOXLINE），高度 22→20，文字 11→10sp。
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PP_TRACK)
                .border(1.dp, PP_BOXLINE, RoundedCornerShape(2.dp))
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
                fontSize = 10.sp,
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
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)  // 第38轮：左右留边，整体上抬
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
        Spacer(Modifier.height(24.dp))  // 第38轮：12→24（上下行间距加大一倍）
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
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {  // 第38轮：居中→左对齐（三栏各自左对齐）
        // ---- 标签行：固定文字（浅灰）+ 可选方框（逆回购▶/转账▶）----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = PP_LABEL,          // 标签=浅灰
                fontSize = 14.sp,          // 第37轮：13→14（+1号）
                fontWeight = FontWeight.Normal,  // 第37轮：去掉加粗
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
            fontSize = 13.sp,          // 第37轮：11→13（+2号）
            fontWeight = FontWeight.Bold,   // 第37轮：Medium→Bold（加粗）
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
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = "★",
            color = Color(0xFFFFD200),
            fontSize = (size.value * 0.5f).sp,
            maxLines = 1
        )
    }
}

// =====================================================================================
// 第 39 轮：持仓表格（表头 9 栏 + 股票列表，每只两行；部分栏单行居中）
//
//   列顺序（9 栏）：市值 | 盈亏 | 持仓/可用 | 成本/现价 | 当日盈亏 | 个股仓位 | 持仓天数 | 止盈止损 | 证券代码
//   - 前 5 栏两行（上行说明/金额，下行副值），后 4 栏单行居中
//   - 左滑：第 1 栏(市值)钉死不动；右侧 8 栏整体左滑钻到固定列下方（复用自选页 hScroll 模式）
//   - 颜色：盈红亏蓝带 +/-；证券代码黑字；表头文字同「总资产」标签色（浅灰 PP_LABEL）
//   - 表头每栏文字右侧跟随一对浅灰三角（上尖↑ / 下尖↓ 叠放）
// =====================================================================================

/** 第 1 栏（市值）固定列宽度 dp —— 左滑时钉死不动 */
private const val PP_HOLD_FIXED_W = 96
/** 右侧每栏宽度 dp（8 栏，横滑区）—— 第40轮加宽以容纳 14sp 表头 */
private const val PP_HOLD_COL_W = 86
/** 表头文字色：与 6 格统计「总资产」标签同色（浅灰 #888888） */
private val PP_HEAD = Color(0xFF888888)
/** 分栏竖分隔线（极浅，仅作视觉分栏） */
private val PP_COLLINE = Color(0xFFF2F2F2)
/** 表格行高（两行内容） */
private val PP_ROW_H = 52.dp
/** 第40轮：表头字号（与上方「总资产」标签一致 = 14sp） */
private val PP_HEAD_FS = 14.sp
/** 第40轮：股票名称字号（加大1号：13 → 14sp） */
private val PP_NAME_FS = 14.sp
/** 第40轮：表头行高（放大以配合 14sp 文字 + 大三角） */
private val PP_HEAD_H = 42.dp

/** 表头栏定义：title 标题；twoLine 是否两行数据列 */
private data class PPCol(val title: String, val twoLine: Boolean)

/** 9 栏定义（顺序即显示顺序） */
private val PP_COLS: List<PPCol> = listOf(
    PPCol("市值", true),
    PPCol("盈亏", true),
    PPCol("持仓/可用", true),
    PPCol("成本/现价", true),
    PPCol("当日盈亏", true),
    PPCol("个股仓位", false),
    PPCol("持仓天数", false),
    PPCol("止盈止损", false),
    PPCol("证券代码", false)
)

/**
 * 持仓表格：表头 + 列表（共享同一个 hScroll 实现左右同步滑动）。
 */
@Composable
private fun PosHoldTable(positions: List<PositionItem>) {
    // 表头与所有数据行共用同一横向滚动状态 → 左滑同步、第 1 栏钉死
    val hScroll = rememberScrollState()
    // 全部持仓市值合计（用于「个股仓位% = 该股市值 / 合计市值」）
    val totalMv = positions.sumOf { it.marketValue }

    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        // ---------- 表头 ----------
        PosHoldHeader(hScroll = hScroll)
        Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))

        // ---------- 列表 ----------
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(positions) { p ->
                PosHoldRow(p = p, totalMv = totalMv, hScroll = hScroll)
                Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
            }
        }
    }
}

/**
 * 表头行：第 1 栏(市值)钉死；右侧 8 栏铺在横滑层（随 hScroll 左移，钻到固定列下方）。
 * 表头本身不响应手指拖动（enabled = false），滑动由数据行驱动。
 */
@Composable
private fun PosHoldHeader(hScroll: ScrollState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PP_HEAD_H)
            .background(Color.White)
    ) {
        // 下层：右侧 8 栏（从固定列右缘起铺，随 hScroll 左移）
        //   第40轮：文字 + 三角 整体右对齐到栏右边界（与下方数据右对齐同一竖线）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PP_HOLD_FIXED_W.dp)
                .horizontalScroll(hScroll, enabled = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 与固定列对齐留位（数据行同款占位，保证列对齐）
            for (c in PP_COLS.drop(1)) {
                Box(
                    modifier = Modifier
                        .width(PP_HOLD_COL_W.dp)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = c.title,
                            color = PP_HEAD,
                            fontSize = PP_HEAD_FS,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(2.dp))
                        // 一对浅灰三角（上尖 / 下尖 叠放）
                        PpSortArrows()
                    }
                }
            }
        }
        // 上层：第 1 栏「市值」白底，盖住滑过来的右侧栏
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(PP_HOLD_FIXED_W.dp)
                .fillMaxHeight()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = PP_COLS[0].title,
                    color = PP_HEAD,
                    fontSize = PP_HEAD_FS,
                    maxLines = 1
                )
                Spacer(Modifier.width(2.dp))
                PpSortArrows()
            }
        }
    }
}

/**
 * 单只股票数据行：第 1 栏(市值)钉死；右侧 8 栏横滑。
 * 两行栏：上行 + 下行；单行栏：居中一行。
 */
@Composable
private fun PosHoldRow(p: PositionItem, totalMv: Double, hScroll: ScrollState) {
    // 个股仓位% = 该股市值 / 全部持仓市值合计
    val holdPct = if (totalMv > 0.0) (p.marketValue / totalMv) * 100.0 else 0.0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PP_ROW_H)
            .background(Color.White)
    ) {
        // 下层：右侧 8 栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PP_HOLD_FIXED_W.dp)
                .horizontalScroll(hScroll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 盈亏（两行）---
            PpCellTwo(
                top = ppSigned(p.pnl),
                topColor = ppPnColor(p.pnl),
                bottom = ppPct(p.pnlPct),
                bottomColor = ppPnColor(p.pnl)
            )
            // --- 持仓/可用（两行）：数量红蓝（正=红，负=蓝；持仓数恒正 → 红）---
            PpCellTwo(
                top = ppQty(p.quantity),
                topColor = ppPnColor(p.quantity.toDouble()),
                bottom = ppQty(p.quantity),   // 可用：暂=持仓（T+1 待做）
                bottomColor = ppPnColor(p.quantity.toDouble())
            )
            // --- 成本/现价（两行）：成本红蓝（相对现价）；现价红蓝（相对成本）---
            PpCellTwo(
                top = ppPrice(p.avgCost),
                topColor = ppPnColor(p.price - p.avgCost),
                bottom = ppPrice(p.price),
                bottomColor = ppPnColor(p.price - p.avgCost)   // 现价相对成本红/蓝
            )
            // --- 当日盈亏（两行）：暂以总盈亏代入（后端无独立当日字段）---
            PpCellTwo(
                top = ppSigned(p.pnl),
                topColor = ppPnColor(p.pnl),
                bottom = ppPct(p.pnlPct),
                bottomColor = ppPnColor(p.pnl)
            )
            // --- 个股仓位（单行）：该股市值 / 全部持仓市值合计；第40轮：红蓝、去正负号 ---
            PpCellOne(text = ppPctPlain(holdPct), color = ppPnColor(p.pnl))
            // --- 持仓天数（单行）：第40轮接后端 holdDays（交易日数）---
            PpCellOne(text = ppDays(p.holdDays), color = PP_BLACK)
            // --- 止盈止损（单行）：后端无字段，-- 占位 ---
            PpCellOne(text = "--", color = PP_BLACK)
            // --- 证券代码（单行，黑字）---
            PpCellOne(text = p.code, color = PP_BLACK)
        }
        // 上层：第 1 栏「市值」白底（钉死）
        //   上行 = 股票名称；下行 = 持仓金额(marketValue)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(PP_HOLD_FIXED_W.dp)
                .fillMaxHeight()
                .background(Color.White),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                // 第40轮：第1栏两行（股票名 / 市值）字号字重统一 —— 与全表数据一致 12sp Bold
                Text(
                    text = p.name,
                    color = PP_BLACK,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = ppMoney(p.marketValue),
                    color = ppPnColor(p.pnl),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PPNumFont,
                    maxLines = 1
                )
            }
        }
    }
}

/** 两行数据栏：上行 + 下行（宽度 = PP_HOLD_COL_W）；第40轮：内容右对齐到栏右边界 */
@Composable
private fun PpCellTwo(top: String, topColor: Color, bottom: String, bottomColor: Color) {
    Column(
        modifier = Modifier
            .width(PP_HOLD_COL_W.dp)
            .fillMaxHeight()
            .padding(end = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End
    ) {
        // 第40轮：上下两行统一同字号同字重（用户要求「表头下面两行数据一样大、都是粗体」）：均 12sp Bold
        Text(text = top, color = topColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(text = bottom, color = bottomColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
    }
}

/** 单行数据栏：垂直居中一行（宽度 = PP_HOLD_COL_W）；第40轮：右对齐 */
@Composable
private fun PpCellOne(text: String, color: Color) {
    Box(
        modifier = Modifier
            .width(PP_HOLD_COL_W.dp)
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
    }
}

/** 一对浅灰三角（上尖 / 下尖 叠放），跟在每栏表头文字后面；第40轮：放大且上下贴紧 */
@Composable
private fun PpSortArrows() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center          // Center 让两三角紧贴
    ) {
        Text(text = "▲", color = PP_HEAD, fontSize = 8.sp, maxLines = 1)
        Text(text = "▼", color = PP_HEAD, fontSize = 8.sp, maxLines = 1)
    }
}

/** 百分比：带正负号（+x.xx% / -x.xx%） */
private fun ppPct(v: Double): String =
    if (kotlin.math.abs(v) < 5e-3) "0.00%"
    else String.format(Locale.CHINA, "%+.2f%%", v)

/** 百分比：不带正负号（x.xx%），第40轮个股仓位用 */
private fun ppPctPlain(v: Double): String =
    String.format(Locale.CHINA, "%.2f%%", v)

/** 持仓天数：后端 holdDays（交易日数）；无数据时显示 -- */
private fun ppDays(v: Int?): String =
    if (v == null) "--" else String.format(Locale.CHINA, "%d", v)

/** 数量（股）：整数带千分位 */
private fun ppQty(v: Int): String = String.format(Locale.CHINA, "%,d", v)

/** 价格：两位小数 */
private fun ppPrice(v: Double): String = String.format(Locale.CHINA, "%.2f", v)

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
                verticalArrangement = Arrangement.Bottom,   // 第38轮：Center→Bottom，红线贴切换栏底边
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
                //   第38轮：贴底，与下方账户栏上分割线重叠对齐。
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