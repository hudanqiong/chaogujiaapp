package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

/** 首页：头像 + 用户名 + 会员标识 + 总资产 + 会员套餐 + 免责声明 */
@Composable
fun HomeScreen(token: String? = null, username: String = "") {
    var account by remember { mutableStateOf(AccountInfo()) }
    var member by remember { mutableStateOf(MemberInfo()) }
    var loading by remember { mutableStateOf(true) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    // 进入页面时拉一次数据
    LaunchedEffect(Unit) {
        loading = true
        errMsg = null
        try {
            val a = withContext(Dispatchers.IO) { Api.account(token) }
            val m = withContext(Dispatchers.IO) { Api.member(token) }
            account = a
            member = m
        } catch (e: Exception) {
            errMsg = e.message ?: "网络请求失败"
        }
        loading = false
    }

    val money = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 顶部红色渐变区：头像 + 用户名 + 会员标识 + 总资产 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD62830))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // 头像 + 用户名 + 会员标识
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "股",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = username.ifEmpty { "股友" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    // 会员标识小标签
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (member.isMember) member.levelName else "普通用户",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
                // 会员到期时间
                if (member.expireText.isNotEmpty()) {
                    Text(
                        text = member.expireText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 总资产
            Text(
                text = "总资产（元）",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (loading) "--" else money.format(account.total),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "初始资金 ¥" + money.format(account.initial),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }

        // ===== 会员套餐列表 =====
        Spacer(Modifier.height(12.dp))
        Text(
            text = "会员套餐",
            color = Color(Config.COLOR_TEXT_MAIN),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (errMsg != null) {
            Text(
                text = "加载失败：$errMsg",
                color = Color(Config.COLOR_PRIMARY),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (member.plans.isEmpty() && !loading && errMsg == null) {
            Text(
                text = "暂无套餐",
                color = Color(Config.COLOR_TEXT_SUB),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        member.plans.forEach { plan ->
            PlanCard(plan)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))

        // ===== 免责声明 =====
        Text(
            text = "免责声明：本应用所有行情与交易数据均为模拟演示，仅供学习交流使用，不构成任何投资建议。股市有风险，投资需谨慎。",
            color = Color(Config.COLOR_TEXT_SUB),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(20.dp))
    }
}

/** 单个会员套餐卡片 */
@Composable
private fun PlanCard(plan: MemberPlan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.name,
                    color = Color(Config.COLOR_TEXT_MAIN),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (plan.hot) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(Config.COLOR_PRIMARY))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("热销", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
            if (plan.desc.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plan.desc,
                    color = Color(Config.COLOR_TEXT_SUB),
                    fontSize = 12.sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "¥" + plan.price.toInt(),
                color = Color(Config.COLOR_PRIMARY),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (plan.unit.isNotEmpty()) {
                Text(
                    text = plan.unit,
                    color = Color(Config.COLOR_TEXT_SUB),
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(Config.COLOR_PRIMARY))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* TODO: 开通逻辑，后续接 buy 接口 */ }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "开通",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}