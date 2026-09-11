#!/usr/bin/env bash
#
# BlueBook 备份脚本
# 数据库凭据走 ~/.my.cnf（chmod 600），不要写进本脚本，也不要提交到仓库。
# 建议由 blue-book-backup.timer 每天调用一次。
#
set -euo pipefail

STAMP=$(date +%Y%m%d-%H%M)
DEST=${DEST:-/data/backup/blue-book}          # 必须在另一块盘/另一个挂载点上

# !!! 部署前必须核对的媒体路径 !!!
# 代码里目前存在矛盾，两边不一致：
#   - 应用侧：application.yml 的 app.upload.storage-path 默认 ./upload，
#     相对 systemd 的 WorkingDirectory=/opt/blue-book，即 /opt/blue-book/upload；
#     TranscodeConsumer 也硬编码读取 /opt/blue-book/upload、输出 /opt/blue-book/hls。
#   - nginx.conf：alias 指向 /data/blue-book/upload 与 /data/blue-book/hls。
# 二者只能是同一目录（符号链接/绑定挂载），或其中一处写错了。
# 下面按 nginx 侧的路径取值；若实际是 /opt/blue-book/*，用环境变量覆盖（SRC_UPLOAD=... SRC_HLS=...）或直接改这里。
SRC_UPLOAD=${SRC_UPLOAD:-/data/blue-book/upload}
SRC_HLS=${SRC_HLS:-/data/blue-book/hls}
KEEP_DAYS=${KEEP_DAYS:-14}

mkdir -p "$DEST"

# 显式校验：媒体目录不存在就直接失败，避免"备份成功但什么都没备"这种静默失败
for d in "$SRC_UPLOAD" "$SRC_HLS"; do
    if [ ! -d "$d" ]; then
        echo "[$(date '+%F %T')] ERROR 媒体目录不存在: $d —— 请先核对脚本顶部的路径说明" >&2
        exit 1
    fi
done

# 数据库全量导出（--single-transaction 保证不锁表）
mysqldump --single-transaction --routines blue_book | gzip > "$DEST/blue_book-$STAMP.sql.gz"

# 上传的原始视频与 HLS 产物：体积大，用递增同步而非全量拷贝
rsync -a --delete "$SRC_UPLOAD/" "$DEST/upload/"
rsync -a --delete "$SRC_HLS/" "$DEST/hls/"

# 只保留最近 KEEP_DAYS 天的 SQL 导出
find "$DEST" -name '*.sql.gz' -mtime +"$KEEP_DAYS" -delete

# 备份后校验：能读出内容才算成功（空文件或损坏的 gzip 会被 gzip -t 发现）
gzip -t "$DEST/blue_book-$STAMP.sql.gz"

SQL_SIZE=$(du -h "$DEST/blue_book-$STAMP.sql.gz" | cut -f1)
MEDIA_COUNT=$(find "$DEST/upload" "$DEST/hls" -type f 2>/dev/null | wc -l)
echo "[$(date '+%F %T')] backup ok: $DEST/blue_book-$STAMP.sql.gz ($SQL_SIZE), 媒体文件 $MEDIA_COUNT 个"
