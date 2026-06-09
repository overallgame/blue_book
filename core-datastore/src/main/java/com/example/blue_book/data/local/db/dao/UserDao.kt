package com.example.blue_book.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.blue_book.data.local.db.entity.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity): Long

    @Update(entity = UserEntity::class)
    suspend fun update(user: UserEntity): Int

    @Delete(entity = UserEntity::class)
    suspend fun delete(user: UserEntity): Int

    @Query("SELECT * FROM user WHERE phone = :phone LIMIT 1")
    suspend fun getCurrentUser(phone: String): UserEntity?

    @Query("SELECT avatar FROM user WHERE phone = :phone LIMIT 1")
    suspend fun getAvatar(phone: String): String?

    @Query("SELECT background FROM user WHERE phone = :phone LIMIT 1")
    suspend fun getBackground(phone: String): String?

    @Query("UPDATE user SET avatar = :avatar WHERE phone = :phone")
    suspend fun updateAvatar(avatar: String, phone: String): Int

    @Query("UPDATE user SET background = :background WHERE phone = :phone")
    suspend fun updateBackground(background: String, phone: String): Int

}