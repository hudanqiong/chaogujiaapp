#!/usr/bin/env python3
# 专门分析同花顺"K线实体柱"和"成交量柱"的颜色
from PIL import Image
from collections import Counter

im = Image.open("/sdcard/Download/ths.png").convert("RGB")
W, H = im.size
px = im.load()
print(f"size={W}x{H}")

# 逐行扫描，找出"成片的纯色块"（柱子因为是实心矩形，会有连续色）
# K线实体柱：一般宽度 6~20px，高度几十~上百
# 用"连续横向同色 >5px 且纵向也存在"来识别柱体

# 方法：统计每一列出现最多的"非背景色"
bg = Counter()
for y in range(0, H, 3):
    for x in range(0, W, 3):
        bg[px[x, y]] += 1
print("全图最高频颜色(背景):")
for c, n in bg.most_common(6):
    print(f"   rgb{c}  #{c[0]:02X}{c[1]:02X}{c[2]:02X}  n={n}")

# 专门找"饱和的绿/红柱"——K线实体
print("\n=== 高饱和有色像素分类统计（找柱体）===")
green = Counter()
red = Counter()
for y in range(0, H, 1):
    for x in range(0, W, 1):
        r, g, b = px[x, y]
        mx, mn = max(r, g, b), min(r, g, b)
        sat = (mx - mn) / mx if mx else 0
        if sat < 0.45:
            continue
        if g >= r and g >= b:
            green[((r//2)*2, (g//2)*2, (b//2)*2)] += 1
        elif r >= g and r >= b:
            red[((r//2)*2, (g//2)*2, (b//2)*2)] += 1

print("\n绿系(高饱和) top12 —— 这些是'绿柱/绿线'本体:")
for (r, g, b), n in green.most_common(12):
    print(f"   #{r:02X}{g:02X}{b:02X}  n={n}")

print("\n红系(高饱和) top8 —— 对照(红柱/红线):")
for (r, g, b), n in red.most_common(8):
    print(f"   #{r:02X}{g:02X}{b:02X}  n={n}")