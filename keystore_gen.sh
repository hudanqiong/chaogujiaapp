#!/bin/bash
# 生成固定调试签名 keystore（仅 CI/Linux 有 keytool 时用）
keytool -genkeypair -v \
  -keystore app/cgj-fixed-debug.keystore \
  -alias cgjdebug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass cgjfixed123 -keypass cgjfixed123 \
  -dname "CN=Chaogujia Debug, OU=Dev, O=Chaogujia, L=City, ST=State, C=CN"
echo "GEN_EXIT=$?"
ls -l app/cgj-fixed-debug.keystore
