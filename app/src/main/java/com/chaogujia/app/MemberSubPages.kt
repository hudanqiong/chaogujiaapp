package com.chaogujia.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 子页面路由 */
@Composable
fun SubPage(
    key: String,
    token: String?,
    username: String,
    account: AccountInfo,
    member: MemberInfo,
    points: Int,
    brokerAccountId: Int = 0,
    onBack: () -> Unit,
    onRefreshMember: (MemberInfo) -> Unit,
    onLogout: () -> Unit
) {
    when (key) {
        "initial" -> PageInitial(token, brokerAccountId, account, onBack)
        "points" -> PagePoints(token, points, onBack)
        "invite" -> PageInvite(token, onBack)
        "exchange" -> PageExchange(token, member, onBack, onRefreshMember)
        "broker" -> PageBroker(token, onBack)
        "groups" -> PageGroups(token, onBack)
        "addstock" -> PageAddStock(token, onBack)
        "pwd" -> PagePwd(token, onBack)
        "tradeedit" -> PageTradeEdit(token, brokerAccountId, onBack)
        "simulate" -> PageSimulate(token, brokerAccountId, onBack)
        "feedback" -> PageFeedback(token, onBack)
        "recharge" -> PageRecharge(token, onBack)
    }
}

/** 子页面外壳：返回栏 + 内容 */
@Composable
fun SubShell(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("←", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 20.sp)
            }
            Text(title, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            content()
        }
    }
}

