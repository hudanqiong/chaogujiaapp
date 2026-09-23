# -*- coding: utf-8 -*-
"""Step1: 副图可切换框架 + MACD 三处修正
关键修复：所有替换都在「同一个 src」上顺序进行，最后统一写盘。
D 段（副图块）改用唯一的字符串锚点，不再用行号切片（避免覆盖前面的插入）。
"""
import io
import sys

P = "/root/cgj/app/src/main/java/com/chaogujia/app/StockDetailPage.kt"
src = io.open(P, encoding="utf-8").read()
orig = len(src)

def rep_once(old, new, tag):
    global src
    cnt = src.count(old)
    if cnt != 1:
        print("FAIL:", tag, "count=", cnt)
        sys.exit(1)
    src = src.replace(old, new, 1)
    print("OK:", tag)

# ================= A. 常量区：新增色 =================
rep_once(
    'private val TICK_GREEN = Color(0x3319A05A)   // 蜡烛绿（半透明填充）',
    '''private val TICK_GREEN = Color(0x3319A05A)   // 蜡烛绿（半透明填充）
// ===== 副图指标切换：标签框（长方形、无圆角、宽自适应包住 名称+▼）=====
private val SUB_TAG_BORDER = Color(0xFFBBBBBB)   // 标签框描边：浅灰
private val SUB_TAG_BG = Color(0xFFF7F7F7)       // 标签框底：近白
private val SUB_TAG_FG = Color(0xFF333333)       // 标签框文字
// ===== MACD 三线（柱改线后）=====
private val MACD_DIF_COLOR = Color(0xFF111111)   // DIF 快线：黑
private val MACD_DEA_COLOR = Color(0xFFE64A3B)   // DEA 慢线：红
private val MACD_BAR_COLOR = Color(0xFF2E8BE6)   // MACD 原柱改线：蓝''',
    "const-colors")

# ================= B. SubIndicator 枚举 =================
rep_once(
    '/** MACD 指标值：+0.123 / -0.456（3 位小数，正数带 +） */',
    '''// ============================================================
// 副图指标枚举（可切换）
// ============================================================
/** 副图可切换指标；顺序即「点副图空白循环切换」的顺序。 */
internal enum class SubIndicator(val label: String, val tag: String) {
    VOL("成交量", "成交量"),
    MACD("MACD", "MACD(12,26,9)"),
    KDJ("KDJ", "KDJ(9,3,3)"),
    RSI("RSI", "RSI(6,12,24)"),
    WR("WR", "WR(14)"),
    CCI("CCI", "CCI(14)"),
    BIAS("BIAS", "BIAS(6,12,24)"),
    PSY("PSY", "PSY(12)"),
    OBV("OBV", "OBV"),
    DMI("DMI", "DMI(14,6)"),
    BOLL("BOLL", "BOLL(20,2)");

    fun next(): SubIndicator = entries[(ordinal + 1) % entries.size]
}

/** MACD 指标值：+0.123 / -0.456（3 位小数，正数带 +） */''',
    "enum")

# ================= C. 状态变量 =================
rep_once(
    'val macdFull = remember(d?.code, line.size) { computeMACD(line) }',
    '''val macdFull = remember(d?.code, line.size) { computeMACD(line) }
    // 当前副图指标（点副图空白循环切换 / 点标签框弹层选择）
    var currentSub by remember(d?.code) { mutableStateOf(SubIndicator.VOL) }
    var subPickerOpen by remember { mutableStateOf(false) }''',
    "state")

# ================= D. 副图两块 -> 一块可切换（字符串锚点）=================
# 老的「成交量 Box + MACD Box」整块：从成交量 Box 开头到 MACD Box 结尾。
# 用「Box( + KLineVolumeCanvas + ... + KLineMacdCanvas + ... + }」的唯一起止锚点。
old_vol_start = '''        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.21f)'''
idx = src.find(old_vol_start)
if idx < 0:
    print("FAIL: D head not found")

# 从该位置起找到 MACD Box 的结束：即该副图块后第一个 "\n        }\n    }\n}"
# 更稳：以「KLineMacdCanvas 调用结束」为锚，向后找到它所属 Box 的收尾
tail_anchor = '''            KLineMacdCanvas(
                dif = macdShow.dif,
                dea = macdShow.dea,
                macd = macdShow.macd,
                selLocal = selIdx'''
ti = src.find(tail_anchor)
if ti < 0:
    print("FAIL: D tail anchor not found (KLineMacdCanvas)")
    sys.exit(1)
# 从 tail_anchor 起，找到 "canvas 参数块)" 后连续两个 Box 收尾 "        }\n"
seg = src[ti:]
# 找第一个 "\n        }\n" —— 这是 MACD Box 的收尾（Box 内容结束）
close_idx = seg.find("\n        }\n")
if close_idx < 0:
    print("FAIL: D close brace not found")
    sys.exit(1)
end_idx = ti + close_idx + len("\n        }\n")

old_block = src[idx:end_idx]
# 校验：old_block 必须同时含两个画布调用，且以 Box 开头、以 } 结尾
assert "KLineVolumeCanvas" in old_block and "KLineMacdCanvas" in old_block, "D block sanity failed"
print("D block len:", len(old_block), "head:", old_block[:30].replace("\n","|"), "tail:", old_block[-30:].replace("\n","|"))

