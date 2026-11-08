@file:UseSerializers(BigDecimalSerializer::class)

package com.p2pacademy.domain

import com.p2pacademy.trading.FeeConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.math.RoundingMode

/** Сторона пользователя: BUY — пользователь покупает USDT, SELL — продаёт. */
@Serializable
enum class Side { BUY, SELL }

@Serializable
enum class Difficulty(val title: String, val volBp: Int, val fraudPct: Int, val maxActive: Int, val hints: Boolean) {
    NOVICE("Новичок", 8, 30, 2, true),
    MIDDLE("Средний", 15, 100, 3, false),
    ADVANCED("Продвинутый", 30, 130, 5, false),
    HARDCORE("Hardcore", 60, 170, 8, false),
}

@Serializable
enum class MarketMode(val title: String) { TRAINING("Учебный рынок"), REAL_ANALYSIS("Анализ реального рынка") }

@Serializable
enum class TradeStatus(val title: String) {
    CREATED("Создана"),
    RESERVED("Зарезервировано"),
    WAITING_FOR_PAYMENT("Ожидает оплаты"),
    PAYMENT_SIMULATED("Платёж отправлен"),
    COMPLETED("Завершена"),
    CANCELLED("Отменена"),
    DISPUTED("Спор"),
    REFUNDED("Возврат"),
}

@Serializable
enum class TraderType(val title: String) {
    EXP_SELLER("Опытный продавец"),
    EXP_BUYER("Опытный покупатель"),
    NEWBIE("Новичок"),
    BIG("Крупный рекламодатель"),
    SLOW("Медленный контрагент"),
    UNRELIABLE("Ненадёжный участник"),
    SUSPICIOUS("Подозрительный участник"),
    AGGRESSIVE("Агрессивный участник"),
    MANIPULATOR("Манипулятор"),
}

@Serializable
data class Trader(
    val id: Int,
    val name: String,
    val type: TraderType,
    val ageDays: Int,
    val trades: Int,
    val successPct: Int,
    val cancels: Int,
    val disputes: Int,
    val avgMinutes: Int,
    val reputation: Int,
)

@Serializable
data class Ad(
    val id: Int,
    val traderId: Int,
    val side: Side,
    val rate: BigDecimal,
    val minRub: BigDecimal,
    val maxRub: BigDecimal,
    val reserveUsdt: BigDecimal,
    val bankIds: List<Int>,
)

@Serializable
enum class Scenario(val title: String, val side: Side, val fraud: Boolean) {
    // Пользователь продаёт USDT, платит контрагент
    S_HONEST("Честная оплата", Side.SELL, false),
    S_PENDING_OK("Платёж зачисляется с задержкой", Side.SELL, false),
    S_BANK_HOLD("Банк проверяет платёж", Side.SELL, false),
    S_NO_SHOW("Покупатель не оплатил", Side.SELL, false),
    S_FAKE_RECEIPT("Чек без зачисления", Side.SELL, true),
    S_FAKE_NOTIFY("Поддельное уведомление", Side.SELL, true),
    S_WRONG_AMOUNT("Неверная сумма", Side.SELL, true),
    S_SPLIT("Разделённый платёж", Side.SELL, true),
    S_NAME_MISMATCH("Платёж от третьего лица", Side.SELL, true),
    S_OLD_RECEIPT("Чек от другой операции", Side.SELL, true),
    S_WRONG_DATE("Неправильная дата чека", Side.SELL, true),
    S_CANCELLED("Отменённый перевод", Side.SELL, true),
    S_PENDING_REJECT("Неокончательный статус", Side.SELL, true),

    // Пользователь покупает USDT, платит он
    B_HONEST("Честный продавец", Side.BUY, false),
    B_SLOW("Медленный продавец", Side.BUY, false),
    B_BANK_HOLD("Банк проверяет ваш платёж", Side.BUY, false),
    B_REJECTED("Банк отклонил перевод", Side.BUY, false),
    B_NO_RELEASE("Продавец не отпускает актив", Side.BUY, true),
    B_REPEAT("Просьба повторить перевод", Side.BUY, true),
    B_ASK_CANCEL("Просьба отменить после оплаты", Side.BUY, true),
    B_OFF_PLATFORM("Уход за пределы площадки", Side.BUY, true),
}

@Serializable
enum class BankStatus(val title: String) {
    CREATED("Создан"),
    PROCESSING("Обрабатывается"),
    SENT("Отправлен"),
    PENDING_CREDIT("Ожидает зачисления"),
    CREDITED("Зачислен"),
    REJECTED("Отклонён"),
    RETURNED("Возвращён"),
    REVIEW("На дополнительной проверке"),
}

