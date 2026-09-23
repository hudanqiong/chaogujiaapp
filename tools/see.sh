#!/bin/bash
# 便捷入口: see.sh <图片路径> "<问题>"
source /root/cgj/tools/.vision_env
exec python3 /root/cgj/tools/vl_call.py "$1" "$2"
