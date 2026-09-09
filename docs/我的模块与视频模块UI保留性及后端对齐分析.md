# 我的模块与视频模块 UI 保留性及后端对齐分析

> 分析日期：2026-09-04
> 分析基线：本机 UI 改版提交 `443d1e4`（视频播放页重构）与 `47c3200`（资料编辑页卡片化）、`0f411d4`（全面屏适配）
> 对比对象：合并进 main 的后端代码（`backend/`）及协作者提交 `c507990`（资料编辑重构）、`8f5b7e8`（视频数据链补齐）
> 目标：**视频模块与我的模块的 UI 保持我们此前的改版结果**，数据流与最新后端对齐

---

## 一、结论摘要

| 检查项 | 结论 | 说明 |
|---|---|---|
| 视频模块 UI 布局 | ✅ 完整保留 | `443d1e4` 之后无任何提交改动 feature-video 的 layout 文件 |
| 视频模块 UI 代码 | ✅ 完整保留 | Kotlin 侧仅我们自己加的全面屏避让（`0f411d4`）和协作者新增的数据层方法（不动 UI） |
| 我的模块 UI 布局 | ✅ 完整保留 | `user_profile_page.xml` 为我们的卡片版、`mine_page.xml` 为我们的 ConstraintLayout 版（含 mine_content id） |
| 我的模块 UI 代码 | ❌ **编译断裂（18 个错误）** | 合并保留了我方 Fragment/ViewModel，却采用了协作者重写的 Contract，两者不匹配 |
| 数据层与后端对齐 | ✅ 全部对齐 | 按字段更新、头像/背景上传、feed/likes/collections、DTO 字段映射均已核对一致 |

**核心问题只有一个**：`feature-mine` 资料编辑链路的 Contract 与 ViewModel/Fragment 版本错配导致编译不过。UI 布局本身没有任何丢失。

---

## 二、视频模块 UI 完整性检查

### 2.1 检查方法

```bash
git log --oneline 443d1e4..HEAD -- feature-video/src/main/res/layout/
# 结果：空（无提交触碰布局）
```

当前 `video_item_view.xml` 中我们改版的全部特征元素均在（grep 计数 6 处）：
`video_item_back`（返回）、`video_item_share`（分享）、`video_item_fullscreen`（全屏观看胶囊）、`video_item_follow_btn`（关注钮）、`video_item_tags`（话题标签行）。

### 2.2 Kotlin 侧改动归属

| 提交 | 改动 | 性质 |
|---|---|---|
| `0f411d4`（我方） | VideoActivity 全面屏 + VideoAdapter insets 避让 | 我们自己的全面屏工作 |
| `8f5b7e8`（协作者） | VideoApi/VideoRemoteDataSource/VideoRepositoryImpl/VideoProviderImpl 新增 `myLikes`/`myCollections` 端点 | 纯数据层，不触及 UI |
| `98e40fe`（合并） | 无视频 UI 变化 | — |

**结论：视频模块 UI 与我们交付时完全一致，无需任何恢复工作。**

---

## 三、我的模块 UI 完整性检查

### 3.1 布局层：我们的版本在位

- `user_profile_page.xml`：分区卡片版（头像+相机角标 / 名字·小红书号·背景图 / 简介 / 性别·生日·地区·职业·学校），特征引用（`shape_profile_card`、`userInfo_row_nickname`、`_chevron_right`）共 13 处 ✓
- `mine_page.xml`：ConstraintLayout 版上半部 + 顶栏右侧动作组，仅比我们版本多一个 `mine_content` id（我方 `0f411d4` 全面屏所需）✓
- `fragment_profile_field_edit.xml`（单字段编辑页）：我们的版本 ✓

### 3.2 Kotlin 层：合并错配，编译断裂

协作者 `c507990` 曾完整重写资料编辑（按字段弹窗编辑 `EditFieldDialog` + 图片预览即传 + 新 Contract），其 12 个文件在合并 `98e40fe` 时**只保留了数据层与 Contract 部分**，UI 三件（ViewModel / UserProfileEditFragment / ProfileFieldEditFragment）被我们的版本覆盖，而 `EditFieldDialog.kt`（112 行）、`dialog_edit_field.xml`、`ic_edit_arrow.xml` 成为无引用的孤儿资源。

