package com.example.blue_book.scan

/**
 * 站内码的格式契约：**编 + 解，纯函数，无 Android / 网络依赖**。
 *
 * ## 为什么放在 lib-base 而不是 feature-scan
 *
 * 站内码有两个**不同模块的消费方**：生成方是 `feature-video` 的分享按钮（R7），
 * 解析方是 `feature-scan`。各自实现必然漂移——而这个项目已经被同类问题咬过三次：
 * 主题色板 6 份（`feature-message` 的值静默失效）、样式 3 份（靠注释提醒人手同步）、
 * `default_avatar` 3 份（其中一份是完全不同的图）。
 *
 * 码格式比它们更危险：色板漂移只是"颜色不对"，**码格式漂移是"自己分享出去的码自己扫不出来"**，
 * 而且只有真机上扫了才会发现。所以编解码整体收敛到一处，与 `RoutePath`（路径契约）、
 * `ExtraKeys`（参数键契约）并列——性质相同，都是跨模块的字符串契约。
 *
 * ## ★ host 为什么不取 ApiGateway.BASE_URL
 *
 * 那个值现在是 `http://192.168.17.128:8080/`——**私有网段 IP + 明文 HTTP**。把它印进二维码：
 *
 * 1. 私有地址（RFC 1918）等于把内网拓扑发出去，且任何外部设备扫到必然是死链
 * 2. **它会变**（换虚拟机 / 换局域网 / 换端口），而码是**会离开设备**的东西（分享到微信等），
 *    生命周期比部署地址长得多
 * 3. 明文 HTTP 与"对外可分享的正式链接"这个语义不符
 *
 * 所以：**码里的 host 是契约**（用户手里有实物，改了就是灾难），**BASE_URL 是实现细节**（换环境就变）。
 * 两类不同变化原因的东西必须解耦，这里用一个独立常量 + 白名单来隔离。
 *
 * ## 安全边界
 *
 * [parse] 是**完全不可信外部数据**的入口（任何人都能生成任意二维码），因此：
 * - 只认 http(s)，其它 scheme 一律按纯文本处理（UI 对纯文本不提供"打开"入口 → 防"扫码即执行"）
 * - host 必须**精确命中**白名单，不做子域/后缀匹配（`bluebook.invalid.evil.com` 必须判为非站内码）
 * - 手写正则而不是用 `java.net.URI`/`URL`：这是安全边界，需要完全可控、可穷举的判定，
 *   而 URI 对畸形输入的抛异常/宽松归一化行为不易枚举；顺带也保证了纯 JVM 可测
 */
object ScanCodeFormat {

	/**
	 * 码里使用的 host。
	 *
	 * 本期取 **RFC 2606 保留的 `.invalid` 顶级域**：它**保证永不解析**，因此
	 * ① 不会被别人注册后拿来服务恶意内容 ② 不会让人误以为"点了能打开"。
	 *
	 * 将来有了真域名：改这里，并把新域名**追加**进 [ACCEPTED_HOSTS]——**已发出的老码继续有效**。
	 * 这正是把 host 从部署地址里独立出来的收益。
	 */
	const val CODE_HOST: String = "bluebook.invalid"

	/** 解析时接受的 host 白名单。★ **只加不删**（删掉等于让已发出的码失效）。 */
	private val ACCEPTED_HOSTS: List<String> = listOf(CODE_HOST)

	private const val SCHEME = "https"

	/** 路径段：与后端约定一致。生成侧用它拼、解析侧不校验它（见 [parse] 注释）。 */
	private const val PATH_VIDEO = "v"
	private const val PATH_USER = "u"
	private const val PATH_LOGIN = "qr"

	/**
	 * 超长 payload 直接按纯文本处理：二维码可以塞进几 KB 内容，而我们关心的格式都很短。
	 * 设上限是为了不为畸形输入做无谓的解析。
	 */
	private const val MAX_PAYLOAD_LENGTH = 2048

	/**
	 * 严格匹配 http(s) URL。分组：1=scheme，2=host，3=port，4=path。
	 *
	 * host 用 `[^/?#:]+` 而不是"合法域名"正则：**故意不在这里判域名合法性**。
	 * 白名单命中的才是站内码，其余一律外部链接——把"像不像域名"这种判断留空，
	 * 反而没有"某个畸形 host 恰好通过了域名校验又被误判为站内码"的风险。
	 */
	private val HTTP_URL = Regex(
		"^(https?)://([^/?#:]+)(?::(\\d+))?([^?#]*)(?:\\?[^#]*)?(?:#.*)?$",
		RegexOption.IGNORE_CASE
	)

	/** 登录票据路径：`/qr/{ticket}`。票据字符集是后端契约，这里取保守的 token 字符集。 */
	private val LOGIN_PATH = Regex("^/$PATH_LOGIN/([A-Za-z0-9._~-]+)/?$")

	/** 分享视频时用它生成链接。**必须与 [parse] 接受的格式严格对应**。 */
	fun videoUrl(aid: Long): String {
		require(aid > 0) { "视频 aid 必须为正数，实际为 $aid" }
		return "$SCHEME://$CODE_HOST/$PATH_VIDEO/$aid"
	}

	/** 分享用户主页时用它生成链接。 */
	fun userUrl(id: Long): String {
		require(id > 0) { "用户 id 必须为正数，实际为 $id" }
		return "$SCHEME://$CODE_HOST/$PATH_USER/$id"
	}

	/**
	 * 把扫码结果归类。**不做网络、不判内容是否可见**——只回答"这是哪一类"。
	 * 真伪与可见性交服务端（`/scan/resolve`），这样纯函数可以穷举测试，
	 * 且不因后端策略变化而改动。
	 *
	 * **不在本地校验路径结构**（除了登录码）：host 命中白名单就交给服务端裁定。
	 * 这样后端将来加新格式（比如 `/t/{id}`）时，老版本客户端也能正确工作——
	 * 与"resolve 传原始字符串、让服务端当唯一裁判"是同一个思路。
	 * 唯一必须本地认出来的是登录码：它不走 resolve。
	 */
	fun parse(payload: String): ScanTarget {
		val text = payload.trim()
		if (text.isEmpty() || text.length > MAX_PAYLOAD_LENGTH) {
			return ScanTarget.PlainText(payload)
		}

		val match = HTTP_URL.matchEntire(text) ?: return ScanTarget.PlainText(payload)
		val host = match.groupValues[2].lowercase()
		val path = match.groupValues[4]

		// 白名单之外的 host 一律外部链接。注意这里**不做后缀/子域匹配**：
		// bluebook.invalid.evil.com、a.bluebook.invalid 都会走到这个分支。
		if (host !in ACCEPTED_HOSTS) return ScanTarget.ExternalUrl(text)

		val ticket = LOGIN_PATH.matchEntire(path)?.groupValues?.get(1)
		return if (ticket != null) ScanTarget.LoginTicket(ticket) else ScanTarget.InternalCode(text)
	}
}
