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

# ★ 改动资源（styles/colors/drawable/values）后必须跑这个
#   debug 变体**不做**库资源自洽校验：:lib-base:verifyReleaseResources 等任务只在 release 跑。
#   只跑 assembleDebug 会漏掉"库模块里引用了它没声明的库的资源"这类错误——
#   实际踩过：把 parent="Widget.MaterialComponents.Button" 的样式搬进 lib-base，
#   而 lib-base 没声明 Material，debug 一路绿灯、release 才炸。
./gradlew assembleRelease
```

## Git 提交规范

- **提交信息必须使用中文**，简洁描述本次改动内容
- 示例：`添加评论模块ui代码，优化视频播放页布局`、`创建core-player模块，迁入播放器引擎`
- 格式：`<做了什么>，<还做了什么>`

## 架构概览

Android 应用，最低支持 API 31（Android 12），**Kotlin 1.9.24**、**AGP 8.5.2**、**Java 17**。采用 **Clean Architecture** + MVVM + 自定义 **UDF（单向数据流）**，已拆分为 **12 个 Android 模块**（`settings.gradle.kts` 里另有一个独立的 `:backend` JVM 子项目）。所有 UI 文案、注释、API 返回信息均为中文。Git 用户：`overfloatGame`。

### 模块总览

```
:app                 ← 壳工程：Application + MainActivity + 主题 + 权限声明

── 业务功能层（底部4个Tab） ──
:feature-home        ← 首页：瀑布流发现页 + 搜索 + 热搜榜
:feature-video       ← 视频：全屏沉浸播放 + 评论 + 发布
:feature-message     ← 消息：通知列表
:feature-mine        ← 我的：个人中心 + 资料编辑

── 独立功能模块（从 Tab 或其它页面经路由进入）──
:feature-auth        ← 登录注册：AuthEntry + Login + Register
:feature-image       ← 图片选择：Gallery + Crop + ImagePickerActivity
:feature-scan        ← 扫一扫：相机识别 + 相册识别 + 站内码格式契约

── 核心能力层 ──
:core-network        ← ApiGateway 门面 + TokenHolder + 拦截器 + Retrofit API
:core-player         ← ExoPlayerEngine + MediaCache(200MB LRU) + PlayerEnginePool + GL 滤镜
:core-datastore      ← IDataStore/AppDataStore（DataStore 封装） + Room 数据库

── 基础层 ──
:lib-base            ← UDF 基类 + 公共 bean + Provider 接口 + 路由常量 + 跨模块字符串契约
                        （`RoutePath` / `ExtraKeys` / `scan/ScanCodeFormat`）
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
- feature 模块互不依赖：跨模块能力靠**接口下沉到 lib-base + Hilt 绑定**（见下节），
  编译期不需要 `implementation(project(":feature-*"))`
- 各 feature 的实际依赖（构建文件为准）：
  `feature-image` 仅 `lib-base`；`feature-message` 为 `lib-base` + `core-network`；
  `feature-home` 为 `lib-base` + `core-datastore` + `core-network`
- 宿主能力（`IMainHost`：全屏进出、是否有底部导航）定义在 `lib-base`，feature 通过它回调宿主，
  不依赖 `:app`；页面跳转不用宿主，一律走路由

### 跨模块服务：接口在 lib-base，实现在能力所属模块，用 Hilt 注入

| 接口（lib-base） | 实现模块 | Hilt 绑定位置 | 消费方 |
|---|---|---|---|
| `IAuthProvider` | feature-auth | `AuthRepositoryModule` 的 companion `@Provides` | 6 个 Fragment、MainActivity、`LogoutUseCase` |
| `IVideoProvider` | feature-video | `VideoRepositoryModule` 的 companion `@Provides` | 8 个 ViewModel、MessageFragment |
| `IUserStore` | core-datastore | `StoreModule` 的 `@Binds` | `AuthRepositoryImpl`、`UserRepositoryImpl` |
| `INotificationProvider` | feature-message | `MessageModule` 的 `@Provides` | MainActivity（未读角标） |

**消费方一律构造注入（ViewModel / UseCase）或字段注入（Fragment / Activity）**，
不写 `TheRouter.get(X::class.java)`：依赖在构造函数/字段上就能看见，
而且提供方模块没被打进 APK 时是**编译期**报错，不是运行期 NPE。