错配明细（`:feature-mine:compileDebugKotlin` 实测 18 个错误）：

| 文件 | 问题 |
|---|---|
| `UserProfileContract.kt`（协作者版在位） | 已删除 `SubmitUpdate`/`UpdateImages`，新增 `UpdateNickname/UpdateBio/UpdateGender/UpdateBirthday/UpdateOccupation/UpdateRegion/UpdateSchool/UploadAvatar/UploadBackground/CancelAvatarPreview/CancelBackgroundPreview` 与 Effect `FieldUpdated` |
| `UserProfileViewModel.kt`（我方旧版在位） | 仍在 dispatch `SubmitUpdate`/`UpdateImages` → Unresolved；when 不 exhaustive |
| `ProfileFieldEditFragment.kt`（我方旧版在位） | 7 处 `SubmitUpdate` Unresolved；when 缺 `FieldUpdated` 分支 |
| `UserProfileEditFragment.kt`（我方旧版在位） | 2 处 `UpdateImages` Unresolved；when 缺 `FieldUpdated` 分支 |

### 3.3 修复方案（推荐 B）

**方案 A**：整体恢复 `c507990` 的 UI 三件 → 丢失我们的卡片式布局、单字段编辑页、全面屏避让，与目标相悖，弃。

**方案 B（推荐，与"UI 按我们之前修改"的目标一致）**：
1. 保留我们的 `user_profile_page.xml`、`fragment_profile_field_edit.xml`、两个 Fragment 的视图逻辑与 insets 避让
2. 重写 `UserProfileViewModel.handleIntent` 适配新 Contract：各 `UpdateXxx` intent 调用协作者新加的仓库方法（`updateNickname/updateBio/...`，均已对齐后端）；`UploadAvatar/UploadBackground` 走 `uploadAvatarFile/uploadBackgroundFile`；`CancelXxxPreview` 清空 UiState 中的预览 URI
3. `ProfileFieldEditFragment` 的 7 处 `SubmitUpdate(...)` 改为对应字段的 `UpdateXxx(value)`
4. `UserProfileEditFragment` 图片选择回调改为 `UploadAvatar/UploadBackground`（可选用 UiState 的预览 URI 字段做"预览+即时上传"）
5. 两处 `when(effect)` 补 `FieldUpdated` 分支（或改用其触发返回刷新）
6. 决定孤儿资源去留：`EditFieldDialog` 与我们的单字段编辑页功能重叠，建议删除（连同 `dialog_edit_field.xml`、`ic_edit_arrow.xml`）；若想保留弹窗交互作为轻量入口再接入

---

## 四、数据流与后端对齐分析（逐链路核对）

### 4.1 用户资料：按字段更新链 ✅

| 环节 | Android | 后端 | 对齐 |
|---|---|---|---|
| 端点 | `UserApi`: `@PUT /api/v2/me/nickname`（bio/gender/birthday/occupation/region/school 同构，共 7 个） | `UserController`: `@PutMapping("/api/v2/me/nickname")` 等 7 个 | ✅ 路径一致 |
| 请求体 | `NicknameUpdateRequest(nickname)` 等，字段名与端点名同 | `body["nickname"]` 等 Map 取键 | ✅ 键名一致 |
| 响应处理 | `persistAndRestore(dto)`：DTO→domain→Room 持久化→`CurrentUser.restore()` | 返回 `UserV2MeDto` | ✅ |

### 4.2 头像/背景图上传链 ✅

| 环节 | Android | 后端 | 对齐 |
|---|---|---|---|
| 新链路（推荐） | `uploadAvatarFile` → part 名 `avatar` → `/api/v2/me/avatar`；`uploadBackgroundFile` → part 名 `background` → `/api/v2/me/background` | `@RequestParam("avatar")` / `@RequestParam("background")` | ✅ part 名匹配 |
| 旧链路（兼容仍在用） | `updateUserProfile` 整包：头像 part `avatar` → `/me/avatar`；背景 part `file` → FileApi 通用上传 → 相对路径放 `backgroundImage` → `PUT /api/v2/me` 批量端点 | FileController `@RequestParam("file")`；UserController `updateMe` 标注"保留兼容" | ✅ |

