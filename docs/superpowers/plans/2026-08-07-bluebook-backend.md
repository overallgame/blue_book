# 小蓝书后端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 项目根目录下新建 `backend/` Spring Boot 3.3.x 子项目，实现完整的短视频社交后端（认证/用户/视频/评论/文件上传/转码/搜索/通知）。

**Architecture:** 单体 Spring Boot 应用，内部按 auth/video/comment/user/file/notification/search/interaction 分包；JPA + QueryDSL 操作 MySQL；Redis 缓存；ES 搜索；RabbitMQ 异步转码。采用与前端一致的 `ApiResponse<T>(code, message, ttl, data)` 响应格式。

**Tech Stack:** Spring Boot 3.3.x + Spring Security + Spring Data JPA + QueryDSL + MySQL 8 + Redis 7 + Elasticsearch 8 + RabbitMQ 3.13 + JWT (jjwt 0.12.x) + Kotlin + Gradle 8.x + Java 21 Virtual Threads

**Spec:** `docs/superpowers/specs/2026-08-06-bluebook-backend-design.md`

---

## 阶段 1: 项目骨架搭建

### Task 1.1: 创建 backend 子项目和 Gradle 配置

**Files:**
- Create: `backend/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] **Step 1: 修改 settings.gradle.kts，加入 backend 子项目**

在 `settings.gradle.kts` 末尾添加：
```kotlin
include(":backend")
```

- [ ] **Step 2: 创建 backend/build.gradle.kts**

```kotlin
plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
}

group = "com.example.bluebook"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    maven { url = uri("https://maven.aliyun.com/repository/spring") }
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // Elasticsearch
    implementation("co.elastic.clients:elasticsearch-java:8.14.3")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.h2database:h2") // test

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 3: 修改根 build.gradle.kts**

在 `plugins` 块末尾添加：
```kotlin
id("org.springframework.boot") version "3.3.4" apply false
id("io.spring.dependency-management") version "1.1.6" apply false
kotlin("plugin.spring") version "1.9.24" apply false
kotlin("plugin.jpa") version "1.9.24" apply false
```

- [ ] **Step 4: 创建 backend/src/main/kotlin/com/example/bluebook/BlueBookApplication.kt**

```kotlin
package com.example.bluebook

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BlueBookApplication

fun main(args: Array<String>) {
    runApplication<BlueBookApplication>(*args)
}
```

- [ ] **Step 5: 创建 backend/src/main/resources/application.yml**

```yaml
server:
  port: 8080

spring:
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:mysql://localhost:3306/blue_book?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      timeout: 3s
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 100MB

app:
  jwt:
    secret: ${JWT_SECRET:default-jwt-secret-change-in-production}
    access-token-ttl: 86400000   # 24h
    refresh-token-ttl: 604800000 # 7d
  upload:
    image-max-size: 10485760     # 10MB
    video-max-size: 104857600    # 100MB
    chunk-size: 2097152          # 2MB
    storage-path: ${UPLOAD_PATH:./upload}

logging:
  level:
    root: INFO
    com.example.bluebook: DEBUG
  file:
    path: /var/log/blue-book
```

- [ ] **Step 6: 创建 backend/src/test/kotlin/com/example/bluebook/BlueBookApplicationTests.kt**

```kotlin
package com.example.bluebook

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class BlueBookApplicationTests {
    @Test
    fun contextLoads() {}
}
```

- [ ] **Step 7: 创建 backend/src/main/resources/application-test.yml（测试环境用 H2）**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  rabbitmq:
    listener:
      auto-startup: false
```

- [ ] **Step 8: 添加 .gitignore 条目**

在 `.gitignore` 末尾添加：
```
# Backend
backend/build/
backend/.gradle/
backend/out/
upload/
*.jar
!gradle/wrapper/gradle-wrapper.jar
```

- [ ] **Step 9: 验证编译**

```bash
./gradlew :backend:compileKotlin
./gradlew :backend:test --tests "com.example.bluebook.BlueBookApplicationTests"
```

- [ ] **Step 10: 提交**

```bash
git add backend/ settings.gradle.kts build.gradle.kts .gitignore
git commit -m "搭建backend模块骨架：Spring Boot 3.3.x + Gradle + application.yml"
```

---

## 阶段 2: 公共基础设施

### Task 2.1: 统一响应与异常处理

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/common/ApiResponse.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/common/BusinessException.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/common/GlobalExceptionHandler.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/common/TraceFilter.kt`

- [ ] **Step 1: 创建 ApiResponse.kt**

```kotlin
package com.example.bluebook.common

data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "success",
    val ttl: Int = 0,
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T?): ApiResponse<T> = ApiResponse(data = data)
        fun ok(): ApiResponse<Any> = ApiResponse(data = null)
        fun fail(code: Int, message: String): ApiResponse<Any> =
            ApiResponse(code = code, message = message, data = null)
    }
}
```

- [ ] **Step 2: 创建 BusinessException.kt 及错误码常量**

```kotlin
package com.example.bluebook.common

open class BusinessException(
    val code: Int,
    override val message: String
) : RuntimeException(message)

// 认证相关 10001-10999
class InvalidCredentialsException : BusinessException(10001, "手机号或密码错误")
class TokenExpiredException : BusinessException(10002, "登录已过期，请重新登录")
class SmsRateLimitException : BusinessException(10003, "验证码已发送，请60秒后再试")
class AccountLockedException : BusinessException(10004, "账号已锁定，请15分钟后再试")
class UnauthorizedException : BusinessException(10005, "请先登录")

// 视频相关 11001-11999
class VideoNotFoundException : BusinessException(11001, "视频不存在或已被删除")
class TranscodeFailedException : BusinessException(11002, "视频转码失败，请重新上传")

// 评论相关 12001-12999
class CommentNotFoundException : BusinessException(12001, "评论不存在或已被删除")

// 文件相关 13001-13999
class FileTooLargeException : BusinessException(13001, "文件大小超过限制")
class ChunkMissingException : BusinessException(13002, "分片缺失，请重新上传缺失的分片")
class InvalidFileTypeException : BusinessException(13003, "不支持的文件格式")

// 通用 14001-14999
class ForbiddenException : BusinessException(14001, "无权执行此操作")
class ServerBusyException : BusinessException(14999, "服务器繁忙，请稍后再试")
```

