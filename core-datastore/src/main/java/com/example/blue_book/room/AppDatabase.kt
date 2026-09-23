package com.example.blue_book.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.blue_book.room.dao.UploadSessionDao
import com.example.blue_book.room.dao.UserDao
import com.example.blue_book.room.entity.UploadPartEntity
import com.example.blue_book.room.entity.UploadSessionEntity
import com.example.blue_book.room.entity.UserEntity

@Database(
	entities = [UserEntity::class, UploadSessionEntity::class, UploadPartEntity::class],
	version = 3,
	exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun userDao(): UserDao
	abstract fun uploadSessionDao(): UploadSessionDao

	companion object {
		val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE user ADD COLUMN is_followed INTEGER NOT NULL DEFAULT 0")
			}
		}

		/**
		 * v2 → v3：加入本地上传会话与分片账本（跨进程续传用）。
		 *
		 * 纯新增两张表，**不触碰既有数据**（用户的登录态在 `user` 表里，一行都不能动）。
		 *
		 * 手工 SQL 必须与 Room 依据实体生成的期望 schema 一致，否则打开数据库时会抛
		 * `IllegalStateException: Migration didn't properly handle ...`——而那是在**用户设备上**
		 * 第一次启动时才会发生的事。这条迁移由 `UploadMigrationTest`（androidTest，
		 * 用 `MigrationTestHelper`）在真机上验过：建表、旧数据保留、两张新表可用。
		 *
		 * SQL 与实体列名逐项对应的清单：
		 * `upload_session(uri TEXT PK, file_name TEXT, file_size INTEGER, file_md5 TEXT,
		 *  chunk_size INTEGER, total_chunks INTEGER, upload_id TEXT NULL, status TEXT, updated_at INTEGER)`
		 * `upload_part(uri TEXT, part_index INTEGER, part_offset INTEGER, part_size INTEGER,
		 *  status TEXT, retry_count INTEGER, PRIMARY KEY(uri, part_index))`
		 */
		val MIGRATION_2_3 = object : Migration(2, 3) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `upload_session` (" +
						"`uri` TEXT NOT NULL, " +
						"`file_name` TEXT NOT NULL, " +
						"`file_size` INTEGER NOT NULL, " +
						"`file_md5` TEXT NOT NULL, " +
						"`chunk_size` INTEGER NOT NULL, " +
						"`total_chunks` INTEGER NOT NULL, " +
						"`upload_id` TEXT, " +
						"`status` TEXT NOT NULL, " +
						"`updated_at` INTEGER NOT NULL, " +
						"PRIMARY KEY(`uri`))"
				)
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `upload_part` (" +
						"`uri` TEXT NOT NULL, " +
						"`part_index` INTEGER NOT NULL, " +
						"`part_offset` INTEGER NOT NULL, " +
						"`part_size` INTEGER NOT NULL, " +
						"`status` TEXT NOT NULL, " +
						"`retry_count` INTEGER NOT NULL, " +
						"PRIMARY KEY(`uri`, `part_index`))"
				)
			}
		}

		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getDatabase(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				val instance = Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java,
					"app_database"
				).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
				.build()
				INSTANCE = instance
				instance
			}
		}
	}
}
