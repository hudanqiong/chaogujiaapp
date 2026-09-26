package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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