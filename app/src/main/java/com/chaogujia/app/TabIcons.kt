package com.chaogujia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 底栏 5 个图标 —— 纯代码手绘（同花顺风格）
 * 规则：选中(点亮)=红色实心 + 白色元素；未选=黑色空心/轮廓 + 黑色元素
 *
 * 统一在 24x24 的画布坐标里绘制（用 size.width 做归一化基数），
 * 这样任何尺寸下都不会变形。
 */

private val RED = Color(Config.COLOR_PRIMARY)      // 点亮主色
private val BLACK = Color(Config.COLOR_TEXT_MAIN)  // 未选主色
private val WHITE = Color.White

/**
 * 首页：三根带上下影线的行情柱，从左到右一根比一根高
 * 排列规律：空 → 实 → 空（第1根空心、第2根实心、第3根空心）
 * 每根 = 上下贯穿的细影线 + 中间一段"偏胖"的柱体；
 * 点亮=红色（实心柱用红填充/空心柱用红描边）+ 红针；
 * 不点亮=黑色（实心柱用黑填充/空心柱用黑描边）+ 黑针。
 */
@Composable
fun IconHome(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f            // 归一化单位
        val bodyStroke = 1.4f * u           // 空心柱：细边框（不要太粗）
        val barW = 4.6f * u                 // 柱体宽（偏胖）
        val baseY = 20.5f * u               // 所有柱子的下端参考线
        // 三根的 x 中心
        val centers = listOf(5.0f, 12f, 19f)
        // 每根：柱体高度（从下往上）、上影线长度、下影线长度
        val bodyH = listOf(5.5f, 10f, 14.5f)   // 越高越靠右
        val upWick = listOf(2.5f, 3.5f, 4.5f)  // 上影线（针尖向上）
        val downWick = listOf(1.8f, 2.2f, 2.6f) // 下影线（针尖向下）
        val filled = listOf(false, true, false) // 空 → 实 → 空

        centers.forEachIndexed { i, cx ->
            val c = cx * u
            val bodyBottom = baseY - downWick[i] * u      // 柱体底
            val bodyTop = bodyBottom - bodyH[i] * u       // 柱体顶
            val wickTop = bodyTop - upWick[i] * u         // 上针尖
            val wickBottom = baseY + 0f * u               // 下针尖

            // 1) 上下影线（贯穿的细针）
            drawLine(
                color = main,
                start = Offset(c, wickTop),
                end = Offset(c, wickBottom),
                strokeWidth = 1.3f * u,
                cap = StrokeCap.Round
            )

            // 2) 柱体
            val r = Rect(
                topLeft = Offset(c - barW / 2f, bodyTop),
                size = Size(barW, bodyBottom - bodyTop)
            )
            if (filled[i]) {
                // 实心（无论点亮与否，实心柱都用主色填充）
                drawRect(color = main, topLeft = r.topLeft, size = r.size)
            } else {
                // 空心柱：细边框
                drawRect(
                    color = main,
                    topLeft = r.topLeft,
                    size = r.size,
                    style = Stroke(bodyStroke)
                )
            }
        }
    }
}

