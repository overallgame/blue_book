# 小蓝书（BlueBook）后端设计文档

> 版本：v1.0 | 日期：2026-08-06 | 作者：overfloatGame

---

## 1. 概述

### 1.1 项目背景

小蓝书是一款类小红书的短视频社交 App，Android 端采用 Clean Architecture + MVVM + UDF 模式，已拆分为 11 个模块。本文档为后端设计，与 Android 端对齐 API 契约。

### 1.2 设计目标

- 支持前端已有的 22 个 API 接口，补充文件上传、通知等新接口
- 支持视频分片上传 + 断点续传 + 秒传
- 支持 FFmpeg 服务端转码为 HLS 自适应码率
- MVP 阶段单机 VPS 部署，架构预留水平扩展能力
- 所有错误信息使用中文

### 1.3 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java / Kotlin | 21 LTS |
| 框架 | Spring Boot + Spring Security + Spring Data JPA | 3.3.x |
| 数据库 | MySQL (InnoDB) | 8.0 |
| 缓存 | Redis | 7.x |
| 搜索 | Elasticsearch + IK 分词器 | 8.x |
| 消息队列 | RabbitMQ | 3.13 |
| 鉴权 | JWT (jjwt) + Spring Security | 0.12.x |
| ORM | Hibernate + QueryDSL | 6.x / 5.x |
| 校验 | Jakarta Validation | 8.x |
| 转码 | FFmpeg | 6.x |
| 文档 | SpringDoc OpenAPI | 2.x |
| 构建 | Gradle (Kotlin DSL) | 8.x |

---

## 2. 系统架构

### 2.1 整体拓扑

```
📱 客户端 (Android / iOS 未来)
        │
        ▼ HTTPS / REST API + JWT
        │
🖥️ Nginx (80/443)
   ├── 反向代理 → Spring Boot :8080
   ├── 静态文件服务 (图片/HLS)
   ├── SSL 终结 (Let's Encrypt)
   └── 限流
        │
        ▼
⚡ Spring Boot 3.x 主应用 (:8080)
   Java 21 + Virtual Threads
        │
        ├── 🗄️ MySQL 8 (主数据库)
        ├── ⚡ Redis (缓存/Session/计数器)
        ├── 🔍 ES 8 (视频搜索)
        └── 📨 RabbitMQ (转码/通知)
        │
        ▼
🎬 FFmpeg Worker (独立进程, 非 JVM)
   消费 RabbitMQ 消息 → 转码 → 输出 HLS
```

### 2.2 关键设计决策

| 决策 | 说明 |
|------|------|
| 单体模块化而非微服务 | MVP 阶段单机跑多个 JVM 内存爆炸，内部模块化可后续拆分 |
| Virtual Threads | Java 21 虚拟线程处理高 IO 场景，无需 async 代码 |
| Nginx 直连静态文件 | 图片/HLS 切片不经过 Spring Boot，减少应用层压力 |
| FFmpeg 独立进程 | 非 JVM 进程，避免 GC 影响转码；Shell 脚本包装 |
| 响应信封统一 | `ApiResponse<T>(code, message, ttl, data)` — code=0 成功 |

---

## 3. 模块划分

### 3.1 内部模块（Spring Boot 包结构）

```
com.example.bluebook
├── common        // 公共：BaseEntity, JwtUtil, ApiResponse, 全局异常
├── config        // 安全配置、WebMvcConfig、JacksonConfig
├── auth          // 登录/注册/验证码/JWT 签发与刷新/登出
├── video         // 视频上传/发布/Feed/搜索/播放URL/转码状态
├── comment       // 评论发表/删除/回复/点赞
├── user          // 个人资料/头像/关注取关/粉丝列表
├── interaction   // 点赞/收藏（跨 video 和 comment）
├── file          // 图片上传/视频分片上传/合并/转码消息投递
├── notification  // 通知列表/未读数/已读（拉取模式）
├── search        // ES 搜索/热搜词/索引同步
└── BlueBookApplication.kt
```

### 3.2 跨模块调用规则

- ✅ Service → 其他模块 Service 接口
- ❌ Service → 其他模块 Repository（必须通过 Service）
- ❌ 循环依赖（如 A→B 且 B→A，抽公共逻辑到 common）

---

## 4. 数据库设计

### 4.1 ER 核心实体

