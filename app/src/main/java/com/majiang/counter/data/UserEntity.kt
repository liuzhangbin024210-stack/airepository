package com.majiang.counter.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val passwordHash: String,
    val salt: String,
    val createdAtMillis: Long,
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserEntity)

    /** 更新指定用户的密码哈希与盐（修改密码时使用）。 */
    @Query(
        "UPDATE users SET passwordHash = :passwordHash, salt = :salt WHERE username = :username",
    )
    suspend fun updateCredentials(username: String, passwordHash: String, salt: String)
}