/** 行情：长方形 + 中间一条折线 */
@Composable
fun IconQuote(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    val line = if (selected) WHITE else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.8f * u
        // 外框矩形
        val left = 3.5f * u
        val top = 5f * u
        val right = 20.5f * u
        val bottom = 19f * u
        val w = right - left
        val h = bottom - top
        if (selected) {
            drawRect(color = main, topLeft = Offset(left, top), size = Size(w, h))
        } else {
            drawRect(color = main, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(strokeW))
        }
        // 中间折线（左低右高、带起伏）
        val p = Path().apply {
            moveTo(left + 2.5f * u, bottom - 4.5f * u)
            lineTo(left + w * 0.32f, bottom - 6.5f * u)
            lineTo(left + w * 0.55f, bottom - 3.5f * u)
            lineTo(right - 2.5f * u, bottom - 8.5f * u)
        }
        drawPath(
            path = p,
            color = line,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

/** 自选：人像剪影（头 + 肩，无外框）+ 胸前对勾 */
@Composable
fun IconWatch(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    val tick = if (selected) WHITE else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.8f * u

        // ---- 人像（头 + 肩），作为一个连通路径 ----
        val person = Path().apply {
            // 头（圆）
            addOval(
                Rect(
                    center = Offset(12f * u, 8f * u),
                    radius = 3.6f * u
                )
            )
            // 肩（一个圆润的梯形/半圆状）
            moveTo(4.5f * u, 20.5f * u)
            cubicTo(
                4.5f * u, 15.5f * u,
                8f * u, 13.5f * u,
                12f * u, 13.5f * u
            )
            cubicTo(
                16f * u, 13.5f * u,
                19.5f * u, 15.5f * u,
                19.5f * u, 20.5f * u
            )
            close()
        }
        if (selected) {
            drawPath(person, color = main)                     // 实心红
        } else {
            drawPath(person, color = main, style = Stroke(strokeW))
        }

        // ---- 胸前对勾 ----
        val check = Path().apply {
            moveTo(9.2f * u, 18.6f * u)
            lineTo(11.2f * u, 20.5f * u)
            lineTo(15.2f * u, 16.2f * u)
        }
        drawPath(
            path = check,
            color = tick,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

/** 交易：圆形 + 人民币符号 ¥ */
@Composable
fun IconTrade(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    val sym = if (selected) WHITE else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.8f * u

        // 圆
        val circle = Rect(center = Offset(12f * u, 12f * u), radius = 8.5f * u)
        if (selected) {
            drawOval(color = main, topLeft = circle.topLeft, size = circle.size)
        } else {
            drawOval(color = main, topLeft = circle.topLeft, size = circle.size, style = Stroke(strokeW))
        }

        // ¥ 符号：两斜线 + 两横
        val yTop = 7.6f * u
        val yMid = 13.2f * u
        val yBot = 16.4f * u
        // 左斜线
        val left = Path().apply {
            moveTo(9.2f * u, yTop)
            lineTo(12f * u, yMid)
        }
        // 右斜线
        val right = Path().apply {
            moveTo(14.8f * u, yTop)
            lineTo(12f * u, yMid)
        }
        // 竖线
        val stem = Path().apply {
            moveTo(12f * u, yMid)
            lineTo(12f * u, yBot)
        }
        // 两横
        val bar1 = Path().apply {
            moveTo(9.4f * u, yMid + 0.6f * u)
            lineTo(14.6f * u, yMid + 0.6f * u)
        }
        val bar2 = Path().apply {
            moveTo(9.4f * u, yBot + 0.2f * u)
            lineTo(14.6f * u, yBot + 0.2f * u)
        }
        val s = Stroke(width = strokeW, cap = StrokeCap.Round)
        drawPath(left, sym, style = s)
        drawPath(right, sym, style = s)
        drawPath(stem, sym, style = s)
        drawPath(bar1, sym, style = s)
        drawPath(bar2, sym, style = s)
    }
}

/** 资讯：竖长方形（像门）+ 三横（上两长、下短，左对齐） */
@Composable
fun IconNews(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    val line = if (selected) WHITE else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.8f * u

        // 竖长方形：上下短、左右长？不——你说"像房门"，即竖的：宽小高大
        val left = 6f * u
        val top = 4.5f * u
        val w = 12f * u
        val h = 15f * u
        if (selected) {
            drawRect(color = main, topLeft = Offset(left, top), size = Size(w, h))
        } else {
            drawRect(color = main, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(strokeW))
        }

        // 三横线（左对齐）：上两横长，下横短
        val lineStroke = Stroke(width = strokeW, cap = StrokeCap.Round)
        val xStart = left + 2.2f * u
        val longEnd = left + w - 2.2f * u
        val shortEnd = left + w * 0.55f
        // 第一横
        val l1 = Path().apply { moveTo(xStart, top + 4.2f * u); lineTo(longEnd, top + 4.2f * u) }
        // 第二横
        val l2 = Path().apply { moveTo(xStart, top + 7.4f * u); lineTo(longEnd, top + 7.4f * u) }
        // 第三横（短）
        val l3 = Path().apply { moveTo(xStart, top + 10.6f * u); lineTo(shortEnd, top + 10.6f * u) }
        drawPath(l1, line, style = lineStroke)
        drawPath(l2, line, style = lineStroke)
        drawPath(l3, line, style = lineStroke)
    }
}