- [ ] **Step 3: 创建 GlobalExceptionHandler.kt**

```kotlin
package com.example.bluebook.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ApiResponse<Any> =
        ApiResponse.fail(ex.code, ex.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ApiResponse<Any> {
        val msg = ex.bindingResult.fieldErrors
            .joinToString("; ") { (it as FieldError).let { f -> "${f.field}: ${f.defaultMessage}" } }
        return ApiResponse.fail(14001, msg)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.fail(10005, "请先登录"))

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ApiResponse<Any> {
        log.error("未处理异常", ex)
        return ApiResponse.fail(14999, "服务器繁忙，请稍后再试")
    }
}
```

- [ ] **Step 4: 创建 TraceFilter.kt（traceId 全链路追踪）**

```kotlin
package com.example.bluebook.common

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TraceFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val traceId = UUID.randomUUID().toString().replace("-", "").take(16)
        MDC.put("traceId", traceId)
        try { chain.doFilter(request, response) } finally { MDC.clear() }
    }
}
```

- [ ] **Step 5: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/common/
git commit -m "添加公共基础设施：ApiResponse、BusinessException、全局异常处理、traceId过滤器"
```

### Task 2.2: JPA 基础实体

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/common/BaseEntity.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/common/JpaConfig.kt`

- [ ] **Step 1: 创建 BaseEntity.kt**

```kotlin
package com.example.bluebook.common

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
}
```

- [ ] **Step 2: 创建 JpaConfig.kt**

```kotlin
package com.example.bluebook.common

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
class JpaConfig
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/common/
git commit -m "添加BaseEntity基础实体和JPA审计配置"
```

---

## 阶段 3: 数据库实体与 Repository

### Task 3.1: 用户、Token 实体

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/entity/User.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/entity/RefreshToken.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/repository/UserRepository.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/repository/RefreshTokenRepository.kt`

- [ ] **Step 1: 创建 User.kt**

```kotlin
package com.example.bluebook.auth.entity

import com.example.bluebook.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "`user`")
class User(
    @Column(name = "phone", nullable = false, unique = true, length = 20)
    var phone: String,

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null,

    @Column(name = "background_url", length = 500)
    var backgroundUrl: String? = null,

    @Column(name = "bio", length = 200)
    var bio: String? = null,

    @Column(name = "gender", length = 10)
    var gender: String? = null,

    @Column(name = "birthday")
    var birthday: LocalDate? = null,

    @Column(name = "occupation", length = 100)
    var occupation: String? = null,

    @Column(name = "region", length = 100)
    var region: String? = null,

    @Column(name = "school", length = 100)
    var school: String? = null,

    @Column(name = "follower_count", nullable = false)
    var followerCount: Long = 0,

    @Column(name = "following_count", nullable = false)
    var followingCount: Long = 0
) : BaseEntity()
```

- [ ] **Step 2: 创建 RefreshToken.kt**

```kotlin
package com.example.bluebook.auth.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "refresh_token")
class RefreshToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
}
```

- [ ] **Step 3: 创建 UserRepository.kt**

```kotlin
package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByPhone(phone: String): Optional<User>
    fun existsByPhone(phone: String): Boolean
}
```

- [ ] **Step 4: 创建 RefreshTokenRepository.kt**

```kotlin
package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId")
    fun deleteAllByUserId(userId: Long)

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    fun deleteAllExpired(now: Instant)
}
```

- [ ] **Step 5: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/auth/
git commit -m "添加User和RefreshToken实体及Repository"
```

### Task 3.2: 视频、评论、通知实体

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/video/entity/Video.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/video/repository/VideoRepository.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/entity/Comment.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/repository/CommentRepository.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/notification/entity/Notification.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/notification/repository/NotificationRepository.kt`

- [ ] **Step 1: 创建 Video.kt**

```kotlin
package com.example.bluebook.video.entity

import com.example.bluebook.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "video", indexes = [
    Index(name = "idx_uploader_created", columnList = "uploader_id,created_at"),
    Index(name = "idx_feed", columnList = "status,created_at")
])
class Video(
    @Column(name = "uploader_id", nullable = false)
    var uploaderId: Long,

    @Column(name = "title", length = 200)
    var title: String? = null,

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "cover_url", length = 500)
    var coverUrl: String? = null,

    @Column(name = "original_url", length = 500)
    var originalUrl: String? = null,

    @Column(name = "hls_url", length = 500)
    var hlsUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "transcode_status", nullable = false, length = 20)
    var transcodeStatus: TranscodeStatus = TranscodeStatus.PENDING,

    @Column(name = "duration")
    var duration: Int? = null,

    @Column(name = "width")
    var width: Int? = null,

    @Column(name = "height")
    var height: Int? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "collect_count", nullable = false)
    var collectCount: Long = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: VideoStatus = VideoStatus.PUBLISHED
) : BaseEntity()

enum class TranscodeStatus { PENDING, PROCESSING, DONE, FAILED }
enum class VideoStatus { PUBLISHED, DELETED, REVIEWING }
```

- [ ] **Step 2: 创建 VideoRepository.kt（含原生查询）**

```kotlin
package com.example.bluebook.video.repository

import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface VideoRepository : JpaRepository<Video, Long> {
    fun findByIdAndStatus(id: Long, status: VideoStatus): Video?

    fun findByUploaderIdAndStatus(uploaderId: Long, status: VideoStatus, pageable: Pageable): List<Video>

    // Feed: 按创建时间倒序，游标分页
    @Query("SELECT v FROM Video v WHERE v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE' AND (:cursorId IS NULL OR v.id < :cursorId) ORDER BY v.id DESC")
    fun findFeedVideos(cursorId: Long?, pageable: Pageable): List<Video>

    @Modifying
    @Query("UPDATE Video v SET v.likeCount = v.likeCount + :delta WHERE v.id = :id")
    fun incrementLikeCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.collectCount = v.collectCount + :delta WHERE v.id = :id")
    fun incrementCollectCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.commentCount = v.commentCount + :delta WHERE v.id = :id")
    fun incrementCommentCount(id: Long, delta: Long)
}
```

- [ ] **Step 3: 创建 Comment.kt**

```kotlin
package com.example.bluebook.comment.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "comment", indexes = [
    Index(name = "idx_video_parent", columnList = "video_id,parent_id,created_at")
])
class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "video_id", nullable = false)
    var videoId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "parent_id")
    var parentId: Long? = null,

    @Column(name = "reply_to_user_id")
    var replyToUserId: Long? = null,

    @Column(name = "content", nullable = false, length = 1000)
    var content: String,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: CommentStatus = CommentStatus.NORMAL,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class CommentStatus { NORMAL, DELETED }
