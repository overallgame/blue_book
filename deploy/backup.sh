#!/usr/bin/env bash
#
# BlueBook 备份脚本
# 数据库凭据走 ~/.my.cnf（chmod 600），不要写进本脚本，也不要提交到仓库。
# 建议由 blue-book-backup.timer 每天调用一次。
#
set -euo pipefail

STAMP=$(date +%Y%m%d-%H%M)
DEST=/data/backup/blue-book          # 必须在另一块盘/另一个挂载点上
SRC_UPLOAD=/data/blue-book/upload
SRC_HLS=/data/blue-book/hls
KEEP_DAYS=14

mkdir -p "$DEST"

# 数据库全量导出（--single-transaction 保证不锁表）
mysqldump --single-transaction --routines blue_book | gzip > "$DEST/blue_book-$STAMP.sql.gz"

# 上传的原始视频与 HLS 产物：体积大，用递增同步而非全量拷贝
if [ -d "$SRC_UPLOAD" ]; then
    rsync -a --delete "$SRC_UPLOAD/" "$DEST/upload/"
fi
if [ -d "$SRC_HLS" ]; then
    rsync -a --delete "$SRC_HLS/" "$DEST/hls/"
fi

# 只保留最近 KEEP_DAYS 天的 SQL 导出
find "$DEST" -name '*.sql.gz' -mtime +"$KEEP_DAYS" -delete

# 备份后校验：能读出内容才算成功（空文件或损坏的 gzip 会被 gzip -t 发现）
gzip -t "$DEST/blue_book-$STAMP.sql.gz"

SIZE=$(du -h "$DEST/blue_book-$STAMP.sql.gz" | cut -f1)
echo "[$(date '+%F %T')] backup ok: $DEST/blue_book-$STAMP.sql.gz ($SIZE)"
