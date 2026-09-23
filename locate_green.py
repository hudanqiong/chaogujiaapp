#!/usr/bin/env python3
# 定位各"绿值"出现在图上的哪个区域，并扫K线主图区
from PIL import Image
from collections import Counter

path = "/sdcard/Download/ths.png"
im = Image.open(path).convert("RGB")
W, H = im.size
px = im.load()

targets = {
    "326612": (0x32, 0x66, 0x12),
    "66C63A": (0x66, 0xC6, 0x3A),
    "329A1E": (0x32, 0x9A, 0x1E),
    "66B656": (0x66, 0xB6, 0x56),
    "02DA6A": (0x02, 0xDA, 0x6A),
    "06CA62": (0x06, 0xCA, 0x62),
    "02FE02": (0x02, 0xFE, 0x02),
}

def near(p, t, tol=10):
    return abs(p[0]-t[0])<=tol and abs(p[1]-t[1])<=tol and abs(p[2]-t[2])<=tol

for name, t in targets.items():
    ys = []
    xs = []
    cnt = 0
    for y in range(0, H, 2):
        for x in range(0, W, 2):
            if near(px[x, y], t):
                cnt += 1
                ys.append(y); xs.append(x)
    if cnt:
        ys.sort(); xs.sort()
        print(f"#{name}: n={cnt*4}  y[{ys[0]}..{ys[-1]}] median_y={ys[len(ys)//2]}  x[{xs[0]}..{xs[-1]}]")
    else:
        print(f"#{name}: not found")

# 切 K 线主图区（大致中上部）单独看绿
print("\n=== 分区域扫绿 ===")
regions = {
    "顶部状态栏/header": (0, 0, W, 300),
    "价格大字区": (0, 300, W, 700),
    "K线主图区": (0, 700, W, 1700),
    "成交量区": (0, 1700, W, 2100),
    "底部tab区": (0, 2500, W, H),
}
for rn, (x0, y0, x1, y1) in regions.items():
    c = Counter()
    for y in range(y0, y1, 2):
        for x in range(x0, x1, 2):
            r, g, b = px[x, y]
            if g > r + 25 and g > b + 25 and g > 80 and (max(r,g,b)-min(r,g,b))/max(r,g,b) > 0.35:
                c[((r//8)*8, (g//8)*8, (b//8)*8)] += 1
    top = c.most_common(6)
    print(f"\n[{rn}] 绿像素 top6:")
    for (r, g, b), n in top:
        print(f"   #{r:02X}{g:02X}{b:02X} n={n*4}")