```kotlin
// ViewModel：构造注入
class HomeFindViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    private val videoProvider: IVideoProvider
) : UdfViewModel<...>(...)

// Fragment / Activity：字段注入（这些类由系统实例化，只能字段注入）
@AndroidEntryPoint
class MessageFragment : Fragment() {
    @Inject lateinit var authProvider: IAuthProvider
}
```

TheRouter 只用于**页面路由**（`@Route` + routeMap.json），不承担依赖注入；
不要再用服务定位器绕开依赖声明。

### 权限归属

**权限随使用它的模块走**：模块自洽（单独构建时声明齐全），移除模块时权限随之消失。
清单以各模块自己的 `AndroidManifest.xml` 为准（2026-09-20 更正：原文写的是"三权全归 :app"，
与实现不符——实际只有 `INTERNET` 在 :app）。

| 权限 | 所属模块 | 说明 |
|------|---------|------|
| `INTERNET` | `:app` | 壳工程声明；全部网络请求都经它 |
| `ACCESS_NETWORK_STATE` | `:core-network` | 断网等待网络恢复用 |
| `ACCESS_COARSE_LOCATION` | `:feature-home`、`:feature-video` | 本地流按城市过滤 |
| `READ_MEDIA_IMAGES`、`READ_MEDIA_VISUAL_USER_SELECTED`、`READ_EXTERNAL_STORAGE` | `:feature-image` | 选图/裁剪；`READ_EXTERNAL_STORAGE` 兼容旧版本 |

**代价（如实记下）**：审计全部权限需要翻所有模块的清单，没有单一入口。一条命令可汇总
（必须按 `android:name` 抓而不是按行抓——`READ_EXTERNAL_STORAGE` 是跨行声明的，按行抓会漏）：

```bash
grep -rho 'android:name="android\.permission\.[A-Z_]*"' --include=AndroidManifest.xml . \
  | grep -v build | sed 's/.*permission\.//;s/"//' | sort -u
```

### 资源文件归属

**跨模块共享的资源统一放 `lib-base`**，各模块只持有自己独有的。这不是偏好而是库模块的硬约束：
库模块的资源必须自洽（若只依赖 `:app` 的色板，aapt 会报找不到资源），而所有模块都依赖
`lib-base`，所以它是**唯一**能放共享资源的地方。

| 资源 | 共享部分（`lib-base`） | 各模块自持 |
|---|---|---|
| colors | `values/colors.xml` + `values-night/colors.xml`：整套色板（`md_theme_*` 47 个基础色）+ 跨模块语义色（`brand_blue` / `brand_on_blue` / `text_on_dark_*` / `page_dark_background`） | 只放本模块独有的装饰色（feature-auth 的输入框色、feature-video 的播放页深色常量、feature-mine 的封面与资料页色、feature-message 的消息图标圆底） |
| styles | `values/style.xml`：三个共享样式 `RadioGroupButtonStyle` / `RadioGroupButtonStyle1` / `NoMaterialButtonStyle` | 本模块独有样式（feature-home 的 `SearchInputStyle`/`SearchActionStyle`、feature-mine 的 `ShapeAppearance.Profile.Thumb`、feature-video 的 `CircleImageStyle`） |
| drawable | 共享图形：返回箭头 `_chevron_left` / `_chevron_left_on_dark`、`_chevron_left1`、`default_avatar`、`navigation_item_selector`、`ic_launcher_background` | 本模块独有的图形 |

- **主题**：`AppTheme` 保留在 `:app`；各 feature 模块的 `AndroidManifest.xml` 仅声明 Activity
- **改色只改一处**：色板已从"每个模块各存一份、由资源合并优先级决定谁生效"收敛为单一定义。
  历史上 `feature-message` 的 `md_theme_onSurfaceVariant` 就曾与 app 差一个色阶、**静默失效**无人察觉
- `feature-home` / `feature-scan` / `feature-image` 已经**没有** `colors.xml`（全部用共享色板）
- `:app` 的 `colors.xml` 现在只剩两套**无人引用**的 `_mediumContrast` / `_highContrast`
  （M3 模板残留，文件内有注释说明），要清理可连同 `theme_overlays.xml` 一起删

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
- **Room**：`AppDatabase`（**版本 3**）→ 三张表：
  - `UserEntity`（表名 `user`，主键 phone）—— 登录态，`UserDao`
  - `UploadSessionEntity`（`upload_session`，主键 uri）+ `UploadPartEntity`（`upload_part`，复合主键 uri+part_index）
    —— 本地上传会话与分片账本，`UploadSessionDao`；用于**跨进程续传**与**指纹缓存**
    （服务端也能回答"传到哪了"，但这张表让"进页面立刻看到进度"不必等一次网络往返）
  - 加表要同步写 `MIGRATION_*` 并跑 `:core-datastore:connectedDebugAndroidTest`
    （`UploadMigrationTest` 会造一个旧版本库、让 Room 自己跑迁移并校验 schema——
    手写 SQL 与实体对不上的话，用户是"一升级就崩"，纯 JVM 测不出来）
