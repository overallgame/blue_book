#!/usr/bin/env python3
"""检查客户端与后端的「站内码格式契约」是否一致。

## 这条规则要解决什么

站内码（二维码里那串 URL）有两份实现，而且是**刻意的重复**：

    客户端  lib-base/src/main/java/com/example/blue_book/scan/ScanCodeFormat.kt
    服务端  backend/src/main/kotlin/com/example/bluebook/scan/ScanCodeFormat.kt

为什么不能共享：Android Gradle 工程与 `:backend` 是两个独立编译单元，没有共同源码集
（Kotlin Multiplatform 能解决，但对一个二维码格式实在过度）。而两份实现的漂移后果很重：

  - host 不一致 → **自己分享出去的码，自己扫不出来**（客户端判为外部链接，压根不调 resolve）
  - 长度上限/正则不一致 → 客户端认、服务端不认（或反之），表现为"扫码没反应"
  - 路径段不一致 → 同一条码一边判视频、一边判用户（跳错页面，不报错）

这些都不会崩、不会报错，只会**静默地不正确**，而且只有真机上扫了才发现。
所以它值得一个能自动跑的检查，而不是靠"两个文件都记得改"。

## 检查什么

  1. CODE_HOST            —— 码域名（换真域名时两边必须同时改，且服务端白名单只加不删）
  2. SCHEME               —— https
  3. PATH_VIDEO / PATH_USER —— 路径段
  4. MAX_PAYLOAD_LENGTH   —— 超长判定（不一致会让"畸形输入"的处理在两端分叉）
  5. HTTP_URL 正则源码     —— 真正的安全边界：白名单命中靠它先切出 host

## 有意不一致的地方（不检查）

客户端多一个 `PATH_LOGIN`（`/qr/{ticket}`，Phase 2 登录码）。它**不走 resolve**，
所以服务端不认它是正确的，不是漂移。

## 用法

    python tools/check_scan_code_contract.py

退出码 0 = 一致；1 = 有字段两边对不上（或某一侧将来被删/改名）。
"""

import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CLIENT = "lib-base/src/main/java/com/example/blue_book/scan/ScanCodeFormat.kt"
SERVER = "backend/src/main/kotlin/com/example/bluebook/scan/ScanCodeFormat.kt"

# 必须逐字相同的常量：(展示名, 正则)
# 表达式容忍 `private`/`const` 前缀、可选的类型标注与任意空白——
# 两侧的声明风格本来就不必一致，只有**值**必须一致
STRING_CONST = r'const\s+val\s+%s\s*(?::\s*String)?\s*=\s*"([^"]*)"'
INT_CONST = r"const\s+val\s+%s\s*(?::\s*Int)?\s*=\s*(\d+)"

# HTTP_URL 的源码字面量（含转义符本身）：用非贪婪匹配到同一行的结束引号
HTTP_URL_LITERAL = r'HTTP_URL\s*=\s*Regex\(\s*"((?:[^"\\]|\\.)*)"'


def extract(path, pattern, label):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError as exc:
        return None, "读不到文件：%s" % exc
    m = re.search(pattern % label if "%s" in pattern else pattern, text)
    if not m:
        return None, "没有匹配到 %s" % label
    return m.group(1), None


def main():
    print("客户端：%s" % CLIENT)
    print("服务端：%s" % SERVER)
    print()

    checks = [
        ("CODE_HOST", STRING_CONST),
        ("SCHEME", STRING_CONST),
        ("PATH_VIDEO", STRING_CONST),
        ("PATH_USER", STRING_CONST),
        ("MAX_PAYLOAD_LENGTH", INT_CONST),
        ("HTTP_URL", HTTP_URL_LITERAL),
    ]

    failures = []
    for label, pattern in checks:
        client, c_err = extract(os.path.join(ROOT, CLIENT), pattern, label)
        server, s_err = extract(os.path.join(ROOT, SERVER), pattern, label)
        if c_err or s_err:
            failures.append("%s：%s" % (label, c_err or s_err))
            print("❌ %-20s %s" % (label, c_err or s_err))
            continue
        if client == server:
            print("✅ %-20s %s" % (label, client))
        else:
            failures.append("%s：两边不一致" % label)
            print("❌ %-20s 客户端 = %s" % (label, client))
            print("   %-20s 服务端 = %s" % ("", server))

    # 生成侧与解析侧必须互为逆：客户端有 videoUrl()/userUrl()，服务端也要有，
    # 否则"服务端能解但生成不了"，R7 分享升级成链接时会缺一半
    print()
    for fn in ("videoUrl", "userUrl"):
        missing = [
            side for side, rel in (("客户端", CLIENT), ("服务端", SERVER))
            if not re.search(r"fun\s+%s\s*\(" % fn, open(os.path.join(ROOT, rel), encoding="utf-8").read())
        ]
        if missing:
            failures.append("%s() 缺少实现：%s" % (fn, "、".join(missing)))
            print("❌ %s() 缺少实现：%s" % (fn, "、".join(missing)))
        else:
            print("✅ %s() 两侧都有" % fn)

    print()
    if failures:
        print("❌ 站内码契约不一致（%d 处）：" % len(failures))
        for item in failures:
            print("   - %s" % item)
        print()
        print("   改哪边都要**同时**改另一边，且服务端 host 白名单只加不删")
        print("   （删了等于让已经发出去的码失效，见 ScanCodeFormat.kt 的注释）。")
        return 1

    print("✅ 站内码契约一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
