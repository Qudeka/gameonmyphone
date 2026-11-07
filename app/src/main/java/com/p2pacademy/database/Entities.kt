package com.p2pacademy.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class Converters {
    @TypeConverter fun fromDecimal(v: BigDecimal): String = v.toPlainString()
    @TypeConverter fun toDecimal(s: String): BigDecimal = BigDecimal(s)
}

/** Единственная строка (id = 1): состояние кошелька и прогресса. */
@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val rub: BigDecimal,
    val usdt: BigDecimal,
    val lockedRub: BigDecimal,
    val lockedUsdt: BigDecimal,
    val avgBuyPrice: BigDecimal,
    val realizedProfit: BigDecimal,
    val initialCapital: BigDecimal,
    val tradingDay: Int,
    val seed: Long,
    val xp: Int,
    val totalTrades: Int,
    val successfulTrades: Int,
)

@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Int,
    val title: String,
    val createdAt: Long,
)

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1")
    fun observe(): Flow<WalletEntity?>

    @Query("SELECT * FROM wallet WHERE id = 1")
    suspend fun get(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallet: WalletEntity)

    @Insert
    suspend fun addJournal(entry: JournalEntity)

    @Query("SELECT * FROM journal ORDER BY id DESC LIMIT 10")
    fun observeJournal(): Flow<List<JournalEntity>>
}

@Database(entities = [WalletEntity::class, JournalEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
}
