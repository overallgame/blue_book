package com.example.blue_book.util

/**
 * 把服务端下发的**相对资源路径**拼成可直接加载的绝对地址。
 *
 * 服务端只存文件的相对路径（如 `2026-09-22/uuid.jpg`），下发时补上访问前缀变成
 * `/hls/...`、`/upload/images/...`（见后端 `AssetUrls.kt`），最后一段由客户端补 host：
 * 只有客户端知道当前的 `BASE_URL`（它可以在运行时被切换）。
 *
 * 三条规则，与后端 `assetUrl` 对称：
 * - 空/null → null（"没有这张图"和"图地址是空串"不该被混成同一个结果，调用方自行 `?: ""`）
 * - 已经是 `http(s)://` 的**绝对地址原样返回**（服务端有时直接下发完整外链）
 * - 以 `/` 开头直接接 host，否则补一个 `/`
 *
 * **为什么放在 lib-base**：这条规则此前在 4 个 mapper 里各写了一份局部函数
 * （feature-auth / feature-mine ×2 / feature-video ×2），扫码的数据层需要第 5 份——
 * 与其再抄一遍，不如给出一条共享实现。纯函数、零依赖，可纯 JVM 单测。
 *
 * @param baseUrl 当前服务地址，如 `http://192.168.17.128:8080/`（尾部斜杠可有可无）
 */
fun absoluteUrl(baseUrl: String, path: String?): String? {
	val value = path?.trim().orEmpty()
	if (value.isBlank()) return null
	if (value.startsWith("http://") || value.startsWith("https://")) return value
	val base = baseUrl.trimEnd('/')
	return if (value.startsWith("/")) "$base$value" else "$base/$value"
}