```

- [ ] **Step 4: 创建 CommentRepository.kt**

```kotlin
package com.example.bluebook.comment.repository

import com.example.bluebook.comment.entity.Comment
import com.example.bluebook.comment.entity.CommentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.videoId = :videoId AND c.parentId IS NULL AND c.status = 'NORMAL' AND (:cursorId IS NULL OR c.id < :cursorId) ORDER BY c.id DESC")
    fun findRootComments(videoId: Long, cursorId: Long?, pageable: Pageable): List<Comment>

    @Query("SELECT c FROM Comment c WHERE c.parentId = :parentId AND c.status = 'NORMAL' AND (:cursorId IS NULL OR c.id < :cursorId) ORDER BY c.id ASC")
    fun findReplies(parentId: Long, cursorId: Long?, pageable: Pageable): List<Comment>

    fun findByIdAndStatus(id: Long, status: CommentStatus): Comment?

    @Modifying
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + :delta WHERE c.id = :id")
    fun incrementLikeCount(id: Long, delta: Int)
}
```

- [ ] **Step 5: 创建 Notification.kt 和 Repository**

```kotlin
package com.example.bluebook.notification.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notification", indexes = [
    Index(name = "idx_receiver_read", columnList = "receiver_id,is_read,created_at")
])
class Notification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "receiver_id", nullable = false)
    var receiverId: Long,

    @Column(name = "sender_id", nullable = false)
    var senderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: NotifyType,

    @Column(name = "video_id")
    var videoId: Long? = null,

    @Column(name = "comment_id")
    var commentId: Long? = null,

    @Column(name = "content", length = 500)
    var content: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class NotifyType { LIKE, COMMENT, FOLLOW, SYSTEM }
```

```kotlin
package com.example.bluebook.notification.repository

import com.example.bluebook.notification.entity.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationRepository : JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.receiverId = :userId AND (:cursorId IS NULL OR n.id < :cursorId) ORDER BY n.id DESC")
    fun findByReceiverId(userId: Long, cursorId: Long?, pageable: Pageable): List<Notification>

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiverId = :userId AND n.isRead = false")
    fun countUnreadByReceiverId(userId: Long): Long

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiverId = :userId AND n.isRead = false")
    fun markAllReadByReceiverId(userId: Long)

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.receiverId = :userId")
    fun markReadByIdAndReceiverId(id: Long, userId: Long): Int
}
```

- [ ] **Step 6: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/
git commit -m "添加Video、Comment、Notification实体及Repository"
```

