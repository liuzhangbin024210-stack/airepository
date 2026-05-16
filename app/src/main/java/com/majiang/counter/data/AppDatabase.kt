package com.majiang.counter.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "roi_config")
data class RoiConfigEntity(
    @PrimaryKey val appId: String,
    val rectsJson: String,
)

@Dao
interface RoiDao {
    @Query("SELECT * FROM roi_config WHERE appId = :appId LIMIT 1")
    suspend fun get(appId: String): RoiConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoiConfigEntity)
}

@Database(
    entities = [RoiConfigEntity::class, UserEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roiDao(): RoiDao
    abstract fun userDao(): UserDao
}
