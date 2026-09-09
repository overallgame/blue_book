package com.example.blue_book.router

/**
* TheRouter 路由路径常量
* 格式：/模块/页面
*/
object RoutePath {
	/** 主页面（底部 Tab 容器） */
	const val MAIN = "/app/main"

	/** 首页 */
	const val HOME = "/home/main"

	/** 视频页 */
	const val VIDEO = "/video/main"

	/** 视频发布页（独立入口，底部导航栏正中） */
	const val PUBLISH = "/video/publish"

	/** 消息页 */
	const val MESSAGE = "/message/main"

	/** 我的页 */
	const val MINE = "/mine/main"

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
}
