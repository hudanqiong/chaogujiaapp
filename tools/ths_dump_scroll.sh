#!/system/bin/sh
# 同花顺详情页：从顶滑到底，逐屏 dump 控件树 + 截图
# 用法: ths_dump_scroll.sh <输出前缀> <滑动次数>
PKG=com.hexin.plat.android
OUT=${1:-/sdcard/Download/ths_scroll}
N=${2:-6}

mkdir -p $OUT
rm -f $OUT/*

echo "[1] 回到主页并启动同花顺"
am start -n $PKG/.SplashActivity >/dev/null 2>&1
sleep 6

echo "[2] 截第一屏"
screencap -p $OUT/screen_00.png
echo "[2b] dump 第一屏"
uiautomator dump $OUT/tree_00.xml >/dev/null 2>&1

# 起始滑动 y 坐标：从屏幕中部往下拖动
Y1=2000
Y2=600
X=632

i=1
while [ $i -le $N ]; do
  echo "[3.$i] 丝滑滑动第 $i 次（向上拖 $Y1->$Y2）"
  input swipe $X $Y1 $X $Y2 600
  sleep 2.5
  printf -v idx "%02d" $i
  screencap -p $OUT/screen_$idx.png
  uiautomator dump $OUT/tree_$idx.xml >/dev/null 2>&1
  i=$((i+1))
done

echo "[4] 完成，输出目录：$OUT"
ls -la $OUT
