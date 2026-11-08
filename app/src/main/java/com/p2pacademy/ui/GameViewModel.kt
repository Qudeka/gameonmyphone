package com.p2pacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pacademy.analytics.StatisticsManager
import com.p2pacademy.chat.Quick
import com.p2pacademy.data.GameRepository
import com.p2pacademy.domain.*
import com.p2pacademy.education.EducationManager
import com.p2pacademy.education.ExamEngine
import com.p2pacademy.education.Question
import com.p2pacademy.investigations.CaseFile
import com.p2pacademy.investigations.Cases
import com.p2pacademy.market.MarketEngine
import com.p2pacademy.trading.FeeConfig
import com.p2pacademy.trading.TradeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class GameViewModel(private val repo: GameRepository) : ViewModel() {
    val market = MarketEngine()
    val trading = TradeEngine()
    val stats = StatisticsManager()
    val education = EducationManager()
    val exams = ExamEngine()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // экзамен: активные вопросы живут, пока приложение открыто
    var examSeed: Long = 0
    var examQuestions: List<Question> = emptyList()

    init {
        viewModelScope.launch {
            val loaded = repo.load()
            _state.value = loaded ?: market.newGame(System.nanoTime(), Difficulty.NOVICE).also { repo.save(it) }
        }
    }

    fun consumeMessage() { _message.value = null }
    fun toast(text: String) { _message.value = text }

    private fun commit(next: GameState) {
        val unlocked = stats.unlock(next)
        _state.value = unlocked
        viewModelScope.launch { repo.save(unlocked) }
    }

    private fun mutate(f: (GameState) -> GameState) {
        val cur = _state.value ?: return
        commit(f(cur))
    }

    private fun act(f: (GameState) -> Res): Res? {
        val cur = _state.value ?: return null
        val res = f(cur)
        commit(res.state)
        _message.value = res.msg
        return res
    }

    // ---- торговля ----
    fun createTrade(adId: Int, rub: BigDecimal, bankId: Int): Res? = act { trading.createTrade(it, adId, rub, bankId) }
    fun pay(id: Int) { act { trading.pay(it, id) } }
    fun release(id: Int) { act { trading.release(it, id) } }
    fun cancel(id: Int) { act { trading.cancel(it, id) } }
    fun doublePay(id: Int) { act { trading.doublePay(it, id) } }
    fun openDispute(id: Int) { act { trading.openDispute(it, id) } }
    fun quick(id: Int, q: Quick) = mutate { trading.quick(it, id, q) }
    fun advance(min: Int) = mutate { trading.advance(it, min) }
    fun newDay() { mutate { trading.newDay(it) }; _message.value = "Начался новый торговый день" }

    fun internalTransfer(from: Int, to: Int, amount: BigDecimal) {
        val cur = _state.value ?: return
        val bank = com.p2pacademy.banking.Banks.all[from]
        val fee = com.p2pacademy.banking.BankingEngine().feeFor(bank, amount)
        val total = amount + fee
        when {
            from == to -> toast("Выберите другой банк")
            amount > bank.limit -> toast("Превышен лимит банка: ${bank.limit.toPlainString()} ₽")
            cur.bankBalances[from] < total -> toast("Недостаточно средств")
            else -> {
                commit(cur.addBank(from, total.negate()).addBank(to, amount)
                    .note("Перевод ${amount.r2().toPlainString()} ₽: ${bank.name} → ${com.p2pacademy.banking.Banks.all[to].name}", from))
                toast("Перевод выполнен, комиссия ${fee.r2().toPlainString()} ₽")
            }
        }
    }

    // ---- настройки ----
    fun setDifficulty(d: Difficulty) = mutate { it.copy(difficulty = d) }
    fun setMode(m: MarketMode) = mutate { it.copy(mode = m) }
    fun setFee(f: FeeConfig) = mutate { it.copy(fee = f) }

    // ---- обучение ----
    fun completeLesson(id: String, right: Int, total: Int) = mutate { education.complete(it, id, right, total) }
    fun recordPractice(skill: String, ok: Boolean) = mutate {
        it.copy(practiceRight = it.practiceRight + if (ok) 1 else 0, practiceTotal = it.practiceTotal + 1)
            .skillAdd(skill, if (ok) 2 else -1).gain(if (ok) 5 else 0)
    }

    fun startExam(): List<Question> {
        examSeed = System.nanoTime() % 1_000_000_007L
        examQuestions = exams.generate(examSeed)
        return examQuestions
    }

    fun finishExam(answers: List<Int>): ExamEngine.Outcome? {
        val cur = _state.value ?: return null
        val out = exams.grade(examSeed, examQuestions, answers, cur.examResults)
        commit(exams.apply(cur, out))
        return out
    }

    fun finishCase(case: CaseFile, answers: List<Int>): Int {
        val score = Cases.grade(case, answers)
        mutate { Cases.apply(it, case, score) }
        return score
    }

    // ---- сохранение ----
    fun backup() {
        val cur = _state.value ?: return
        viewModelScope.launch { repo.backup(cur); _message.value = "Резервная копия сохранена" }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            val b = repo.loadBackup()
            if (b == null) _message.value = "Резервной копии нет"
            else { commit(b); _message.value = "Прогресс восстановлен из копии" }
        }
    }

    fun fullReset() {
        viewModelScope.launch {
            repo.reset()
            val fresh = market.newGame(System.nanoTime(), _state.value?.difficulty ?: Difficulty.NOVICE)
            _state.value = fresh
            repo.save(fresh)
            _message.value = "Прогресс сброшен"
        }
    }
}
