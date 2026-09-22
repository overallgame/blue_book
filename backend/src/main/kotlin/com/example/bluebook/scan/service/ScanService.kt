package com.example.bluebook.scan.service

import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.NotBlueBookCodeException
import com.example.bluebook.common.ScanContentForbiddenException
import com.example.bluebook.common.ScanTargetNotFoundException
import com.example.bluebook.common.assetUrl
import com.example.bluebook.scan.ScanCode
import com.example.bluebook.scan.ScanCodeFormat
import com.example.bluebook.scan.dto.ScanResolveDto
import com.example.bluebook.scan.dto.ScanTargetType
import com.example.bluebook.video.entity.VideoStatus
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.stereotype.Service

/**
 * 扫一扫的解析：把一段**未经信任的**字符串变成"该跳到哪里、怎么展示"。
 *
 * 三步，顺序不能换：
 * 1. 判格式（[ScanCodeFormat]）——不是我们的码直接 400，不查库（省掉畸形输入的库开销）
 * 2. 查内容——不存在 → 404；存在但不可见 → 403
 * 3. 组装展示信息
 *
 * **不含权限/登录判断**：游客也能扫（与 `/api/v2/feed`、`/videos/{id}/dto` 同一策略）。
 * 本期返回的字段里没有任何"针对当前用户"的状态（是否点赞/关注），因此不需要 currentUserId——
 * 一旦将来要加，它必须是**显式参数**，而不是让 controller 读 SecurityContext 传进来。
 */
@Service
class ScanService(
	private val videoRepository: VideoRepository,
	private val userRepository: UserRepository
) {

	fun resolve(payload: String): ScanResolveDto {
		val code = ScanCodeFormat.parse(payload) ?: throw NotBlueBookCodeException()
		return when (code) {
			is ScanCode.Video -> resolveVideo(code.aid)
			is ScanCode.User -> resolveUser(code.id)
		}
	}

	private fun resolveVideo(aid: Long): ScanResolveDto {
		// 不用 findByIdAndStatus(id, PUBLISHED)：那样 DELETED 与 REVIEWING 会合并成同一种结果，
		// 而它们在语义上是两件事（"没了" 对 "有但不可见"），客户端也要给出不同的提示。
		val video = videoRepository.findById(aid).orElse(null)
			?: throw ScanTargetNotFoundException("视频不存在或已被删除")

		when (video.status) {
			VideoStatus.PUBLISHED -> Unit
			// 当前没有审核流（publish 直接落 PUBLISHED），所以这个分支实际不可达。
			// 仍然写出来：VideoStatus 有这个取值，沉默地按 404 处理是错的，
			// 而"将来加了审核就自动正确"比"到时候再想起来"便宜。
			VideoStatus.REVIEWING -> throw ScanContentForbiddenException()
			VideoStatus.DELETED -> throw ScanTargetNotFoundException("视频不存在或已被删除")
		}

		return ScanResolveDto(
			type = ScanTargetType.VIDEO,
			targetId = video.id,
			// 与 VideoMappers（客户端）一致：标题为空时退回描述，避免扫出来是一张没有文字的卡片
			title = video.title?.takeIf { it.isNotBlank() } ?: video.description.orEmpty(),
			subtitle = userRepository.findById(video.uploaderId).orElse(null)?.nickname,
			cover = assetUrl(video.coverUrl, "hls")
		)
	}

	private fun resolveUser(id: Long): ScanResolveDto {
		val user = userRepository.findById(id).orElse(null)
			?: throw ScanTargetNotFoundException("用户不存在")

		return ScanResolveDto(
			type = ScanTargetType.USER,
			targetId = user.id,
			title = user.nickname,
			// 简介优先，没有再退回小红书号；xhsId 是懒生成的，这里不触发生成（读接口不该写库）
			subtitle = user.bio?.takeIf { it.isNotBlank() } ?: user.xhsId,
			cover = assetUrl(user.avatarUrl, "upload/images")
		)
	}
}