### Task 3.3: 互动实体（点赞/收藏/关注）

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/interaction/entity/VideoLike.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/interaction/entity/VideoCollect.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/entity/UserFollow.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/interaction/repository/VideoLikeRepository.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/interaction/repository/VideoCollectRepository.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/repository/UserFollowRepository.kt`

- [ ] **Step 1: 创建 VideoLike.kt 和 VideoCollect.kt 和 UserFollow.kt**

```kotlin
// VideoLike.kt
package com.example.bluebook.interaction.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "video_like")
@IdClass(VideoLikeId::class)
class VideoLike(
    @Id @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Id @Column(name = "video_id", nullable = false)
    var videoId: Long,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class VideoLikeId : Serializable {
    var userId: Long = 0
    var videoId: Long = 0
    override fun equals(other: Any?) = other is VideoLikeId && other.userId == userId && other.videoId == videoId
    override fun hashCode() = 31 * userId.hashCode() + videoId.hashCode()
}

// VideoCollect.kt
@Entity
@Table(name = "video_collect")
@IdClass(VideoCollectId::class)
class VideoCollect(
    @Id @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Id @Column(name = "video_id", nullable = false)
    var videoId: Long,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class VideoCollectId : Serializable {
    var userId: Long = 0
    var videoId: Long = 0
    override fun equals(other: Any?) = other is VideoCollectId && other.userId == userId && other.videoId == videoId
    override fun hashCode() = 31 * userId.hashCode() + videoId.hashCode()
}

// UserFollow.kt
@Entity
@Table(name = "user_follow")
@IdClass(UserFollowId::class)
class UserFollow(
    @Id @Column(name = "follower_id", nullable = false)
    var followerId: Long,
    @Id @Column(name = "followee_id", nullable = false)
    var followeeId: Long,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class UserFollowId : Serializable {
    var followerId: Long = 0
    var followeeId: Long = 0
    override fun equals(other: Any?) = other is UserFollowId && other.followerId == followerId && other.followeeId == followeeId
    override fun hashCode() = 31 * followerId.hashCode() + followeeId.hashCode()
}
```

- [ ] **Step 2: 创建 Repository**

```kotlin
// VideoLikeRepository.kt
package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoLike
import org.springframework.data.jpa.repository.JpaRepository

interface VideoLikeRepository : JpaRepository<VideoLike, Long> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
}

// VideoCollectRepository.kt
package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoCollect
import org.springframework.data.jpa.repository.JpaRepository

interface VideoCollectRepository : JpaRepository<VideoCollect, Long> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
}

// UserFollowRepository.kt
package com.example.bluebook.user.repository

import com.example.bluebook.user.entity.UserFollow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserFollowRepository : JpaRepository<UserFollow, Long> {
    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean
    fun deleteByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Int
    fun countByFollowerId(followerId: Long): Long
    fun countByFolloweeId(followeeId: Long): Long

    @Query("SELECT uf.followeeId FROM UserFollow uf WHERE uf.followerId = :followerId")
    fun findFolloweeIdsByFollowerId(followerId: Long): List<Long>
}
```

- [ ] **Step 3: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/interaction/ backend/src/main/kotlin/com/example/bluebook/user/
git commit -m "添加互动实体：VideoLike、VideoCollect、UserFollow及Repository"
```

### Task 3.4: MySQL DDL 初始化脚本

**Files:**
- Create: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/resources/application.yml`（添加 `ddl-auto: none` + `sql.init.mode: never`）

- [ ] **Step 1: 创建 schema.sql（完整 DDL，用于手动建表，非自动执行）**

```sql
-- 小蓝书数据库初始化脚本
-- 执行：mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS blue_book DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE blue_book;

CREATE TABLE `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) UNIQUE NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    background_url VARCHAR(500),
    bio VARCHAR(200),
    gender VARCHAR(10),
    birthday DATE,
    occupation VARCHAR(100),
    region VARCHAR(100),
    school VARCHAR(100),
    follower_count BIGINT DEFAULT 0,
    following_count BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE video (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uploader_id BIGINT NOT NULL,
    title VARCHAR(200),
    description VARCHAR(1000),
    cover_url VARCHAR(500),
    original_url VARCHAR(500),
    hls_url VARCHAR(500),
    transcode_status ENUM('PENDING','PROCESSING','DONE','FAILED') DEFAULT 'PENDING',
    duration INT,
    width INT,
    height INT,
    file_size BIGINT,
    like_count BIGINT DEFAULT 0,
    collect_count BIGINT DEFAULT 0,
    comment_count BIGINT DEFAULT 0,
    view_count BIGINT DEFAULT 0,
    status ENUM('PUBLISHED','DELETED','REVIEWING') DEFAULT 'PUBLISHED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_uploader_created (uploader_id, created_at),
    INDEX idx_feed (status, created_at)
) ENGINE=InnoDB;

CREATE TABLE comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT,
    reply_to_user_id BIGINT,
    content VARCHAR(1000) NOT NULL,
    like_count INT DEFAULT 0,
    status ENUM('NORMAL','DELETED') DEFAULT 'NORMAL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_video_parent (video_id, parent_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE video_like (
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_id)
) ENGINE=InnoDB;

CREATE TABLE video_collect (
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_id)
) ENGINE=InnoDB;

CREATE TABLE user_follow (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type ENUM('LIKE','COMMENT','FOLLOW','SYSTEM'),
    video_id BIGINT,
    comment_id BIGINT,
    content VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver_read (receiver_id, is_read, created_at)
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB;

CREATE TABLE search_hot_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(100) NOT NULL,
    search_count BIGINT DEFAULT 0,
    rank INT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE file_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(255),
    storage_path VARCHAR(500),
    file_type ENUM('IMAGE','VIDEO'),
    file_size BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE upload_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_size BIGINT,
    file_md5 VARCHAR(32),
    total_chunks INT,
    chunk_size INT,
    status ENUM('UPLOADING','MERGING','DONE','EXPIRED'),
    video_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/resources/schema.sql
git commit -m "添加MySQL DDL初始化脚本：10张表完整建表语句"
```

---

## 阶段 4: 安全模块

### Task 4.1: JWT 工具类

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/common/JwtUtil.kt`

- [ ] **Step 1: 创建 JwtUtil.kt**

```kotlin
package com.example.bluebook.common

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${app.jwt.secret}") secret: String,
    @Value("\${app.jwt.access-token-ttl}") private val accessTtl: Long,
    @Value("\${app.jwt.refresh-token-ttl}") private val refreshTtl: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray().let { bytes ->
        if (bytes.size < 32) bytes + ByteArray(32 - bytes.size) { 0 } else bytes
    })

    fun generateAccessToken(userId: Long, phone: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("phone", phone)
            .issuedAt(now)
            .expiration(Date(now.time + accessTtl))
            .id(UUID.randomUUID().toString())
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(): String =
        "${UUID.randomUUID()}_${UUID.randomUUID()}_${System.currentTimeMillis()}"

    fun validateToken(token: String): Boolean = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
    }.isSuccess

    fun getUserId(token: String): Long = Jwts.parser()
        .verifyWith(key).build()
        .parseSignedClaims(token).payload.subject.toLong()

    fun getJti(token: String): String = Jwts.parser()
        .verifyWith(key).build()
        .parseSignedClaims(token).payload.id

    fun hashToken(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 2: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/common/JwtUtil.kt
git commit -m "添加JwtUtil：accessToken生成/验证/解析、refreshToken生成、SHA-256哈希"
```

### Task 4.2: Spring Security 配置

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/config/SecurityConfig.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/config/JwtAuthFilter.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/config/RedisConfig.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/config/WebMvcConfig.kt`

- [ ] **Step 1: 创建 RedisConfig.kt**

```kotlin
package com.example.bluebook.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RedisConfig {
    @Bean
    fun stringRedisTemplate(factory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)
}
```

- [ ] **Step 2: 创建 JwtAuthFilter.kt**

```kotlin
package com.example.bluebook.config

import com.example.bluebook.common.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
class JwtAuthFilter(
    private val jwtUtil: JwtUtil,
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {
    companion object {
        val PUBLIC_PATHS = setOf(
            "/api/v2/auth/login", "/api/v2/auth/register", "/api/v2/auth/code",
            "/api/v2/auth/refresh", "/actuator/health", "/api/v2/feed",
            "/api/v2/videos/search", "/api/v1/comments"
        )
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val path = request.requestURI
        if (PUBLIC_PATHS.any { path.startsWith(it) } || request.method == "GET") {
            // GET 请求和公开路径允许携带可选的鉴权头（用于获取 isLike/isFollow 等状态）
            val token = extractToken(request)
            if (token != null && jwtUtil.validateToken(token)) {
                val jti = jwtUtil.getJti(token)
                if (redisTemplate.opsForValue().get("token:blacklist:$jti") == null) {
                    setAuth(jwtUtil.getUserId(token))
                }
            }
            chain.doFilter(request, response)
            return
        }
        val token = extractToken(request) ?: run {
            response.sendError(401, "请先登录")
            return
        }
        if (!jwtUtil.validateToken(token)) {
            response.sendError(401, "登录已过期")
            return
        }
        val jti = jwtUtil.getJti(token)
        if (redisTemplate.opsForValue().get("token:blacklist:$jti") != null) {
            response.sendError(401, "Token已失效")
            return
        }
        setAuth(jwtUtil.getUserId(token))
        chain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (StringUtils.hasText(header) && header.startsWith("Bearer ")) header.substring(7) else null
    }

    private fun setAuth(userId: Long) {
        val auth = UsernamePasswordAuthenticationToken(userId, null, listOf())
        SecurityContextHolder.getContext().authentication = auth
    }
}
```

- [ ] **Step 3: 创建 SecurityConfig.kt**

```kotlin
package com.example.bluebook.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/api/v2/auth/**", "/actuator/health", "/actuator/info",
                    "/api/v2/feed", "/api/v2/videos/search", "/api/v2/videos/*/dto",
                    "/api/v1/comments", "/api/v2/users/*"
                ).permitAll()
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

- [ ] **Step 4: 创建 WebMvcConfig.kt（CORS）**

```kotlin
package com.example.bluebook.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }
}
```

- [ ] **Step 5: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/config/
git commit -m "添加Spring Security配置：JWT过滤器、BCrypt、CORS、Redis配置"
```

