#!/usr/bin/env python3
"""找出「在块注释正文里写了 `/*`」的 Kotlin 代码。

## 这条规则要解决什么

Kotlin 的块注释**可以嵌套**（`/* /* */ */`），所以在 KDoc 正文里出现一个 `/*`
（典型写法：`` `/api/file/**` ``）会**开一层嵌套注释**，于是本该闭合外层注释的那个 `*/`
只关掉了内层——编译器从文件末尾往回报 `Unclosed comment`，指向的位置离真正的错处很远。
本文件就是被这个坑咬过之后写的：`FileController` 的类注释里写了 `` `/api/file/**` ``，
报错点在 80 行之后的文件末尾。

## 为什么会误报，以及怎么收紧

`/*` 出现在**字符串字面量**里（如 `"/api/v2/auth/**"`）和 **`//` 行注释**里（如 `/videos/*/dto`）
都无害：字符串不是注释、行注释不嵌套。所以判据只取一种情况：

    块注释正文的续行（行首是 `*`）里含 `/*`

代码行、字符串、`//` 注释一律不看。这样误报率极低，而唯一会命中的写法正是真正致命的那种。

## 用法

    python tools/check_kotlin_comment_nesting.py

退出码 0 = 没有这种写法；1 = 有（列出文件:行）。
"""

import glob
import io
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 块注释续行：可选空白 + `*`（但不是 `*/`），且该行不是注释起始行本身
COMMENT_CONTINUATION = re.compile(r"^\s*\*(?!/)")


def kotlin_files():
    for pattern in ("**/*.kt", "**/*.kts"):
        for path in glob.glob(os.path.join(ROOT, pattern), recursive=True):
            norm = path.replace(os.sep, "/")
            if "/build/" in norm or "/.git/" in norm:
                continue
            yield path, os.path.relpath(path, ROOT).replace(os.sep, "/")


def main():
    hits = []
    for path, rel in kotlin_files():
        for lineno, line in enumerate(io.open(path, encoding="utf-8", errors="replace"), 1):
            if not COMMENT_CONTINUATION.match(line):
                continue
            if "/*" in line:
                hits.append((rel, lineno, line.strip()[:100]))

    if not hits:
        print("✅ 没有在块注释正文里写 `/*`（Kotlin 会把它当嵌套注释）")
        return 0

    print("❌ 块注释正文里出现了 `/*`，会开一层嵌套注释，吃掉外层注释的闭合符：")
    for rel, lineno, text in hits:
        print("   %s:%d" % (rel, lineno))
        print("      %s" % text)
    print()
    print("   改法：把 `**` 拆开写（例如 `/api/file/` 下的接口），或用 `\\*\\*` 转义。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