@Serializable
data class BankOp(
    val id: Int,
    val bankId: Int,
    val tradeId: Int,
    val incoming: Boolean,
    val amount: BigDecimal,
    val fee: BigDecimal,
    val counterparty: String,
    val createdMin: Int,
    val tSent: Int,
    val tPending: Int,
    val tFinal: Int,
    val hold: Boolean,
    val finalStatus: BankStatus,
    val applied: Boolean = false,
    val reversed: Boolean = false,
    val note: String = "",
)

@Serializable
enum class DocKind(val title: String) {
    RECEIPT("Чек"),
    SLIP("Квитанция"),
    STATEMENT("Выписка"),
    NOTICE("Уведомление"),
    CONFIRM("Подтверждение перевода"),
    BANK_MSG("Сообщение банка"),
    CANCEL("Документ об отмене"),
}

@Serializable
data class Doc(
    val id: Int,
    val code: String,
    val kind: DocKind,
    val amount: BigDecimal,
    val atMin: Int,
    val sender: String,
    val receiver: String,
    val bankId: Int,
    val status: String,
    val tradeRef: Int,
    val opRef: Int?,
    val genuine: Boolean,
)

@Serializable
data class ChatMsg(val min: Int, val from: Int, val text: String, val docId: Int? = null) // from: 0 система, 1 вы, 2 контрагент

@Serializable
data class SchedMsg(val atMin: Int, val text: String)

@Serializable
data class Trade(
    val id: Int,
    val adId: Int,
    val traderId: Int,
    val side: Side,
    val rub: BigDecimal,
    val usdt: BigDecimal,
    val netUsdt: BigDecimal,
    val rate: BigDecimal,
    val feeRub: BigDecimal,
    val bankFee: BigDecimal,
    val paidRub: BigDecimal,
    val bankId: Int,
    val scenario: Scenario,
    val status: TradeStatus,
    val createdMin: Int,
    val day: Int,
    val payAtMin: Int? = null,
    val releaseAtMin: Int? = null,
    val opId: Int? = null,
    val docId: Int? = null,
    val sched: List<SchedMsg> = emptyList(),
    val chat: List<ChatMsg> = emptyList(),
    val timeline: List<String> = emptyList(),
    val result: String = "",
    val pnl: BigDecimal = BigDecimal.ZERO,
    val fraudLoss: Boolean = false,
    val disputed: Boolean = false,
    val report: List<String> = emptyList(),
) {
    val isActive: Boolean
        get() = status == TradeStatus.WAITING_FOR_PAYMENT ||
            status == TradeStatus.PAYMENT_SIMULATED ||
            status == TradeStatus.DISPUTED
    val success: Boolean get() = status == TradeStatus.COMPLETED && !fraudLoss
}

@Serializable
data class MarketEvent(val atMin: Int, val title: String, val shockBp: Int, val bankId: Int = -1, val untilMin: Int = 0)

@Serializable
data class Note(val min: Int, val text: String, val bankId: Int = -1, val fake: Boolean = false, val tradeId: Int = -1)

@Serializable
data class DaySummary(val day: Int, val equity: BigDecimal, val completed: Int, val pnl: BigDecimal)

@Serializable
data class ExamResult(val seed: Long, val scorePct: Int, val byCategory: Map<String, Int>, val passed: Boolean)

object Skill {
    const val MARKET = "Анализ рынка"
    const val CALC = "Финансовые расчёты"
    const val VERIFY = "Проверка платежей"
    const val SAFETY = "Безопасность"
    const val CAPITAL = "Управление капиталом"
    const val STRESS = "Стрессовые решения"
    val all = listOf(MARKET, CALC, VERIFY, SAFETY, CAPITAL, STRESS)
}

