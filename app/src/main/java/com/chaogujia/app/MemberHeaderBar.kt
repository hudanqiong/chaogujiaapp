package com.chaogujia.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 共用红色顶栏（5 个 Tab 页面共用，高度固定一致）
 *
 * 内容按当前页面切换：
 *   - index（首页）   ：头像 + 昵称 + 等级 + 到期时间
 *   - watch（自选）   ：图标1 + "同花顺自选"(居中) + 图标2 + 图标3
 *   - 其他页面        ：空占位（保持高度一致）
 */
@Composable
fun MemberHeaderBar(
    current: String = "index",
    username: String = "",
    isMember: Boolean = false,
    levelName: String = "普通用户",
    expireAt: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
                )
            )
            .statusBarsPadding()
    ) {
        when (current) {
            // 首页：会员信息
            "index" -> MemberInfoRow(username, isMember, levelName, expireAt)

            // 自选页：同花顺自选 + 三图标
            "watch" -> WatchHeaderRow()

            // 其他页面：空占位，保证红条高度一致
            else -> Spacer(Modifier.height(74.dp))
        }
    }
}

/** 首页：会员信息行（头像 + 昵称 + 等级 + 到期时间） */
@Composable
private fun MemberInfoRow(
    username: String,
    isMember: Boolean,
    levelName: String,
    expireAt: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像：用户名首字圆形
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).ifEmpty { "股" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = username.ifEmpty { "股友" },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isMember) levelName else "普通用户",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        if (expireAt.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "到期时间",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = expireAt,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 自选页顶栏：图标1(最左) + "同花顺自选"(居中) + 图标2、3(靠右挨着)
 * 内容整体靠底部，下方间距留小。
 * 图标不缩放（原图大小），仅展示、不绑定点击。
 */
@Composable
private fun WatchHeaderRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .padding(top = 14.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 图标1：靠最左侧（放大到 48dp）
        Image(
            painter = painterResource(R.drawable.ic_watch_left),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .height(48.dp),
            contentScale = ContentScale.Fit
        )

        // 居中文字
        Text(
            text = "同花顺自选",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )

        // 图标2(24dp，不变)、3(放大到48dp)：靠右
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.Bottom
        ) {
            Image(
                painter = painterResource(R.drawable.ic_watch_mid),
                contentDescription = null,
                modifier = Modifier.height(24.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(R.drawable.ic_watch_right),
                contentDescription = null,
                modifier = Modifier.height(48.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}