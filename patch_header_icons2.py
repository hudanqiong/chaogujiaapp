# -*- coding: utf-8 -*-
# patch_header_icons2.py
# 改动（相对 patch21 / 74357df）：
# 1) 图标 24dp -> 18dp
# 2) 去掉点击水波纹（indication=null + interactionSource）
# 3) 左滑(hScroll.value>0)时：图标2消失，改显示 "编辑表头" 文字在前 + 图标1 在后
#    未左滑(hScroll.value==0)：显示两个图标
#    持仓股分组(-2)：仍显示原文字

import io, sys

PATH = '/root/cgj/app/src/main/java/com/chaogujia/app/IndexBar.kt'

with io.open(PATH, 'r', encoding='utf-8') as f:
    src = f.read()

# ---- 旧块（第一轮写入的整段 if 分支内容）----
OLD = '''                        if (selectedGroup != -2) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_watch_add),
                                    contentDescription = "编辑表头",
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .size(24.dp)
                                        .clickable {
                                            // TODO: 进入「表头管理页」（交互口径待定）
                                        }
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_watch_share),
                                    contentDescription = "排序",
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .size(24.dp)
                                        .clickable {
                                            // 排序：暂不接逻辑（空按钮）
                                        }
                                )
                            }
                        } else {'''

# ---- 新块 ----
NEW = '''                        if (selectedGroup != -2) {
                            // 左滑(hScroll.value>0)时：图标2隐藏，显示「编辑表头 [图标1]」
                            // 未左滑：显示两个 18dp 图标（点击无视觉反馈）
                            if (hScroll.value == 0) {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_add),
                                        contentDescription = "编辑表头",
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // TODO: 进入「表头管理页」（交互口径待定）
                                            }
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_share),
                                        contentDescription = "排序",
                                        modifier = Modifier
                                            .padding(start = 18.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // 排序：暂不接逻辑（空按钮）
                                            }
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "编辑表头",
                                        color = COLOR_TEXT,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_watch_add),
                                        contentDescription = "编辑表头",
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // TODO: 进入「表头管理页」（交互口径待定）
                                            }
                                    )
                                }
                            }
                        } else {'''

cnt = src.count(OLD)
if cnt != 1:
    print('ERROR: OLD block found %d times, expected 1' % cnt)
    sys.exit(1)

src = src.replace(OLD, NEW)

with io.open(PATH, 'w', encoding='utf-8') as f:
    f.write(src)

print('OK: patched. size=%d' % len(src))