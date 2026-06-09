# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 构建与开发命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 构建单个模块（示例）
./gradlew :feature-home:assembleDebug
./gradlew :core-player:assembleDebug

# 运行所有单元测试
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.example.blue_book.ExampleTest"

# 运行 Android 插桩测试
./gradlew connectedAndroidTest

# 清理构建产物
./gradlew clean

# 生成 Hilt/Dagger 组件（用于检查 DI 编译是否通过）
./gradlew kaptDebugKotlin
```

## Git 提交规范

- **提交信息必须使用中文**，简洁描述本次改动内容
- 示例：`添加评论模块ui代码，优化视频播放页布局`、`创建core-player模块，迁入播放器引擎`
- 格式：`<做了什么>，<还做了什么>`

## 架构概览

Android 应用，最低支持 API 31（Android 12），**Kotlin 1.9.24**、**AGP 8.5.2**、**Java 17**。采用 **Clean Architecture** + MVVM + 自定义 **UDF（单向数据流）**，已拆分为 **11 个模块**。所有 UI 文案、注释、API 返回信息均为中文。Git 用户：`overfloatGame`。

### 模块总览

```
:app                 ← 壳工程：Application + MainActivity + 主题 + 权限声明

── 业务功能层（底部4个Tab） ──
:feature-home        ← 首页：瀑布流发现页 + 搜索 + 热搜榜
:feature-video       ← 视频：全屏沉浸播放 + 评论
:feature-message     ← 消息：占位页
:feature-mine        ← 我的：个人中心 + 资料编辑

── 独立功能模块 ──
:feature-auth        ← 登录注册：AuthEntry + Login + Register
:feature-image       ← 图片选择：Gallery + Crop + ImagePickerActivity

── 核心能力层 ──
:core-network        ← OkHttp + AuthInterceptor + TokenAuthenticator + Retrofit API
:core-player         ← ExoPlayerEngine + MediaCache(200MB LRU) + PlayerEnginePool + GL 滤镜
:core-datastore      ← DataStore + TokenCache + Room 数据库 + AuthPreferences

── 基础层 ──
:lib-base            ← UDF 基类 + 公共 bean + 领域模型 + 仓库接口 + UseCase
```

### 模块依赖层次

```
:lib-base
    │
    ├──────────────┬──────────────┬──────────────┐
    │              │              │              │
:core-network  :core-player  :core-datastore     │
    │              │              │              │
    └──────┬───────┘              │              │
           │                      │              │
    ┌──────┴──────────────────────┴──────────────┘
    │      │         │         │         │
:feature-auth :feature-home :feature-video :feature-mine :feature-message :feature-image
    │      │         │         │         │              │
    └──────┴─────────┴─────────┴─────────┴──────────────┘
                           │
                          :app
```
    ┌──────────────┼──────────────┐
    │              │              │
:feature-auth  :feature-image  :feature-home  :feature-video  :feature-message  :feature-mine
    │              │              │              │                │                │
    └──────┴─────────┴─────────┴─────────┴──────────────┘
                           │
                          :app
```

### 权限归属

| 权限 | 所属模块 | 说明 |
|------|---------|------|
| `INTERNET` | `:app` | 壳工程统一声明 |
| `READ_MEDIA_IMAGES` | `:app` | 壳工程统一声明 |
| `READ_EXTERNAL_STORAGE` | `:app` | 兼容旧版本存储读取 |

### 资源文件归属

- **drawable**：各模块独立持有 layout 引用的 drawable，无跨模块引用
- **styles**：各模块独立管理所需样式
- **colors**：各 feature 模块持有实际使用的颜色值，`:app` 持有完整 Material 色板和主题
- **主题**：`AppTheme` 保留在 `:app`，各 feature 模块的 `AndroidManifest.xml` 仅声明 Activity

## UDF 模式（`:lib-base/core/udf/`）

每个页面都在 `*Contract.kt` 文件中定义严格的三件套：

- **UiIntent**（sealed interface）—— 用户操作，由 Fragment 分发给 ViewModel
- **UiState**（data class）—— 页面唯一数据源
- **UiEffect**（sealed interface）—— 一次性副作用（Toast、导航跳转）

ViewModel 继承 `UdfViewModel<I, S, E>`，提供以下能力：
- `dispatch(intent)` — 通过 `MutableSharedFlow`（缓冲区 64）入队一个 intent，在 `viewModelScope` 中收集
- `handleIntent(intent)` — 抽象方法，子类用 `when` 分支处理每种 intent
- `setState { copy(...) }` — 通过 `MutableStateFlow.update` 原子更新状态
- `sendEffect(effect)` — 通过 `MutableSharedFlow`（缓冲区 16）发射一次性副作用
- `runResult<T> { call, onSuccess, onFailure }` — 结构化异步调用包装器，依次执行 `onStart` → `call()` → `onSuccess` / `onFailure` → `onFinally`，依赖 Kotlin `Result<T>`

### 乐观更新模式

点赞/收藏的切换采用"先改 UI，失败回滚"策略（`HomeFindViewModel` 和 `VideoViewModel` 均使用）：
1. 立即更新 `StateFlow` 中的状态（计数 ±1，`isLike`/`isCollect` 取反）
2. 调用 API
3. 失败 → 回滚状态到原始值，弹出 Toast 提示

