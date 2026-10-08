package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size


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
    onAccountChange: (String, String) -> Unit = { _, _ -> },
    /**
     * ★ 第 55h-16 修正：账户选择浮层的显示开关改为「受控」——状态提升到 MainActivity。
     *   只有红条 ▼（证券名/代码/小三角）能把 showAccountPicker 置 true，
     *   其余任何区域、任何页面重组都不会误弹。
     */
    showAccountPicker: Boolean = false,
    /** ★ 第 55h-16：浮层关闭回调（点遮罩 / 选中账户后调用，通知父级复位开关）。 */
    onAccountPickerDismiss: () -> Unit = {},
    /** 需求④：点「行情►」进入个股详情页。参数=(可展示列表, 当前项下标) */
    onOpenDetail: (List<StockItem>, Int) -> Unit = { _, _ -> }
) {
    // ★ 第 44 轮修复：页面级协程作用域。
    //   之前账户浮层内部用 rememberCoroutineScope()，点击账户先 showAccountPicker=false
    //   → 浮层分支从组合移除 → 该 scope 立即被取消 → 切换账户的协程被掐断 → account 不更新
    //   （表现为「切换账户后总资产仍是同一个数据」）。改成页面级 scope 后，浮层销毁不影响切换任务。
    val pageScope = rememberCoroutineScope()
    var curTab by remember { mutableStateOf(defaultTab) }
    // ★ 第 55 轮：外部（交易页五按钮）切换 defaultTab 时，同步重设当前 tab。
    //   否则 remember 只在首帧取一次值，同一会话二次以不同 tab 打开不会生效。
    LaunchedEffect(defaultTab) { curTab = defaultTab }

    // ★ 第 34 轮新增：中栏与 6 格统计所需数据
    var account by remember { mutableStateOf(AccountInfo()) }
    var cashAccount by remember { mutableStateOf("") }   // 资金账号（可空）
    val ppCtx = LocalContext.current
    var brokerId by remember { mutableStateOf(0) }

    // ★ 第 39 轮新增：持仓股票列表（下方 9 栏表格的数据源）
    var positions by remember { mutableStateOf(listOf<PositionItem>()) }

    // ★ 第 43 轮新增：账户列表 + 账户选择浮层开关
    var brokerList by remember { mutableStateOf(listOf<MyBroker>()) }
    // ★ 第 55h-16：showAccountPicker 已提升为受控参数，此处不再自持状态。

    /**
     * ★ 第 43 轮：按账户 id 加载「资金 + 持仓」并回吐红条两行文字。
     *   抽成局部函数，供首次进入与切换账户复用。
     */
    suspend fun loadBrokerAccount(b: MyBroker) {
        onAccountChange(b.broker, b.account)
        brokerId = b.id
        // 第48轮：账户联动——切换/加载账户时同步写入全局存储
        AccountStore.set(ppCtx, b.id)
        cashAccount = b.account
        account = withContext(Dispatchers.IO) { Api.account(token, b.id) }
        positions = withContext(Dispatchers.IO) { Api.positions(token, b.id) }
    }

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
                brokerList = list
                // 第48轮：账户联动——优先恢复全局保存的账户，而非固定取第一个
                val picked = AccountStore.pick(ppCtx, list) { it.id }
                if (picked != null) {
                    loadBrokerAccount(picked)
                }
            } catch (_: Throwable) {
            }
        }
        // 第29轮：资产卡片改为每 5 秒自动刷新（只重拉当前账户数据，不重选账户，避免覆盖用户选择）
        while (token.isNotEmpty()) {
            kotlinx.coroutines.delay(5000)
            try {
                val cur = brokerList.firstOrNull { it.id == brokerId }
                if (cur != null) {
                    account = withContext(Dispatchers.IO) { Api.account(token, cur.id) }
                    positions = withContext(Dispatchers.IO) { Api.positions(token, cur.id) }
                }
            } catch (_: Throwable) {
            }
        }
    }

    // ★ 第 55h-16：弹窗触发逻辑已移除——showAccountPicker 由外部（红条 ▼）控制，
    //   本页不再监听任何 tick，页面重组/重新进入不会再误弹。

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PP_BG)
    ) {
        // ===== ② 5 个切换按钮（固定不滚动）=====
        PosTabBar(cur = curTab, onSelect = { curTab = it })

        // ===== ③ 切换按钮以下：整体为滚动区域（第50轮修正）=====
        //   顶部红条 + 切换按钮固定；以下内容随列表行数增减整体滚动，
        //   列表行数少时底部功能区紧跟最后一行，不留无用白布。
        when (curTab) {
            "买入" -> {
                BuySellPanel(
                    token = token,
                    brokerAccountId = brokerId,
                    side = "buy",
                    positions = positions,
                    account = account,
                    onOpenDetail = onOpenDetail
                )
            }
            "卖出" -> {
                BuySellPanel(
                    token = token,
                    brokerAccountId = brokerId,
                    side = "sell",
                    positions = positions,
                    account = account,
                    onOpenDetail = onOpenDetail
                )
            }
            "撤单" -> {
                OrderListPanel(
                    token = token,
                    brokerAccountId = brokerId,
                    onRefresh = {
                        // 刷新撤单列表的逻辑在 OrderListPanel 内部处理
                    }
                )
            }
            "持仓" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
                    PosAccountBar(account = account)
                    Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
                    PosStatGrid(account = account)
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(PP_BG))
                    PosHoldTable(positions = positions, totalAsset = account.total)
                }
            }
            "查询" -> {
                QueryPanel(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onToast = {
                        android.widget.Toast.makeText(ppCtx, "功能建设中", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // ★ 第 43 轮新增：账户选择浮层（盖住整页）。红条 ▼ 点击后出现，
    //   列出全部券商账户，点选后切换 brokerId 并重新加载资金/持仓，同时更新红条两行文字。
    if (showAccountPicker) {
        // 半透明遮罩（点击遮罩也可关闭）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onAccountPickerDismiss()
                }
        )
        // 居中卡片
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "切换账户",
                color = PP_BLACK,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            if (brokerList.isEmpty()) {
                Text(
                    text = "暂无券商账户",
                    color = PP_LABEL,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                )
            } else {
                brokerList.forEach { b ->
                    val selected = b.id == brokerId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onAccountPickerDismiss()
                                // 已在当前账户则无需重拉
                                if (b.id != brokerId) {
                                    // 切换账户需联网 → 用页面级协程执行（浮层销毁不会取消它）
                                    pageScope.launch { try { loadBrokerAccount(b) } catch (_: Throwable) {} }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = b.broker.ifBlank { "券商账户" },
                            color = if (selected) PP_RED else PP_BLACK,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = b.account,
                            color = PP_LABEL,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
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
        //   第50轮：去掉实边框（太深），改用上下两条细浅分割线（PP_LINE 风格）。
        Column(
            modifier = Modifier
                .width(72.dp)
                .height(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(PP_LINE))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                // 框内居中文字（第50轮：保留两位小数）
                Text(
                    text = String.format(Locale.CHINA, "仓位 %.2f%%", posPct),
                    color = PP_BLACK,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(PP_LINE))
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
                // R50c: 后端已提供 dayPnl 字段（= Σ 持仓量 ×(现价 - 昨收)），改用真实当日盈亏。
                value = ppSigned(account.dayPn),
                valueColor = ppPnColor(account.dayPn),
                suffixText = null,
                // 百分比 = 当日参考盈亏 / 总市值 × 100%（市值>0 时）
                pctText = if (account.marketValue > 0.0) ppPct(account.dayPn / account.marketValue * 100.0) else null,
                pctColor = ppPnColor(account.dayPn),
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
    modifier: Modifier = Modifier,
    // 第50轮：可选百分比副值（小 3-4 号、红蓝带符号、与金额下水平对齐）
    pctText: String? = null,
    pctColor: Color = PP_BLACK
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
        // ---- 数值行：深色数字（第50轮：金额 + 其后小号百分比，底对齐）----
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,          // 第37轮：11→13（+2号）
                fontWeight = FontWeight.Bold,   // 第37轮：Medium→Bold（加粗）
                fontFamily = PPNumFont,
                maxLines = 1
            )
            if (pctText != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = pctText,
                    color = pctColor,
                    fontSize = 10.sp,      // 比 13sp 小 3 号
                    fontWeight = FontWeight.Bold,
                    fontFamily = PPNumFont,
                    maxLines = 1
                )
            }
        }
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
private val PP_COLLINE_VISIBLE = Color(0xFFE8E8E8)   // 第50轮修正3b：极淡的浅灰细竖线（比 D9 再淡一档）
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
private fun PosHoldTable(positions: List<PositionItem>, totalAsset: Double, simple: Boolean = false, onPick: ((PositionItem) -> Unit)? = null) {
    // 表头与所有数据行共用同一横向滚动状态 → 左滑同步、第 1 栏钉死
    val hScroll = rememberScrollState()
    val denom = totalAsset
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        if (!simple) PosTableTitleBar()
        // 表头：完整版（与独立持仓页一致，左右可滑）
        PosHoldHeader(hScroll = hScroll)
        Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
        // 列表：完整版行（与独立持仓页一致）
        Column(modifier = Modifier.fillMaxWidth()) {
            positions.forEach { p ->
                PosHoldRow(p = p, denom = denom, hScroll = hScroll, onPick = onPick)
                Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
            }
        }
        // ---------- 底部功能区（仅完整版）----------
        if (!simple) PosBottomBar()
    }
}

/**
 * 第50轮：表头上方标题行。
 *   左：「持仓股」三字（PP_LABEL 浅灰，字号略小于总资产）；
 *   右：两个占位图标（横线+N 字母 / 正方形+三条竖线），后期统一替换。
 */
@Composable
private fun PosTableTitleBar() {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)                       // 第50轮修正：与底部四个按钮行等高
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "持仓股",
                color = PP_BLACK,                    // 第50轮修正：纯黑色
                fontSize = 15.sp,                    // 第50轮修正：与股票名称(15sp)同号
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            // 占位图标 1：横线上面一个 N 字母
            PlaceholderIconN()
            Spacer(Modifier.width(10.dp))
            // 占位图标 2：正方形里面三条竖线
            PlaceholderIconLines()
        }
        // 第50轮修正：标题行与表头之间加浅浅细细的分割线
        Box(modifier = Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
    }
}

/** 占位图标 1：一条横线，上面一个字母 N（用文字绘制，后期替换为真图标） */
@Composable
private fun PlaceholderIconN() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "N", color = PP_LABEL, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(modifier = Modifier.width(14.dp).height(1.dp).background(PP_LABEL))
    }
}

