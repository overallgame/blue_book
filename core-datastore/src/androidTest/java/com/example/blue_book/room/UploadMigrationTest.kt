package com.example.blue_book.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionStatus
import com.example.blue_book.room.entity.UploadPartEntity
import com.example.blue_book.room.entity.UploadSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MIGRATION_2_3` / `MIGRATION_3_4` 的真实性验证——**在真机上造一个 v2 库，让 Room 自己跑迁移并校验结果**。
 *
 * ## 为什么这条必须有
 *
 * [AppDatabase.MIGRATION_2_3] 里的建表语句是**手写的 SQL**，而 Room 会拿实体推导出的期望 schema
 * 去比对迁移结果。两者只要有一处对不上（列名、类型、NOT NULL、主键组成），抛的是
 * `IllegalStateException: Migration didn't properly handle ...`——而这发生在**用户设备上
 * 第一次启动时**：一升级就崩，且没有回退。纯 JVM 单测跑不出来（Room 需要真 SQLite），
 * 这就是本项目第一条仪器测试存在的理由。
 *
 * ## 为什么不加 room-testing、不导出 schema
 *
 * 标准的 `MigrationTestHelper` 需要**导出的 schema JSON**（`exportSchema = true` +
 * `room.schemaLocation`）才能 `createDatabase(name, 2)`；而本项目 1、2 版从未导出过，补不出来。
 * 这里换个更直接的做法：**手工造一个 v2 库**（`user` 表的形状抄自 `UserEntity`——v2 就等于当前实体），
 * 然后**用 Room 正常打开它**：Room 会执行迁移并校验 schema，任何不一致都会在第一次查询时抛出来。
 * 不引新依赖、不产生 `schemas/` 目录。
 */
@RunWith(AndroidJUnit4::class)
class UploadMigrationTest {

	private val context: Context = ApplicationProvider.getApplicationContext()
	private val dbName = "upload-migration-test.db"
	private val uri = "content://media/external/video/media/42"

	private companion object {
		/**
		 * v2 的 `user` 表：列与 `UserEntity` 一一对应（v1→v2 只是多了 `is_followed`）。
		 * 迁移只新建两张表、不碰它，所以这里形状写错会表现为"迁移后校验失败"——
		 * 那正是要避免的假通过。
		 */
		val V2_USER_TABLE = """
			CREATE TABLE IF NOT EXISTS `user` (
				`phone` TEXT NOT NULL,
				`avatar` TEXT,
				`nickname` TEXT,
				`password` TEXT NOT NULL,
				`introduction` TEXT,
				`sex` TEXT,
				`birthday` TEXT,
				`career` TEXT,
				`region` TEXT,
				`school` TEXT,
				`background` TEXT,
				`auth_token` TEXT,
				`refresh_token` TEXT,
				`last_login` INTEGER NOT NULL,
				`is_followed` INTEGER NOT NULL,
				PRIMARY KEY(`phone`)
			)
		""".trimIndent()

		const val EXISTING_PHONE = "13800000000"
	}

	@Test
	fun migratingV2ToV3_keepsExistingDataAndCreatesUploadTables() = runBlocking {
		createV2Database()

		// Room 打开时会按顺序执行 2→3→4 并校验结果 schema——不一致就在这里抛
		val database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
			.addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
			// 测试里省掉线程调度；生产走 Room 的默认（非主线程）
			.allowMainThreadQueries()
			.build()

		try {
			// ① 旧数据必须原样保留：迁移只该新增两张表，动到登录态就是灾难
			val keptPhone = database.query("SELECT phone FROM user", null).use { cursor ->
				assertTrue("迁移后必须还能查到原来的用户", cursor.moveToFirst())
				cursor.getString(0)
			}
			assertEquals("老用户的手机号不能变", EXISTING_PHONE, keptPhone)

			// ② 新表能用：不走原始 SQL，而是**真的走 DAO**，顺带验证列名与类型
			val dao = database.uploadSessionDao()
			dao.upsertSession(
				UploadSessionEntity(
					uri = uri, fileName = "a.mp4", fileSize = 5_000_000, fileMd5 = "md5",
					chunkSize = 1_000_000, totalChunks = 5, uploadId = "u-1",
					lastModified = 1_700_000_000_000L,
					status = UploadSessionStatus.UPLOADING.name, updatedAt = 123L
				)
			)
			val session = dao.session(uri)
			assertEquals("会话要能按 uri 读回来", "u-1", session?.uploadId)
			assertEquals("分片大小是新增的列，必须存得进也读得出", 1_000_000L, session?.chunkSize)
			assertEquals(
				"改动时间（v4 新增）也要能存能读——它决定缓存的文件指纹还作不作数",
				1_700_000_000_000L, session?.lastModified
			)
			assertEquals("状态以枚举名存字符串", UploadSessionStatus.UPLOADING.name, session?.status)
			assertEquals(
				"没有上传中会话时，latestUnfinishedSession 不该返回已完成的那条",
				"u-1", dao.latestUnfinishedSession(UploadSessionStatus.DONE.name)?.uploadId
			)

			dao.insertParts(
				listOf(0, 1).map { index -> part(index, UploadPartStatus.PENDING, retryCount = 0) }
			)
			assertEquals("分片要能整批写入", 2, dao.parts(uri).size)

			// 复合主键 (uri, part_index)：同一片重复写应当覆盖，而不是插出两行
			dao.insertParts(listOf(part(0, UploadPartStatus.DONE, retryCount = 3)))
			val parts = dao.parts(uri)
			assertEquals("主键 (uri, part_index) 应当覆盖写", 2, parts.size)
			assertEquals("覆盖写要生效", UploadPartStatus.DONE.name, parts.first().status)
			assertEquals(3, parts.first().retryCount)

			// 按片更新状态（上传器每传完一片都会调它）
			dao.updatePartStatus(uri, 1, UploadPartStatus.DONE.name, 1)
			assertEquals(UploadPartStatus.DONE.name, dao.parts(uri)[1].status)

			// 删会话时要连带清分片（没有外键级联，靠 store 的显式删除）
			dao.deleteParts(uri)
			dao.deleteSession(uri)
			assertEquals("删完不该留下分片", 0, dao.parts(uri).size)
		} finally {
			database.close()
		}
	}

	private fun part(index: Int, status: UploadPartStatus, retryCount: Int) = UploadPartEntity(
		uri = uri, partIndex = index, partOffset = index * 1_000_000L, partSize = 1_000_000L,
		status = status.name, retryCount = retryCount
	)

	/** 手工造一个 user_version=2 的库（形状见 [V2_USER_TABLE]），里面先放一行用户 */
	private fun createV2Database() {
		context.getDatabasePath(dbName).parentFile?.mkdirs()
		context.deleteDatabase(dbName)

		val helper = FrameworkSQLiteOpenHelperFactory().create(
			SupportSQLiteOpenHelper.Configuration.builder(context)
				.name(dbName)
				.callback(object : SupportSQLiteOpenHelper.Callback(2) {
					override fun onCreate(db: SupportSQLiteDatabase) {
						db.execSQL(V2_USER_TABLE)
					}

					override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
				})
				.build()
		)
		val db = helper.writableDatabase
		db.execSQL(
			"INSERT INTO user (phone, nickname, password, last_login, is_followed) " +
				"VALUES ('$EXISTING_PHONE', '老用户', 'hash', 1, 0)"
		)
		helper.close()
	}
}