@Serializable
data class GameState(
    val seed: Long,
    val day: Int = 1,
    val clockMin: Int = 540,
    val difficulty: Difficulty = Difficulty.NOVICE,
    val mode: MarketMode = MarketMode.TRAINING,
    val nextId: Int = 1,
    val bankBalances: List<BigDecimal>,
    val usdt: BigDecimal = BigDecimal.ZERO,
    val avgCost: BigDecimal = BigDecimal.ZERO,
    val realized: BigDecimal = BigDecimal.ZERO,
    val initial: BigDecimal = BigDecimal("100000.00"),
    val fee: FeeConfig = FeeConfig(),
    val mid: BigDecimal = BigDecimal("92.00"),
    val priceHistory: List<BigDecimal> = emptyList(),
    val pressureBp: Int = 0,
    val events: List<MarketEvent> = emptyList(),
    val traders: List<Trader> = emptyList(),
    val ads: List<Ad> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val ops: List<BankOp> = emptyList(),
    val docs: List<Doc> = emptyList(),
    val notes: List<Note> = emptyList(),
    val xp: Int = 0,
    val skills: Map<String, Int> = emptyMap(),
    val fraudCaught: Int = 0,
    val fraudMissed: Int = 0,
    val falseAlarms: Int = 0,
    val lessonsDone: List<String> = emptyList(),
    val casesDone: Map<String, Int> = emptyMap(),
    val practiceRight: Int = 0,
    val practiceTotal: Int = 0,
    val examResults: List<ExamResult> = emptyList(),
    val achievements: List<String> = emptyList(),
    val equityHistory: List<BigDecimal> = emptyList(),
    val dayHistory: List<DaySummary> = emptyList(),
    val dayStartEquity: BigDecimal = BigDecimal("100000.00"),
    val dayStartCompleted: Int = 0,
) {
    val rubTotal: BigDecimal get() = bankBalances.fold(BigDecimal.ZERO) { a, b -> a + b }

    val lockedRub: BigDecimal
        get() = trades.filter {
            it.side == Side.BUY && (it.status == TradeStatus.PAYMENT_SIMULATED || it.status == TradeStatus.DISPUTED)
        }.fold(BigDecimal.ZERO) { a, t -> a + t.paidRub }

    val lockedUsdt: BigDecimal
        get() = trades.filter { it.side == Side.SELL && it.isActive }.fold(BigDecimal.ZERO) { a, t -> a + t.usdt }

    val usdtTotal: BigDecimal get() = usdt + lockedUsdt

    val equity: BigDecimal get() = (rubTotal + lockedRub + usdtTotal.multiply(mid)).setScale(2, RoundingMode.HALF_UP)

    val unrealized: BigDecimal
        get() = if (usdtTotal.signum() == 0) BigDecimal.ZERO
        else usdtTotal.multiply(mid - avgCost).setScale(2, RoundingMode.HALF_UP)

    val activeTrades: List<Trade> get() = trades.filter { it.isActive }

    val level: Int get() = 1 + xp / 150

    fun skill(key: String): Int = skills[key] ?: 30
}

// ---- хелперы обновления состояния ----

fun GameState.withTrade(t: Trade): GameState = copy(trades = trades.map { if (it.id == t.id) t else it })

fun GameState.withOp(o: BankOp): GameState = copy(ops = ops.map { if (it.id == o.id) o else it })

fun GameState.addBank(bankId: Int, delta: BigDecimal): GameState =
    copy(bankBalances = bankBalances.mapIndexed { i, b -> if (i == bankId) (b + delta).max(BigDecimal.ZERO) else b })

/** Списывает сумму с банка, при нехватке — с других банков (баланс не уходит ниже нуля). */
fun GameState.takeRub(bankId: Int, amount: BigDecimal): GameState {
    var left = amount
    val order = listOf(bankId) + bankBalances.indices.filter { it != bankId }
    val nb = bankBalances.toMutableList()
    for (i in order) {
        if (left.signum() <= 0) break
        val take = nb[i].min(left)
        nb[i] = nb[i] - take
        left -= take
    }
    return copy(bankBalances = nb)
}

fun GameState.gain(points: Int): GameState = copy(xp = (xp + points).coerceAtLeast(0))

fun GameState.skillAdd(key: String, delta: Int): GameState =
    copy(skills = skills + (key to (skill(key) + delta).coerceIn(0, 100)))

fun GameState.note(text: String, bankId: Int = -1, fake: Boolean = false, tradeId: Int = -1): GameState =
    copy(notes = (listOf(Note(clockMin, text, bankId, fake, tradeId)) + notes).take(80))

fun GameState.tweakTrader(id: Int, f: (Trader) -> Trader): GameState =
    copy(traders = traders.map { if (it.id == id) f(it) else it })

fun GameState.recordEquity(): GameState = copy(equityHistory = (equityHistory + equity).takeLast(300))

fun Trade.log(clock: Int, text: String): Trade = copy(timeline = timeline + "${hhmm(clock)} · $text")

fun Trade.say(clock: Int, from: Int, text: String, docId: Int? = null): Trade =
    copy(chat = chat + ChatMsg(clock, from, text, docId))