---

## 阶段 5: 认证模块

### Task 5.1: AuthService 和 AuthController

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/LoginRequest.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/RegisterRequest.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/SendCodeRequest.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/LoginResponse.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/RefreshRequest.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/dto/UserProfile.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/service/AuthService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/auth/controller/AuthController.kt`

- [ ] **Step 1: 创建 DTO**

```kotlin
// LoginRequest.kt
package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val password: String
)

// RegisterRequest.kt
data class RegisterRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val password: String,
    @field:NotBlank val nickname: String,
    @field:NotBlank val code: String
)

// SendCodeRequest.kt
data class SendCodeRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val nickname: String
)

// LoginResponse.kt
data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val profile: UserProfile?
)

// RefreshRequest.kt
data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

// UserProfile.kt
data class UserProfile(
    val id: Long,
    val phone: String,
    val nickname: String,
    val avatar: String?,
    val bio: String?,
    val gender: String?,
    val birthday: String?,
    val occupation: String?,
    val region: String?,
    val school: String?,
    val followerCount: Long,
    val followingCount: Long
)
```

- [ ] **Step 2: 创建 AuthService.kt**

```kotlin
package com.example.bluebook.auth.service

import com.example.bluebook.auth.dto.*
import com.example.bluebook.auth.entity.RefreshToken
import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.RefreshTokenRepository
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val redisTemplate: StringRedisTemplate
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val failKey = "login:fail:${request.phone}"
        val failCount = redisTemplate.opsForValue().get(failKey)?.toIntOrNull() ?: 0
        if (failCount >= 5) throw AccountLockedException()

        val user = userRepository.findByPhone(request.phone)
            .orElseThrow { InvalidCredentialsException() }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            redisTemplate.opsForValue().increment(failKey)
            redisTemplate.expire(failKey, Duration.ofMinutes(15))
            throw InvalidCredentialsException()
        }
        redisTemplate.delete(failKey)
        return buildLoginResponse(user)
    }

    @Transactional
    fun register(request: RegisterRequest): LoginResponse {
        // 验证码校验（MVP: 固定 "123456" 或 Redis 存真实验证码）
        val codeKey = "sms:${request.phone}"
        val storedCode = redisTemplate.opsForValue().get(codeKey)
        if (storedCode == null || storedCode != request.code) throw BusinessException(10006, "验证码错误或已过期")
        redisTemplate.delete(codeKey)

        if (userRepository.existsByPhone(request.phone)) throw BusinessException(10007, "该手机号已注册")
        val user = User(
            phone = request.phone,
            nickname = request.nickname,
            passwordHash = passwordEncoder.encode(request.password)
        )
        userRepository.save(user)
        return buildLoginResponse(user)
    }

    fun sendCode(request: SendCodeRequest): String {
        val phoneKey = "sms:phone:${request.phone}"
        if (redisTemplate.opsForValue().get(phoneKey) != null) throw SmsRateLimitException()
        // MVP：不接真实短信，固定生成 123456
        val code = "123456"
        redisTemplate.opsForValue().set("sms:${request.phone}", code, Duration.ofMinutes(5))
        redisTemplate.opsForValue().set(phoneKey, "1", Duration.ofSeconds(60))
        return code // 开发环境返回验证码方便测试
    }

    @Transactional
    fun refresh(refreshTokenStr: String): LoginResponse {
        val hash = jwtUtil.hashToken(refreshTokenStr)
        val rt = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { TokenExpiredException() }
        if (rt.expiresAt.isBefore(Instant.now())) throw TokenExpiredException()
        refreshTokenRepository.delete(rt)
        val user = userRepository.findById(rt.userId).orElseThrow { TokenExpiredException() }
        return buildLoginResponse(user)
    }

    fun logout(userId: Long) {
        // 登出时将当前 Token 的 jti 加入 Redis 黑名单（在 Controller 中获取 jti）
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    fun addToBlacklist(jti: String) {
        redisTemplate.opsForValue().set("token:blacklist:$jti", "1", Duration.ofDays(1))
    }

    private fun buildLoginResponse(user: User): LoginResponse {
        val accessToken = jwtUtil.generateAccessToken(user.id, user.phone)
        val refreshTokenStr = jwtUtil.generateRefreshToken()
        val hash = jwtUtil.hashToken(refreshTokenStr)
        refreshTokenRepository.save(RefreshToken(
            userId = user.id,
            tokenHash = hash,
            expiresAt = Instant.now().plusMillis(604800000) // 7d
        ))
        val maskedPhone = user.phone.replaceRange(3, 7, "****")
        return LoginResponse(
            token = accessToken,
            refreshToken = refreshTokenStr,
            profile = UserProfile(
                id = user.id, phone = maskedPhone, nickname = user.nickname,
                avatar = user.avatarUrl, bio = user.bio, gender = user.gender,
                birthday = user.birthday?.toString(), occupation = user.occupation,
                region = user.region, school = user.school,
                followerCount = user.followerCount, followingCount = user.followingCount
            )
        )
    }
}
```

- [ ] **Step 3: 创建 AuthController.kt**

```kotlin
package com.example.bluebook.auth.controller

