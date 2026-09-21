package com.example.blue_book.router

/**
* TheRouter 路由路径常量
* 格式：/模块/页面
*
* 注意：底部四个 Tab（首页/视频/消息/我的）**不再是路由**。
* 它们已从「各自一个 Activity」改为 MainActivity 内的 Fragment，
* 切换走 [com.example.blue_book.host.IMainHost]，路由表中也不再登记这四条路径。
*/
object RoutePath {
	/** 主页面（底部 Tab 容器） */
	const val MAIN = "/app/main"

	/** 视频发布页（独立入口，底部导航栏正中） */
	const val PUBLISH = "/video/publish"

	/**
	 * 播放页（独立页面）。
	 *
	 * 与底部导航的「视频」Tab 是两件事：视频 Tab 是沉浸式播放 Feed，从底部导航进入、
	 * 返回即回首页；本页面是**从其它页面点某个视频时压在上层的播放页**，返回时回到
	 * 点击它的那个页面（搜索结果、消息、我的列表、作者主页……）。入口统一走 [openVideoPlayer]。
	 */
	const val VIDEO_PLAYER = "/video/player"

	/** 搜索页（搜索页 + 搜索结果页，两者是本页面内的 Fragment 前后栈） */
	const val SEARCH = "/home/search"

	/** 资料编辑页（资料页 + 单字段编辑页，两者是本页面内的 Fragment 前后栈） */
	const val PROFILE_EDIT = "/mine/profile_edit"

	/** 作者主页（他人用户主页） */
	const val USER_PROFILE = "/mine/user_profile"

	/** 关注/粉丝列表页 */
	const val FOLLOW_LIST = "/mine/follow_list"

	/** 登录/注册入口 */
	const val AUTH = "/auth/entry"

	/** 登录页 */
	const val LOGIN = "/auth/login"

	/** 注册页 */
	const val REGISTER = "/auth/register"

	/** 图片选择器 */
	const val IMAGE_PICKER = "/image/picker"

	/** 扫一扫（独立入口，我的页顶部图标进入） */
	const val SCAN = "/scan/entry"
}

/**
 * 跨模块 Intent/Bundle 参数键常量
 */
object ExtraKeys {
	const val EXTRA_VIDEO = "EXTRA_VIDEO"
	const val EXTRA_TAG = "TAG_SHOW"
	const val EXTRA_KEYWORD = "keyword"

	/** 播放页来源标记：search=搜索结果，liked=点赞列表，collected=收藏列表，user_videos=作品列表 */
	const val EXTRA_SOURCE = "EXTRA_SOURCE"

	/** 作品列表对应的用户 id（user_videos 来源模式使用） */
	const val EXTRA_SOURCE_USER_ID = "EXTRA_SOURCE_USER_ID"

	/** 作者主页的用户 id */
	const val EXTRA_USER_ID = "EXTRA_USER_ID"

	/** 关注列表类型：following=我关注的，followers=我的粉丝 */
	const val EXTRA_FOLLOW_TYPE = "EXTRA_FOLLOW_TYPE"

	/**
	 * 图片选择器回传的调用方标记（`ImagePickerActivity` 原样回传，调用方据此区分"这张图是干嘛用的"）。
	 *
	 * 收进这里是因为它原本是三处硬编码的字面量 `"tag"`（MineFragment 的传参与回读、
	 * ImagePickerActivity 的 EXTRA_TAG），靠"字面量恰好相同"维系——改一处不改另一处不会报错，
	 * 只会静默拿不到结果。
	 */
	const val EXTRA_IMAGE_TAG = "tag"

	/**
	 * 图片选择器：为 true 时**跳过裁剪页**，选中后直接回传原图。
	 *
	 * 给"要的是原图本身"的调用方用（扫码就是——裁剪既多一步，又可能把二维码裁坏或重压缩）。
	 * 默认 false，即保持既有行为（选图 → 裁剪 → 回传），现有调用方不受影响。
	 */
	const val EXTRA_SKIP_CROP = "EXTRA_SKIP_CROP"
}
