package com.example.blue_book.ui.scan

import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.domain.model.ScannedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扫码结果 → 播放页卡片的映射。
 *
 * 这是"哪个字段进哪个槽"的知识：写错了**不崩、不报错**，只是页面内容错位
 * （标题显示成作者名、封面不显示），靠真机肉眼很难发现。
 */
class ScanNavigationTest {

	private fun content(
		kind: ContentKind = ContentKind.VIDEO,
		targetId: Long = 42L,
		title: String = "视频标题",
		subtitle: String? = "作者昵称",
		cover: String? = "http://host/hls/cover.jpg"
	) = ScannedContent(kind = kind, targetId = targetId, title = title, subtitle = subtitle, cover = cover)

	@Test
	fun `maps scanned video into player card`() {
		val card = content().toVideoCardInfo()

		assertEquals("aid 是跳转与播放的主键", 42L, card.aid)
		assertEquals("标题进 description（播放页按它渲染标题与话题标签）", "视频标题", card.description)
		assertEquals("作者昵称进 nickname", "作者昵称", card.nickname)
		assertEquals("封面进 image", "http://host/hls/cover.jpg", card.image)
	}

	@Test
	fun `leaves personal state at defaults`() {
		// 扫码没有"当前用户对这条视频的态度"信息，不能编一个（编 true 会让用户看到
		// 自己没点过的赞，再点一次反而取消）
		val card = content().toVideoCardInfo()

		assertEquals(false, card.isLike)
		assertEquals(false, card.isCollect)
		assertEquals("计数留 0：目标页自己会拉真实数据", 0, card.like)
		assertEquals(0, card.collection)
		assertEquals(0, card.commentCount)
	}

	@Test
	fun `leaves play url empty so the player self heals`() {
		// resolve 契约里没有播放地址（设计方案 6.2）。留空是**有意的**：
		// VideoAdapter 见 playUrl 为空即触发 onRequestPlayUrl 自己去取，
		// 所以播放不受影响——这条用例把这个依赖钉住，免得将来有人"顺手"填个假地址
		val card = content().toVideoCardInfo()

		assertEquals("", card.playUrl)
	}

	@Test
	fun `leaves uploader unknown instead of guessing`() {
		// uploaderId 不在 resolve 契约里 → 播放页的"点头像进作者主页"不会跳（它判断 uploaderId > 0）。
		// 这是一个已知的、被记录过的缺口（设计方案 6.2）；填一个是伪造的 id 会跳到别人的主页
		val card = content().toVideoCardInfo()

		assertEquals(0L, card.uploaderId)
		assertEquals("", card.avatar)
	}

	@Test
	fun `tolerates missing subtitle and cover`() {
		val card = content(subtitle = null, cover = null).toVideoCardInfo()

		assertEquals("没有副标题就空着，不该崩", "", card.nickname)
		assertEquals("没有封面就空着（Glide 显示占位）", "", card.image)
		assertEquals("标题仍然要在", "视频标题", card.description)
	}

	@Test
	fun `cid is zero not derived from aid`() {
		// 后端 getPlayUrl 忽略 cid（只按 aid 取地址），VideoMappers 也传 0。
		// 造一个"aid*10"之类的假关联 id 会被服务端当成另一个实体，是纯粹的自找麻烦
		val card = content(targetId = 7L).toVideoCardInfo()

		assertEquals(0L, card.cid)
		assertTrue("cid 不能从 aid 猜出来", card.cid != card.aid)
	}
}
