package com.chaogujia.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录 / 注册页（覆盖在内容之上弹出）。
 * onSuccess(token, username) 登录成功后回调。
 * onClose() 点遮罩关闭。
 */
@Composable
fun LoginScreen(
    onSuccess: (token: String, username: String) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var isLogin by remember { mutableStateOf(true) }   // true=登录页, false=注册页

    // 输入
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    // 执行登录 / 注册（在网络线程里跑，完成后回主线程更新状态）
    fun doAuth() {
        val u = username.trim()
        val p = password
        if (u.isEmpty() || p.isEmpty()) { err = "请输入用户名和密码"; return }
        if (!isLogin) {
            if (u.length < 3) { err = "用户名至少3位"; return }
            if (p.length < 6) { err = "密码至少6位"; return }
            if (p != password2) { err = "两次密码不一致"; return }
        }
        busy = true
        err = null
        val loginMode = isLogin
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (loginMode) {
                    Api.login(u, p)
                } else {
                    val reg = Api.register(u, p)
                    if (reg.ok) Api.login(u, p) else reg   // 注册成功后再自动登录
                }
            }
            busy = false
            if (result.ok && result.token.isNotEmpty()) {
                toast(if (loginMode) "登录成功" else "注册成功")
                onSuccess(result.token, result.username.ifEmpty { u })
            } else {
                err = if (!loginMode) result.msg.ifEmpty { "注册失败，请换个用户名" }
                      else result.msg.ifEmpty { "登录失败" }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // 白色卡片（点击卡片本身不关闭）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 拦截，避免点到卡片触发遮罩关闭 */ }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "炒股家",
                color = Color(Config.COLOR_PRIMARY),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isLogin) "登录你的账号" else "注册新账号",
                color = Color(Config.COLOR_TEXT_SUB),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (!isLogin) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password2,
                    onValueChange = { password2 = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            err?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, color = Color(Config.COLOR_PRIMARY), fontSize = 13.sp)
            }

            Spacer(Modifier.height(18.dp))

            // 主按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (busy) Color(0xFFCCCCCC) else Color(Config.COLOR_PRIMARY))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!busy) doAuth() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (busy) "请稍候…" else (if (isLogin) "登录" else "注册"),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            // 切换登录/注册
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLogin) "没有账号？" else "已有账号？",
                    color = Color(Config.COLOR_TEXT_SUB),
                    fontSize = 13.sp
                )
                Text(
                    text = if (isLogin) "去注册" else "去登录",
                    color = Color(Config.COLOR_PRIMARY),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isLogin = !isLogin
                            err = null
                        }
                        .padding(4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}