- **IUserStore**：lib-base 定义的存储接口，由 `UserStoreProviderImpl` 实现，绑定见 `StoreModule`（`@Binds`）
- **Hilt DI**：`DatabaseModule` 提供 IDataStore、AppDatabase、UserDao

## 依赖注入（Hilt 2.48.1）

各模块通过 Hilt `@InstallIn(SingletonComponent::class)` 提供 DI：

| 模块 | Hilt Module | 提供内容 |
|------|-----------|---------|
| `:core-datastore` | `DatabaseModule` / `StoreModule` | IDataStore、AppDatabase、UserDao、UploadSessionDao；`@Binds` IUserStore、IUploadSessionStore |
| `:core-network` | 无（@Inject constructor 自动装配） | ApiGateway、TokenHolder、TokenInterceptor、TokenAuthenticator |
| `:feature-auth` | `AuthRepositoryModule` | `@Binds AuthRepositoryImpl → AuthRepository` |
| `:feature-mine` | `RepositoryModule` | `@Binds UserRepositoryImpl → UserRepository` |
| `:feature-video` | `RepositoryModule` | `@Binds VideoRepositoryImpl → VideoRepository`、`CommentRepositoryImpl → CommentRepository` |

各 feature 模块的 ViewModel 通过 `@HiltViewModel` + `@Inject constructor` 自动注册，无需额外 Module。

跨模块服务（IAuthProvider、IUserStore、IVideoProvider、INotificationProvider）**也是普通 Hilt 绑定**，
绑定位置见「跨模块服务」一节；消费方直接构造注入/字段注入，不走服务定位器。

## 导航与 Tab 结构

**页面分两类**（2026-09 二次重构）：一级页面（四个 Tab）在 `MainActivity` 内，
二级页面（其余全部）都是压在它之上的独立 Activity。规则一句话：
**Tab 图里没有任何二级页；二级页返回即 finish，回到点击它的那个页面**。

```
MainActivity（/app/main）—— 只承载四个一级页面，底部导航常驻
├─ 内容容器 main_content（FrameLayout）+ 底部导航 RadioGroup（常驻，切 Tab 不消失）
├─ tab_home    → HomeFragment     (首页：关注/发现/本地 三个子 Tab + 搜索)
├─ tab_video   → VideoFragment    (视频：竖向翻页播放器 + 全屏)
├─ tab_message → MessageFragment  (消息：通知列表)
└─ tab_mine    → MineFragment     (我的：个人中心 + 作品/收藏/喜欢)

独立 Activity（全部压在 MainActivity 之上，返回即回到来源）：
  /video/player      VideoActivity         播放页（容纳同一个 VideoFragment）
  /home/search       SearchActivity        搜索页 + 搜索结果页（页面内 Fragment 前后栈）
  /mine/profile_edit ProfileEditActivity   资料页 + 单字段编辑页（页面内 Fragment 前后栈）
  /auth/entry        AuthActivity          登录注册（AuthEntry/Login/Register 三个 Fragment）
  /video/publish     PublishActivity       发布页（底部导航正中的入口）
  /image/picker      ImagePickerActivity   图片选择
  /mine/user_profile AuthorProfileActivity 作者主页
  /mine/follow_list  FollowListActivity    关注/粉丝列表
```

### 两个播放入口（容易搞混，改动前请先读这段）

- **视频 Tab**（`tab_video`）是沉浸式播放 Feed，属于 Tab 图：从底部导航进入，返回回首页 Tab。
- **播放页**（`VideoActivity`）是从**其它页面**点某个视频时压在上层的那一个：
  首页三个子流、消息、我的三个列表、搜索结果、作者主页都走
  `lib-base/router/VideoPlayerRoute.kt` 的 `openVideoPlayer(...)`（五个 extra 统一在一处拼，
  调用点不要各自拼）。返回回到点击它的那个页面。
- 之所以要分成两个入口：视频 Tab 只有一份，切过去会把选中项改掉，
  来源页面（如搜索结果）的上下文就丢了，返回回不到原处。
