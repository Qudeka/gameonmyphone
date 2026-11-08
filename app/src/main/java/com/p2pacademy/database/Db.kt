package com.p2pacademy.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Слот сохранения: "main" — текущая игра, "backup" — резервная копия. */
@Entity(tableName = "saves")
data class SaveEntity(
    @PrimaryKey val slot: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface SaveDao {
    @Query("SELECT * FROM saves WHERE slot = :slot")
    suspend fun get(slot: String): SaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(save: SaveEntity)

    @Query("DELETE FROM saves WHERE slot = :slot")
    suspend fun delete(slot: String)
}

@Database(entities = [SaveEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
}