#### `user` 表
```sql
CREATE TABLE user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone           VARCHAR(20) UNIQUE NOT NULL,
    nickname        VARCHAR(50) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    background_url  VARCHAR(500),
    bio             VARCHAR(200),
    gender          VARCHAR(10),
    birthday        DATE,
    occupation      VARCHAR(100),
    region          VARCHAR(100),
    school          VARCHAR(100),
    follower_count  BIGINT DEFAULT 0,
    following_count BIGINT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### `video` 表
```sql
CREATE TABLE video (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    uploader_id      BIGINT NOT NULL,
    title            VARCHAR(200),
    description      VARCHAR(1000),
    cover_url        VARCHAR(500),
    original_url     VARCHAR(500),
    hls_url          VARCHAR(500),
    transcode_status ENUM('PENDING','PROCESSING','DONE','FAILED') DEFAULT 'PENDING',
    duration         INT,
    width            INT,
    height           INT,
    file_size        BIGINT,
    like_count       BIGINT DEFAULT 0,
    collect_count    BIGINT DEFAULT 0,
    comment_count    BIGINT DEFAULT 0,
    view_count       BIGINT DEFAULT 0,
    status           ENUM('PUBLISHED','DELETED','REVIEWING') DEFAULT 'PUBLISHED',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_uploader_created (uploader_id, created_at),
    INDEX idx_feed (status, created_at)
);
```

#### `comment` 表
```sql
CREATE TABLE comment (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    video_id          BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    parent_id         BIGINT,
    reply_to_user_id  BIGINT,
    content           VARCHAR(1000) NOT NULL,
    like_count        INT DEFAULT 0,
    status            ENUM('NORMAL','DELETED') DEFAULT 'NORMAL',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_video_parent (video_id, parent_id, created_at)
);
```

#### `video_like` / `video_collect` — 联合主键去重
```sql
CREATE TABLE video_like (
    user_id   BIGINT NOT NULL,
    video_id  BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_id)
);

CREATE TABLE video_collect (
    user_id   BIGINT NOT NULL,
    video_id  BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_id)
);

