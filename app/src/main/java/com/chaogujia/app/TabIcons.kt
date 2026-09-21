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
 * 排列规律：未点亮 → 三根全部空心（描边）；点亮 → 三根全部实心（填充）
 * 每根 = 上下两端的细影线 + 中间一段"偏胖"的柱体；
 * 点亮=红色（实心柱用红填充）+ 红针；
 * 不点亮=黑色（空心柱用黑描边）+ 黑针。
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
        val bodyH = listOf(5.5f, 8.5f, 12.5f)   // 越高越靠右（右两根略矮）
        val upWick = listOf(2.5f, 3.0f, 3.8f)   // 上影线（针尖向上）
        val downWick = listOf(1.8f, 3.4f, 4.0f) // 下影线（针尖向下，右两根整体上移）
        val filled = listOf(selected, selected, selected) // 未点亮=全空心；点亮=全实心

        centers.forEachIndexed { i, cx ->
            val c = cx * u
            val bodyBottom = baseY - downWick[i] * u      // 柱体底
            val bodyTop = bodyBottom - bodyH[i] * u       // 柱体顶
            val wickTop = bodyTop - upWick[i] * u         // 上针尖
            val wickBottom = baseY + 0f * u               // 下针尖

            // 1) 上下影线：只画在柱体两端，不穿过柱体
            //    上影线：柱体顶 → 上针尖
            drawLine(
                color = main,
                start = Offset(c, bodyTop),
                end = Offset(c, wickTop),
                strokeWidth = 1.3f * u,
                cap = StrokeCap.Round
            )
            //    下影线：柱体底 → 下针尖
            drawLine(
                color = main,
                start = Offset(c, bodyBottom),
                end = Offset(c, wickBottom),
                strokeWidth = 1.3f * u,
                cap = StrokeCap.Round
            )

            // 2) 柱体
            val bodyTopLeft = Offset(c - barW / 2f, bodyTop)
            val bodySize = Size(barW, bodyBottom - bodyTop)
            if (filled[i]) {
                // 实心（无论点亮与否，实心柱都用主色填充）
                drawRect(color = main, topLeft = bodyTopLeft, size = bodySize)
            } else {
                // 空心柱：细边框（内收半宽，使外沿与实心完全一致 → 点亮不跳大）
                val hb = bodyStroke / 2f
                drawRect(
                    color = main,
                    topLeft = Offset(bodyTopLeft.x + hb, bodyTopLeft.y + hb),
                    size = Size(bodySize.width - bodyStroke, bodySize.height - bodyStroke),
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
            // 内收半宽，使外沿与实心一致 → 点亮不跳大
            val hb = strokeW / 2f
            drawRect(
                color = main,
                topLeft = Offset(left + hb, top + hb),
                size = Size(w - strokeW, h - strokeW),
                style = Stroke(strokeW)
            )
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

/** 自选：人像证件照剪影（头 + 脖子 + 肩膀）+ 脖子下向下展开的勾（领口） */
@Composable
fun IconWatch(selected: Boolean, modifier: Modifier = Modifier) {
    val main = if (selected) RED else BLACK
    val tick = if (selected) WHITE else BLACK
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.6f * u

        // ---- 头(不封底) + 脖子 + 肩 连成一条完整外轮廓 ----
        // 画法：从颈左上行 → 绕头顶 → 下行到颈右 → 转肩 → 绕回来闭合。
        // 头部只有顶部圆角 + 两侧竖边，底部（下巴）不画横线，直接向下接脖子。
        val body = Path().apply {
            // 1) 颈部左线往上：从肩颈点往上到「下巴左角」（脖子已缩短）
            moveTo(9.9f * u, 13.0f * u)
            lineTo(9.9f * u, 10.3f * u)             // 脖子左侧 → 下巴左角
            // 2) 头的左轮廓：从下巴左角往上到脸颊，圆角绕头顶，再对称下来（头已加大）
            cubicTo(
                8.9f * u, 9.5f * u,                 // 下颚左弧起（向外扩）
                8.6f * u, 6.2f * u,                 // 左脸颊（最宽）
                9.9f * u, 3.4f * u                  // 左上额
            )
            cubicTo(
                11.0f * u, 0.7f * u,
                13.0f * u, 0.7f * u,
                14.1f * u, 3.4f * u
            )                                       // 绕头顶
            cubicTo(
                15.4f * u, 6.2f * u,                 // 右脸颊（最宽）
                15.1f * u, 9.5f * u,                 // 下颚右弧
                14.1f * u, 10.3f * u                 // 下巴右角
            )
            // 3) 颈部右线往下：从下巴右角直下到肩颈点
            lineTo(14.1f * u, 13.0f * u)
            // 4) 右肩：从颈肩点向外弧到右肩，再下到右底（方肩）
            cubicTo(
                17.2f * u, 13.0f * u,
                20.0f * u, 13.2f * u,
                20.0f * u, 15.4f * u
            )
            lineTo(20.0f * u, 20.8f * u)            // 右底角
            lineTo(4.0f * u, 20.8f * u)             // 底边
            lineTo(4.0f * u, 15.4f * u)             // 左底角
            // 5) 左肩：从肩膀回颈肩点（对称）
            cubicTo(
                4.0f * u, 13.2f * u,
                6.8f * u, 13.0f * u,
                9.9f * u, 13.0f * u
            )
            close()
        }

        // 描边用「内缩 0.8u」的同一轮廓，使未选中的外沿与选中实心完全一致（点亮不跳大）
        fun inset(x: Float, y: Float): Offset {
            val cx = 12.0f; val cy = 12.3f; val e = 0.8f
            val dx = x - cx; val dy = y - cy
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
            return Offset((x - dx / len * e) * u, (y - dy / len * e) * u)
        }
        val bodyStrokePath = Path().apply {
            moveTo(inset(9.9f, 13.0f).x, inset(9.9f, 13.0f).y)
            lineTo(inset(9.9f, 10.3f).x, inset(9.9f, 10.3f).y)
            cubicTo(
                inset(8.9f, 9.5f).x, inset(8.9f, 9.5f).y,
                inset(8.6f, 6.2f).x, inset(8.6f, 6.2f).y,
                inset(9.9f, 3.4f).x, inset(9.9f, 3.4f).y
            )
            cubicTo(
                inset(11.0f, 0.7f).x, inset(11.0f, 0.7f).y,
                inset(13.0f, 0.7f).x, inset(13.0f, 0.7f).y,
                inset(14.1f, 3.4f).x, inset(14.1f, 3.4f).y
            )
            cubicTo(
                inset(15.4f, 6.2f).x, inset(15.4f, 6.2f).y,
                inset(15.1f, 9.5f).x, inset(15.1f, 9.5f).y,
                inset(14.1f, 10.3f).x, inset(14.1f, 10.3f).y
            )
            lineTo(inset(14.1f, 13.0f).x, inset(14.1f, 13.0f).y)
            cubicTo(
                inset(17.2f, 13.0f).x, inset(17.2f, 13.0f).y,
                inset(20.0f, 13.2f).x, inset(20.0f, 13.2f).y,
                inset(20.0f, 15.4f).x, inset(20.0f, 15.4f).y
            )
            lineTo(inset(20.0f, 20.8f).x, inset(20.0f, 20.8f).y)
            lineTo(inset(4.0f, 20.8f).x, inset(4.0f, 20.8f).y)
            lineTo(inset(4.0f, 15.4f).x, inset(4.0f, 15.4f).y)
            cubicTo(
                inset(4.0f, 13.2f).x, inset(4.0f, 13.2f).y,
                inset(6.8f, 13.0f).x, inset(6.8f, 13.0f).y,
                inset(9.9f, 13.0f).x, inset(9.9f, 13.0f).y
            )
            close()
        }

        if (selected) {
            drawPath(body, color = main)
        } else {
            drawPath(bodyStrokePath, color = main, style = Stroke(strokeW))
        }

        // 脖子下、向下展开的勾（V 形领口，人字形）
        val collar = Path().apply {
            moveTo(9.6f * u, 15.4f * u)             // 左上
            lineTo(12.0f * u, 17.6f * u)            // 下尖
            lineTo(14.4f * u, 15.4f * u)            // 右上
        }
        drawPath(
            path = collar,
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

        // 圆（放大：撑满画布，留白更少）
        val circle = Rect(
            left = 2.5f * u,
            top = 2.5f * u,
            right = 21.5f * u,
            bottom = 21.5f * u
        )
        if (selected) {
            drawOval(color = main, topLeft = circle.topLeft, size = circle.size)
        } else {
            // 内收半宽，使外沿与实心一致 → 点亮不跳大
            val hb = strokeW / 2f
            drawOval(
                color = main,
                topLeft = Offset(circle.left + hb, circle.top + hb),
                size = Size(circle.width - strokeW, circle.height - strokeW),
                style = Stroke(strokeW)
            )
        }

        // ¥ 符号：两斜线 + 两横（加宽、压矮，居中）
        val yTop = 8.6f * u
        val yMid = 13.0f * u
        val yBot = 15.8f * u
        // 左斜线
        val left = Path().apply {
            moveTo(8.0f * u, yTop)
            lineTo(12f * u, yMid)
        }
        // 右斜线
        val right = Path().apply {
            moveTo(16.0f * u, yTop)
            lineTo(12f * u, yMid)
        }
        // 竖线
        val stem = Path().apply {
            moveTo(12f * u, yMid)
            lineTo(12f * u, yBot)
        }
        // 两横（加宽）
        val bar1 = Path().apply {
            moveTo(8.4f * u, yMid + 0.2f * u)
            lineTo(15.6f * u, yMid + 0.2f * u)
        }
        val bar2 = Path().apply {
            moveTo(8.4f * u, yMid + 2.6f * u)
            lineTo(15.6f * u, yMid + 2.6f * u)
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
            // 内收半宽，使外沿与实心一致 → 点亮不跳大
            val hb = strokeW / 2f
            drawRect(
                color = main,
                topLeft = Offset(left + hb, top + hb),
                size = Size(w - strokeW, h - strokeW),
                style = Stroke(strokeW)
            )
        }
        // 三横线（左对齐）：比外框细一些、短一些，不要顶到边框
        val lineStroke = Stroke(width = 1.2f * u, cap = StrokeCap.Round)
        val xStart = left + 2.6f * u
        val longEnd = left + w - 4.5f * u
        val shortEnd = left + w * 0.45f
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