package com.p2pacademy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pacademy.database.WalletEntity
import com.p2pacademy.domain.Money
import com.p2pacademy.wallet.WalletRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class HomeUiState(
    val loaded: Boolean = false,
    val portfolioValue: BigDecimal = BigDecimal.ZERO,
    val rub: BigDecimal = BigDecimal.ZERO,
    val usdt: BigDecimal = BigDecimal.ZERO,
    val realizedProfit: BigDecimal = BigDecimal.ZERO,
    val unrealizedProfit: BigDecimal = BigDecimal.ZERO,
    val returnPercent: BigDecimal = BigDecimal.ZERO,
    val totalTrades: Int = 0,
    val successPercent: Int = 0,
    val level: Int = 1,
    val tradingDay: Int = 1,
    val journal: List<String> = emptyList(),
)

class HomeViewModel(private val repo: WalletRepository) : ViewModel() {

    init { viewModelScope.launch { repo.ensureInitialized() } }

    val state: StateFlow<HomeUiState> = combine(repo.wallet, repo.journal) { w, j ->
        if (w == null) HomeUiState() else w.toUi(j.map { "День ${it.day}: ${it.title}" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun newTradingDay() { viewModelScope.launch { repo.startNewTradingDay() } }

    private fun WalletEntity.toUi(journal: List<String>): HomeUiState {
        // Рыночной цены пока нет (этап 2) — оцениваем USDT по средней цене покупки.
        val usdtValue = usdt.multiply(avgBuyPrice)
        val portfolio = Money.rub(rub + lockedRub + usdtValue)
        val ret = if (initialCapital.signum() == 0) BigDecimal.ZERO
        else (portfolio - initialCapital).multiply(Money.HUNDRED).divide(initialCapital, 2, RoundingMode.HALF_UP)
        return HomeUiState(
            loaded = true,
            portfolioValue = portfolio,
            rub = rub,
            usdt = usdt,
            realizedProfit = realizedProfit,
            unrealizedProfit = BigDecimal.ZERO,
            returnPercent = ret,
            totalTrades = totalTrades,
            successPercent = if (totalTrades == 0) 0 else successfulTrades * 100 / totalTrades,
            level = 1 + xp / 100,
            tradingDay = tradingDay,
            journal = journal,
        )
    }
}