CREATE TABLE user_follow (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id)
);
```

#### `notification` 表
```sql
CREATE TABLE notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    type        ENUM('LIKE','COMMENT','FOLLOW','SYSTEM'),
    video_id    BIGINT,
    comment_id  BIGINT,
    content     VARCHAR(500),
    is_read     BOOLEAN DEFAULT FALSE,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver_read (receiver_id, is_read, created_at)
);
```

#### 其他表
- `refresh_token` — SHA-256 哈希存储，关联 user_id，7 天过期
- `search_hot_word` — 热搜词 + search_count + rank
- `file_record` — 文件原始名/存储路径/类型/大小
- `upload_session` — 分片上传会话状态追踪

### 4.2 计数一致性策略

| 计数器 | 策略 |
|--------|------|
| like_count / collect_count / comment_count | DB 事务内同时写关系表 + 更新计数器 |
| view_count（播放数） | Redis INCR，每 5 分钟或每 100 次批量同步 MySQL |
| follower_count / following_count | 事务内同步更新 |
| 定时对账 | 每天凌晨跑 SQL 脚本 COUNT 实际值 vs 冗余计数器，自动修正 |

---

## 5. Redis 缓存设计

### 5.1 分层缓存

| 层级 | 数据结构 | 内容 | TTL | 更新策略 |
|------|---------|------|-----|---------|
| Feed 列表 | List (LPUSH + LTRIM) | 最新 200 条视频 ID | 5min | 新视频 LPUSH；过期后从 DB 重建 |
| 视频详情 | String (JSON) | 单个视频 DTO | 30min | Cache-Aside；点赞后删缓存 |
| 用户 Session | String (JSON) | 当前用户信息 | 7天 | 登录写入，编辑更新，登出删除 |
| 计数器 | Hash + String | 播放数/点赞状态/关注状态 | 永久 | 定时批量落库 |

### 5.2 Key 命名规范

```
feed:latest              → List    最新 Feed 视频 ID
video:{videoId}          → String  视频详情 JSON
video:play_count:{id}    → String  播放计数
user:{userId}            → String  用户信息 JSON
user:following:{uid}     → Set     关注列表（用户 ID 集合）
like:video:{videoId}     → Set     点赞该视频的用户 ID 集合
collect:video:{videoId}  → Set     收藏该视频的用户 ID 集合
hot:search               → SortedSet 热搜词 + 热度分
token:blacklist:{jti}    → String  JWT 黑名单
upload:{uploadId}        → Hash    分片上传进度
sms:phone:{phone}        → String  短信 60s 限流
login:fail:{phone}       → String  登录失败计数
```

---

## 6. Elasticsearch 搜索设计

### 6.1 索引 Mapping

- 分片：1 shard / 0 replica（单机）
- 分词器：IK Analysis (`ik_smart`)
- 索引字段：id (long), title (text, 权重^3), description (text, 权重^1), nickname (text, 权重^1.5), like_count, comment_count, view_count, created_at, status

### 6.2 数据同步

- **应用层双写**：事务写 MySQL 后，异步发 MQ 消息写 ES
- **失败重试**：ES 写入失败 → dead_letter 表 → 定时任务重试
- **全量重建**：`POST /admin/search/reindex` 管理接口，从 MySQL 批量读 → 批量写 ES
- **一致性**：最终一致，允许秒级延迟

### 6.3 搜索接口

- `GET /api/v2/videos/search?keyword=美食&cursorId=0&size=20`
- 排序：相关性分数（_score）优先，发布时间（created_at）其次
- 搜索建议：Redis ZSet 前缀匹配 (`ZRANGEBYLEX`)

---

## 7. 视频上传与转码流水线

### 7.1 分片上传协议（三步）

| 步骤 | 接口 | 说明 |
|------|------|------|
| 0 Init | `POST /api/file/upload/init` | 上报 fileName/fileSize/fileMd5/totalChunks → 返回 uploadId + 已完成分片列表 |
| 1 Chunk | `POST /api/file/upload/chunk` | 上传单个分片（2MB/片），支持并发 3 片 |
| 2 Complete | `POST /api/file/upload/complete` | 合并分片 → MD5 校验 → 创建 video 记录 |

### 7.2 秒传逻辑

- Init 时上报完整文件 MD5
- 服务端查 upload_session 表中相同 MD5 + DONE 的记录
- 存在 → 返回 `skipUpload: true`，跳过上传，复用已有文件

### 7.3 转码流程

1. 上传完成 → 事务内：写 video 表（status=PENDING）+ 发 RabbitMQ 消息 `video.transcode`
2. FFmpeg Worker 消费 → 更新 status=PROCESSING → 执行转码
3. 转码参数：`-c:v libx264 -preset fast -crf 23 -c:a aac -b:a 128k -hls_time 10`
4. 输出 3 档码率：1080p (4Mbps) / 720p (2Mbps) / 480p (800Kbps)
5. 自动生成封面：`-ss 00:00:01 -vframes 1 cover.jpg`
6. 完成 → 更新 hls_url + status=DONE → 异步写 ES

### 7.4 错误处理

- Worker 10 分钟超时 → 标记 FAILED → 重试队列（最多 3 次）
- prefetch = 1（串行转码，避免打满 CPU）
- 原文件转码后保留 7 天，HLS 切片随删除 7 天后清理

---

## 8. API 接口总览

### 8.1 接口清单

#### 认证 (`/api/v2/auth`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /login | 手机号+密码登录 | 否 |
| POST | /register | 注册 | 否 |
| POST | /code | 发送验证码 | 否 |
| POST | /refresh | 刷新 Token | 否 |
| POST | /logout | 登出 | 是 |

#### 视频 (`/api/v2`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /feed | Feed 流（游标分页） | 否 |
| GET | /videos/search | 搜索视频 | 否 |
| GET | /videos/{id}/dto | 视频详情 | 否 |
| GET | /videos/{id}/playUrl | 播放 URL | 是 |
| GET | /videos/{id}/status | 转码状态 | 是 |
| POST | /videos/publish | 发布视频 | 是 |
| POST | /videos/{id}/like | 点赞/取消 | 是 |
| POST | /videos/{id}/collect | 收藏/取消 | 是 |

#### 评论 (`/api/v1/comments`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | / | 根评论列表 | 否 |
| GET | /{id}/replies | 回复列表 | 否 |
| POST | / | 发表评论 | 是 |
| POST | /{id}/like | 点赞评论 | 是 |
| DELETE | /{id} | 删除评论 | 是 |

#### 用户 (`/api/v2`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /me | 当前用户 | 是 |
| PUT | /me | 编辑资料 | 是 |
| POST | /me/avatar | 上传头像 | 是 |
| GET | /users/{id} | 他人主页 | 否 |
| GET | /users/{id}/videos | 作品列表 | 否 |
| POST | /users/{id}/follow | 关注 | 是 |
| DELETE | /users/{id}/follow | 取关 | 是 |
| GET | /users/{id}/followers | 粉丝列表 | 否 |
| GET | /users/{id}/following | 关注列表 | 否 |

#### 文件 (`/api/file`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /upload/image | 上传图片 | 是 |
| POST | /upload/init | 初始化分片上传 | 是 |
| POST | /upload/chunk | 上传分片 | 是 |
| GET | /upload/progress | 查询进度 | 是 |
| POST | /upload/complete | 合并分片 | 是 |

#### 通知 (`/api/v2/notifications`)
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | / | 通知列表 | 是 |
| GET | /unread-count | 未读数 | 是 |
| POST | /read-all | 全部已读 | 是 |
| POST | /{id}/read | 单条已读 | 是 |

### 8.2 统一响应格式

```json
// 成功
{ "code": 0, "message": "success", "ttl": 0, "data": { ... } }