new_block = '''        // ===== 可切换副图（同花顺：点标签框弹层选 / 点空白循环切下一个）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .padding(start = 2.dp, end = 2.dp, top = 4.dp)
                .pointerInput(currentSub, show.size, selIdx) {
                    detectTapGestures { currentSub = currentSub.next() }
                }
        ) {
            val subLocal = selIdx?.let { it - start }?.takeIf { it in show.indices }
            when (currentSub) {
                SubIndicator.VOL -> KLineVolumeCanvas(line = show, selLocal = subLocal)
                SubIndicator.MACD -> KLineMacdCanvas(
                    dif = macdShow.dif,
                    dea = macdShow.dea,
                    macd = macdShow.macd,
                    selLocal = subLocal
                )
                else -> SubPlaceholderCanvas(text = currentSub.label)
            }
            // ===== 副图数值标签（MACD：选中看选中，未选中看最后一根）=====
            if (currentSub == SubIndicator.MACD) {
                val labIdx = subLocal?.takeIf { it in macdShow.dif.indices }
                    ?: (macdShow.dif.size - 1).takeIf { it >= 0 }
                if (labIdx != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 6.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DIF", color = MACD_DIF_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMacdNum(macdShow.dif[labIdx]), color = MACD_DIF_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("DEA", color = MACD_DEA_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMacdNum(macdShow.dea[labIdx]), color = MACD_DEA_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("MACD", color = MACD_BAR_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMacdNum(macdShow.macd[labIdx]), color = MACD_BAR_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // ===== 指标名标签框（长方形 · 无圆角 · 宽自适应包住 名称+▼）=====
            SubIndicatorTag(
                text = if (currentSub == SubIndicator.MACD) currentSub.tag else currentSub.label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp, top = 2.dp)
            ) { subPickerOpen = true }
        }
'''
src = src[:idx] + new_block + src[end_idx:]
print("OK: sub-chart merged")

# ================= E. 弹层调用（挂在 Column 之前）=================
rep_once(
    '    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {',
    '''    // 副图指标选择弹层（点哪个换哪个）
    if (subPickerOpen) {
        SubIndicatorPicker(
            current = currentSub,
            onPick = { currentSub = it; subPickerOpen = false },
            onDismiss = { subPickerOpen = false }
        )
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {''',
    "picker-call")

# ================= F. 新增三个 Composable =================
rep_once(
    '''/**
 * MACD 副图（同花顺风格）。''',
    '''/**
 * 副图指标名标签框：长方形（无圆角），宽高自适应包住「指标名 + ▼」。
 */
@androidx.compose.runtime.Composable
private fun SubIndicatorTag(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .background(SUB_TAG_BG)
            .border(1.dp, SUB_TAG_BORDER)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = SUB_TAG_FG, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(text = "▼", color = SUB_TAG_FG, fontSize = 9.sp, maxLines = 1)
    }
}

/**
 * 副图指标选择弹层：列出全部指标，点哪个换哪个。
 */
@androidx.compose.runtime.Composable
private fun SubIndicatorPicker(
    current: SubIndicator,
    onPick: (SubIndicator) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "指标切换",
                color = TEXT_MAIN,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SubIndicator.entries.forEach { ind ->
                val on = ind == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ind) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ind.label,
                        color = if (on) Color(Config.COLOR_PRIMARY) else TEXT_MAIN,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.weight(1f))
                    if (on) Text("✓", color = Color(Config.COLOR_PRIMARY), fontSize = 14.sp)
                }
            }
        }
    }
}

/** 尚未接入的副图指标占位画布（后续逐个替换为真实计算+绘制）。 */
@androidx.compose.runtime.Composable
private fun SubPlaceholderCanvas(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$text 指标接入中…", color = TEXT_SUB, fontSize = 12.sp)
    }
}

/**
 * MACD 副图（同花顺风格）。''',
    "components")

# ================= G. MACD 柱 -> 折线 =================
rep_once(
    '''        // MACD 柱
        for (i in 0 until n) {
            val cx = i * slot + slot / 2
            val m = macd[i]
            val bh = (kotlin.math.abs(m) * scale).toFloat()
            if (bh < 0.4f) continue
            drawRect(
                color = if (m >= 0) RED_UP else GREEN_DOWN,
                topLeft = Offset(cx - bw / 2, if (m >= 0) midY - bh else midY),
                size = Size(bw, bh),
                style = Fill
            )
        }
''',
    '''        // MACD：柱改折线（与 DIF/DEA 同一套 poly，单色蓝）
''',
    "macd-bar->line")

rep_once(
    '''        poly(dea, Color(0xFFE64A3B), 2.2f)   // 红线（慢线 DEA）
        poly(dif, Color(0xFF111111), 2.2f)   // 黑线（快线 DIF = 上攻线）''',
    '''        poly(macd, MACD_BAR_COLOR, 2.2f)     // 蓝线（MACD，原柱改线）
        poly(dea, MACD_DEA_COLOR, 2.2f)      // 红线（慢线 DEA）
        poly(dif, MACD_DIF_COLOR, 2.2f)      // 黑线（快线 DIF = 上攻线）''',
    "poly-calls")

rep_once(
    " * 绘制：MACD 柱（红正/绿负，零轴上下）+ DIF 线（白）+ DEA 线（黄）。",
    " * 绘制：MACD（蓝线，原柱改线）+ DEA 线（红）+ DIF 线（黑）；零轴居中。",
    "macd-doc")

# bw 已不再使用：删除以保持干净
rep_once(
    '        val bw = max(slot * 0.85f, 1.6f)\n',
    '',
    "drop-unused-bw")

io.open(P, "w", encoding="utf-8").write(src)
print("PATCH_SUB1_OK  orig_len=%d new_len=%d delta=%d" % (orig, len(src), len(src) - orig))
print("enum class count:", src.count("enum class SubIndicator"))
print("SUB_TAG_BG count:", src.count("SUB_TAG_BG"))
