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
    chunk_size INT,
    status ENUM('UPLOADING','MERGING','DONE','EXPIRED'),
    video_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
