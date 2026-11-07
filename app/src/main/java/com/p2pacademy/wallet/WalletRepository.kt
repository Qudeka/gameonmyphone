package com.p2pacademy.wallet

import com.p2pacademy.database.JournalEntity
import com.p2pacademy.database.WalletDao
import com.p2pacademy.database.WalletEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletRepository {
    val wallet: Flow<WalletEntity?>
    val journal: Flow<List<JournalEntity>>
    suspend fun ensureInitialized()
    suspend fun startNewTradingDay()
}

class RoomWalletRepository(private val dao: WalletDao) : WalletRepository {
    override val wallet = dao.observe()
    override val journal = dao.observeJournal()

    override suspend fun ensureInitialized() {
        if (dao.get() == null) {
            dao.upsert(
                WalletEntity(
                    rub = BigDecimal("100000.00"),
                    usdt = BigDecimal.ZERO,
                    lockedRub = BigDecimal.ZERO,
                    lockedUsdt = BigDecimal.ZERO,
                    avgBuyPrice = BigDecimal.ZERO,
                    realizedProfit = BigDecimal.ZERO,
                    initialCapital = BigDecimal("100000.00"),
                    tradingDay = 1,
                    seed = System.nanoTime(),
                    xp = 0,
                    totalTrades = 0,
                    successfulTrades = 0,
                )
            )
            dao.addJournal(JournalEntity(day = 1, title = "Старт: виртуальный капитал 100 000 ₽", createdAt = System.currentTimeMillis()))
        }
    }

    /** Баланс и статистика сохраняются; меняются день и seed рынка (рынок появится на этапе 2). */
    override suspend fun startNewTradingDay() {
        val w = dao.get() ?: return
        val next = w.copy(tradingDay = w.tradingDay + 1, seed = System.nanoTime())
        dao.upsert(next)
        dao.addJournal(JournalEntity(day = next.tradingDay, title = "Начался торговый день ${next.tradingDay}", createdAt = System.currentTimeMillis()))
    }
}
