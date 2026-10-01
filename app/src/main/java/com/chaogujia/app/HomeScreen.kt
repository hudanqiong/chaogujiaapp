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
    // 外部请求：tick 每次自增表示一次“打开子页”请求（仅变化时触发，不因重进首页重复弹）
    LaunchedEffect(openSubPageTick) {
        if (openSubPageTick > 0 && openSubPageKey.isNotEmpty()) subPage = openSubPageKey
    }
    // 首页资产卡取「默认券商账户」= 列表第一个；下拉切换在交易页
    var brokerAccountId by remember { mutableStateOf(0) }

    LaunchedEffect(token) {
        try {
            val brokers = withContext(Dispatchers.IO) { Api.brokerList(token) }
            if (brokerAccountId == 0) brokerAccountId = brokers.firstOrNull()?.id ?: 0
            val a = withContext(Dispatchers.IO) { Api.account(token, brokerAccountId) }
            val m = withContext(Dispatchers.IO) { Api.member(token) }
            val p = withContext(Dispatchers.IO) { Api.point(token) }
            account = a
            member = m
            points = p.points
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
        HomeAssetCard { subPage = "initial" }
        Spacer(Modifier.height(12.dp))
        HomePointCard(points) { subPage = it }
        Spacer(Modifier.height(12.dp))
        HomeMenuCard { subPage = it }
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
}

// ===== 通用组件 =====
@Composable
fun CardBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
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
            .padding(vertical = 12.dp),
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
fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(Config.COLOR_DIVIDER))
    )
}

// ===== 首页卡片 =====
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
        }
    }
}

@Composable
private fun HomeMenuCard(onNav: (String) -> Unit) {
    CardBox {
        Column {
            MenuRow("邀请好友", "邀好友开卡得积分") { onNav("invite") }
            HDivider()
            MenuRow("券商账户", "绑定你的券商席位") { onNav("broker") }
            HDivider()
            MenuRow("自选分组设置", "管理自选股分组") { onNav("groups") }
            HDivider()
            MenuRow("添加自选股", "输入代码加入自选") { onNav("addstock") }
            HDivider()
            MenuRow("交易记录管理", "修改持仓记录日期与价格") { onNav("tradeedit") }
            HDivider()
            MenuRow("一键模拟", "批量生成买卖T记录") { onNav("simulate") }
            HDivider()
            MenuRow("修改密码", "修改登录密码") { onNav("pwd") }
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