### 分页模式

所有列表接口使用**基于游标的分页**，参数为 `cursorId` + `size`。ViewModel 在 state 中维护 `cursorId` 和 `hasMore`。`LoadMore` intent 的处理由 `isLoading` 标志位防重。

## 网络层（`:core-network`）

- **Base URL**：`http://10.0.2.2:8085/`（Android 模拟器本地地址），定义在 `CoreNetworkModule`
- **响应信封**：
  - `ApiResponse<T>(code: Int, message: String, data: T?)` —— `code == 0` 表示成功
  - `CommonResultDto<T>(code: Int, msg: String, data: T?)` —— `code == 200` 表示成功（部分接口使用）
- **API 调用包装器**（`:core-network/.../RemoteCall.kt`）：三个 `suspend inline` 函数封装 Retrofit 调用：
  - `apiCall<T>()` → 检查 HTTP 成功 → 检查 `ApiResponse.code == 0` → 返回 `Result<T>`
  - `apiUnitCall()` → 同上，返回 `Result<Unit>`（不关心 data 体）
  - `commonCall<T>()` → 检查 HTTP 成功 → 检查 `CommonResultDto.code == 200` → 返回 `Result<T>`
- **两个 OkHttpClient 实例**（`:core-network/CoreNetworkModule`）：
  - 默认：包含 `AuthInterceptor` + `TokenAuthenticator`，超时 10s，开启 `retryOnConnectionFailure(true)`，日志级别 `BODY`
  - `@Named("refresh")`：供 `TokenAuthenticator` 刷新 token 专用 —— **不带** auth 拦截器，防无限循环
- **Retrofit 与 API 接口**：Gson + Retrofit + 6 个 `*Api` 接口，均在 `CoreNetworkModule` 中提供

### 鉴权流程

**AuthInterceptor**（`:core-network/core/network/AuthInterceptor.kt`）：
- 为每个请求添加 `Authorization: Bearer <token>` 头
- 跳过 `/api/v2/auth/refresh`（否则 refresh 接口自身 401 会造成死循环）
- token 为空时跳过
- token 仍从 `TokenCache`（`@Volatile` 缓存）同步读取，因为 OkHttp Interceptor 运行在 I/O 线程池，不能调 DataStore 的 suspend 函数

**TokenAuthenticator**（`:core-network/core/network/TokenAuthenticator.kt`）：
- OkHttp `Authenticator` —— 收到 `401` 时触发
- 使用 `synchronized(lock)` 序列化并发刷新请求
- 双重检查：如果 token 已被其他线程刷新过，直接用新 token 重试原请求，不重复刷新
- `responseCount` 守卫：最多允许 1 次刷新重试（防止无限 401 循环）
- 刷新失败或缺少 refreshToken → 清除全部登录态

## 依赖注入（Hilt 2.48.1）

所有 Module 均为 `@InstallIn(SingletonComponent::class)`，分散在各模块中：

| 模块 | Hilt Module | 提供内容 |
|------|-----------|---------|
| `:core-network` | `CoreNetworkModule` | OkHttpClient（2 个）、HttpLoggingInterceptor、BASE_URL、Gson、Retrofit、6 个 API 接口 |
| `:core-datastore` | `DataStoreModule`、`DatabaseModule`、`LocalModule` | `SessionDataStore`、Room `AppDatabase`、`UserDao`、`AuthPreferences` 绑定 |
| `:feature-mine` | `UserRepositoryModule` | `@Binds UserRepositoryImpl → UserRepository` |
| `:feature-video` | `VideoRepositoryModule` | `@Binds VideoRepositoryImpl → VideoRepository`、`CommentRepositoryImpl → CommentRepository` |

各 feature 模块的 ViewModel 通过 `@HiltViewModel` + `@Inject constructor` 自动注册，无需额外 Module。

## 导航

多 Activity 架构，**已移除 Jetpack Navigation**，改用 Intent 字符串跳转。

```
MainActivity（底部 4 个 Tab，RadioGroup）
  ├─ tab_home    → Intent → HomeActivity  (首页：瀑布流 + 搜索)
  ├─ tab_video   → Intent → VideoActivity (视频 Tab + 全屏播放器)
  ├─ tab_message → Intent → MessageActivity (消息占位)
  └─ tab_mine    → Intent → MineActivity  (个人中心 + 资料编辑)

AuthActivity（登录/注册入口）
  ├─ AuthEntryFragment → 登录/注册 → Intent → MainActivity
  ├─ LoginFragment → HomeActivity.navigateToHome()
  └─ RegisterFragment → MineActivity.navigateToHome()

HomeActivity 内部 Fragment 跳转（FragmentManager）：
  HomeFragment → SearchFragment → AfterSearchFragment → VideoActivity（全屏播放）

MineActivity 内部 Fragment 跳转：
  MineFragment → UserProfileEditFragment
```

