#!/bin/bash
# 小蓝书视频转码脚本
# 用法: ./transcode-worker.sh <input_file> <output_dir> <video_id>
# 输出: HLS (multi-bitrate) + cover thumbnail

INPUT=$1
OUTPUT_DIR=$2
VIDEO_ID=$3

if [ -z "$INPUT" ] || [ -z "$OUTPUT_DIR" ] || [ -z "$VIDEO_ID" ]; then
    echo "用法: $0 <input_file> <output_dir> <video_id>"
    exit 1
fi

mkdir -p "$OUTPUT_DIR/$VIDEO_ID"

# Generate cover thumbnail at 1s
ffmpeg -y -ss 00:00:01 -i "$INPUT" -vframes 1 -q:v 2 \
    "$OUTPUT_DIR/$VIDEO_ID/cover.jpg" 2>/dev/null

# 1080p
ffmpeg -y -i "$INPUT" \
    -c:v libx264 -preset fast -crf 23 -b:v 4000k \
    -c:a aac -b:a 128k \
    -vf "scale='min(1920,iw)':-2" \
    -hls_time 10 -hls_list_size 0 -hls_segment_filename "$OUTPUT_DIR/$VIDEO_ID/1080p_%03d.ts" \
    "$OUTPUT_DIR/$VIDEO_ID/1080p.m3u8" 2>/dev/null

# 720p
ffmpeg -y -i "$INPUT" \
    -c:v libx264 -preset fast -crf 23 -b:v 2000k \
    -c:a aac -b:a 128k \
    -vf "scale='min(1280,iw)':-2" \
    -hls_time 10 -hls_list_size 0 -hls_segment_filename "$OUTPUT_DIR/$VIDEO_ID/720p_%03d.ts" \
    "$OUTPUT_DIR/$VIDEO_ID/720p.m3u8" 2>/dev/null

# 480p
ffmpeg -y -i "$INPUT" \
    -c:v libx264 -preset fast -crf 23 -b:v 800k \
    -c:a aac -b:a 128k \
    -vf "scale='min(854,iw)':-2" \
    -hls_time 10 -hls_list_size 0 -hls_segment_filename "$OUTPUT_DIR/$VIDEO_ID/480p_%03d.ts" \
    "$OUTPUT_DIR/$VIDEO_ID/480p.m3u8" 2>/dev/null

# Master playlist
cat > "$OUTPUT_DIR/$VIDEO_ID/master.m3u8" << EOF
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
1080p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
720p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480
480p.m3u8
EOF

echo "{\"videoId\":\"$VIDEO_ID\",\"status\":\"DONE\",\"hlsUrl\":\"$VIDEO_ID/master.m3u8\"}"
exit 0