### 4.3 视频 feed / 喜欢列表 / 收藏列表 / 作品列表 ✅

| 数据 | Android 端点 | 后端端点 | DTO |
|---|---|---|---|
| 发现页/随机流 | `/api/v2/feed?cursorId&size` | `feed(cursorId, size, optionalUserId())` | `FeedResponseDto{items, nextCursorId}` 两端字段一致 ✅ |
| 我的-点赞 Tab | `/api/v2/me/likes` | `myLikes` | 同上 ✅ |
| 我的-收藏 Tab | `/api/v2/me/collections` | `myCollections` | 同上 ✅ |
| 我的-笔记 Tab | `fetchUserVideos(userId)` | 视频模块 provider 新增 | ✅（按 userId 查作品） |

分页协议统一为游标式 `cursorId + size`，与 CLAUDE.md 记载的既有约定一致。

### 4.4 字段映射与 URL 处理 ✅

`UserMappers.kt`（`UserV2MeDto/UserV2ProfileDto → UserAccount`）：
- `bio → introduction`、`occupation → career`、`gender → sex`（命名空间转换）✅
- `avatar/backgroundImage` 相对路径 → `BASE_URL` 绝对路径（Glide 可直接加载）✅
- 新增 `id: Long` 字段贯通（`CurrentUser.userId` 供作品列表查询用）✅

### 4.5 登录态同步 ✅

`CurrentUser`（core-network，@Singleton）：登录/注册写入 → Room 冷启动恢复 → 每次字段更新后 `restore()` 刷新 → 登出 `clear()`。注册流程（`RegisterViewModel`）已接入。

### 4.6 尚未接线的后端能力（与两模块 UI 相关，仅备忘）

- 通知：`/api/v2/notifications`（列表/未读数/已读）后端就绪，消息页 UI 未接
- 发布：`/api/v2/videos/publish` + 分块上传后端就绪，发布页 UI 此前已删除（www_page）
- 热搜：`/api/v2/search/hot` 后端就绪

---

## 五、行动清单（已于 2026-09-04 执行完毕）

1. **【P0·已修复】按方案 B 修复 `feature-mine` 编译断裂**
   - `UserProfileViewModel` 重写为按字段 intent，调用 `UserRepository.updateXxx` 仓库方法；`UploadAvatar/UploadBackground` 走 `uploadAvatarFile/uploadBackgroundFile`，成功后 `refresh()` 重拉
   - `ProfileFieldEditFragment` 7 处 intent 替换为对应 `UpdateXxx`；两 Fragment 的 `when(effect)` 补 `FieldUpdated` 分支
   - `UserProfileEditFragment` 图片选择改 `UploadAvatar/UploadBackground`，渲染改为预览 URI 优先（`avatarPreviewUri ?: user.avatar`）
2. **【P1·已删除】孤儿资源清理**：`EditFieldDialog.kt`、`dialog_edit_field.xml`、`ic_edit_arrow.xml`、无引用的 `UpdateUserProfileUseCase.kt`
3. **【已执行】主页头像/背景切新链路**：`MineViewModel` 的 `updateAvatar/updateBackground` 改为独立上传端点 + 成功后重拉，弃用整包 `updateUserProfile`
4. 视频模块无需任何动作
5. 验证：`:feature-mine:assembleDebug` 通过；全工程 `SubmitUpdate/UpdateImages/EditFieldDialog/UpdateUserProfileUseCase` 零残留

---

## 附录：证据命令

```bash
# 视频布局未被触碰
git log --oneline 443d1e4..HEAD -- feature-video/src/main/res/layout/   # 空

# 我的模块布局仍为卡片版（13 处特征引用）
grep -c "shape_profile_card\|userInfo_row_nickname\|_chevron_right" \
  feature-mine/src/main/res/layout/user_profile_page.xml               # 13

# 编译断裂实测
./gradlew :feature-mine:compileDebugKotlin                             # 18 errors

# 断裂来源
git log --oneline --all -- feature-mine/.../UserProfileContract.kt    # c507990 为最后改动
git show c507990 --stat                                                # 12 文件，合并时 UI 三件被旧版覆盖
```
