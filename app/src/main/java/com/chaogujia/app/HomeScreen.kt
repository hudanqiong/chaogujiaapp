package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap as DrawStrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 首页 = 会员中心。顶部红色由共用 MemberHeaderBar 负责，这里全是白区卡片 */
@Composable
fun HomeScreen(
    token: String? = null,
    username: String = "",
    onLogout: () -> Unit = {},
    onMemberChanged: (MemberInfo) -> Unit = {},
    // 外部（如交易页银证转账）请求打开会员子页：仅当 openSubPageTick 变化且 > 0 时打开一次
    openSubPageTick: Int = 0,
    openSubPageKey: String = ""
) {
    var account by remember { mutableStateOf(AccountInfo()) }
    var member by remember { mutableStateOf(MemberInfo()) }
    var points by remember { mutableStateOf(0) }
    var subPage by remember { mutableStateOf<String?>(null) }
    // 权限网关：进入受限子页前先查 my_permissions.php
    var perm by remember { mutableStateOf(PermissionInfo()) }
    // 无权限时展示的开通引导弹窗（null = 不展示）
    var gateDialog by remember { mutableStateOf<String?>(null) }
    // 外部请求：tick 每次自增表示一次“打开子页”请求（仅变化时触发，不因重进首页重复弹）
    LaunchedEffect(openSubPageTick) {
        if (openSubPageTick > 0 && openSubPageKey.isNotEmpty()) subPage = openSubPageKey
    }
    // 首页资产卡取「默认券商账户」= 列表第一个；下拉切换在交易页
    var brokerAccountId by remember { mutableStateOf(0) }
    // 会员中心公告（app_config.php 的 notice.list）
    var notices by remember { mutableStateOf(listOf<NoticeItem>()) }

    /** 子页 key → 权限网关 feature_key 映射（null = 无需会员权限） */
    fun featureOf(key: String): String? = when (key) {
        "addstock" -> "add_watch"
        "groups" -> "watch_group"
        "simulate" -> "trade_sim"
        "exchange" -> "exchange_vip"
        "invite" -> "invite"
        else -> null
    }

    /** 统一入口：受限功能先校验权限，无权限弹开通引导；否则直接进入子页 */
    fun navTo(key: String) {
        val feat = featureOf(key)
        if (feat != null && !perm.has(feat)) {
            gateDialog = key
        } else {
            subPage = key
        }
    }

    LaunchedEffect(token) {
        try {
            val brokers = withContext(Dispatchers.IO) { Api.brokerList(token) }
            if (brokerAccountId == 0) brokerAccountId = brokers.firstOrNull()?.id ?: 0
            val aS = withContext(Dispatchers.IO) { Api.accountStale(token, brokerAccountId) }
            if (aS != null) account = aS
            val a = withContext(Dispatchers.IO) { Api.account(token, brokerAccountId) }
            val m = withContext(Dispatchers.IO) { Api.member(token) }
            val p = withContext(Dispatchers.IO) { Api.point(token) }
            val pm = withContext(Dispatchers.IO) { Api.permissions(token) }
            val cfg = withContext(Dispatchers.IO) { Api.appConfig() }
            account = a
            member = m
            points = p.points
            perm = pm
            notices = cfg.notice.list
            onMemberChanged(m)
        } catch (e: Exception) {
        }
    }

    if (subPage != null) {
        SubPage(
            key = subPage!!,
            token = token,
            username = username,
            account = account,
            member = member,
            points = points,
            brokerAccountId = brokerAccountId,
            onBack = { subPage = null },
            onRefreshMember = { member = it; onMemberChanged(it) },
            onLogout = onLogout
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // 公告条：有公告才显示（竖向滚动 / 上下翻）
        if (notices.isNotEmpty()) {
            NoticeBar(notices)
            Spacer(Modifier.height(12.dp))
        }
        // 会员状态已合并到顶部红条 MemberHeaderBar（头像/等级/到期时间/续费按钮），此处不再重复展示
        Spacer(Modifier.height(12.dp))
        HomeAssetCard { subPage = "initial" }
        Spacer(Modifier.height(12.dp))
        HomePointCard(points) { navTo(it) }
        Spacer(Modifier.height(12.dp))
        HomeMenuCard { navTo(it) }
        Spacer(Modifier.height(12.dp))
        HomeLogoutCard(onLogout)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "免责声明：本应用所有行情与交易数据均为模拟演示，仅供学习交流使用，不构成任何投资建议。股市有风险，投资需谨慎。",
            color = Color(Config.COLOR_TEXT_SUB),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(20.dp))
    }

    // 无权限时的开通引导弹窗（公共组件，个股页复用）
    if (gateDialog != null) {
        val title = when (gateDialog) {
            "addstock" -> "添加自选股"
            "groups" -> "自选分组管理"
            "simulate" -> "模拟交易"
            "exchange" -> "积分兑换会员"
            "invite" -> "邀请好友"
            else -> "会员功能"
        }
        MemberGateDialog(
            title = title,
            onDismiss = { gateDialog = null },
            onOpen = { gateDialog = null; subPage = "recharge" }
        )
    }
}
// ===== 通用组件 =====
/** 会员专享引导弹窗：首页与个股详情页共用。title=功能名；onOpen=前往开通 */
@Composable
fun MemberGateDialog(title: String, onDismiss: () -> Unit, onOpen: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会员专享", color = Color(Config.COLOR_TEXT_MAIN), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "「$title」为会员专享功能，开通会员后即可使用。是否前往开通？",
                color = Color(Config.COLOR_TEXT_SUB), fontSize = 14.sp
            )
        },
        confirmButton = {
            Text(
                "立即开通",
                color = Color(Config.COLOR_PRIMARY), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = Color(Config.COLOR_TEXT_SUB), fontSize = 14.sp,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    )
}
@Composable
fun CardBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(10.dp)
    ) { content() }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(Config.COLOR_PRIMARY))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
            }
        }
        Text("|", color = Color(Config.COLOR_TEXT_SUB), fontSize = 18.sp)
    }
}