- 同一个 `VideoFragment` 被两个宿主承载，行为一致（它本来就是按 arguments 驱动的）。
  两个宿主各持有独立的 `PlayerEnginePool`（是 `VideoAdapter` 的实例字段、不是单例），互不干扰。

### Tab 切换机制

- **四个 Tab 在 `MainActivity.onCreate` 一次性 `add` 进 `main_content`，用 `show/hide` 切换**，
  不用 `replace`：保留各自的视图状态（滚动位置、播放进度）。
- **非当前 Tab 用 `setMaxLifecycle(STARTED)` 压住**：这会触发 `onPause`，
  于是视频 Tab 切走时自动暂停（`VideoFragment.foreground` 依赖这个时机），
  且视图不销毁、切回来能接着播。
- **返回键**（`MainActivity` 的 `OnBackPressedCallback`）：非首页 Tab→回首页；否则退出。
  它**故意用不带 LifecycleOwner 的 `addCallback(callback)` 重载**：立即入队才能排在 Fragment
  回调（到达 STARTED 时才入队）之后，而返回键是后入队者优先——用带 owner 的重载会抢走
  全屏时的返回键，表现为「切到首页 Tab 却仍横屏、底部导航不可见」。
- **`MainActivity` 声明了 `configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"`**：
  全屏播放要改 `requestedOrientation`，不声明会重建整个 Tab 图；`VideoActivity` 同样声明。
- **全面屏**：`MainActivity` 调 `setDecorFitsSystemWindows(false)`，底部导航按 `bars.bottom`
  抬高；各页面自己避让状态栏与系统手势条。独立 Activity 也全屏延展，页面自己加底部内边距
  （加在页面根布局的 padding 上，底色仍延展到屏幕边缘，否则状态栏/手势条处会出现色带）。

### 跨模块导航：只有全屏能力还走 `IMainHost`

feature 模块不能依赖 `:app`（反向依赖），所以宿主能力由 `lib-base/host/IMainHost.kt` 定义。
它现在只剩「只有承载页面的 Activity 才做得到的事」：

```kotlin
mainHost?.enterFullscreen()
mainHost?.exitFullscreen()
mainHost?.providesBottomNav   // 宿主是否常驻底部导航，决定页面要不要自己避让底部系统栏
```

**页面跳转一律走 TheRouter 路由**（`TheRouter.build(RoutePath.XXX).navigation(context)`），
不要再往宿主上加 `navigateToXxx`：那会让 `mainHost?.xxx()` 在宿主没实现时静默失效。
`IMainHost` 的方法**默认抛 `UnsupportedOperationException`**，实现方只声明自己具备的能力。
当前 `MainActivity` 与 `VideoActivity` 实现它。

**路由常量**：`lib-base/router/RoutePath.kt`（注意：Tab 的四条路径已不再是路由，路由表见
`app/src/main/assets/therouter/routeMap.json`——**该文件是手工维护的输入，不是构建产物**，
新增/删除路由时需手工同步）。

> 历史说明：上一轮重构把四个 Tab 从「各自一个 Activity」收敛为 `MainActivity` 内的 Fragment
> （HomeActivity / VideoActivity / MessageActivity / MineActivity 已删除），当时二级页是叠在
> Tab 之上的 Fragment、返回键要先弹它们。本轮把二级页全部提成独立 Activity，
> 返回语义变成任务栈天然的「从哪来回哪去」，`MainActivity` 里不再有返回栈。

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
3. **跨模块资源**：共享资源已统一到 `:lib-base`（见「资源文件归属」一节的表），
   **不要再复制一份到各模块**。历史上色板复制过 6 份、样式 3 份、`default_avatar` 3 份
   （其中一份还是完全不同的图），每次都是"改了一处、另一处静默失效"。新增共享资源请加到 `:lib-base`。
4. **kapt 缓存**：新增模块或大改依赖后，如遇 kapt `NonExistentClass` 错误，执行 `./gradlew clean assembleDebug`
5. **跨模块依赖**：feature 模块间靠「接口下沉 lib-base + Hilt 绑定 + 构造/字段注入」，
   禁止直接 `implementation(project(":feature-*"))`，也不要用服务定位器（`TheRouter.get`）绕开依赖声明
