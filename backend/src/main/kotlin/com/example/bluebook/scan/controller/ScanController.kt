package com.example.bluebook.scan.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.scan.dto.ScanResolveDto
import com.example.bluebook.scan.service.ScanService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 扫一扫的校验接口。
 *
 * `payload` 是**扫码得到的原始字符串**，不是我方解析后的 id（设计方案 6.2）：
 * 让服务端当"算不算站内码"的唯一裁判，否则判据就落在客户端了。
 *
 * 权限：与 `/api/v2/feed`、`/videos/{id}/dto` 同级——游客可扫（见 `SecurityConfig`）。
 * `JwtAuthFilter` 对所有 GET 走可选鉴权，因此带 token 时会带上身份，
 * 只是本期返回体里没有任何"针对当前用户"的字段，用不上而已。
 */
@RestController
@RequestMapping("/api/v2/scan")
class ScanController(
	private val scanService: ScanService
) {

	@GetMapping("/resolve")
	fun resolve(@RequestParam payload: String): ApiResponse<ScanResolveDto> =
		ApiResponse.ok(scanService.resolve(payload))
}