/** 输入框 */
@Composable
fun InputBox(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 大按钮 */
@Composable
fun BigBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(Config.COLOR_PRIMARY))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== 1. 初始资金 =====
@Composable
private fun PageInitial(token: String?, brokerAccountId: Int, account: AccountInfo, onBack: () -> Unit) {
    var txt by remember { mutableStateOf(account.initial.toInt().toString()) }
    var msg by remember { mutableStateOf("") }
    SubShell("设置初始资金", onBack) {
        CardBox {
            Column {
                Text("当前初始资金：¥${account.initial}", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                InputBox(txt, "请输入初始资金", { txt = it })
                Spacer(Modifier.height(16.dp))
                BigBtn("保存") {
                    val v = txt.toDoubleOrNull()
                    if (v == null || v < 0) { msg = "请输入正确的金额" }
                    else {
                        Thread {
                            val r = Api.setInitial(token, brokerAccountId, v)
                            msg = if (r.ok) "设置成功" else "失败：${r.msg}"
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== 2. 积分明细 =====
@Composable
private fun PagePoints(token: String?, points: Int, onBack: () -> Unit) {
    var logs by remember { mutableStateOf<List<PointLog>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { logs = withContext(Dispatchers.IO) { Api.pointLogs(token) } } catch (e: Exception) { }
    }
    SubShell("积分明细", onBack) {
        CardBox {
            Column {
                Text("当前积分", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("$points", color = Color(Config.COLOR_PRIMARY), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        CardBox {
            Column {
                if (logs.isEmpty()) {
                    Text("暂无记录", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                } else {
                    logs.forEachIndexed { i, log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.desc, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp)
                                Text(log.time, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
                            }
                            Text(
                                text = if (log.points >= 0) "+${log.points}" else "${log.points}",
                                color = if (log.points >= 0) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_SUB),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        if (i < logs.size - 1) HDivider()
                    }
                }
            }
        }
    }
}

// ===== 3. 邀请好友 =====
@Composable
private fun PageInvite(token: String?, onBack: () -> Unit) {
    var info by remember { mutableStateOf(InviteInfo()) }
    LaunchedEffect(Unit) {
        try { info = withContext(Dispatchers.IO) { Api.invite(token) } } catch (e: Exception) { }
    }
    SubShell("邀请好友", onBack) {
        CardBox {
            Column {
                Text("我的邀请码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(info.code.ifEmpty { "--" }, color = Color(Config.COLOR_PRIMARY), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row {
                    LabelValue("已邀请", "${info.invitedCount} 人")
                    Spacer(Modifier.width(24.dp))
                    LabelValue("累计奖励", "${info.totalReward} 积分")
                }
                Spacer(Modifier.height(10.dp))
                Text("邀请规则：好友开卡后，月卡+10 / 年卡+50 / 终身+200 积分归你", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        CardBox {
            Column {
                Text("邀请记录", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (info.users.isEmpty()) {
                    Text("暂无邀请", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                } else {
                    info.users.forEachIndexed { i, u ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.username, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp)
                                Text(u.time, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
                            }
                            Text("+${u.reward}", color = Color(Config.COLOR_PRIMARY), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        if (i < info.users.size - 1) HDivider()
                    }
                }
            }
        }
    }
}

// ===== 4. 兑换会员 =====
@Composable
private fun PageExchange(
    token: String?,
    member: MemberInfo,
    onBack: () -> Unit,
    onRefreshMember: (MemberInfo) -> Unit
) {
    var msg by remember { mutableStateOf("") }
    SubShell("兑换会员", onBack) {
        CardBox {
            Column {
                Text("兑换规则：100 积分 = 1 个月会员", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("距上次兑换需满 30 天", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        CardBox {
            Column {
                member.plans.forEachIndexed { i, plan ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.name, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (plan.desc.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(plan.desc, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
                            }
                        }
                        SmallBtn("兑换") {
                            Thread {
                                val r = Api.exchangeMember(token, plan.key)
                                msg = if (r.ok) {
                                    try { onRefreshMember(Api.member(token)) } catch (e: Exception) { }
                                    "兑换成功"
                                } else "失败：${r.msg}"
                            }.start()
                        }
                    }
                    if (i < member.plans.size - 1) HDivider()
                }
                if (member.plans.isEmpty()) {
                    Text("暂无可兑换套餐", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== 5. 券商账户（多账户管理）=====
@Composable
private fun PageBroker(token: String?, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<MyBroker>>(emptyList()) }
    var brokers by remember { mutableStateOf<List<Broker>>(emptyList()) }
    var selected by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reloadFlag by remember { mutableStateOf(0) }

    // 拉取：券商名单 + 我的账户列表
    LaunchedEffect(reloadFlag) {
        loading = true
        try {
            val bs = withContext(Dispatchers.IO) { Api.brokers() }
            val ls = withContext(Dispatchers.IO) { Api.brokerList(token) }
            brokers = bs
            list = ls
        } catch (e: Exception) { }
        loading = false
    }

    SubShell("券商账户", onBack) {
        // ---------- 已添加的账户列表 ----------
        CardBox {
            Column {
                Text("我的证券账户（${list.size}）", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Text("加载中...", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                } else if (list.isEmpty()) {
                    Text("还没有添加证券账户，请在下方添加", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                } else {
                    list.forEachIndexed { i, it ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(it.broker, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(it.account, color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                            }
                            Text(
                                "删除",
                                color = Color(Config.COLOR_PRIMARY),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        Thread {
                                            val r = Api.delBroker(token, it.id)
                                            msg = if (r.ok) "已删除" else "失败：${r.msg}"
                                            reloadFlag++
                                        }.start()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        if (i < list.size - 1) HDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 新增账户 ----------
        CardBox {
            Column {
                Text("添加证券账户", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))

                Text("选择券商", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(selected.ifEmpty { "请选择券商" }, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp)
                }
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        brokers.forEachIndexed { i, b ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        selected = b.name; expanded = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(b.name, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp)
                            }
                            if (i < brokers.size - 1) HDivider()
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("资金账号", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(account, "请输入资金账号", { account = it })

                Spacer(Modifier.height(16.dp))
                BigBtn("添加账户") {
                    if (selected.isEmpty()) { msg = "请选择券商" }
                    else if (account.isEmpty()) { msg = "请输入资金账号" }
                    else {
                        Thread {
                            val r = Api.addBroker(token, selected, account)
                            msg = if (r.ok) "添加成功" else "失败：${r.msg}"
                            if (r.ok) { account = ""; selected = ""; expanded = false }
                            reloadFlag++
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== 6. 自选分组设置 =====
@Composable
private fun PageGroups(token: String?, onBack: () -> Unit) {
    var groups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    var newName by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WatchGroup?>(null) }
    var editName by remember { mutableStateOf("") }

    fun reload() {
        Thread {
            try { groups = Api.watchGroups(token) } catch (e: Exception) { }
        }.start()
    }
    LaunchedEffect(Unit) { reload() }

    SubShell("自选分组设置", onBack) {
        CardBox {
            Column {
                Text("新增分组（最多 10 组）", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                InputBox(newName, "分组名称", { newName = it })
                Spacer(Modifier.height(12.dp))
                BigBtn("添加分组") {
                    if (newName.isEmpty()) { msg = "请输入分组名" }
                    else {
                        Thread {
                            val r = Api.addGroup(token, newName)
                            msg = if (r.ok) "添加成功" else "失败：${r.msg}"
                            if (r.ok) { newName = ""; groups = Api.watchGroups(token) }
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CardBox {
            Column {
                Text("我的分组", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (groups.isEmpty()) {
                    Text("暂无分组", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                } else {
                    groups.forEachIndexed { i, g ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.name, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            SmallBtn("改名") { editing = g; editName = g.name }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(Config.COLOR_TEXT_SUB))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        Thread {
                                            val r = Api.delGroup(token, g.id)
                                            msg = if (r.ok) "已删除" else "失败：${r.msg}"
                                            if (r.ok) groups = Api.watchGroups(token)
                                        }.start()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("删除", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (i < groups.size - 1) HDivider()
                    }
                }
            }
        }
    }

    if (editing != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column {
                    Text("修改分组名", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    InputBox(editName, "新名称", { editName = it })
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF0F0F0))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editing = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("取消", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp) }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(Config.COLOR_PRIMARY))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    Thread {
                                        val r = Api.renameGroup(token, editing!!.id, editName)
                                        msg = if (r.ok) "改名成功" else "失败：${r.msg}"
                                        if (r.ok) groups = Api.watchGroups(token)
                                        editing = null
                                    }.start()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("确定", color = Color.White, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

// ===== 7. 添加自选股 =====
@Composable
private fun PageAddStock(token: String?, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    var gid by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { groups = withContext(Dispatchers.IO) { Api.watchGroups(token) } } catch (e: Exception) { }
        if (gid == 0 && groups.isNotEmpty()) gid = groups.first().id
    }

    SubShell("添加自选股", onBack) {
        CardBox {
            Column {
                Text("股票代码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(code, "如 600519", { code = it })
                Spacer(Modifier.height(14.dp))
                Text("选择分组", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text("暂无分组，请先到自选分组设置创建", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        groups.forEach { g ->
                            val sel = gid == g.id
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (sel) Color(Config.COLOR_PRIMARY) else Color(0xFFF0F0F0))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { gid = g.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(g.name, color = if (sel) Color.White else Color(Config.COLOR_TEXT_MAIN), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                BigBtn("加入自选") {
                    if (code.isEmpty()) { msg = "请输入股票代码" }
                    else if (gid == 0) { msg = "请先创建分组" }
                    else {
                        Thread {
                            val r = Api.addWatch(token, code, gid)
                            msg = if (r.ok) "添加成功" else "失败：${r.msg}"
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== 8. 修改密码 =====
@Composable
private fun PagePwd(token: String?, onBack: () -> Unit) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var newPwd2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    SubShell("修改密码", onBack) {
        CardBox {
            Column {
                Text("原密码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(oldPwd, "请输入原密码", { oldPwd = it })
                Spacer(Modifier.height(12.dp))
                Text("新密码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(newPwd, "请输入新密码", { newPwd = it })
                Spacer(Modifier.height(12.dp))
                Text("确认新密码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(newPwd2, "再次输入新密码", { newPwd2 = it })
                Spacer(Modifier.height(16.dp))
                BigBtn("确认修改") {
                    if (oldPwd.isEmpty() || newPwd.isEmpty()) { msg = "请填写完整" }
                    else if (newPwd != newPwd2) { msg = "两次新密码不一致" }
                    else {
                        Thread {
                            val r = Api.changePwd(token, oldPwd, newPwd)
                            msg = if (r.ok) "修改成功" else "失败：${r.msg}"
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== 9. 交易记录管理（修改持仓记录：只改日期+价格，改后整账户重放） =====
@Composable
private fun PageTradeEdit(token: String?, brokerAccountId: Int, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<TradeRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf(0) }
    var ePrice by remember { mutableStateOf("") }
    var eDate by remember { mutableStateOf("") }
    // 当前编辑股票可选的「有柱子的日期」及其当日高低价：date -> [high, low]
    var barDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var barHL by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var barLoading by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    fun reload() {
        loading = true
        Thread {
            val l = Api.tradeEditList(token, brokerAccountId)
            list = l
            loading = false
        }.start()
    }
    // 拉取某股票的 K线，得到「有柱子的日期」+ 当日最高/最低价
    fun loadBars(code: String) {
        barLoading = true
        barDates = emptyList()
        barHL = emptyMap()
        Thread {
            val d = Api.detail(code, "day", 1)
            val points = d?.line ?: emptyList()
            val dates = ArrayList<String>()
            val hl = HashMap<String, Pair<Double, Double>>()
            for (p in points) {
                if (p.date.isNullOrEmpty()) continue
                dates.add(p.date)
                hl[p.date] = Pair(p.high, p.low)
            }
            // 后端按日期升序返回，选择器从新到旧展示
            dates.reverse()
            barDates = dates
            barHL = hl
            barLoading = false
        }.start()
    }
    LaunchedEffect(brokerAccountId) { reload() }
    SubShell("交易记录管理", onBack) {
        Text(
            "仅可修改「成交日期」与「成交价」，数量/方向不可改；保存后资金与持仓会自动重算。日期只能选择该股票有K线柱子的交易日。",
            color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        if (loading) {
            Text("加载中…", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
        } else if (list.isEmpty()) {
            CardBox {
                Column {
                    Text("暂无交易记录", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp)
                }
            }
        } else {
            for (t in list) {
                CardBox {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (t.direction == 1) "买入" else "卖出",
                                color = if (t.direction == 1) Color(0xFFE53935) else Color(0xFF43A047),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${t.code} ${t.name}", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "价 ${t.price} × ${t.quantity} 股　金额 ¥${t.amount}　费 ¥${t.commission}",
                            color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("成交时间 ${t.time}", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        if (editId == t.id) {
                            InputBox(ePrice, "成交价（如 8.500）", { ePrice = it })
                            Spacer(Modifier.height(8.dp))
                            // ===== 成交日期：选择器（只能选有柱子的交易日） =====
                            Text("成交日期", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF2F2F2))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        if (barDates.isNotEmpty()) showPicker = !showPicker
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (barLoading) "加载可交易日…"
                                    else if (eDate.isNotEmpty()) eDate
                                    else "点击选择日期",
                                    color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp
                                )
                            }
                            // 选完日期后的提示：当日最高/最低价
                            val hl = barHL[eDate]
                            if (hl != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "当日最高 ${hl.first}　最低 ${hl.second}",
                                    color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp
                                )
                            }
                            // 可选日期列表（从新到旧）
                            if (showPicker && barDates.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFAFAFA))
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    for (d in barDates) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                    eDate = d
                                                    showPicker = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                d,
                                                color = if (d == eDate) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_MAIN),
                                                fontSize = 13.sp,
                                                fontWeight = if (d == eDate) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEEEEEE))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editId = 0; showPicker = false }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("取消", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(Config.COLOR_PRIMARY))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            val pv = ePrice.toDoubleOrNull()
                                            if (pv == null || pv <= 0) { msg = "请输入正确的成交价" }
                                            else if (eDate.isEmpty()) { msg = "请选择成交日期" }
                                            else if (barDates.isNotEmpty() && !barDates.contains(eDate)) { msg = "该日期没有K线柱子，请从列表中选择" }
                                            else {
                                                msg = ""
                                                Thread {
                                                    val r = Api.tradeUpdate(token, brokerAccountId, t.id, pv, eDate)
                                                    msg = if (r.ok) "修改成功，资金与持仓已重算（现金 ¥${r.settle}）" else "失败：${r.msg}"
                                                    if (r.ok) { editId = 0; showPicker = false }
                                                    reload()
                                                }.start()
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("保存", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(Config.COLOR_PRIMARY))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        editId = t.id
                                        ePrice = t.price.toString()
                                        eDate = t.time.take(10)
                                        msg = ""
                                        showPicker = false
                                        loadBars(t.code)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("修改", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
        }
    }
}

// ===== 10. 一键模拟（批量生成买卖T记录）=====
@Composable
private fun PageSimulate(token: String?, brokerAccountId: Int, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var dateStart by remember { mutableStateOf("") }
    var dateEnd by remember { mutableStateOf("") }
    // mode: qty=总股数, amount=总金额
    var mode by remember { mutableStateOf("qty") }
    var totalTxt by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    SubShell("一键模拟", onBack) {
        Text(
            "按区间随机生成 买/卖/做T 交易记录，并自动重算资金与持仓（含浮盈浮亏）。生成前会校验账户可用资金，超出则自动缩减规模。",
            color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp, lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))

        CardBox {
            Column {
                Text("股票代码", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(code, "如 600519 / sh600519", { code = it })

                Spacer(Modifier.height(14.dp))
                Text("日期范围（YYYY-MM-DD）", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InputBox(dateStart, "开始日期（近期），如 2025-08-01", { dateStart = it })
                Spacer(Modifier.height(8.dp))
                InputBox(dateEnd, "结束日期（近期），如 2025-09-20", { dateEnd = it })

                Spacer(Modifier.height(14.dp))
                Text("规模模式", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ModeChip("按总股数", mode == "qty") { mode = "qty" }
                    Spacer(Modifier.width(10.dp))
                    ModeChip("按总金额", mode == "amount") { mode = "amount" }
                }
                Spacer(Modifier.height(10.dp))
                InputBox(
                    totalTxt,
                    if (mode == "qty") "总股数，如 5000" else "总金额，如 50000",
                    { totalTxt = it }
                )

                Spacer(Modifier.height(16.dp))
                BigBtn(if (running) "生成中…" else "开始生成") {
                    if (running) return@BigBtn
                    val tv = totalTxt.toDoubleOrNull()
                    if (code.isEmpty()) { msg = "请输入股票代码" }
                    else if (dateStart.isEmpty() || dateEnd.isEmpty()) { msg = "请填写完整日期范围" }
                    else if (dateStart > dateEnd) { msg = "开始日期不能晚于结束日期" }
                    else if (tv == null || tv <= 0) { msg = "请输入正确的总股数/总金额" }
                    else {
                        msg = ""
                        running = true
                        // code 前端先归一化：剥离 sh/sz/bj 前缀，只保留 6 位数字（后端虽有归一化，前端统一更稳）
                        val pureCode = code.trim().let { if (it.length >= 6) it.takeLast(6) else it }
                        Thread {
                            val r = Api.simulate(token, brokerAccountId, pureCode, dateStart, dateEnd, mode, tv)
                            msg = if (r.ok) {
                                // ★ 通知全局：交易数据已变更 → K 线详情页会自动重拉 detail，刷新 BST 标记
                                Config.bumpTradeData()
                                "生成成功：${r.quantity} 笔，现金余额 ¥${r.settle}\n已自动刷新K线标记，请到「日K」查看（如未看到请把K线拖到最右端）"
                            } else "失败：${r.msg}"
                            running = false
                        }.start()
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CardBox {
            Column {
                Text("说明", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("• 买卖节奏与做T随机生成，不遵循 T+1", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp, lineHeight = 18.sp)
                Text("• 生成后会真实写入持仓记录，浮盈浮亏自动计算", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp, lineHeight = 18.sp)
                Text("• 会校验账户可用金额，不足时自动缩减规模", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp, lineHeight = 18.sp)
                Text("• 生成后的记录可在「交易记录管理」中逐条修改", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** 模式选择小胶囊 */
@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(Config.COLOR_PRIMARY) else Color(0xFFF0F0F0))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Color.White else Color(Config.COLOR_TEXT_MAIN), fontSize = 12.sp)
    }
}

// ===== 意见反馈（第15轮 批2D）=====
/**
 * 意见反馈页：文字必填 + 最多 3 张图片。
 * 图片经 upload.php 上传后拿到相对路径，随文字一并提交 feedback_submit.php。
 */
@Composable
private fun PageFeedback(token: String?, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var txt by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 已成功上传的图片相对路径
    val imgs = remember { mutableStateListOf<String>() }

    // 选取图库图片 → 复制到缓存临时文件 → 上传
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

    SubShell("意见反馈", onBack) {
        CardBox {
            Column {
                Text("问题或建议", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = txt,
                    onValueChange = { if (it.length <= 500) txt = it },
                    placeholder = { Text("请描述你遇到的问题或想提的建议（必填）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
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
            }
        }
    }
}

/**
 * 网络图片加载（无 coil 依赖）：OkHttp 拉字节流 → BitmapFactory 解码 → Image。
 * pathOrUrl 既可为后端相对路径（/stock_api/uploads/xxx.png），也可为绝对 URL。
 * 加载失败或为空时渲染占位提示，不影响页面其余部分。
 */
@Composable
private fun NetImage(pathOrUrl: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val url = when {
        pathOrUrl.isBlank() -> ""
        pathOrUrl.startsWith("http") -> pathOrUrl
        else -> "http://121.43.178.214:8080" + pathOrUrl
    }
    var bmp by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (url.isEmpty()) { failed = true; return@LaunchedEffect }
        bmp = withContext(Dispatchers.IO) {
            try {
                val resp = Net.rawClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute()
                resp.body?.byteStream()?.use { ins ->
                    android.graphics.BitmapFactory.decodeStream(ins)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
        if (bmp == null) failed = true
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                if (failed) "收款码加载失败" else "加载中…",
                color = Color(Config.COLOR_TEXT_SUB),
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ===== 收款页（会员开通 / 续费）=====
// 流程：拉套餐 → 选套餐 + 选支付方式 → payCreate 下单拿订单号与收款码 →
//       展示对应收款码 → 用户转账后上传支付凭证（upload.php type=proof）→ paySubmitProof 提交 →
//       后台审核后生效；可在页面底部查询历史订单状态。
@Composable
private fun PageRecharge(token: String?, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var plans by remember { mutableStateOf<List<PayPlan>>(emptyList()) }
    var qrWechat by remember { mutableStateOf("") }
    var qrAlipay by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("year") }        // 默认选中「年卡」
    var payMethod by remember { mutableStateOf("wechat") }     // wechat / alipay
    var order by remember { mutableStateOf<PayOrder?>(null) }  // 下单结果
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 已上传的支付凭证相对路径
    var proofUrl by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    // 我的订单
    var orders by remember { mutableStateOf<List<PayOrder>>(emptyList()) }

    // 首次进入：拉套餐 + 拉订单
    LaunchedEffect(Unit) {
        val p = withContext(Dispatchers.IO) { Api.plans(token) }
        val o = withContext(Dispatchers.IO) { Api.payStatus(token) }
        plans = p.plans
        qrWechat = p.qrcodeWechat
        qrAlipay = p.qrcodeAlipay
        orders = o
        // 若后端返回套餐，默认选中带 hot 的档
        p.plans.firstOrNull { it.hot }?.let { selected = it.key }
        loading = false
    }

    // 选图上传支付凭证
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        msg = "凭证上传中…"
        Thread {
            val saved = try {
                val tmp = java.io.File(ctx.cacheDir, "proof_${System.currentTimeMillis()}.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    tmp.outputStream().use { outs -> ins.copyTo(outs) }
                }
                val url = Api.uploadImage(token, tmp, "proof")
                tmp.delete()
                url
            } catch (e: Exception) {
                null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                if (saved != null) { proofUrl = saved; msg = "凭证已上传，请点「提交支付凭证」" }
                else msg = "凭证上传失败，请重试"
            }
        }.start()
    }

    SubShell("开通 / 续费会员", onBack) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (loading) {
                CardBox { Text("加载中…", color = Color(Config.COLOR_TEXT_SUB), fontSize = 13.sp) }
            } else {
                // ---- 1. 套餐选择 ----
                CardBox {
                    Column {
                        Text("选择套餐", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        plans.forEach { p ->
                            val sel = p.key == selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Color(0xFFFFF0F0) else Color(0xFFF7F7F7))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        selected = p.key; order = null; proofUrl = ""; msg = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (p.hot) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(Config.COLOR_PRIMARY))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) { Text("热销", color = Color.White, fontSize = 10.sp) }
                                        }
                                    }
                                    if (p.desc.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(p.desc, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
                                    } else if (p.days > 0) {
                                        Spacer(Modifier.height(2.dp))
                                        Text("有效期 ${p.days} 天", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    "¥${p.price}",
                                    color = Color(Config.COLOR_PRIMARY),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ---- 2. 支付方式 ----
                CardBox {
                    Column {
                        Text("支付方式", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            PayMethodChip("微信", payMethod == "wechat") { payMethod = "wechat"; order = null }
                            Spacer(Modifier.width(10.dp))
                            PayMethodChip("支付宝", payMethod == "alipay") { payMethod = "alipay"; order = null }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                // ---- 3. 下单按钮 / 收款码 ----
                if (order == null) {
                    BigBtn(if (busy) "请稍候…" else "生成订单并显示收款码") {
                        if (busy) return@BigBtn
                        if (selected.isBlank()) { msg = "请选择套餐"; return@BigBtn }
                        busy = true
                        msg = "下单中…"
                        Thread {
                            val o = Api.payCreate(token, selected, payMethod)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                busy = false
                                if (o != null) {
                                    order = o
                                    msg = "订单号 #${o.id}，请扫码支付 ¥${o.amount}"
                                } else {
                                    msg = "下单失败，请重试"
                                }
                            }
                        }.start()
                    }
                } else {
                    val od = order!!
                    CardBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("订单号 #${od.id}", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${od.levelName.ifBlank { od.levelKey }} · ¥${od.amount} · ${if (od.payMethod == "alipay") "支付宝" else "微信"}",
                                color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            val qr = od.qrcode.ifBlank { if (payMethod == "alipay") qrAlipay else qrWechat }
                            NetImage(qr, 200.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("请使用${if (payMethod == "alipay") "支付宝" else "微信"}扫码支付 ¥${od.amount}", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)

                            Spacer(Modifier.height(16.dp))
                            HDivider()
                            Spacer(Modifier.height(16.dp))
                            Text("支付后上传转账截图作为凭证", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (proofUrl.isNotBlank()) {
                                    NetImage(proofUrl, 80.dp)
                                    Spacer(Modifier.width(12.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF0F0F0))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            if (!busy) picker.launch("image/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (proofUrl.isBlank()) "+ 上传凭证" else "重传", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            InputBox(remark, "备注（选填，如转账尾号）", { remark = it })
                            Spacer(Modifier.height(14.dp))
                            BigBtn(if (busy) "请稍候…" else "提交支付凭证") {
                                if (busy) return@BigBtn
                                if (proofUrl.isBlank()) { msg = "请先上传支付凭证"; return@BigBtn }
                                busy = true
                                msg = "提交中…"
                                Thread {
                                    val r = Api.paySubmitProof(token, od.id, proofUrl, remark.trim())
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        busy = false
                                        if (r.ok) {
                                            msg = "凭证已提交，请等待后台审核开通"
                                            proofUrl = ""
                                            remark = ""
                                        } else {
                                            msg = "提交失败：${r.msg}"
                                        }
                                    }
                                }.start()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF2F2F2))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                order = null; proofUrl = ""; remark = ""; msg = ""
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("重新选择套餐", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp) }
                }

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(msg, color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                }

                // ---- 4. 我的订单 ----
                if (orders.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    CardBox {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("我的订单", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                SmallBtn("刷新") {
                                    Thread {
                                        val o = Api.payStatus(token)
                                        android.os.Handler(android.os.Looper.getMainLooper()).post { orders = o }
                                    }.start()
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            orders.forEach { o ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("#${o.id} · ${o.levelKey} · ¥${o.amount}", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${o.createdAt}${if (o.adminRemark.isNotBlank()) " · ${o.adminRemark}" else ""}",
                                            color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        orderStatusText(o.status),
                                        color = orderStatusColor(o.status),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                HDivider()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** 支付方式小胶囊 */
@Composable
private fun PayMethodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(Config.COLOR_PRIMARY) else Color(0xFFF0F0F0))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else Color(Config.COLOR_TEXT_MAIN), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 订单状态文案 */
private fun orderStatusText(s: String): String = when (s) {
    "paid", "approved" -> "已开通"
    "pending" -> "待审核"
    "rejected" -> "已驳回"
    else -> s.ifBlank { "未知" }
}

/** 订单状态颜色 */
private fun orderStatusColor(s: String): Color = when (s) {
    "paid", "approved" -> Color(0xFF2FA84F)
    "pending" -> Color(0xFFE6A23C)
    "rejected" -> Color(Config.COLOR_PRIMARY)
    else -> Color(Config.COLOR_TEXT_SUB)
}