6. **单元测试**：`lib-base` / `feature-mine` / `feature-scan` 已配 `testImplementation`
   （junit + coroutines-test）。跑单个模块 `./gradlew :feature-mine:testDebugUnitTest`，全跑 `./gradlew test`。
   约定：**方法名英文、断言消息中文**——JUnit 4 没有 `@DisplayName`，失败时被人读到的是断言消息。
   测 ViewModel 需要 `MainDispatcherRule`：UDF 的 init 与 dispatch 都跑在 `viewModelScope` 上，
   纯 JVM 里没有 `Dispatchers.Main` 会直接抛异常。Fake 的写法可参考
   `feature-mine/src/test/.../FakeVideoProvider.kt`（未用到的接口方法**故意抛异常**，
   这样走错数据来源会立刻炸，而不是静默拿到空数据）。
   **可测的前提是分层干净**：ViewModel 的构造签名里不要出现下层/平台类型（见第 7 条）。
7. **分层检查脚本**：`python tools/check_viewmodel_layer.py` 扫所有 `@HiltViewModel` 的构造签名，
   禁止 `*RemoteDataSource` / `*Api` / `*Dao` / `ApiGateway` / `Context` / `TokenHolder` /
   `OkHttp*` / `ExoPlayer*` 这类依赖——它们要么让 ViewModel **无法在纯 JVM 上构造**，
   要么说明**分层被穿透**（ViewModel 越过 Repository 直接够到了数据层/平台）。
   已知欠债**当前为 0 条**：出现**新违规**退出码为 1，
   欠债被消掉却还留在清单里也会报错，避免清单腐烂。**新增 ViewModel 时先跑它。**
   已被它拦下并修掉的三例：
   - `VideoViewModel` 曾直接注入 `VideoRemoteDataSource` 取转码状态，根因是 `VideoRepository`
     缺 `transcodeStatus` 方法；补上接口方法后即回可测范围
   - `PublishViewModel` 曾依赖 `@ApplicationContext Context`（给定位与构造内容源用），
     代价是**发布页的编排完全测不了**——而它承载的恰好是"取消要真的停掉""publish 只调一次"
     这类有真实后果的规则。把两处平台能力收进 `LocationProvider` / `UploadSourceFactory`
     之后，构造签名里再没有任何平台类型（连 `Uri` 都换成了字符串）
   - `MessageViewModel` 曾直接注入 `MessageRemoteDataSource`（`feature-message` 没有
     domain/repository 层）→ 补了 `MessageRepository` 与 DTO→domain 映射，
     顺带修掉一条潜伏 bug：通知头像此前没做绝对化，Glide 一直加载不出来
   注意脚本会**剥掉注释**再匹配：构造参数的注释里常提到 Context/DataStore 这些词，
   不剥就会把解释性文字当成依赖（`PublishViewModel` 就因此被误报过一次）
8. **跨语言契约检查脚本**：`python tools/check_scan_code_contract.py` 比对客户端
   `lib-base/.../scan/ScanCodeFormat.kt` 与后端 `backend/.../scan/ScanCodeFormat.kt` 里
   `CODE_HOST` / 路径段 / 长度上限 / `HTTP_URL` 正则是否一致。
   站内码（二维码里那串 URL）的编解码**无法跨工程共享代码**（Android 工程与 `:backend`
   是两个独立编译单元），所以是刻意的两份实现；不一致的后果不是崩溃而是**自己分享出去的码
   自己扫不出来**，只有真机上扫了才发现。**改任何一侧都要同时改另一侧**，
   且服务端 host 白名单只加不删（删了等于让已发出的码失效）。
9. **改完资源要跑 release**：`./gradlew assembleDebug` **不会**跑 `verifyReleaseResources`，
   而它只在 release 变体执行。资源类改动（尤其把资源挪进/挪出 `:lib-base`）必须
   `./gradlew assembleRelease` 才算验过——上一轮 `NoMaterialButtonStyle` 迁进 `:lib-base`
   缺 Material 依赖，debug 一路绿灯、release 直接失败。
10. **`clean` 前先离开 build 目录**：在 `xxx/build/...` 里执行过命令后，该目录会成为一个
    进程的当前工作目录，Windows 上无法删除——`./gradlew clean` 会以
    `Unable to delete directory` 失败（本项目已踩两次）。跑构建前先 `cd` 回仓库根。
11. **注释里别写 `/*`**：Kotlin 的块注释**可以嵌套**，所以在 KDoc 正文里写
    `` `/api/file/**` `` 会开一层嵌套注释、吃掉本该闭合外层注释的那个 `*/`，
    报错是 `Unclosed comment` 且指向**文件末尾**（离真正错处很远）。
    用 `python tools/check_kotlin_comment_nesting.py` 一秒钟定位（它会报出文件:行）。
