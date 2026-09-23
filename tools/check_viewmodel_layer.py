#!/usr/bin/env python3
"""检查 ViewModel 的构造签名有没有依赖「下层类型」或「平台类型」。

## 这条规则要解决什么

ViewModel 的构造签名是它依赖什么、以及它能不能被单独测试的**唯一**声明处。
一旦签名里出现数据源 / Api / Dao / 网关 / Context 这类东西：

  - 单测里构造不出这个 ViewModel（那些类型要么是 final 具体类、要么需要 Android 环境）；
  - 更根本的是**分层被穿透**了：ViewModel 越过 Repository 直接够到了数据层/平台。

这条规则是「拆得好不好」的可执行版本。原话（方法论文章）是
「如果你发现同步逻辑没法写单元测试，说明它和 UI/数据库耦合死了」——
那个判据是主观的（"你发现"），这条是可枚举、能自动拦的。

## 允许依赖什么

  ✅ 接口形式的跨模块能力（IVideoProvider / IAuthProvider / IUserStore / INotificationProvider）
  ✅ Repository（接口）
  ✅ UseCase（可用假 Repository 构造出来）
  ✅ SavedStateHandle（能直接 new）
  ✅ 可注入的持有状态类（CurrentUser / VideoInteractionBus —— 无参可构造）
  ✅ Kotlin/AndroidX 的纯数据与协程类型

## 禁止依赖什么

  ❌ *RemoteDataSource / *Api / *Dao / ApiGateway / *DataStore / *Database / TokenHolder
  ❌ Context / ContentResolver / SharedPreferences / Looper / Activity / Fragment
  ❌ OkHttp* / Retrofit / ExoPlayer* 等三方 SDK 类型

判定与「它是接口还是类」无关——接口同样禁止，因为这是**分层**规则：
ViewModel 不该越过 Repository 去够存储与网络。

## 用法

    python tools/check_viewmodel_layer.py

退出码 0 = 只有下面 KNOWN_DEBT 里登记的已知欠债；1 = 出现新的违规。
"""

import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# 扫描范围：所有 Android 模块的 main 源集（跳过 build 产物）
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 禁止出现在构造参数类型里的标记
FORBIDDEN = [
    # 具体下层实现
    r"\b\w*RemoteDataSource\b", r"\bApiGateway\b", r"\b\w*Dao\b",
    r"\b\w*DataStore\b", r"\b\w*Database\b", r"\bTokenHolder\b",
    # Api 接口（Retrofit 的 service 接口）也属于数据层，不该出现在这里
    r"\b\w*Api\b",
    # 平台类型
    r"\bContext\b", r"\bContentResolver\b", r"\bSharedPreferences\b",
    r"\bLooper\b", r"\b\w*Activity\b", r"\b\w*Fragment\b",
    # 三方 SDK
    r"\bOkHttp\w*\b", r"\bRetrofit\b", r"\bExoPlayer\w*\b", r"\bMedia3\w*\b",
]

# 已登记的技术债：键是 ViewModel 类名，值是「为什么还在 + 修它要做什么」。
# 出现在这里会让检查通过，但**每一条都应当被消掉**，而不是当作永久豁免。
# 已清空：最后一条（MessageViewModel 依赖 MessageRemoteDataSource）已通过
# 给 feature-message 补上 domain/repository 层修掉。出现新的违规时**不要往这里加**，
# 而是照下面两例的办法把分层补回去：
#   - VideoViewModel：曾直接注入 VideoRemoteDataSource 取转码状态 → 给仓库补 transcodeStatus 方法
#   - PublishViewModel：曾依赖 @ApplicationContext Context（定位 + 构造内容源）
#     → 把两处平台能力收进 LocationProvider / UploadSourceFactory
#   - MessageViewModel：曾直接注入 MessageRemoteDataSource → 补 MessageRepository
KNOWN_DEBT = {}


def strip_comments(text):
    """去掉注释与 KDoc。

    必须做这一步：构造参数的**注释**里常常会提到 Context/DataStore 这些词
    （例如"平台细节收在实现里，本类因此不认识 Context"），
    不剥注释就会把解释性的文字当成依赖——本项目刚踩过：`PublishViewModel` 明明已经
    不依赖 Context 了，却因为参数上写了一句解释而被继续判定为欠债。
    """
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return text


def viewmodel_constructor_signatures():
    """产出 (类名, 文件相对路径, 构造参数文本)"""
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in ("build", ".git", ".gradle", ".idea")]
        parts = dirpath.replace(os.sep, "/")
        if "/src/main/" not in parts:
            continue
        for fn in filenames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            try:
                text = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            if "@HiltViewModel" not in text:
                continue
            for m in re.finditer(r"class\s+(\w+)\s*@Inject\s*constructor\s*\(", text):
                name = m.group(1)
                i = m.end()
                depth, buf = 1, []
                while i < len(text) and depth:
                    ch = text[i]
                    if ch == "(":
                        depth += 1
                    elif ch == ")":
                        depth -= 1
                        if depth == 0:
                            break
                    buf.append(ch)
                    i += 1
                rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                yield name, rel, strip_comments("".join(buf))


def main():
    violations = []
    total = 0
    for name, rel, sig in viewmodel_constructor_signatures():
        total += 1
        hits = []
        for pat in FORBIDDEN:
            for hit in re.findall(pat, sig):
                if hit not in hits:
                    hits.append(hit)
        if hits:
            violations.append((name, rel, hits))

    new = [v for v in violations if v[0] not in KNOWN_DEBT]
    stale = [n for n in KNOWN_DEBT if n not in {v[0] for v in violations}]

    print("扫描到 %d 个 @HiltViewModel" % total)
    print()

    if violations:
        print("构造签名里含下层/平台类型的：%d 个" % len(violations))
        for name, rel, hits in sorted(violations):
            tag = "已知欠债" if name in KNOWN_DEBT else "★ 新增违规"
            print("  [%s] %s  ->  %s" % (tag, name, ", ".join(hits)))
            if name in KNOWN_DEBT:
                print("        %s" % KNOWN_DEBT[name])
            else:
                print("        %s" % rel)
        print()
    else:
        print("构造签名全部干净。")
        print()

    failed = False
    if new:
        print("❌ 出现 %d 个新违规：ViewModel 不该依赖下层/平台类型——" % len(new))
        print("   要么把它收进 Repository（像 VideoViewModel 收 transcodeStatus 那样），")
        print("   要么给平台能力抽个接口。改完再跑本检查。")
        failed = True
    if stale:
        print("⚠️  KNOWN_DEBT 里这几条已经不再违规了，请从清单里删掉：%s" % ", ".join(stale))
        failed = True

    if not failed:
        if KNOWN_DEBT:
            print("✅ 通过（已知欠债 %d 条，已登记在脚本内并附原因与修法）" % len(KNOWN_DEBT))
        else:
            print("✅ 通过：所有 ViewModel 的构造签名都干净，已知欠债已清零")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