import com.example.bluebook.auth.dto.*
import com.example.bluebook.auth.service.AuthService
import com.example.bluebook.common.ApiResponse
import com.example.bluebook.common.JwtUtil
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtUtil: JwtUtil
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.login(request))

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.register(request))

    @PostMapping("/code")
    fun sendCode(@Valid @RequestBody request: SendCodeRequest): ApiResponse<String> =
        ApiResponse.ok(authService.sendCode(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String): ApiResponse<Any> {
        val token = authHeader.removePrefix("Bearer ")
        val userId = jwtUtil.getUserId(token)
        val jti = jwtUtil.getJti(token)
        authService.logout(userId)
        authService.addToBlacklist(jti)
        SecurityContextHolder.clearContext()
        return ApiResponse.ok()
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
./gradlew :backend:compileKotlin
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/kotlin/com/example/bluebook/auth/
git commit -m "添加认证模块：登录/注册/验证码/刷新Token/登出完整实现"
```

---

## 阶段 6: 用户模块

### Task 6.1: 用户资料与关注服务

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/user/controller/UserController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/service/UserService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/dto/UserV2MeDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/dto/UserV2ProfileDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/dto/UserV2UpdateRequestDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/dto/UserV2FollowListResponseDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/user/dto/UserV2AvatarUploadResponseDto.kt`

- [ ] **Step 1: 创建 DTO**

```kotlin
// UserV2MeDto.kt
package com.example.bluebook.user.dto

data class UserV2MeDto(
    val id: Long, val phone: String, val nickname: String,
    val avatar: String?, val backgroundImage: String?, val bio: String?,
    val gender: String?, val birthday: String?, val occupation: String?,
    val region: String?, val school: String?,
    val followerCount: Long, val followingCount: Long
)

// UserV2ProfileDto.kt
data class UserV2ProfileDto(
    val id: Long, val nickname: String, val avatar: String?,
    val backgroundImage: String?, val bio: String?, val gender: String?,
    val birthday: String?, val occupation: String?, val region: String?,
    val school: String?, val followerCount: Long, val followingCount: Long,
    val isFollowed: Boolean
)

// UserV2UpdateRequestDto.kt
data class UserV2UpdateRequestDto(
    val nickname: String?, val bio: String?, val gender: String?,
    val birthday: String?, val occupation: String?, val region: String?,
    val school: String?, val backgroundImage: String?
)

// UserV2FollowListResponseDto.kt
data class UserV2FollowListResponseDto(
    val items: List<UserV2ProfileDto>,
    val nextCursorId: Long?
)

// UserV2AvatarUploadResponseDto.kt
data class UserV2AvatarUploadResponseDto(val url: String)
```

- [ ] **Step 2: 创建 UserService.kt**

```kotlin
package com.example.bluebook.user.service

import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.UnauthorizedException
import com.example.bluebook.user.dto.*
import com.example.bluebook.user.entity.UserFollow
import com.example.bluebook.user.repository.UserFollowRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val followRepository: UserFollowRepository,
    private val redisTemplate: StringRedisTemplate
) {
    fun me(userId: Long): UserV2MeDto {
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        val maskedPhone = user.phone.replaceRange(3, 7, "****")
        return UserV2MeDto(
            id = user.id, phone = maskedPhone, nickname = user.nickname,
            avatar = user.avatarUrl, backgroundImage = user.backgroundUrl, bio = user.bio,
            gender = user.gender, birthday = user.birthday?.toString(),
            occupation = user.occupation, region = user.region, school = user.school,
            followerCount = user.followerCount, followingCount = user.followingCount
        )
    }

    @Transactional
    fun updateMe(userId: Long, request: UserV2UpdateRequestDto): UserV2MeDto {
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        request.nickname?.let { user.nickname = it }
        request.bio?.let { user.bio = it }
        request.gender?.let { user.gender = it }
        request.birthday?.let { user.birthday = LocalDate.parse(it) }
        request.occupation?.let { user.occupation = it }
        request.region?.let { user.region = it }
        request.school?.let { user.school = it }
        request.backgroundImage?.let { user.backgroundUrl = it }
        userRepository.save(user)
        // 刷新缓存
        redisTemplate.delete("user:${userId}")
        return me(userId)
    }

    fun profile(userId: Long, currentUserId: Long?): UserV2ProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(11001, "用户不存在") }
        val isFollowed = currentUserId?.let { followRepository.existsByFollowerIdAndFolloweeId(it, userId) } ?: false
        return UserV2ProfileDto(
            id = user.id, nickname = user.nickname, avatar = user.avatarUrl,
            backgroundImage = user.backgroundUrl, bio = user.bio, gender = user.gender,
            birthday = user.birthday?.toString(), occupation = user.occupation,
            region = user.region, school = user.school,
            followerCount = user.followerCount, followingCount = user.followingCount,
            isFollowed = isFollowed
        )
    }

    @Transactional
    fun follow(followerId: Long, followeeId: Long) {
        if (followerId == followeeId) throw BusinessException(14001, "不能关注自己")
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) return
        followRepository.save(UserFollow(followerId = followerId, followeeId = followeeId))
        userRepository.findById(followerId).orElseThrow().let { it.followingCount++; userRepository.save(it) }
        userRepository.findById(followeeId).orElseThrow().let { it.followerCount++; userRepository.save(it) }
        redisTemplate.opsForSet().add("user:following:$followerId", followeeId.toString())
    }

    @Transactional
    fun unfollow(followerId: Long, followeeId: Long) {
        val deleted = followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId)
        if (deleted > 0) {
            userRepository.findById(followerId).orElseThrow().let { it.followingCount--; userRepository.save(it) }
            userRepository.findById(followeeId).orElseThrow().let { it.followerCount--; userRepository.save(it) }
            redisTemplate.opsForSet().remove("user:following:$followerId", followeeId.toString())
        }
    }

    fun followers(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): UserV2FollowListResponseDto {
        // 从 follow 表查 followee_id = userId 的记录
        // 简化实现：直接查 user_follow 表，逐条组装 profile
        val page = PageRequest.of(0, size)
        val users = userRepository.findAllById(
            followRepository.findFolloweeIdsByFollowerId(userId)
        )
        val items = users.map { u ->
            UserV2ProfileDto(
                id = u.id, nickname = u.nickname, avatar = u.avatarUrl,
                backgroundImage = u.backgroundUrl, bio = u.bio, gender = u.gender,
                birthday = u.birthday?.toString(), occupation = u.occupation,
                region = u.region, school = u.school,
                followerCount = u.followerCount, followingCount = u.followingCount,
                isFollowed = currentUserId?.let {
                    followRepository.existsByFollowerIdAndFolloweeId(it, u.id)
                } ?: false
            )
        }
        return UserV2FollowListResponseDto(items = items, nextCursorId = items.lastOrNull()?.id)
    }

    fun following(userId: Long, currentUserId: Long?): UserV2FollowListResponseDto {
        // 类似 followers 实现，查 follower_id = userId
        val users = userRepository.findAllById(
            followRepository.findFolloweeIdsByFollowerId(userId)
        )
        val items = users.map { u -> /* 同上 */ } as List<UserV2ProfileDto>
        return UserV2FollowListResponseDto(items = items, nextCursorId = items.lastOrNull()?.id)
    }
}
```

（注意：Step 2 中的 following() 方法在实施时需补全 UserV2ProfileDto 转换逻辑）

- [ ] **Step 3: 创建 UserController.kt（简洁结构，实施时补全各端点）**

实施时需创建完整的 UserController，包含 `/me` GET, `/me` PUT, `/me/avatar` POST, `/users/{id}` GET, `/users/{id}/follow` POST/DELETE, `/users/{id}/followers` GET, `/users/{id}/following` GET, `/users/{id}/videos` GET。

- [ ] **Step 4: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/user/
git commit -m "添加用户模块：个人资料CRUD、关注取关、粉丝/关注列表"
```

---

## 阶段 7: 文件上传模块

### Task 7.1: 图片上传 + 视频分片上传

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/file/controller/FileController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/service/FileService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/service/ChunkUploadService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/dto/UploadInitRequest.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/dto/UploadInitResponse.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/entity/UploadSession.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/file/repository/UploadSessionRepository.kt`

（该模块代码量较大，实施时按 Task 7.1.1 ~ 7.1.5 拆分为 Subtask：Entity→DTO→Service→Controller→测试）

- [ ] **关键实现要点**：
  1. `POST /api/file/upload/image` — 单次上传，校验类型（魔数）+ 大小
  2. `POST /api/file/upload/init` — 创建 UploadSession，MD5 查重实现秒传
  3. `POST /api/file/upload/chunk` — 分片写入 `{storage-path}/chunks/{uploadId}/{chunkIndex}`，Redis 记录进度
  4. `GET /api/file/upload/progress` — 从 Redis Hash 读取已完成分片列表
  5. `POST /api/file/upload/complete` — 按顺序合并分片 + MD5 校验 + 发 RabbitMQ 转码消息

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/file/
git commit -m "添加文件上传模块：图片上传、视频分片上传/断点续传/秒传"
```

---

## 阶段 8: 视频模块

### Task 8.1: 视频发布 + Feed + 播放 + 点赞收藏

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/video/controller/VideoController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/video/service/VideoService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/video/dto/Video2Dto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/video/dto/FeedResponseDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/video/dto/PublishRequest.kt`

（代码量较大，实施时拆分为 Task 8.1.1 ~ 8.1.6：DTO→Feed→详情→点赞/收藏→Controller→缓存）

- [ ] **关键实现要点**：
  1. `GET /api/v2/feed` — 先从 Redis `feed:latest` List 取 ID 列表，再批量查详情（Cache-Aside），未命中回源 MySQL
  2. `POST /api/v2/videos/{id}/like` — 事务内 INSERT/Delete video_like + UPDATE video.like_count
  3. `POST /api/v2/videos/{id}/collect` — 同上
  4. `GET /api/v2/videos/{id}/playUrl` — 返回 HLS m3u8 地址
  5. `GET /api/v2/videos/{id}/status` — 返回转码状态
  6. `POST /api/v2/videos/publish` — 关联 uploadSession → video 表 → 异步 ES 索引

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/video/
git commit -m "添加视频模块：Feed流、发布、详情、点赞收藏、播放URL、转码状态"
```

---

## 阶段 9: 评论模块

### Task 9.1: 评论 CRUD + 点赞

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/controller/CommentController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/service/CommentService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/dto/CommentDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/dto/CommentListDto.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/comment/dto/PostCommentRequestDto.kt`

（实施时拆分为 Task 9.1.1 ~ 9.1.4）

- [ ] **关键实现要点**：
  1. `GET /api/v1/comments` — 查根评论，游标分页
  2. `GET /api/v1/comments/{id}/replies` — 查某评论的回复列表
  3. `POST /api/v1/comments` — 发表评论 → 事务内 comment_count +1 → 发通知
  4. `DELETE /api/v1/comments/{id}` — 软删除（status=DELETED）
  5. `POST /api/v1/comments/{id}/like` — 点赞计数

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/comment/
git commit -m "添加评论模块：评论发表/删除/回复/点赞"
```

---

## 阶段 10: 通知模块

### Task 10.1: 通知生成与查询

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/notification/controller/NotificationController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/notification/service/NotificationService.kt`

- [ ] **关键实现要点**：
  1. 通知在点赞/评论/关注时由对应 Service 异步生成（`@Async` 或 MQ）
  2. `GET /api/v2/notifications` — 游标分页查 receiver 的通知列表
  3. `GET /api/v2/notifications/unread-count` — `COUNT(*) WHERE is_read=false`
  4. `POST /api/v2/notifications/read-all` — 批量标记已读
  5. `POST /api/v2/notifications/{id}/read` — 单条已读

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/notification/
git commit -m "添加通知模块：通知列表/未读数/已读标记"
```

---

## 阶段 11: 搜索模块（ES）

### Task 11.1: ES 索引与搜索 API

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/search/controller/SearchController.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/search/service/SearchService.kt`
- Create: `backend/src/main/kotlin/com/example/bluebook/search/config/ElasticsearchConfig.kt`

- [ ] **关键实现要点**：
  1. 视频发布/转码完成后 → 异步写 ES 索引
  2. `GET /api/v2/videos/search` — 多字段匹配 + 游标分页（search_after）
  3. `GET /api/v2/search/hot` — Redis SortedSet `hot:search` 取 Top 20
  4. 搜索结果中补充当前用户的 isLike/isCollect 状态

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/search/
git commit -m "添加搜索模块：ES视频搜索、热搜词、索引同步"
```

---

## 阶段 12: 定时任务

### Task 12.1: 计数同步 + 文件清理

**Files:**
- Create: `backend/src/main/kotlin/com/example/bluebook/common/ScheduledTasks.kt`

- [ ] **关键实现要点**：
  1. 每 5 分钟：Redis 播放计数批量同步到 MySQL
  2. 每天凌晨：计数对账（COUNT vs 冗余计数器）
  3. 每天凌晨：清理 7 天前已删除视频的 HLS 切片和原始文件
  4. 每天凌晨：清理过期 UploadSession 和分片临时文件

- [ ] **Step: 验证编译 + 提交**

```bash
./gradlew :backend:compileKotlin
git add backend/src/main/kotlin/com/example/bluebook/common/ScheduledTasks.kt
git commit -m "添加定时任务：播放数同步、计数对账、文件清理"
```

---

## 阶段 13: 转码 Worker

### Task 13.1: FFmpeg 转码脚本

**Files:**
- Create: `backend/src/main/resources/transcode-worker.sh`

- [ ] **关键实现要点**：
  1. RabbitMQ 消费者（Spring Boot `@RabbitListener`）+ 独立 Shell 脚本包装 FFmpeg
  2. 多码率 HLS：1080p + 720p + 480p
  3. 封面截图：`ffmpeg -ss 00:00:01 -i input.mp4 -vframes 1 cover.jpg`
  4. 完成后更新 video 表（API 调用或直接 UPDATE）
  5. 错误处理：重试 3 次 → 标记 FAILED

- [ ] **Step: 验证编译 + 提交**

```bash
git add backend/src/main/resources/transcode-worker.sh
git commit -m "添加FFmpeg转码Worker脚本：HLS多码率转码+封面生成+错误重试"
```

---

## 阶段 14: 集成测试与验证

### Task 14.1: 认证流程集成测试

**Files:**
- Create: `backend/src/test/kotlin/com/example/bluebook/auth/AuthIntegrationTest.kt`

- [ ] **Step 1: 编写注册→登录→刷新→登出测试**

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `should register login refresh and logout`() {
        // 注册
        val registerBody = mapOf("phone" to "13800001111", "password" to "pass123", "nickname" to "测试", "code" to "123456")
        val registerResp = restTemplate.postForEntity("/api/v2/auth/register", registerBody, ApiResponse::class.java)
        assert(registerResp.body?.code == 0)

        // 登录
        val loginResp = restTemplate.postForEntity("/api/v2/auth/login", mapOf("phone" to "13800001111", "password" to "pass123"), ApiResponse::class.java)
        // ...验证 token
    }
}
```

- [ ] **Step 2: 编写视频上传→发布→Feed→搜索测试**

- [ ] **Step 3: 编写评论→回复→删除→点赞测试**

- [ ] **Step 4: 编写关注→粉丝列表→通知测试**

- [ ] **Step 5: 运行全部测试**

```bash
./gradlew :backend:test
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/test/
git commit -m "添加集成测试：认证/视频/评论/用户/通知完整流程"
```

---

## 阶段 15: 部署配置

### Task 15.1: systemd + Nginx 配置文件

**Files:**
- Create: `deploy/nginx.conf`
- Create: `deploy/blue-book.service`
- Create: `deploy/blue-book-transcode.service`

- [ ] **Step 1: 创建 deploy/nginx.conf**

```nginx
server {
    listen 80;
    server_name _;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name _;
    ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    client_max_body_size 100m;

    location /hls/ { alias /data/blue-book/hls/; add_header Access-Control-Allow-Origin *; }
    location /upload/ { alias /data/blue-book/upload/; add_header Access-Control-Allow-Origin *; }
    location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; }
    location /actuator/ { proxy_pass http://127.0.0.1:8080; }
}
```

- [ ] **Step 2: 创建 systemd 服务文件**

```ini
# deploy/blue-book.service
[Unit]
Description=BlueBook Backend
After=network.target

[Service]
User=bluebook
WorkingDirectory=/opt/blue-book
ExecStart=/usr/bin/java -Xmx2g -Xms2g --enable-preview -jar backend.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

- [ ] **Step 3: 提交**

```bash
git add deploy/
git commit -m "添加部署配置：Nginx反向代理、systemd服务、SSL配置"
```

---

## 实施顺序与依赖

```
阶段1  项目骨架 ← 开始
阶段2  公共基础设施
阶段3  DB实体+Repository
  ↓
阶段4  安全模块 ─────────┐
  ↓                     │
阶段5  认证模块 ←───────┘ 可并行
  ↓
阶段6  用户模块 ────┐
阶段7  文件上传 ────┤ 可并行
  ↓                │
阶段8  视频模块 ←───┘ 依赖6,7
  ↓
阶段9  评论模块 ────┐
阶段10 通知模块 ────┤ 可并行
阶段11 搜索模块 ────┘
  ↓
阶段12 定时任务
阶段13 转码Worker ←── 依赖7,8
  ↓
阶段14 集成测试
阶段15 部署配置 ←── 最后
```

## 预计工作量

| 阶段 | 内容 | 预估 Task 数 | 预估时间 |
|------|------|-------------|---------|
| 1-3 | 骨架+实体 | 15 | 1 天 |
| 4-5 | 安全+认证 | 12 | 1 天 |
| 6-7 | 用户+文件 | 10 | 1.5 天 |
| 8 | 视频模块 | 8 | 1.5 天 |
| 9-11 | 评论+通知+搜索 | 12 | 1.5 天 |
| 12-13 | 定时任务+转码 | 5 | 0.5 天 |
| 14-15 | 测试+部署 | 8 | 1 天 |
| **合计** | | **~70 Task** | **~8 天** |