**跨模块跳转**：使用 `Intent.setClassName(packageName, "完整类名")`，模块间零编译依赖。  
**模块内跳转**：Fragment 通过 `(requireActivity() as XxxActivity).navigateToXxx()` 调用宿主 Activity 的公共方法。  
**底部 Tab**：`singleTask` 启动模式 + `FLAG_ACTIVITY_REORDER_TO_FRONT`，每个 Tab 只有一个 Activity 实例。

## 数据库（`:core-datastore`）

Room（`AppDatabase`，版本 1），目前只有一张表 `UserEntity`（表名 `user`，主键 phone）。单例通过伴生对象中的双重检查锁定实现。`UserDao` 提供 CRUD + 头像/背景图更新方法。

## 播放器（`:core-player`）

基于 Media3 ExoPlayer（1.4.1）封装的 `PlayerEngine` 抽象层。

### ExoPlayerEngine
- 封装 `ExoPlayer`，实现 `PlayerEngine` 接口 + `PlayerEvents` 事件桥接
- **缓存策略**：200MB LRU 缓存，使用 `SimpleCache` + `LeastRecentlyUsedCacheEvictor`，索引通过 `StandaloneDatabaseProvider` 持久化到 SQLite，冷启动后缓存仍有效。`CacheDataSource` 包裹 `OkHttpDataSource`，设置 `FLAG_IGNORE_CACHE_ON_ERROR` —— 缓存数据损坏时自动回源重新下载
- **LoadControl**：缓冲区参数 `1500 / 12000 / 250 / 500` ms（最小缓冲 / 最大缓冲 / 重新缓冲最小 / 重新缓冲最大）
- **超时重试机制**（仅起播阶段）：首次超时 5s，每次退避增量 1.5s，最多重试 2 次。超时后：`stop()` → 重新 `prepare()` → `seekTo(上次位置)`。重试耗尽 → 上报 `"起播超时"` 错误。由 `isInitialBuffering` 标志位保护，播放中途的卡顿不会触发此重试逻辑
- **音频焦点**：完全委托给 ExoPlayer，通过 `setAudioAttributes(CONTENT_TYPE_MOVIE, USAGE_MEDIA, handleAudioFocus=true)` 自动处理，无自定义 `AudioManager` 逻辑
- **媒体下载 OkHttpClient**：独立实例，超时 20s（在 `PlayerFactories` 中配置），日志级别 `BASIC`

### PlayerEnginePool（播放池）
对象池模式，复用和预加载播放器实例：
- `acquire(key)` → 先查活跃表 → 再从空闲队列（FIFO）取 → 都没有则 `factory()` 新建
- `preload(key, url)` → `acquire` + 以 `playWhenReady=false` 调 `prepare()`，让视频提前缓冲好，用户切过来直接 `play()` 秒开
- `release(key)` → 空闲队列未满则放回复用，否则彻底 `release()` 释放
- `releaseAll()` → 释放所有实例（页面销毁时调用）

### GL 渲染（`:core-player/gl/`）
- `GlVideoSurfaceView` —— 基于 GLSurfaceView，通过 OES 纹理渲染视频帧，支持 Fragment Shader 滤镜
- `FilterType` —— 枚举：NONE（无滤镜）、GRAY（黑白）、WARM（暖色）
- `GlSurfaceProvider` —— 将 GL Surface 桥接到 `VideoSurfaceProvider` 接口

## 关键依赖

| 用途 | 库 |
|------|-----|
| 依赖注入 | Hilt 2.48.1 |
| 网络请求 | Retrofit 2.9.0 + OkHttp 4.11.0 + Gson |
| 数据库 | Room 2.6.1 |
| 媒体播放 | AndroidX Media3 1.4.1 |
| 图片加载 | Glide 4.13.2 + uCrop 2.2.6 |
| UI | Material 1.12.0 + Compose BOM 2024.06.00 |
| 导航 | Navigation 2.7.7 |
| 序列化 | Moshi 1.13.0 |
| 后端服务 | Firebase Firestore 25.0.0 |

### UI 技术栈

大部分页面使用 **View 体系**（XML + ViewBinding）。Compose 可用（Compose BOM 2024.06.00、Material3），但当前仅用于主题定义（`:app/ui/theme/`）。Fragment 使用 `Fragment(R.layout.xxx)` 或手动 ViewBinding 方式加载布局。

## 多模块开发注意事项

1. **R 类路径**：各模块的命名空间为 `com.example.blue_book.<module>`，R 类为 `com.example.blue_book.<module>.R`。迁移文件时必须同时更新源代码中的 `import ...R` 和 `import ...databinding.*`
2. **ViewBinding**：layout 文件迁入模块后，DataBinding 生成的类在模块自己的包下，需要同步更新 import
3. **跨模块资源**：共享 drawable 当前采用复制策略（各模块各持一份）。后续计划提取到 `:lib-base`
4. **kapt 缓存**：新增模块或大改依赖后，如遇 kapt `NonExistentClass` 错误，执行 `./gradlew clean assembleDebug`
5. **导航 ID 占位**：各 feature 模块的 `res/values/ids.xml` 中定义了 nav_graph 中使用的 action/fragment ID，用于编译期通过。这些 ID 在 ARouter 迁移后应删除
