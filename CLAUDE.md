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
:lib-base            ← UDF 基类 + 公共 bean + Provider 接口 + 路由常量
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
- **IUserStore**：lib-base 定义的存储接口，由 `UserStoreProviderImpl` 实现，绑定见 `StoreModule`（`@Binds`）
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
3. **跨模块资源**：共享 drawable 当前采用复制策略（各模块各持一份）。后续计划提取到 `:lib-base`
4. **kapt 缓存**：新增模块或大改依赖后，如遇 kapt `NonExistentClass` 错误，执行 `./gradlew clean assembleDebug`
5. **跨模块依赖**：feature 模块间靠「接口下沉 lib-base + Hilt 绑定 + 构造/字段注入」，
   禁止直接 `implementation(project(":feature-*"))`，也不要用服务定位器（`TheRouter.get`）绕开依赖声明
