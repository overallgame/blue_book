# BlueBook 项目全面审计与需求文档

> **日期**: 2026-08-01 | **版本**: 1.0 | **审计范围**: 全部 11 个模块，163 个 Kotlin 源文件

---

## 目录

1. [项目总览](#1-项目总览)
2. [模块详解](#2-模块详解)
3. [架构评估](#3-架构评估)
4. [类小红书短视频 APP 产品需求文档 (PRD)](#4-类小红书短视频-app-产品需求文档-prd)
5. [实现状态与差距分析](#5-实现状态与差距分析)
6. [已知 Bug 与质量问题](#6-已知-bug-与质量问题)
7. [资源管理问题](#7-资源管理问题)
8. [优先级排序的后续工作](#8-优先级排序的后续工作)

---

## 1. 项目总览

**BlueBook（小蓝书）** 是一款类小红书的短视频社交 Android 应用。

### 技术栈

| 维度 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| 构建工具 | AGP 8.5.2, Gradle 8.8 |
| 最低 SDK | API 31 (Android 12) |
| 目标 SDK | 34 |
| Java 目标 | 17 |
| DI 框架 | Hilt 2.48.1 |
| 网络层 | Retrofit 2.9.0 + OkHttp 4.11.0 + Gson |
| 数据库 | Room 2.6.1 + DataStore Preferences 1.0.0 |
| 路由 | TheRouter 1.3.0（货拉拉 fork） |
| 播放器 | AndroidX Media3 (ExoPlayer) 1.4.1 |
| 图片加载 | Glide 4.13.2 |
| UI 技术栈 | View 体系 (XML + ViewBinding) 为主，Compose BOM 2024.06.00 仅用于主题 |
| 架构模式 | Clean Architecture + MVVM + 自定义 UDF |

### 模块全景图

```
:app  (壳工程: Application + MainActivity + 主题 + 权限)
 │
 ├── :lib-base        (UDF 基类 + 公共 Bean + Provider 接口 + 路由常量)
 │
 ├── :core-network    (ApiGateway + Token 管理 + 拦截器链 + Retrofit API)
 ├── :core-player     (ExoPlayer 引擎 + 200MB LRU 缓存 + PlayerPool + GL 滤镜)
 ├── :core-datastore  (IDataStore 封装 + Room 数据库 + UserDao)
 │
 ├── :feature-auth    (登录/注册: AuthEntry + Login + Register)
 ├── :feature-home    (首页: 瀑布流发现页 + 搜索 + 热搜榜)
 ├── :feature-video   (视频: 全屏沉浸播放 + 评论系统)
 ├── :feature-message (消息: 占位页)
 ├── :feature-mine    (我的: 个人中心 + 资料编辑)
 └── :feature-image   (图片选择: Gallery + 裁剪)
```

### 依赖层次

```
:lib-base
    │
    ├──────────┬──────────┬──────────┐
    │          │          │          │
:core-network :core-player :core-datastore
    │          │          │
    └────┬─────┘          │
         │                │
    ┌────┴────────────┬───┴──────────┐
    │      │          │        │     │
:feature-auth :feature-home :feature-video :feature-mine :feature-message :feature-image
    │      │          │        │     │        │              │
    └──────┴──────────┴────────┴─────┴────────┴──────────────┘
                              │
                            :app
```

- Feature 模块间**零直接依赖**，通过 TheRouter `@ServiceProvider` 服务发现通信
- 3 个跨模块 Provider 接口（均定义在 `:lib-base`）：`IAuthProvider`、`IVideoProvider`、`IUserStore`

---

## 2. 模块详解

### 2.1 :lib-base（基础库，11 个文件）

| 文件 | 用途 | 状态 |
|------|------|------|
| `udf/UdfViewModel.kt` | UDF 基类：Intent(SharedFlow buffer=64) + State(StateFlow) + Effect(SharedFlow buffer=16) | ✅ 完善 |
| `udf/UiIntent.kt` | Intent 标记接口 | ✅ |
| `udf/UiState.kt` | State 标记接口 | ✅ |
| `udf/UiEffect.kt` | Effect 标记接口 | ✅ |
| `data/UserAccount.kt` | 用户数据类（phone + 8 个可空字段） | ✅ |
| `data/VideoCardInfo.kt` | 视频卡片数据类（@Parcelize, 13 字段） | ✅ |
| `provider/IAuthProvider.kt` | 登录状态 + 登出 | ✅ |
| `provider/IVideoProvider.kt` | 视频获取 + 点赞（缺少收藏/评论数接口） | ⚠️ 不完整 |
| `provider/IUserStore.kt` | 用户 CRUD | ✅ |
| `router/RoutePath.kt` | 9 个路由常量 | ✅ |
| `AppContext.kt` | 全局 Application 引用 | ✅ |

### 2.2 :core-network（网络层，18 个文件）

| 组件 | 说明 | 状态 |
|------|------|------|
| `ApiGateway` | 门面类，内部构建 OkHttp/Retrofit，10MB HTTP Cache，ConnectionPool(10, 5min)，超时 10s | ✅ 完善 |
| `TokenHolder` | @Volatile 字段 + 异步持久化 | ✅ 完善 |
| `TokenInterceptor` | 附加 Authorization: Bearer 头 | ✅ |
| `TokenAuthenticator` | 401 → synchronized 刷新 → 重试（最多 1 次） | ✅ |
| `RetryInterceptor` | GET/HEAD 指数退避重试（1s/2s/4s，最多 30s） | ✅ |
| `CommonParamsInterceptor` | 公共参数注入 | ✅ |
| `LogSanitizer` | 日志脱敏（Token 仅显首尾 4 位，body 截断 4KB） | ✅ |
| `NetworkMonitor` | ConnectivityManager → Flow<Boolean> | ✅ |
| `VideoOkHttpProvider` | 视频专用 OkHttp（HTTP/1.1, read 60s, ConnectionPool(6,2min)） | ✅ |
| `ApiCall.kt` | 3 组 Result 风格响应解析（apiResult/commonResult/apiUnitResult） | ✅ |
| `TokenApi` + `TokenRemoteDataSource` | Token 刷新 API | ✅ |
| 2 种响应信封 | `ApiResponse<T>`(code==0) + `CommonResult<T>`(code==200) | ✅ |
| `overrideBaseUrl()` | 运行时切换 Base URL（持久化到 DataStore，**但启动时未恢复**） | ⚠️ Bug |

**已知问题**:
- `overrideBaseUrl()` 持久化后，启动时未从 DataStore 恢复——重启后覆盖失效
- `consumer-rules.pro` 存在但未在 build.gradle.kts 中引用
- `LogSanitizer` 未包装 body（与文档说明不一致）

### 2.3 :core-player（播放器，10 个文件）

| 组件 | 说明 | 状态 |
|------|------|------|
| `PlayerEngine` 接口 | 播放/暂停/释放/进度/速度/事件 | ✅ |
| `ExoPlayerEngine` | Media3 封装，双超时机制 | ✅ 生产级 |
| `PlayerEnginePool` | 对象池（max 3），FIFO 复用 + 预加载 | ⚠️ 有边界条件 Bug |
| `MediaCache` | 200MB LRU 缓存，SQLite 索引持久化 | ✅ |
| `PlayerFactories` | 缓存数据源 + FLAG_IGNORE_CACHE_ON_ERROR + LoadErrorHandlingPolicy(3) | ✅ |
| `PlayerAnalytics` | TTFB 测量 + 错误上报（占位 Log.d） | ⚠️ 需接入真实埋点 |
| `GlVideoSurfaceView` | GLSurfaceView + OES 纹理 + Fragment Shader 滤镜 | ✅ |
| `GlSurfaceProvider` | GL Context 丢失恢复（contextGeneration 追踪） | ✅ |
| `FilterType` | NONE / GRAY / WARM | ✅ |
| `VideoSurfaceProvider` 接口 | Surface 桥接 | ✅ |

**关键参数**:
- 起播超时: 5s, 退避 1.5s/次, 最多重试 2 次
- 播放中卡顿: 15s 检测，1 次恢复尝试
- 缓冲区: 1500/12000/250/500 ms
- 视频 OkHttp: HTTP/1.1, read 60s, ConnectionPool(6, 2min)

**已知问题**:
- Pool 驱逐时可能释放仍绑定到 ViewHolder 的 Engine → 黑屏/无声
- VideoOkHttpProvider 的 BASIC 日志在生产环境也开启

### 2.4 :core-datastore（数据存储，8 个文件）

| 组件 | 说明 | 状态 |
|------|------|------|
| `IDataStore` 接口 | 通用 putString/getInt/putBoolean/putLong/remove/clear | ✅ |
| `AppDataStore` | DataStore Preferences 实现，`remove()` 同时删除 4 种类型键 | ✅ |
| `AppDatabase` | Room 数据库（版本 1） | ✅ |
| `UserEntity` | 用户表（主键 phone） | ✅ |
| `UserDao` | 用户 CRUD | ✅ |
| `UserStoreProviderImpl` | 实现 IUserStore，通过 TheRouter @ServiceProvider 暴露 | ✅ |
| `DatabaseModule` | Hilt DI 提供 IDataStore、AppDatabase、UserDao | ✅ |

### 2.5 :feature-auth（登录注册，27 个文件）

| 页面 | 功能 | 状态 |
|------|------|------|
| AuthEntryFragment | 入口页，自动检测登录状态 | ✅ |
| LoginFragment + ViewModel + Contract | 手机号 + 密码登录 | ✅ |
| RegisterFragment + ViewModel + Contract | 5 字段注册 + 60s 验证码倒计时 | ✅ |
| 完整的 Clean Architecture 分层 | Repository → UseCase → ViewModel，含表单验证 | ✅ |

### 2.6 :feature-home（首页，14 个文件）

| 页面 | 功能 | 状态 |
|------|------|------|
| HomeFragment | DrawerLayout + ViewPager2（3 Tab）+ RadioGroup 同步 | ✅ |
| HomeFindFragment + ViewModel + Contract | 瀑布流发现页（StaggeredGrid, 2 列），下拉刷新 + 无限滚动 + 乐观点赞 | ✅ |
| HomeFocusFragment | "关注" Feed | ❌ 空壳 |
| HomeLocalFragment | "同城" Feed | ❌ 空壳 |
| SearchFragment | 搜索输入页 | ✅ |
| AfterSearchFragment + SearchResultViewModel | 搜索结果页（瀑布流 + 点赞） | ⚠️ 有 Bug |

**已知 Bug**:
- `AfterSearchFragment.toggleLike()` 双向更新导致点赞数**翻倍**（Fragment 层 + ViewModel 层各 +1）
- `SearchResultViewModel.loadMore()` 无分页参数，重复加载同一页
- Toast Effect 被注释吞噬：`/* 可按需提示 */`

### 2.7 :feature-video（视频播放，38 个文件）

| 页面/组件 | 功能 | 状态 |
|------|------|------|
| VideoFragment | 垂直 ViewPager2 全屏播放，页面切换 + 预加载 + 位置恢复 | ⚠️ 后台恢复 Bug |
| VideoViewModel | InitRandom/InitSearch/LoadMore/ToggleLike/ToggleCollect | ⚠️ 服务端状态丢失 |
| VideoAdapter | 播放器绑定/释放/位置保存/进度条/预加载 | ⚠️ Pool 驱逐 Bug |
| CommentBottomSheet | 底部评论弹窗 | ✅ |
| CommentViewModel | 加载更多/发表/回复/乐观点赞/删除 | ✅ |
| CommentAdapter + ReplyAdapter | DiffUtil + 嵌套 RecyclerView | ✅ |
| VideoTabFragment | 视频 Tab 入口 | ❌ 显示错误的页面（publication_page） |
| 进度条 | SeekBar，暂停时可见 | ✅ |
| MD3 深色主题 | 10 色暗色方案 + 16dp 网格间距 | ✅ |

**已知 Bug**:
- 切后台再回来：`adapter.release()` 释放所有 Engine，但 `restore()` 是空方法 → 播放无法恢复
- `toUi()` 硬编码 `isLike=false, isCollect=false, playUrl=""` → 服务端点赞/收藏状态全部丢失
- `VideoCardInfo.commentCount` 默认 0，从未获取 → 评论数永远是 0
- `DiffUtil.getChangePayload()` 构建了 `VideoPayload` 但 `onBindViewHolder` 从未消费 → 死代码
- `VideoUiState.cursorId` 声明了但从未赋值 → 死字段

### 2.8 :feature-mine（个人中心，25 个文件）

| 页面 | 功能 | 状态 |
|------|------|------|
| MineFragment + ViewModel + Contract | DrawerLayout + ViewPager2（3 Tab），个人资料展示/编辑入口，头像/背景图选择 | ✅ |
| MineWorkFragment | "我的作品" | ❌ 空壳 |
| MineLoveFragment | "我的喜欢" | ❌ 空壳 |
| MineCollectionFragment | "我的收藏" | ❌ 空壳 |
| UserProfileEditFragment + ViewModel | 完整资料编辑（9 字段） | ✅ |

### 2.9 :feature-message（消息，2 个文件）

| 文件 | 状态 |
|------|------|
| MessageActivity + MessageFragment | ❌ 纯空壳，仅 inflate "该区域尚未开发" |

### 2.10 :feature-image（图片选择，3 个文件）

| 页面 | 功能 | 状态 |
|------|------|------|
| ImagePickerActivity | 宿主 Activity | ✅ |
| GalleryFragment | 系统相册网格（3 列 Glide 缩略图） | ✅ |
| ImageCropFragment | 裁剪页 | ⚠️ 简化版 |

**裁剪页限制**:
- 触摸拖动/缩放手势**未实现**：`// 简化：这里暂时不实现拖动缩放`
- 仅固定中心方形裁剪框
- 权限被拒绝时无用户提示

### 2.11 :app（壳工程，7 个文件）

| 文件 | 说明 | 状态 |
|------|------|------|
| BlueBookApplication | TheRouter + AppContext + Provider 初始化 | ✅ |
| MainActivity | 4 个底部 Tab（RadioGroup + Fragment 容器）| ✅ |
| Theme (Compose) | Material3 浅色主题 + 色彩系统 | ✅ |

---

## 3. 架构评估

### 优势

1. **Clean Architecture 分层清晰**：每个 Feature 模块遵循 `data/domain/ui` 三层 + Repository + UseCase 模式
2. **跨模块通信设计良好**：TheRouter @ServiceProvider + Hilt @EntryPoint 桥接，Feature 模块零直接依赖
3. **网络层健壮**：Token 自动刷新 + 退避重试 + 日志脱敏 + HTTP 缓存 + 连接池
4. **播放器已达生产级**：对象池 + 预加载 + 双超时恢复 + 200MB 边播边缓存 + 数据损坏自动回源
5. **乐观更新模式**：点赞/收藏采用"先改 UI，失败回滚"，体验流畅
6. **UDF 模式一致**：所有新页面使用 Intent/State/Effect 三件套

### 待改进

1. **数据模型重复**：`Video`(domain) 和 `VideoCardInfo`(data) 高度重叠，映射层丢失服务端状态
2. **字符串字面量散落**：Intent Extra Key、DataStore Key 在多处硬编码，无集中常量管理
3. **线程模型隐式**：依赖"都在主线程"的隐含约定，缺少显式声明
4. **代码生成（kapt）**：TheRouter + Hilt + Room 三重 kapt，编译速度有优化空间（可考虑 KSP 迁移）
5. **资源大量重复**：51 个 drawable 中相当比例是跨模块复制的相同文件

---

## 4. 类小红书短视频 APP 产品需求文档 (PRD)

### 4.1 产品定位

**小蓝书（BlueBook）**——面向年轻用户的短视频社区，融合小红书的图文笔记基因与抖音的沉浸式短视频体验。

### 4.2 核心功能模块

#### M1: 用户系统

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| AUTH-01 | 手机号 + 验证码注册 | P0 | ✅ 已实现 |
| AUTH-02 | 手机号 + 密码登录 | P0 | ✅ 已实现 |
| AUTH-03 | 自动登录（Token 持久化） | P0 | ✅ 已实现 |
| AUTH-04 | Token 自动刷新 | P0 | ✅ 已实现 |
| AUTH-05 | 登出（清除本地数据） | P0 | ✅ 已实现 |
| AUTH-06 | 修改密码 | P1 | ❌ 未实现 |
| AUTH-07 | 第三方登录（微信/QQ） | P2 | ❌ 未实现 |

#### M2: 个人资料

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| PROFILE-01 | 查看个人资料 | P0 | ✅ 已实现 |
| PROFILE-02 | 编辑资料（昵称、简介、性别、生日、地区、学校、职业） | P0 | ✅ 已实现 |
| PROFILE-03 | 上传/更换头像 | P0 | ✅ 已实现（但仅本地裁剪，服务端上传待验证） |
| PROFILE-04 | 上传/更换个人主页背景图 | P1 | ✅ 已实现（同上） |
| PROFILE-05 | 查看他人主页 | P1 | ❌ 未实现（TODO 注释） |
| PROFILE-06 | 关注/取消关注用户 | P1 | ❌ 未实现 |

#### M3: 视频 Feed

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| FEED-01 | 推荐流（瀑布流发现页） | P0 | ✅ 已实现 |
| FEED-02 | 全屏沉浸式短视频播放（竖直滑动切换） | P0 | ✅ 已实现 |
| FEED-03 | 视频预加载（下一个视频提前缓冲） | P0 | ✅ 已实现 |
| FEED-04 | 上划恢复播放位置（返回上一个视频续播） | P0 | ✅ 已实现 |
| FEED-05 | 起播超时自动重试 | P0 | ✅ 已实现 |
| FEED-06 | 播放中卡顿恢复 | P0 | ✅ 已实现 |
| FEED-07 | 下拉刷新 | P0 | ✅ 已实现 |
| FEED-08 | 无限滚动加载更多（基于游标分页） | P0 | ✅ 已实现 |
| FEED-09 | 视频进度条（暂停时显示） | P1 | ✅ 已实现 |
| FEED-10 | 关注 Feed（仅显示已关注用户的内容） | P0 | ❌ 空壳 |
| FEED-11 | 同城 Feed（基于位置的本地内容） | P1 | ❌ 空壳 |
| FEED-12 | 搜索视频（关键词搜索） | P0 | ⚠️ 已实现但有 Bug |
| FEED-13 | 长按视频倍速播放 | P2 | ❌ 未实现 |
| FEED-14 | 双击点赞动画 | P2 | ❌ 未实现 |

#### M4: 互动系统

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| INTERACT-01 | 点赞视频（乐观更新） | P0 | ✅ 已实现 |
| INTERACT-02 | 收藏视频（乐观更新） | P0 | ✅ 已实现 |
| INTERACT-03 | 评论视频 | P0 | ✅ 已实现 |
| INTERACT-04 | 回复评论 | P0 | ✅ 已实现 |
| INTERACT-05 | 点赞评论（乐观更新） | P0 | ✅ 已实现 |
| INTERACT-06 | 删除自己的评论 | P1 | ✅ 已实现 |
| INTERACT-07 | 查看评论列表（分页加载更多） | P0 | ✅ 已实现 |
| INTERACT-08 | 展开/收起子回复 | P1 | ✅ 已实现 |
| INTERACT-09 | 分享视频（微信/QQ/复制链接） | P1 | ❌ TODO |
| INTERACT-10 | 评论数展示 | P0 | ⚠️ 永远显示 0 |

#### M5: 个人作品管理

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| WORKS-01 | "我的作品"列表 | P0 | ❌ 空壳 |
| WORKS-02 | "我的喜欢"列表 | P0 | ❌ 空壳 |
| WORKS-03 | "我的收藏"列表 | P0 | ❌ 空壳 |
| WORKS-04 | 发布视频 | P0 | ⚠️ Tab 入口布局存在但未接入 |

#### M6: 消息系统

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| MSG-01 | 消息列表（评论/点赞/关注通知） | P0 | ❌ 空壳 |
| MSG-02 | 消息未读红点 | P1 | ❌ 未实现 |
| MSG-03 | 推送通知 | P2 | ❌ 未实现 |

#### M7: 内容审核与安全

| 需求 ID | 功能 | 优先级 | 当前状态 |
|---------|------|--------|---------|
| SAFETY-01 | 内容举报 | P1 | ❌ 未实现 |
| SAFETY-02 | 用户拉黑 | P2 | ❌ 未实现 |
| SAFETY-03 | 敏感词过滤 | P1 | ❌ 未实现 |

### 4.3 非功能性需求

| 类别 | 要求 |
|------|------|
| 性能 | 视频首帧 < 1.5s（WiFi），< 2s（4G）；列表滑动 60fps |
| 缓存 | 视频边播边缓存，最大 200MB LRU；评论/用户数据内存缓存 |
| 网络 | 弱网重试，Token 自动刷新，请求合并防重复 |
| 安全 | Token 本地加密存储，HTTPS 全链路，日志脱敏 |
| 兼容性 | Android 12+ (API 31) |
| 主题 | 视频播放页深色模式，其他页面跟随系统 |

---

## 5. 实现状态与差距分析

### 5.1 总体完成度估算

| 模块 | 完成度 | 评估 |
|------|--------|------|
| :lib-base | 90% | UDF 框架、Provider 接口基本完善，IVideoProvider 缺方法 |
| :core-network | 90% | 生产就绪，baseUrl 恢复 Bug 需修 |
| :core-player | 85% | 核心引擎生产级，Pool 边界条件 + 埋点占位待修 |
| :core-datastore | 95% | 功能完整，DataStore + Room 均正常 |
| :feature-auth | 95% | 登录注册完整，缺修改密码和第三方登录（P1/P2） |
| :feature-home | 45% | 发现页完整，**关注/同城两个 Tab 是空壳**，搜索结果有 Bug |
| :feature-video | 65% | 播放器核心完整，**后台恢复 Bug + 服务端状态丢失 + Tab 入口错误** |
| :feature-mine | 50% | 个人中心外壳完整，**3 个子 Tab 全部是空壳** |
| :feature-message | 5% | 仅占位文字，**完全未开发** |
| :feature-image | 70% | 相册选择完整，**裁剪缺少手势交互** |

**整体评估：约 55-60% 完成度**。核心基础设施已生产就绪，但业务功能层面存在大量空壳和 Bug。

### 5.2 实现完整度分级

#### 🟢 已完整实现（生产级）

1. **UDF 架构框架**：Intent/State/Effect 三件套 + runResult 异步包装
2. **网络层**：ApiGateway + Token 鉴权链 + 重试 + 脱敏日志 + HTTP 缓存 + 视频专用 OkHttp
3. **播放器核心**：ExoPlayer 封装 + 对象池 + 预加载 + 200MB LRU 缓存 + 双超时恢复 + GL 滤镜
4. **登录注册**：手机号注册 + 密码登录 + Token 持久化 + 自动刷新
5. **个人中心外壳**：DrawerLayout + ViewPager2 + 资料展示 + 头像设置
6. **资料编辑**：9 字段完整编辑 + 头像/背景图选择
7. **评论系统**：发表/回复/删除/点赞 + 乐观更新 + 分页加载 + 嵌套展示
8. **MD3 深色主题**（feature-video）：10 色暗色方案 + 16dp 网格间距

#### 🟡 已实现但有 Bug

1. **搜索结果页**：点赞数翻倍 + 分页重复 + Toast 被注释吞噬
2. **视频 Feed**：后台恢复失败 + 服务端点赞/收藏/评论数状态丢失 + cursorId 死字段
3. **视频 Tab 入口**：inflate 错误页面（publication_page）
4. **PlayerEnginePool**：驱逐时可能释放已绑定 Engine → 黑屏
5. **ApiGateway.overrideBaseUrl**：持久化后重启失效
6. **图片裁剪**：无拖动/缩放手势，仅为简单中心裁剪
7. **UdfViewModel.runResult**：协程取消时 Catch Throwable 可能弹出异常 Toast

#### 🔴 完全未实现（空壳/TODO）

1. **关注 Feed**（HomeFocusFragment）：仅 5 行空壳代码
2. **同城 Feed**（HomeLocalFragment）：仅 5 行空壳代码
3. **消息系统**（整个 feature-message 模块）：仅 2 个文件，纯占位
4. **我的作品**（MineWorkFragment）：空壳
5. **我的喜欢**（MineLoveFragment）：空壳
6. **我的收藏**（MineCollectionFragment）：空壳
7. **分享功能**：两处 TODO 注释（VideoFragment.onClickShare, onClickAvatar）
8. **查看他人主页**：TODO 注释
9. **视频发布**：Tab 布局存在但未接入业务逻辑
10. **内容举报/用户拉黑/敏感词过滤**：完全未实现
11. **修改密码/第三方登录**：完全未实现
12. **推送通知/消息红点**：完全未实现

---

## 6. 已知 Bug 与质量问题

### 6.1 P0（阻塞级——影响核心功能）

| # | 位置 | 问题 | 影响 |
|---|------|------|------|
| B1 | `VideoFragment.kt` + `VideoAdapter.kt` | 切后台 → `adapter.release()` 释放所有 Engine → 回前台 `restore()` 是空方法 → 播放无法恢复 | **切后台再回来视频黑屏/无声** |
| B2 | `AndroidManifest.xml` × 5 | 5 个 Feature 模块的 Manifest 声明了不存在的 Activity 类路径（`presentation.*` vs 实际的 `ui.*`） | **TheRouter 可能找不到 Activity（需验证路由映射机制是否覆盖）** |
| B3 | `VideoViewModel.toUi()` | `isLike=false, isCollect=false, playUrl=""` 硬编码 | **所有视频加载时点赞/收藏状态丢失，需重新请求播放 URL** |

### 6.2 P1（高优先级——体验/数据正确性受损）

| # | 位置 | 问题 | 影响 |
|---|------|------|------|
| B4 | `PlayerEnginePool.acquire()` | 超过 maxSize 时驱逐最旧的活跃 Engine 并 release()，但 ViewHolder 仍持有引用 | 快速滑动时某个视频播放器被释放但仍显示 → **黑屏** |
| B5 | `AfterSearchFragment.toggleLike()` | Fragment 层 + ViewModel 层各对点赞数 +1 | **搜索结果页点赞数翻倍** |
| B6 | `SearchResultViewModel.loadMore()` | 无分页游标参数，每次加载同一页 | **搜索结果滚动到底部重复显示相同内容** |
| B7 | `ApiGateway.overrideBaseUrl()` | 持久化到 DataStore 但初始化时未恢复 | **Base URL 覆盖重启后丢失** |
| B8 | `CommentBottomSheet.newInstance()` | cid 硬编码为 0 | **评论数永远为 0** |
| B9 | `IVideoProvider` | 缺少 `collectVideo()` 和评论数获取方法 | **跨模块契约与使用不一致** |

### 6.3 P2（中优先级——代码质量/死代码）

| # | 位置 | 问题 |
|---|------|------|
| B10 | `VideoAdapter.VideoPayload` | `getChangePayload()` 构建了 `VideoPayload` 但绑定层从未消费，点赞/收藏每次重绑整行 |
| B11 | `VideoUiState.cursorId` | 声明但从未赋值，实际用 `items.last().aid` 作为游标 |
| B12 | `UdfViewModel.runResult()` | `onFailure` 捕获 `Throwable`，协程取消时的 `CancellationException` 也会触发 toast |
| B13 | `AfterSearchFragment` | Toast Effect 被注释：`/* 可按需提示 */`，用户看不到错误提示 |
| B14 | `VideoOkHttpProvider.BASIC` | 日志在生产环境也开启，泄露视频 URL |
| B15 | `GlVideoSurfaceView` 集成 | 已经在 feature-video 的 Adapter 中移除使用（未接入），但 `core-player/gl/` 代码仍保留 |

### 6.4 P3（低优先级 / 技术债）

| # | 位置 | 问题 |
|---|------|------|
| B16 | `UdfViewModel` | `runResult` 的 `onStart/onFinally` 回调在主线程修改 UI 但本身不是 suspend |
| B17 | `VideoFragment` + `VideoActivity` | Intent Extra Key 字符串散落（"EXTRA_VIDEO", "TAG_SHOW", "search", "keyword"），无集中常量 |
| B18 | `TokenHolder.clear()` | 清除所有 DataStore 数据而非仅 Token 相关键 |
| B19 | `ApiGateway.retrofit` | 每次 `createApi` 都重新构建 Retrofit，无缓存 |
| B20 | `MineFragment` | 空的 `DefaultLifecycleObserver` 注册 |

---

## 7. 资源管理问题

### 7.1 重复资源统计

| 资源 | 重复模块数 | 建议 |
|------|-----------|------|
| `_chevron_left1.xml` (返回箭头) | 4 个模块 | 提取到 `:lib-base` |
| `ic_launcher_background.xml` | 3 个模块 | 提取到 `:lib-base` |
| `navigation_item_selector.xml` | 3 模块 × 2 目录(drawable+color) = 6 个 | 提取到 `:lib-base` |
| `like_icon2.xml`, `like_icon3.xml` | 2 模块各 | 提取到 `:lib-base` |
| `tag_background.xml`, `music_note.xml` | 2 模块各 | 提取到 `:lib-base` |
| `main_bottom_navigation.xml` (menu) | 2 模块 | 提取到 `:lib-base` |
| `NoMaterialButtonStyle`, `RadioGroupButtonStyle` | 3 模块各 | 提取到 `:lib-base` |
| `md_theme_*` 颜色值 | 4 个 feature 模块 | 统一到 `:app` |
| `default_avatar.png` | 2 模块 | 提取到 `:lib-base` |

**建议**：将共享资源提取到 `:lib-base`，各模块通过 `com.example.blue_book.lib_base.R` 引用。

### 7.2 缺失资源

| 资源 | 说明 |
|------|------|
| 视频播放占位图 | `ic_launcher_background` 被复用为头像/视频占位，应有专用 placeholder |
| 空状态插图 | 消息/关注/同城/作品等空页面无空状态 UI |
| 加载中动画 | 各列表页无统一的 loading shimmer/skeleton |
| 错误重试按钮 | 加载失败时仅 Toast，无重试 UI |

---

## 8. 优先级排序的后续工作

### 第一阶段：修 Bug（P0，约 2-3 天）

1. **修复 B2**：修正 5 个模块的 AndroidManifest.xml 中 Activity 类路径
2. **修复 B1**：后台恢复 Bug——`VideoAdapter.restore()` 实现 Engine 重新绑定逻辑
3. **修复 B3**：`toUi()` 从服务端响应中正确映射 `isLike`/`isCollect`/`playUrl`/`commentCount`
4. **修复 B4**：`PlayerEnginePool.acquire()` 驱逐前检查是否仍有活跃引用
5. **修复 B5**：移除 `AfterSearchFragment` 中的重复点赞更新
6. **修复 B8**：评论数从 `VideoCardInfo.commentCount` 传入

### 第二阶段：补全核心功能（P1，约 1-2 周）

1. **关注 Feed**（FEED-10）：实现 HomeFocusFragment——调用关注用户的视频列表 API
2. **我的作品/喜欢/收藏**（WORKS-01/02/03）：实现 3 个子 Tab 的 RecyclerView 列表
3. **消息列表**（MSG-01）：实现 MessageFragment 消息列表 UI
4. **分享功能**（INTERACT-09）：实现分享弹窗（复制链接 + 第三方分享）
5. **查看他人主页**（PROFILE-05）：点击头像跳转用户主页

### 第三阶段：体验优化（P2，约 1 周）

1. 修复搜索分页（B6）和 baseUrl 恢复（B7）
2. 完善图片裁剪手势（拖动/缩放/旋转）
3. 清理死代码（VideoPayload、cursorId、空 Observer）
4. 统一 Intent Extra Key 为常量
5. 接入真实埋点替换 `PlayerAnalytics` 中的 `Log.d`
6. 提取共享资源到 `:lib-base`

### 第四阶段：增强功能（P3，约 2-3 周）

1. 视频发布流程
2. 推送通知
3. 修改密码
4. 敏感词过滤
5. 长按倍速/双击点赞动画
6. 第三方登录
7. 关注/取消关注用户
8. 内容举报

---

> **文档维护者**: Claude Code | **下次审计建议**: 第二阶段完成后重新审计