@Composable
fun MenuCell(title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(sub, color = Color(Config.COLOR_TEXT_SUB), fontSize = 10.sp)
    }
}

@Composable
fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(Config.COLOR_DIVIDER))
    )
}

// ===== 首页卡片 =====
/** 会员中心卡：展示当前会员状态，右侧「开通 / 续费」直达收款页 */
@Composable
private fun HomeMemberCard(
    isMember: Boolean,
    levelName: String,
    expireAt: String,
    onOpen: () -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("会员中心", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isMember) Color(Config.COLOR_PRIMARY).copy(alpha = 0.12f)
                                else Color(0xFFEEEEEE)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isMember) levelName else "未开通",
                            color = if (isMember) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_SUB),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isMember && expireAt.isNotEmpty()) "有效期至 $expireAt"
                    else "开通会员，解锁全部会员专享功能",
                    color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() }
                    .background(Color(Config.COLOR_PRIMARY))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    if (isMember) "续费" else "开通会员",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HomeAssetCard(onSetInitial: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("资金设置", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("设置你的初始资金", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSetInitial() }
                    .background(Color(Config.COLOR_PRIMARY).copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("设置初始资金", color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HomePointCard(points: Int, onNav: (String) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("我的积分", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("$points", color = Color(Config.COLOR_PRIMARY), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            SmallBtn("积分明细") { onNav("points") }
            Spacer(Modifier.width(8.dp))
            SmallBtn("兑换会员") { onNav("exchange") }
            Spacer(Modifier.width(8.dp))
            SmallBtn("邀请好友") { onNav("invite") }
        }
    }
}

@Composable
private fun HomeMenuCard(onNav: (String) -> Unit) {
    CardBox {
        Column {
            // 两列网格：每格一个入口（邀请好友已合并到积分卡）
            val items = listOf(
                Triple("券商账户", "绑定券商席位", "broker"),
                Triple("自选分组设置", "管理自选股分组", "groups"),
                Triple("添加自选股", "输入代码加入自选", "addstock"),
                Triple("交易记录管理", "改持仓日期价格", "tradeedit"),
                Triple("一键模拟", "批量生成买卖T", "simulate"),
                Triple("意见反馈", "提交问题与建议", "feedback"),
                Triple("修改密码", "修改登录密码", "pwd")
            )
            items.chunked(2).forEachIndexed { rowIdx, rowItems ->
                if (rowIdx > 0) HDivider()
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEachIndexed { colIdx, it ->
                        if (colIdx > 0) Box(Modifier.width(1.dp).height(44.dp).background(Color(Config.COLOR_DIVIDER)))
                        MenuCell(it.first, it.second, Modifier.weight(1f)) { onNav(it.third) }
                    }
                    if (rowItems.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeLogoutCard(onLogout: () -> Unit) {
    CardBox {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLogout() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("退出登录", color = Color(Config.COLOR_PRIMARY), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
/** 会员中心公告条：白卡 + 📢 + 竖向滚动公告（自动轮播 + 手动上下翻）。 */
@Composable
fun NoticeBar(notices: List<NoticeItem>) {
    if (notices.isEmpty()) return
    var idx by remember { mutableStateOf(0) }
    // 自动轮播：每 3.5 秒切下一条
    LaunchedEffect(notices.size) {
        while (notices.size > 1) {
            kotlinx.coroutines.delay(3500)
            idx = (idx + 1) % notices.size
        }
    }
    val cur = notices[idx.coerceIn(0, notices.size - 1)]
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDCE2", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = cur.title.ifEmpty { "公告" },
                    color = Color(Config.COLOR_TEXT_MAIN),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = cur.content,
                    color = Color(Config.COLOR_TEXT_SUB),
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
            if (notices.size > 1) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u25B2",
                        color = Color(Config.COLOR_TEXT_SUB),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                idx = if (idx <= 0) notices.size - 1 else idx - 1
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "\u25BC",
                        color = Color(Config.COLOR_TEXT_SUB),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                idx = (idx + 1) % notices.size
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
