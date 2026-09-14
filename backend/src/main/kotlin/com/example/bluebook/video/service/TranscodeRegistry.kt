package com.example.bluebook.video.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 本进程内**正在执行**的转码任务 id。
 *
 * 存在的理由：`transcodeStatus = PROCESSING` 有两种完全相反的含义——
 *  - worker 正在跑：**绝不能重投**，否则第二个 ffmpeg 会用 `-y` 覆写同一组 HLS 切片，
 *    两个进程交错写，最终谁先退出 0 谁把它标成 DONE，产出损坏的播放列表；
 *  - worker 已被杀（进程重启/崩溃）：**必须重投**，否则视频永远停在 PROCESSING。
 *
 * 仅凭数据库状态无法区分这两者。加上本进程的运行时记录就可以了：
 * 在集合里 ⇒ 真在跑；不在集合里且已超过阈值 ⇒ 已死。
 *
 * 单实例部署下这是充分判据。进程重启会丢失记录，此时可能对仍在运行的**孤儿 ffmpeg**
 * 重复投递一次，所以 `ScheduledTasks` 的阈值取得远大于任何合理的转码耗时。
 */
@Component
class TranscodeRegistry {
    private val running: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun markRunning(videoId: Long) {
        running.add(videoId)
    }

    fun markFinished(videoId: Long) {
        running.remove(videoId)
    }

    fun isRunning(videoId: Long): Boolean = videoId in running
}