// 错误
{ "code": 10001, "message": "手机号或密码错误", "ttl": 0, "data": null }
```

### 8.3 错误码规划

| 范围 | 模块 |
|------|------|
| 0 | 成功 |
| 10001-10999 | 认证相关 |
| 11001-11999 | 视频相关 |
| 12001-12999 | 评论相关 |
| 13001-13999 | 文件相关 |
| 14001-14999 | 通用（参数/权限/限流/服务器） |

---

## 9. 安全设计

### 9.1 JWT 鉴权

- **Access Token**：HS256 签名，Payload 含 userId/phone/iat/exp/jti，24h 有效
- **Refresh Token**：随机 UUID 字符串，SHA-256 哈希存 DB，7 天有效
- **黑名单**：登出后 jti 写入 Redis `token:blacklist:{jti}`，24h TTL
- **Spring Security Filter**：验证签名 → 查黑名单 → 解析 userId → SecurityContext

### 9.2 接口防护

| 防护项 | 策略 |
|--------|------|
| 验证码频率 | 同手机号 60s 一次，同 IP 每小时 10 次（Redis） |
| 登录失败锁定 | 5 次失败 → 锁定 15 分钟 |
| 全局限流 | Nginx `limit_req` 单 IP 100 req/s |
| 文件限制 | 图片 ≤ 10MB，视频 ≤ 100MB，类型白名单 + 魔数校验 |
| XSS/SQL 注入 | Spring HTML 转义 + JPA 参数绑定 |

### 9.3 敏感数据保护

| 数据 | 存储 | 传输 |
|------|------|------|
| 密码 | BCrypt 加盐哈希 | HTTPS |
| 手机号 | AES-256 加密 | API 返回脱敏 138****1234 |
| Refresh Token | SHA-256 哈希 | 仅返回一次 |
| JWT Secret | 环境变量 | — |
| HTTPS | Let's Encrypt 免费证书，Certbot 自动续期 | — |

---

## 10. 异常处理与日志

### 10.1 全局异常处理

`@RestControllerAdvice` 统一处理，不暴露堆栈到客户端：
- `BusinessException` → 返回对应 code + 中文 message
- `MethodArgumentNotValidException` → 返回字段校验错误详情
- `AuthenticationException` → 返回 10005 "请先登录"
- `Exception` → 返回 14999 "服务器繁忙，请稍后再试"，完整堆栈仅记日志

### 10.2 日志规范

- 格式：Logback JSON（timestamp, level, logger, thread, message, mdc{traceId, userId, ip}, exception）
- traceId：每个请求分配，MDC 贯穿全链路
- 滚动：按天 + 压缩，保留 30 天
- 输出：`/var/log/blue-book/` (app.log / error.log / access.log)

### 10.3 健康监控（MVP）

- `GET /actuator/health` — 检查 DB/Redis/ES/RabbitMQ 连接
- cron 每分钟 curl → 不健康发钉钉/邮件告警
- 后续接入 Prometheus + Grafana

---

## 11. 部署方案

### 11.1 服务器配置

**推荐：4 核 8G + 200G SSD，单 VPS**

| 进程 | 内存 | 说明 |
|------|------|------|
| Spring Boot JVM | 2G | -Xmx2g -Xms2g |
| MySQL | 1G | InnoDB Buffer Pool 768M |
| Redis | 512M | maxmemory 512mb |
| Elasticsearch | 1.5G | -Xms1g -Xmx1g |
| RabbitMQ | 512M | Erlang VM 最小配置 |
| FFmpeg + OS | 2.5G | 系统预留 + 转码峰值 |

### 11.2 部署步骤

```bash
# 构建
./gradlew :backend:bootJar

# 部署
scp backend.jar user@vps:/opt/blue-book/
ssh user@vps "sudo systemctl restart blue-book"
```

- **systemd**：`/etc/systemd/system/blue-book.service`，Restart=on-failure
- **中间件**：MySQL/Redis/RabbitMQ/FFmpeg → apt install，ES → tar.gz
- **Nginx**：apt install + certbot（Let's Encrypt），反向代理 + 静态文件 + HLS

---

## 12. 项目结构建议

后端代码建议放在 Android 项目根目录的 `backend/` 子目录下，复用同一个 Gradle 根项目：

```
blue_book/
├── app/                    # Android 壳工程
├── feature-*/              # Android 业务模块
├── core-*/                 # Android 核心模块
├── lib-base/               # Android 基础库
├── backend/                # 🆕 Spring Boot 后端
│   ├── src/main/kotlin/com/example/bluebook/
│   ├── src/main/resources/application.yml
│   └── build.gradle.kts
├── build.gradle.kts        # 根构建脚本
└── settings.gradle.kts     # 加入 backend 子项目
```

---

## 13. 验证

1. `./gradlew :backend:build` — 编译检查
2. `./gradlew :backend:test` — 单元测试
3. 启动后端 → curl `/actuator/health` 验证所有中间件连通性
4. 使用前端 Dev 环境指向 `http://localhost:8080` 做端到端调试
