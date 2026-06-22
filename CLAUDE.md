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
:core-network        ← ApiGateway 门面 + TokenHolder + 拦截器 + Retrofit API
:core-player         ← ExoPlayerEngine + MediaCache(200MB LRU) + PlayerEnginePool + GL 滤镜
:core-datastore      ← IDataStore/AppDataStore（DataStore 封装） + Room 数据库

── 基础层 ──
:lib-base            ← UDF 基类 + 公共 bean + Provider 接口 + 路由常量 + AppContext
```

### 模块依赖层次

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

- `core-network` → `core-datastore`（TokenHolder 注入 IDataStore）
- feature 模块互不依赖，通过 TheRouter `@ServiceProvider` 服务发现
- `feature-home` / `feature-message` / `feature-image` 仅依赖 `lib-base`

### 跨模块服务发现（TheRouter）

| 接口（lib-base） | 实现模块 | 消费模块 |
|---|---|---|
| `IAuthProvider` | feature-auth | feature-mine |
| `IVideoProvider` | feature-video | feature-home |
| `IUserStore` | core-datastore | feature-auth, feature-mine |

模式：`TheRouter.get(IUserStore::class.java)!!.saveUser(account)` + Hilt `@EntryPoint` 桥接。

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

## UDF 模式（`:lib-base/udf/`）

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

- **门面**：`ApiGateway`（`@Singleton`）是对外的唯一消费入口，内部创建 OkHttpClient / Retrofit
- **Token 管理**：`TokenHolder`（`@Singleton`）—— `@Volatile` 字段同步读写 + 自动异步持久化到 `IDataStore`
- **Base URL**：`ApiGateway.BASE_URL`，通过 BuildConfig 注入（`build.gradle.kts` 中 `buildConfigField`）
- **响应信封**：
  - `ApiResponse<T>(code: Int, message: String, data: T?)` —— `code == 0` 表示成功
  - `CommonResult<T>(code: Int?, msg: String?, data: T?)` —— `code == 200` 表示成功
- **API 调用**：`ApiGateway.apiResult() / commonResult() / apiUnitResult()` 三种 Result 风格 + `request() / commonRequest()` 回调风格
- **拦截器链**（`interceptor/` 子包）：`CommonParamsInterceptor` → `TokenInterceptor` → `TokenAuthenticator` → `HttpLoggingInterceptor`
- **TokenAuthenticator**：内建独立的 `refreshClient`，不带 auth 拦截器防无限循环

### 鉴权流程

**TokenInterceptor**（`:core-network/interceptor/TokenInterceptor.kt`）：
- 从 `TokenHolder.authToken`（`@Volatile`）同步读取 token，附加 `Authorization: Bearer` 头
- 跳过 `/api/v2/auth/refresh`

**TokenAuthenticator**（`:core-network/interceptor/TokenAuthenticator.kt`）：
- OkHttp `Authenticator` —— 收到 `401` 时触发
- `synchronized(lock)` 序列化 + 双重检查 + `responseCount` 守卫（最多 1 次刷新重试）
- 刷新成功 → `tokenHolder.saveAuthToken() / saveRefreshToken()`；失败 → `tokenHolder.clear()`

## 数据存储层（`:core-datastore`）

- **IDataStore / AppDataStore**：通用 key-value 封装（putString/getString/putInt/getInt/putBoolean/putLong/remove/clear），底层用 DataStore Preferences
- **Room**：`AppDatabase`（版本 1）→ `UserDao` → `UserEntity`（表名 `user`，主键 phone）
- **IUserStore**：lib-base 定义的存储接口，由 `UserStoreProviderImpl` 实现，通过 TheRouter `@ServiceProvider` 暴露
- **Hilt DI**：`DatabaseModule` 提供 IDataStore、AppDatabase、UserDao

## 依赖注入（Hilt 2.48.1）

各模块通过 Hilt `@InstallIn(SingletonComponent::class)` 提供 DI：

| 模块 | Hilt Module | 提供内容 |
|------|-----------|---------|
| `:core-datastore` | `DatabaseModule` | IDataStore、AppDatabase、UserDao |
| `:core-network` | 无（@Inject constructor 自动装配） | ApiGateway、TokenHolder、TokenInterceptor、TokenAuthenticator |
| `:feature-auth` | `AuthRepositoryModule` | `@Binds AuthRepositoryImpl → AuthRepository` |
| `:feature-mine` | `RepositoryModule` | `@Binds UserRepositoryImpl → UserRepository` |
| `:feature-video` | `RepositoryModule` | `@Binds VideoRepositoryImpl → VideoRepository`、`CommentRepositoryImpl → CommentRepository` |

各 feature 模块的 ViewModel 通过 `@HiltViewModel` + `@Inject constructor` 自动注册，无需额外 Module。

跨模块服务（IAuthProvider、IUserStore、IVideoProvider）通过 TheRouter `@ServiceProvider` + Hilt `@EntryPoint` 暴露，不经过 Hilt DI。

## 导航（TheRouter 1.3.0）

多 Activity 架构，使用 TheRouter 路径导航替代 Intent 字符串。

```
MainActivity（底部 4 个 Tab，RadioGroup）
  /app/main
  ├─ tab_home    → /home/main   (首页：瀑布流 + 搜索)
  ├─ tab_video   → /video/main  (视频 Tab + 全屏播放器)
  ├─ tab_message → /message/main (消息占位)
  └─ tab_mine    → /mine/main   (个人中心 + 资料编辑)

AuthActivity（登录/注册入口）
  /auth/entry
  ├─ AuthEntryFragment → 登录/注册 → /app/main
  ├─ LoginFragment → /app/main
  └─ RegisterFragment → /app/main

图片选择器：/image/picker
```

**跨模块跳转**：`TheRouter.build(RoutePath.XXX).navigation(context)`
**模块内跳转**：Fragment 通过 `(requireActivity() as XxxActivity).navigateToXxx()` 调用宿主 Activity 的公共方法。
**底部 Tab**：`FLAG_ACTIVITY_REORDER_TO_FRONT`，每个 Tab 只有一个 Activity 实例。
**路由常量**：`lib-base/router/RoutePath.kt` 集中管理。

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
| 路由 | TheRouter 1.3.0 |
| 媒体播放 | AndroidX Media3 1.4.1 |
| 图片加载 | Glide 4.13.2 + uCrop 2.2.6 |
| UI | Material 1.12.0 + Compose BOM 2024.06.00 |

### UI 技术栈

大部分页面使用 **View 体系**（XML + ViewBinding）。Compose 可用（Compose BOM 2024.06.00、Material3），但当前仅用于主题定义（`:app/ui/theme/`）。Fragment 使用 `Fragment(R.layout.xxx)` 或手动 ViewBinding 方式加载布局。

## 多模块开发注意事项

1. **R 类路径**：各模块的命名空间为 `com.example.blue_book.<module>`，R 类为 `com.example.blue_book.<module>.R`。迁移文件时必须同时更新源代码中的 `import ...R` 和 `import ...databinding.*`
2. **ViewBinding**：layout 文件迁入模块后，DataBinding 生成的类在模块自己的包下，需要同步更新 import
3. **跨模块资源**：共享 drawable 当前采用复制策略（各模块各持一份）。后续计划提取到 `:lib-base`
4. **kapt 缓存**：新增模块或大改依赖后，如遇 kapt `NonExistentClass` 错误，执行 `./gradlew clean assembleDebug`
5. **跨模块依赖**：feature 模块间通过 TheRouter `@ServiceProvider` 服务发现，禁止直接 `implementation(project(":feature-*"))`
