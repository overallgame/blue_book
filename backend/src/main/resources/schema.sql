-- 小蓝书数据库初始化脚本
-- 执行：mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS blue_book DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE blue_book;

CREATE TABLE `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) UNIQUE NOT NULL,
    xhs_id VARCHAR(20) UNIQUE,
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
    -- 转码专用时间戳：只由转码路径写入。不能复用 updated_at —— 点赞/播放/评论都会把
    -- updated_at 顶掉（MySQL ON UPDATE CURRENT_TIMESTAMP），于是卡死的任务永远不"过期"。
    -- 历史行可为 NULL，判定时回退到 created_at。
    transcode_updated_at DATETIME,
    duration INT,
    width INT,
    height INT,
    file_size BIGINT,
    like_count BIGINT DEFAULT 0,
    collect_count BIGINT DEFAULT 0,
    comment_count BIGINT DEFAULT 0,
    view_count BIGINT DEFAULT 0,
    region VARCHAR(100),
    status ENUM('PUBLISHED','DELETED','REVIEWING') DEFAULT 'PUBLISHED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_uploader_created (uploader_id, created_at),
    INDEX idx_feed (status, created_at),
    INDEX idx_region (region, status, created_at)
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
    PRIMARY KEY (user_id, video_id),
    -- 主键以 user_id 为前导列，按 video_id 聚合（每日对账的 COUNT(*)）用不上，
    -- 缺这个索引时对账要对 video_like 做全表扫描
    INDEX idx_video_id (video_id)
) ENGINE=InnoDB;

CREATE TABLE video_collect (
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_id),
    INDEX idx_video_id (video_id)
) ENGINE=InnoDB;

CREATE TABLE comment_like (
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, comment_id)
) ENGINE=InnoDB;

CREATE TABLE user_follow (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    -- 主键以 follower_id 为前导列，按 followee_id 聚合（每日对账的 COUNT(*)）用不上
    INDEX idx_followee_id (followee_id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    -- 新增值必须追加在末尾：ENUM 的顺序决定内部序号，
    -- 插在中间会触发整表重建（ALGORITHM=COPY）来重排序号
    type ENUM('LIKE','COMMENT','FOLLOW','SYSTEM','COLLECT'),
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
    `rank` INT DEFAULT 0,
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
    -- 本次会话的分片大小。服务端必须记住它：合并是按序号硬拼的，
    -- 前后分片大小不一致会拼出"长度对、内容错"的文件，所以续传时要靠它判断契约是否变了
    chunk_size BIGINT,
    status ENUM('UPLOADING','MERGING','DONE','EXPIRED'),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────────────
-- 已部署数据库的升级语句（生产用 ddl-auto=validate，不改库会启动失败）
--
-- 全新安装不需要执行（上面的 CREATE TABLE 已包含新列）。
-- 如果是从旧版本升级，执行：
--
--   ALTER TABLE upload_session ADD COLUMN chunk_size BIGINT;
--   ALTER TABLE upload_session DROP COLUMN video_id;
--
-- 注意：旧的 UPLOADING 会话 chunk_size 为 NULL。服务端在续传匹配时把 NULL 视为
-- "与当前分片契约不一致"，会作废并重建会话（客户端表现为从头重传一次，不会出错）。
-- DONE 的会话不受影响——它们的会话行只在合并时用过，秒传只看 file_md5。
-- ─────────────────────────────────────────────────────────────────────────────