/** 占位图标 2：一个正方形边框，里面三条竖线（后期替换为真图标） */
@Composable
private fun PlaceholderIconLines() {
    Box(
        modifier = Modifier
            .size(14.dp)
            .border(1.dp, PP_LABEL, RoundedCornerShape(1.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) {
                Box(modifier = Modifier.width(1.dp).height(8.dp).background(PP_LABEL))
            }
        }
    }
}

/**
 * 第50轮：底部固定功能区（三行，始终贴屏幕最下方，随列表行数增减自动上下移动）。
 *   第1行（灰底）：最右「查看已清仓股票┛」，┛ 逆时针旋转 45°，字号比总资产小两号，PP_LABEL 色。
 *   第2行（白底）：四个文字按钮「持仓管理丨批量买入丨批量卖出丨止盈止损」，未点亮按钮色，浅浅细细竖线。
 *   第3行（灰底）：两个蓝色标签「持仓资讯丨资产分析」，浅浅细细竖线。
 */
@Composable
private fun PosBottomBar() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------- 第1行：查看已清仓股票 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)                       // 第50轮修正：加高 1/4（原约 28dp → 36dp）
                .background(PP_BG)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看已清仓股票",
                color = PP_LABEL,
                fontSize = 11.sp,      // 比总资产标签小两号
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
            // 「┛」逆时针旋转 45°，与文字垂直居中
            Text(
                text = "┛",
                color = PP_LABEL,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.rotate(-45f)
            )
        }

        // ---------- 第2行：四个文字按钮 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color.White),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items2 = listOf("持仓管理", "批量买入", "批量卖出", "止盈止损")
            items2.forEachIndexed { i, t ->
                if (i > 0) {
                    // 浅浅细细的竖线，不上下顶到底，居中略高，约字体高度
                    Box(
                        modifier = Modifier
                            .width(0.6.dp)
                            .height(13.dp)
                            .background(PP_COLLINE)
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t,
                        color = PP_GRAY,   // 未点亮切换按钮色
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // ---------- 第3行：两个蓝色标签 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(PP_BG),
            horizontalArrangement = Arrangement.Center,   // 第50轮修正2：两标签整体居中
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 第50轮修正2：两个标签整体居中于屏幕底部正中间，中间一条可见的细竖线
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "持仓资讯",
                    color = PP_BLUE,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(0.8.dp)
                        .height(14.dp)
                        .background(PP_COLLINE_VISIBLE)
                )
                Text(
                    text = "资产分析",
                    color = PP_BLUE,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
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
            contentAlignment = Alignment.CenterStart
        ) {
            // 第41轮：第1栏表头改为左对齐（与下方股票名/持仓金额同一左基线）
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
private fun PosHoldRow(p: PositionItem, denom: Double, hScroll: ScrollState, onPick: ((PositionItem) -> Unit)? = null) {
    // 第41轮：个股仓位% = 该股市值 / 账户总资产（与顶栏仓位%同口径）
    val holdPct = if (denom > 0.0) (p.marketValue / denom) * 100.0 else 0.0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PP_ROW_H)
            .background(Color.White)
            .then(if (onPick != null) Modifier.clickable { onPick(p) } else Modifier)
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
            // --- 当日盈亏（两行）：R51 改用后端 dayPnl（= (现价-昨收)×数量）---
            //   如此列表各股 dayPnl 加总 ≡ 顶部「当日参考盈亏」（account.dayPn），口径守恒
            PpCellTwo(
                top = ppSigned(p.dayPnl),
                topColor = ppPnColor(p.dayPnl),
                bottom = if (p.avgCost > 0.0 && p.quantity > 0)
                    ppPct(p.dayPnl / (p.avgCost * p.quantity) * 100.0) else ppPct(0.0),
                bottomColor = ppPnColor(p.dayPnl)
            )
            // --- 个股仓位（单行）：该股市值 / 全部持仓市值合计；第40轮：红蓝、去正负号 ---
            PpCellOne(text = ppPctPlain(holdPct), color = ppPnColor(p.pnl))
            // --- 持仓天数（单行）：第40轮接后端 holdDays（交易日数）；第43轮：随盈亏红蓝 ---
            PpCellOne(text = ppDays(p.holdDays), color = ppPnColor(p.pnl))
            // --- 止盈止损（单行）：第44轮：由黑色 -- 改为随盈亏红蓝显示（红赚蓝亏）---
            PpCellOne(text = ppSigned(p.pnl), color = ppPnColor(p.pnl))
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
                // 第42轮：股票名字号再加大一号(14→15sp)；其余保持第41轮口径
                Text(
                    text = p.name,
                    color = ppPnColor(p.pnl),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = ppMoney(p.marketValue),
                    color = ppPnColor(p.pnl),
                    fontSize = 13.sp,
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
        // 第41轮：数据字号加大 1 号(12→13sp)；两行间距加大一倍(3→6dp)
        Text(text = top, color = topColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(text = bottom, color = bottomColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
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
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, maxLines = 1)
    }
}

/** 一对浅灰三角（上尖 / 下尖 叠放），跟在每栏表头文字后面；第41轮：上下间距再压缩到一半 */
@Composable
private fun PpSortArrows() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "▲", color = PP_HEAD, fontSize = 7.sp, lineHeight = 5.sp, maxLines = 1)
        Text(text = "▼", color = PP_HEAD, fontSize = 7.sp, lineHeight = 5.sp, maxLines = 1)
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
private fun ppPrice(v: Double): String = String.format(Locale.CHINA, "%.3f", v)

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
/** R54-v2: 买入/卖出面板 - 按光大证券买入界面高度还原
 *  上半部分：顶部信息行(均价/最新/涨跌幅/行情) + 左右7:3分栏(左输入区+右五档盘口) + 底部信息行(个股%/总仓位%/预估成本)
 *  下半部分：4标签(持仓/委托/成交/自选) + 对应列表
 */
@Composable
private fun BuySellPanel(
    token: String,
    brokerAccountId: Int,
    side: String,         // "buy" or "sell"
    positions: List<PositionItem>,
    account: AccountInfo,
    onOpenDetail: (List<StockItem>, Int) -> Unit = { _, _ -> }
) {
    var code by remember { mutableStateOf("") }
    var stockName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var orderBook by remember { mutableStateOf<OrderBook?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    var showGear by remember { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(false) } // 展开/收起盘口明细
    var quickRatio by remember { mutableStateOf(0) } // 0=全仓,1=1/2仓,2=1/3仓,3=1/4仓
    var subTab by remember { mutableStateOf("持仓") } // 下半部分4标签
    val scope = rememberCoroutineScope()
    val red = PP_RED
    val green = Color(0xFF00B800)
    val gray = PP_GRAY
    val labelGray = PP_LABEL
    val bgFill = PP_BG
    val lineColor = PP_LINE
    val black = PP_BLACK
    val blue = PP_BLUE
    val yellowData = Color(0xFFFFAA00) // 黄色数据
    // R55h-18 卖出页专属色
    val sellBlue = Color(0xFF4691EE)      // 边框/加减号/卖出按钮
    val sellFill = Color(0xFFF5F8FD)      // 加减号区域填充
    val boxLine = if (side == "sell") sellBlue else red
    val minusFill = if (side == "sell") sellFill else Color(0xFFFDF5F3)
    val lightGray = Color(0xFFCCCCCC)
    val veryLightBorder = Color(0xFFE0E0E0) // 极浅边框
    val hasCode = code.isNotBlank()

    // 查询五档
    LaunchedEffect(code) {
        val c = code.trim()
        if (c.isBlank()) { orderBook = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(250)  // 防抖：停止输入 250ms 后再查
        try {
            val ob = withContext(Dispatchers.IO) { Api.orderbook(c) }
            orderBook = ob
            android.util.Log.d("CGJS", "orderbook c=" + c + " got=" + (ob != null) + " price=" + (ob?.price ?: -1.0))
            if (ob != null && ob.price > 0) {
                price = "%.3f".format(ob.price)
            }
            if (ob != null && ob.name.isNotBlank()) {
                stockName = ob.name
            } else if (ob == null && c.isNotBlank()) {
                stockName = ""
            }
        } catch (e: Exception) {
            android.util.Log.e("CGJS", "orderbook c=" + c + " EX=" + e)
        }
    }

    // 计算涨跌停价
    val ob = orderBook
    val prevClose = ob?.prevClose ?: 0.0
    val upperLimit = if (ob != null && ob.limitUp > 0) "%.3f".format(ob.limitUp) else if (prevClose > 0) "%.3f".format(prevClose * 1.1) else ""
    val lowerLimit = if (ob != null && ob.limitDown > 0) "%.3f".format(ob.limitDown) else if (prevClose > 0) "%.3f".format(prevClose * 0.9) else ""
    val curPrice = ob?.price ?: 0.0
    val changePct = if (prevClose > 0 && hasCode) ((curPrice - prevClose) / prevClose * 100) else 0.0
    val avgPrice = ob?.avgPrice ?: 0.0 // 后端提供当日均价
    val availStr = if (side == "buy") {
        val avail = account.available
        val p = price.toDoubleOrNull() ?: 0.0
        if (p > 0 && hasCode) (avail / p / 100L).toInt() * 100 else -1
    } else {
        val pos = positions.find { it.code == code.trim() }
        (pos?.quantity ?: 0) - 0
    }

    // ===== R55h-16-滚动：买入/卖出面板（五个指令以下）整体上下滚动 =====
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(Color.White)) {
        // ===== 上半部分 =====

        // --- 顶部信息行（输入代码后才显示）---
        if (hasCode) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 均价: 黄色数据
                Text("均价:", color = Color(0xFFF4A939), fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Text(if (avgPrice > 0) "%.3f".format(avgPrice) else "--", color = Color(0xFFF4A939), fontSize = 12.sp, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Spacer(Modifier.width(6.dp))
                // 最新: 黑色数据
                Text("最新:", color = black, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Text(if (curPrice > 0) "%.3f".format(curPrice) else "--", color = black, fontSize = 12.sp, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Spacer(Modifier.width(6.dp))
                // 涨跌幅（红涨绿跌平黑，只有百分比无标签）
                val pctColor = when {
                    changePct > 0 -> red
                    changePct < 0 -> green
                    else -> black
                }
                Text("%.2f%%".format(changePct), color = pctColor, fontSize = 12.sp, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Spacer(Modifier.width(6.dp))
                // 行情┛（浅灰色，符号逆时针45°即尖头朝右 ►）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // 需求④：进入个股详情页。用持仓列表构造可翻页列表，定位当前代码。
                        if (code.isNotBlank()) {
                            val lst = positions.map { StockItem(code = it.code, name = it.name, price = it.price, change = it.price - (if (it.avgCost > 0) it.avgCost else 0.0)) }
                            val idx = lst.indexOfFirst { it.code == code }.let { if (it < 0) 0 else it }
                            onOpenDetail(lst, idx)
                        }
                    }
                ) {
                    Text("行情", color = Color(0xFFD4D4D4), fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    // 需求8：图标改用 ┛ 并逆时针旋转45°（=尖头朝右），与文字上下居中
                    Text("┛", color = Color(0xFFD4D4D4), fontSize = 12.sp, modifier = Modifier.rotate(-45f), style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                }
                Spacer(Modifier.weight(1f))
                // 右侧：齿轮图标+展开/收起
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙", color = gray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Spacer(Modifier.width(2.dp))
                    val expandLabel = if (panelExpanded) "收起" else "展开"
                    // 问题5：展开=╝ 顺时针45°（尖头朝下）；收起=镜像 ╔ 顺时针45°（尖头朝上）
                    val expandSymbol = if (panelExpanded) "╝" else "╔"
                    Text(expandLabel, color = gray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Text(expandSymbol, color = gray, fontSize = 12.sp, modifier = Modifier.rotate(45f), style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                }
            }
        }

        // --- 中部：左右7:3分栏 ---
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // ===== 左侧 70% =====
            Column(
                modifier = Modifier
                    .weight(0.66f)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            ) {
                // --- 代码框：无圆角，红边 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .border(1.dp, boxLine)
                        .background(Color.White)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = black, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, textAlign = TextAlign.Left),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { inner ->
                            if (code.isEmpty()) {
                                Text("股票代码/简拼", color = Color(0xFFD4D4D4), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            inner()
                        }
                    )
                    // R55h-17 需求1：名称右对齐叠加，不参与代码居中的宽度计算
                    if (stockName.isNotEmpty()) {
                        Text(stockName, color = black, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
                Spacer(Modifier.height(8.dp))

                // --- 价格框：无圆角，+/-按钮共用竖线 ---
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp).border(1.dp, boxLine),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // - 按钮
                    Box(
                        modifier = Modifier
                            .width(32.dp).fillMaxHeight()
                            .background(minusFill)
.drawBehind { drawLine(boxLine, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                            .clickable {
                                val p = price.toDoubleOrNull() ?: 0.0
                                if (p > 0.01) price = "%.3f".format(p - 0.01)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("−", color = boxLine, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    // 价格输入（共用竖线：左边-按钮的右边框就是竖线，这里不加左边框）
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = price,
                            onValueChange = { price = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            textStyle = TextStyle(color = black, fontSize = 12.sp, fontFamily = PPNumFont, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { inner ->
                                if (price.isEmpty()) {
                                    Text("价格", color = Color(0xFFD4D4D4), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                                inner()
                            }
                        )
                    }
                    // + 按钮
                    Box(
                        modifier = Modifier
                            .width(32.dp).fillMaxHeight()
                            .background(minusFill)
.drawBehind { drawLine(boxLine, Offset(0.5.dp.toPx(), 0f), Offset(0.5.dp.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                            .clickable {
                                val p = price.toDoubleOrNull() ?: 0.0
                                price = "%.3f".format(p + 0.01)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = boxLine, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }

                // --- 涨跌停 / 可买提示（价格框与数量框之间）---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasCode && lowerLimit.isNotEmpty()) {
                        // 跌停+跌停价（胶囊圆角边框）
                        Row(
                            modifier = Modifier
                                .border(1.dp, lightGray, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("跌停", color = black, fontSize = 12.sp)
                            Spacer(Modifier.width(2.dp))
                            Text(lowerLimit, color = green, fontSize = 10.sp, fontFamily = PPNumFont, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("--", color = black, fontSize = 9.sp)
                    }
                    if (hasCode && upperLimit.isNotEmpty()) {
                        // 涨停+涨停价（胶囊圆角边框）
                        Row(
                            modifier = Modifier
                                .border(1.dp, lightGray, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("涨停", color = black, fontSize = 12.sp)
                            Spacer(Modifier.width(2.dp))
                            Text(upperLimit, color = red, fontSize = 10.sp, fontFamily = PPNumFont, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("--", color = black, fontSize = 9.sp)
                    }
                }

                // --- 数量框：无圆角，+/-共用竖线 ---
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp).border(1.dp, boxLine),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp).fillMaxHeight()
                            .background(minusFill)
.drawBehind { drawLine(boxLine, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                            .clickable {
                                val q = quantity.toIntOrNull() ?: 0
                                if (q >= 100) quantity = (q - 100).toString()
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("−", color = boxLine, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            textStyle = TextStyle(color = black, fontSize = 12.sp, fontFamily = PPNumFont, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            decorationBox = { inner ->
                                if (quantity.isEmpty()) {
                                    Text("数量", color = Color(0xFFD4D4D4), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                                inner()
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(32.dp).fillMaxHeight()
                            .background(minusFill)
.drawBehind { drawLine(boxLine, Offset(0.5.dp.toPx(), 0f), Offset(0.5.dp.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                            .clickable {
                                val q = quantity.toIntOrNull() ?: 0
                                quantity = (q + 100).toString()
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = boxLine, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }

                // --- 可买 N 股 ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (side == "sell") {
                        val sellAvail = if (hasCode && availStr >= 0) availStr else 0
                        Text("可卖 ", color = labelGray, fontSize = 12.sp)
                        Text(sellAvail.toString(), color = yellowData, fontSize = 12.sp, fontFamily = PPNumFont)
                        Text(" 股", color = labelGray, fontSize = 12.sp)
                    } else {
                        Text("可买 ", color = labelGray, fontSize = 12.sp)
                        if (hasCode && availStr >= 0) {
                            Text(availStr.toString(), color = yellowData, fontSize = 12.sp, fontFamily = PPNumFont)
                        } else {
                            Text("--", color = black, fontSize = 9.sp)
                        }
                        Text(" 股", color = labelGray, fontSize = 12.sp)
                    }
                }

                // --- 快捷仓位按钮：矮框、极浅极细边框 ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ratios = listOf("全仓" to 0, "1/2仓" to 1, "1/3仓" to 2, "1/4仓" to 3)
                    ratios.forEach { (label, idx) ->
                        val selected = quickRatio == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .border(0.5.dp, veryLightBorder, RoundedCornerShape(2.dp))
                                .background(Color.White, RoundedCornerShape(2.dp))
                                .clickable {
                                    quickRatio = idx
                                    val p = price.toDoubleOrNull() ?: 0.0
                                    if (p > 0) {
                                        val avail = if (side == "buy") account.available else {
                                            val pos = positions.find { it.code == code.trim() }
                                            (pos?.quantity ?: 0).toDouble()
                                        }
                                        val maxQty = (avail / p / 100).toInt() * 100
                                        val qty = when (idx) {
                                            0 -> maxQty
                                            1 -> maxQty / 2 / 100 * 100
                                            2 -> maxQty / 3 / 100 * 100
                                            3 -> maxQty / 4 / 100 * 100
                                            else -> 0
                                        }
                                        quantity = qty.toString()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = gray, fontSize = 11.sp)
                        }
                    }
                }

                // --- 齿轮设置 + 买入/卖出按钮 ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(0.5.dp, veryLightBorder, RoundedCornerShape(2.dp))
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .clickable { showGear = !showGear },
                        contentAlignment = Alignment.Center
                    ) { Text("⚙", color = gray, fontSize = 16.sp) }

                    val btnLabel = if (side == "buy") "买入" else "卖出"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(boxLine, RoundedCornerShape(4.dp))
                            .clickable {
                                val p = price.toDoubleOrNull() ?: 0.0
                                val q = quantity.toIntOrNull() ?: 0
                                if (code.isBlank() || q <= 0) {
                                    resultMsg = "请填写完整"
                                } else if (q % 100 != 0) {
                                    resultMsg = "A股数量须为 100 的整数倍"
                                } else if (p <= 0) {
                                    resultMsg = "请填写有效价格"
                                } else {
                                    scope.launch {
                                        isLoading = true
                                        resultMsg = ""
                                        // R55fix-A：统一下单数据层，线程调度/异常兜底由 Api.placeOrder 负责
                                        val res = Api.placeOrder(token, brokerAccountId, side == "buy", code.trim(), stockName, q, "quick", p)
                                        resultMsg = if (res.ok) "${if (side == "buy") "买入" else "卖出"}成交：${res.quantity} 股 @ ${ppMoney(res.price)}" else "失败：${res.msg}"
                                        isLoading = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(btnLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (resultMsg.isNotEmpty()) {
                    Text(resultMsg, color = if (resultMsg.contains("成交")) (if (side == "buy") red else blue) else red, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (showGear) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).border(0.5.dp, lineColor, RoundedCornerShape(3.dp)).background(bgFill, RoundedCornerShape(3.dp)).padding(6.dp)
                    ) {
                        Text("委托设置", color = black, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text("有效期：当日有效", color = labelGray, fontSize = 10.sp)
                        Text("委托方式：限价委托", color = labelGray, fontSize = 10.sp)
                    }
                }
            }

            // ===== 右侧 30%：五档盘口（极浅极细整体边框）=====
            Column(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .padding(end = 8.dp, top = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(0.5.dp, veryLightBorder)
                        .background(Color.White)
                        .padding(horizontal = 5.dp, vertical = 5.dp)
                ) {
                    if (ob != null) {
                        // 卖5→卖1
                        ob.asks.reversed().forEachIndexed { idx, level ->
                            BSLevelRow(modifier = Modifier.weight(1f), label = "卖${5 - idx}", price = level.price, vol = level.vol, isBuy = false, prevClose = ob.prevClose, onPriceClick = { price = it })
                        }
                        // 买1卖1之间横分割线
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(lineColor))
                        // 买1→买5
                        ob.bids.forEachIndexed { idx, level ->
                            BSLevelRow(modifier = Modifier.weight(1f), label = "买${idx + 1}", price = level.price, vol = level.vol, isBuy = true, prevClose = ob.prevClose, onPriceClick = { price = it })
                        }
                    } else {
                        for (i in 5 downTo 1) {
                            BSLevelRow(modifier = Modifier.weight(1f), label = "卖$i", price = 0.0, vol = 0.0, isBuy = false, prevClose = 0.0)
                        }
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(lineColor))
                        for (i in 1..5) {
                            BSLevelRow(modifier = Modifier.weight(1f), label = "买$i", price = 0.0, vol = 0.0, isBuy = true, prevClose = 0.0)
                        }
                    }
                }
            }
        }

        // --- 底部信息行（输入代码后才显示）---
        if (hasCode) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 需求5/6/7：个股/总仓位 标签在前 + 一个空格 + X.XX%/X.XX%
                val posItem = positions.find { it.code == code.trim() }
                val stockPct = if (account.total > 0.0 && posItem != null) posItem.marketValue / account.total * 100.0 else 0.0
                val totalPct = if (account.total > 0.0) account.marketValue / account.total * 100.0 else 0.0
                if (side == "sell") {
                    // R55h-18 卖出页：个股仓位(最左) + 总仓位(行中间)，间距加大，去掉预估加仓后成本
                    Text("个股仓位", color = labelGray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Spacer(Modifier.width(4.dp))
                    Text("%.2f%%".format(stockPct), color = Color(0xFFFE7902), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Spacer(Modifier.weight(1f))
                    Text("总仓位", color = labelGray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Spacer(Modifier.width(4.dp))
                    Text("%.2f%%".format(totalPct), color = Color(0xFFFE7902), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Spacer(Modifier.weight(1f))
                } else {
                Text("个股/总仓位", color = labelGray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Spacer(Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%.2f%%".format(stockPct), color = Color(0xFFFE7902), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Text("/", color = labelGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Text("%.2f%%".format(totalPct), color = Color(0xFFFE7902), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                }
                Spacer(Modifier.width(10.dp))
                // 需求8：预估加仓后成本；有持仓且有输入数量才计算，否则 --
                val addQty = quantity.toIntOrNull() ?: 0
                val addPrice = price.toDoubleOrNull() ?: 0.0
                val estCost = if (posItem != null && posItem.quantity > 0 && addQty > 0 && addPrice > 0.0)
                    (posItem.avgCost * posItem.quantity + addPrice * addQty) / (posItem.quantity + addQty) else null
                Text("预估加仓后成本", color = labelGray, fontSize = 12.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                Text(if (estCost != null) "%.3f".format(estCost) else "--", color = Color(0xFFFE7902), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)))
                }
            }
        }

        // ===== 下半部分：4标签 + 对应列表 =====
        // 灰底分隔带：往下移（上方留白，不再紧贴上方）
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).background(PP_BG))
        val bottomTabs = if (side == "sell") listOf("持仓", "委托", "成交") else listOf("持仓", "委托", "成交", "自选")
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).height(36.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { t ->
                val selected = t == subTab
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { subTab = t },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(t, color = if (selected) red else gray, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // 切换按钮行与下方列表表头之间的细浅分割线
        Box(Modifier.fillMaxWidth().height(0.4.dp).background(PP_LINE))
        // 下半部分内容
        when (subTab) {
            "持仓" -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PosHoldTable(positions = positions, totalAsset = account.total, simple = true, onPick = { pi ->
                        code = pi.code
                        stockName = pi.name
                    })
                }
            }
            "委托" -> {
                OrderListPanel(token = token, brokerAccountId = brokerAccountId, noScroll = true)
            }
            "自选" -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PosHoldTable(positions = positions, totalAsset = account.total, simple = true)
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(60.dp))
                    Text("建设中…", color = gray, fontSize = 14.sp)
                }
            }
        }
    }
}

/** R54-v2: 五档单行 */
@Composable
private fun BSLevelRow(
    modifier: Modifier = Modifier,
    label: String,
    price: Double,
    vol: Double,
    isBuy: Boolean,
    prevClose: Double,
    onPriceClick: (String) -> Unit = {}
) {
    val red = PP_RED
    val green = Color(0xFF00B800)
    // 需求⑪：价格 涨红/跌绿/平黑（与前收比）
    val priceColor = when {
        price <= 0.0 -> PP_BLACK
        prevClose <= 0.0 -> PP_BLACK
        price > prevClose -> red
        price < prevClose -> green
        else -> PP_BLACK
    }
    // 需求4：价格保留两位小数，整数部分≥5位时缩小字号以挤入一排
    val priceStr = if (price > 0) "%.2f".format(price) else "--"
    // 问题3：按总字符长度分档，位数越多字号越小，保证完整显示（六位数如 123456.78 = 9字符）
    val priceLen = priceStr.length
    val priceFontSize = when {
        priceLen <= 6 -> 13.sp
        priceLen == 7 -> 11.sp
        priceLen == 8 -> 10.sp
        priceLen == 9 -> 9.sp
        priceLen == 10 -> 8.sp
        priceLen == 11 -> 7.sp
        else -> 6.sp
    }
    // 需求4：数量保留四位（<10000 原样），≥10000 以“万”为单位且字号小一点
    val volStr: String
    val volFontSize: androidx.compose.ui.unit.TextUnit
    if (vol <= 0.0) {
        volStr = "--"; volFontSize = 12.sp
    } else if (vol < 10000.0) {
        volStr = vol.toLong().toString(); volFontSize = 12.sp
    } else {
        val w = vol / 10000.0
        // 问题4：仅拼接一次“万”，避免出现两个“万”；整数或≥100万取整，否则保留2位小数
        volStr = if (w >= 100 || w == Math.floor(w)) "%d万".format(w.toInt()) else "%.2f万".format(w)
        volFontSize = 10.sp
    }
    // 需求⑪：量黑色
    val bsText = Color(0xFF000000)

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = bsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(priceStr, color = priceColor, fontSize = priceFontSize, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, modifier = Modifier.weight(1f).clickable { if (price > 0) onPriceClick(priceStr) }, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        Text(volStr, color = bsText, fontSize = volFontSize, fontWeight = FontWeight.Bold, fontFamily = PPNumFont, modifier = Modifier.width(38.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}


/** R54: 撤单列表面板 */
@Composable
private fun OrderListPanel(
    token: String,
    brokerAccountId: Int,
    noScroll: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var orders by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val red = PP_RED
    val labelGray = PP_LABEL
    val black = PP_BLACK
    val lineColor = PP_LINE

    // 加载委托列表
    fun loadOrders() {
        isLoading = true
        msg = ""
        try {
            orders = Api.orderList(token, brokerAccountId)
        } catch (e: Exception) {
            msg = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadOrders() }

    val rootMod = if (noScroll) Modifier.fillMaxWidth().background(Color.White)
        else Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(Color.White)
    Column(
        modifier = rootMod
    ) {
        // 刷新按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text("刷新", color = red, fontSize = 13.sp,
                modifier = Modifier.clickable { scope.launch { loadOrders() } })
        }

        if (isLoading) {
            Box(modifier = (if (noScroll) Modifier.fillMaxWidth().padding(vertical = 48.dp) else Modifier.fillMaxWidth().weight(1f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = red, modifier = Modifier.size(24.dp))
            }
        } else if (msg.isNotEmpty()) {
            Box(modifier = (if (noScroll) Modifier.fillMaxWidth().padding(vertical = 48.dp) else Modifier.fillMaxWidth().weight(1f)), contentAlignment = Alignment.Center) {
                Text(msg, color = red, fontSize = 13.sp)
            }
        } else if (orders.isEmpty()) {
            Box(modifier = (if (noScroll) Modifier.fillMaxWidth().padding(vertical = 48.dp) else Modifier.fillMaxWidth().weight(1f)), contentAlignment = Alignment.Center) {
                Text("暂无委托", color = labelGray, fontSize = 14.sp)
            }
        } else {
            // 表头
            Row(
                modifier = Modifier.fillMaxWidth().background(PP_BG).padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("股票", color = labelGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("买卖", color = labelGray, fontSize = 11.sp, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                Text("价格", color = labelGray, fontSize = 11.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                Text("数量", color = labelGray, fontSize = 11.sp, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                Text("操作", color = labelGray, fontSize = 11.sp, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
            }
            orders.forEach { om ->
                val oCode = om.code
                val oName = om.name
                val oSide = if (om.direction == 1) "买" else "卖"
                val oPrice = "%.3f".format(om.price)
                val oQty = om.quantity.toString()
                val oId = om.id.toString()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(oName, color = black, fontSize = 12.sp)
                        Text(oCode, color = labelGray, fontSize = 10.sp)
                    }
                    Text(oSide, color = if (oSide == "买") red else Color(0xFF00B800), fontSize = 12.sp, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                    Text(oPrice, color = black, fontSize = 12.sp, fontFamily = PPNumFont, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                    Text(oQty, color = black, fontSize = 12.sp, fontFamily = PPNumFont, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                    Text("撤单", color = red, fontSize = 12.sp,
                        modifier = Modifier.weight(0.4f).clickable {
                            scope.launch {
                                try {
                                    val res = Api.orderCancel(token, oId.toInt())
                                    if (res.ok) loadOrders() else msg = "撤单失败: ${res.msg}"
                                } catch (e: Exception) {
                                    msg = "错误: ${e.message}"
                                }
                            }
                        }, textAlign = TextAlign.Center)
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(lineColor))
            }
        }
    }
}


// ============================================================================
// 查询页（第 55h-20 轮新增）
//   布局：6 栏，每栏灰底分隔；栏内标题左对齐(黑 18sp)、符号 ┗ 右对齐(浅灰 18sp，逆时针旋转 90°)
//   6 栏内容：
//     栏1：当日成交 / 当日委托
//     栏2：历史成交 / 历史委托
//     栏3：已清仓股票
//     栏4：对账单
//     栏5：转账记录
//     栏6：T操作
//   最后一栏下方灰底：提示文字（其中「新股申购 -- 查询页面」为蓝色）
//   页面最底部（导航条上方）：功能帮助（蓝色、居中）
//   整页不滚动。所有可点项点击 → Toast「功能建设中」。
// ============================================================================

private val Q_TEXT_BLACK = Color(0xFF222222)   // 标题黑
private val Q_TEXT_GRAY  = Color(0xFFBBBBBB)   // 符号浅灰
private val Q_BLUE       = Color(0xFF2A7BFF)   // 蓝色（提示 & 功能帮助）
private val Q_BG         = Color(0xFFF5F5F5)   // 栏间灰底
private val Q_LINE       = Color(0xFFF0F0F0)   // 栏内两行之间极细浅灰线
private val Q_FS         = 18.sp               // 与顶部证券名称同字号

/** 查询页 6 栏的数据模型：一栏内含 1~2 行标题 */
private val QUERY_GROUPS = listOf(
    listOf("当日成交", "当日委托"),
    listOf("历史成交", "历史委托"),
    listOf("已清仓股票"),
    listOf("对账单"),
    listOf("转账记录"),
    listOf("T操作")
)

/** 单行：标题（左，黑） + 符号 ┗（右，浅灰，逆时针旋转 90°，与标题垂直居中对齐） */
@Composable
private fun QueryRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = Q_TEXT_BLACK,
            fontSize = Q_FS,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 与交易页列表项右端同款图标（天生朝右）
        Image(
            painter = painterResource(R.drawable.ic_pnl_arrow),
            contentDescription = "进入",
            modifier = Modifier
                .width(9.dp)
                .height(15.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
    }
}

/** 一栏：1~2 行，行间用极细浅灰线分隔；栏外上下用灰底分隔 */
@Composable
private fun QueryGroup(rows: List<String>, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        rows.forEachIndexed { idx, title ->
            if (idx > 0) {
                // 栏内两行之间的极细浅灰分割线（参考持仓页）
                Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.4.dp).background(Q_LINE))
            }
            QueryRow(title = title, onClick = onClick)
        }
    }
}

/**
 * 查询面板：6 栏 + 底部提示 + 功能帮助。整页不滚动（用 Column + weight 撑满）。
 */
@Composable
private fun QueryPanel(modifier: Modifier = Modifier, onToast: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Q_BG)
    ) {
        // ===== 6 栏：栏与栏之间用灰底条分隔（一根牙签直径 ≈ 2dp）=====
        QUERY_GROUPS.forEachIndexed { idx, rows ->
            if (idx > 0) {
                // 栏间灰底分隔（加高，视觉更清晰）
                Box(Modifier.fillMaxWidth().height(10.dp).background(Q_BG))
            }
            QueryGroup(rows = rows, onClick = onToast)
        }

        // ===== 最后一栏下方的灰底区域：提示文字 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Q_BG)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToast() },
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "新股配号、中签查询，请前往",
                    color = Q_TEXT_GRAY,
                    fontSize = 14.sp
                )
                Text(
                    text = "新股申购 -- 查询页面",
                    color = Q_BLUE,
                    fontSize = 14.sp
                )
            }
        }

        // ===== 页面最底部（导航条上方）：功能帮助（蓝色、居中）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToast() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "功能帮助",
                color = Q_BLUE,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
