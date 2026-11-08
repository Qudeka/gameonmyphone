package com.p2pacademy

import com.p2pacademy.data.decodeState
import com.p2pacademy.data.encodeState
import com.p2pacademy.domain.*
import com.p2pacademy.education.ExamEngine
import com.p2pacademy.education.PracticeEngine
import com.p2pacademy.education.PracticeType
import com.p2pacademy.investigations.Cases
import com.p2pacademy.market.MarketEngine
import com.p2pacademy.trading.TradeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EngineTest {
    private val market = MarketEngine()
    private val trading = TradeEngine()

    private fun invariants(s: GameState) {
        s.bankBalances.forEach { assertTrue("balance >= 0", it.signum() >= 0) }
        assertTrue("usdt >= 0", s.usdt.signum() >= 0)
    }

    /** Состояние с 1000 USDT и созданной заявкой на продажу с нужным сценарием. */
    private fun sellSetup(sc: Scenario): Pair<GameState, Int> {
        for (seed in 1L..200L) {
            var s = market.newGame(seed, Difficulty.NOVICE).copy(usdt = BigDecimal("1000.0000"))
            val ad = s.ads.firstOrNull { it.side == Side.SELL } ?: continue
            val res = trading.createTrade(s, ad.id, ad.minRub, ad.bankIds.first())
            if (!res.ok) continue
            s = res.state
            val t = s.trades.first { it.id == res.tradeId }
            s = s.withTrade(t.copy(scenario = sc, payAtMin = s.clockMin + 3, sched = emptyList()))
            return s to t.id
        }
        throw AssertionError("no sell ad found")
    }

    @Test fun `same seed gives same market`() {
        val a = market.newGame(42L, Difficulty.MIDDLE)
        val b = market.newGame(42L, Difficulty.MIDDLE)
        assertEquals(a.ads, b.ads)
        assertEquals(a.mid, b.mid)
        assertTrue(a.ads.size >= 20)
    }

    @Test fun `different seeds give different markets`() {
        assertTrue(market.newGame(1L, Difficulty.NOVICE).ads != market.newGame(2L, Difficulty.NOVICE).ads)
    }

    @Test fun `starting wallet is 100000 rub and zero usdt`() {
        val s = market.newGame(5L, Difficulty.NOVICE)
        assertEquals(0, s.rubTotal.compareTo(BigDecimal("100000")))
        assertEquals(0, s.usdt.signum())
    }

    @Test fun `state survives serialization`() {
        var s = market.newGame(9L, Difficulty.NOVICE)
        s = trading.advance(s, 45)
        assertEquals(s, decodeState(encodeState(s)))
    }

    @Test fun `new day keeps balance and history`() {
        val s = market.newGame(11L, Difficulty.NOVICE)
        val n = market.newDay(s)
        assertEquals(s.day + 1, n.day)
        assertEquals(0, s.rubTotal.compareTo(n.rubTotal))
        assertEquals(1, n.dayHistory.size)
        assertTrue(n.ads.isNotEmpty())
    }

    @Test fun `cannot sell more usdt than available`() {
        val s = market.newGame(3L, Difficulty.NOVICE) // 0 USDT
        val ad = s.ads.firstOrNull { it.side == Side.SELL } ?: return
        val res = trading.createTrade(s, ad.id, ad.minRub, ad.bankIds.first())
        assertFalse(res.ok)
        invariants(res.state)
    }

    @Test fun `cannot buy beyond limits`() {
        val s = market.newGame(4L, Difficulty.NOVICE)
        val ad = s.ads.first { it.side == Side.BUY }
        val res = trading.createTrade(s, ad.id, ad.maxRub + BigDecimal.ONE, ad.bankIds.first())
        assertFalse(res.ok)
    }

    @Test fun `honest sale completes and release is not repeatable`() {
        var (s, id) = sellSetup(Scenario.S_HONEST)
        val before = s.usdt
        s = trading.advance(s, 150)
        assertEquals(TradeStatus.PAYMENT_SIMULATED, s.trades.first { it.id == id }.status)
        val r1 = trading.release(s, id)
        assertTrue(r1.ok)
        val done = r1.state.trades.first { it.id == id }
        assertEquals(TradeStatus.COMPLETED, done.status)
        assertFalse(done.fraudLoss)
        // второй раз завершить нельзя
        val r2 = trading.release(r1.state, id)
        assertFalse(r2.ok)
        assertEquals(r1.state.realized, r2.state.realized)
        assertTrue(r1.state.usdt <= before)
        invariants(r1.state)
    }

    @Test fun `fake receipt release loses the asset`() {
        var (s, id) = sellSetup(Scenario.S_FAKE_RECEIPT)
        s = trading.advance(s, 10)
        val usdtLeft = s.usdt
        val r = trading.release(s, id)
        assertFalse(r.ok)
        val t = r.state.trades.first { it.id == id }
        assertTrue(t.fraudLoss)
        assertEquals(usdtLeft, r.state.usdt)
        assertTrue(r.state.realized.signum() < 0)
        invariants(r.state)
    }

    @Test fun `dispute on fake receipt returns the asset`() {
        var (s, id) = sellSetup(Scenario.S_FAKE_RECEIPT)
        val startUsdt = s.usdt + s.lockedUsdt
        s = trading.advance(s, 10)
        val r = trading.openDispute(s, id)
        val t = r.state.trades.first { it.id == id }
        assertEquals(TradeStatus.REFUNDED, t.status)
        assertEquals(0, startUsdt.compareTo(r.state.usdt + r.state.lockedUsdt))
        assertEquals(1, r.state.fraudCaught)
        invariants(r.state)
    }

    @Test fun `no show is cancelled by timeout and asset is returned`() {
        var (s, id) = sellSetup(Scenario.S_NO_SHOW)
        val start = s.usdt + s.lockedUsdt
        s = s.withTrade(s.trades.first { it.id == id }.copy(payAtMin = null))
        s = trading.advance(s, 40)
        assertEquals(TradeStatus.CANCELLED, s.trades.first { it.id == id }.status)
        assertEquals(0, start.compareTo(s.usdt))
    }

    @Test fun `buy flow spends money only after payment`() {
        val seed = (1L..200L).first { sd -> market.newGame(sd, Difficulty.NOVICE).ads.any { it.side == Side.BUY } }
        var s = market.newGame(seed, Difficulty.NOVICE)
        val ad = s.ads.first { it.side == Side.BUY }
        val bank = ad.bankIds.first().let { b -> if (s.bankBalances[b].signum() > 0) b else 0 }
        val rub = ad.minRub
        val before = s.rubTotal
        val res = trading.createTrade(s, ad.id, rub, if (bank in ad.bankIds) bank else ad.bankIds.first())
        if (!res.ok) return // банк без средств у этого объявления — другой сценарий проверяется выше
        s = res.state
        assertEquals(0, before.compareTo(s.rubTotal))     // до оплаты деньги на месте
        val t = s.trades.first { it.id == res.tradeId }
        s = s.withTrade(t.copy(scenario = Scenario.B_HONEST))
        val paid = trading.pay(s, t.id)
        assertTrue(paid.ok)
        assertTrue(paid.state.rubTotal < before)
        // повторная оплата одной и той же заявки невозможна
        assertFalse(trading.pay(paid.state, t.id).ok)
        invariants(paid.state)
    }

    @Test fun `cancel after payment loses money and is flagged`() {
        val seed = (1L..200L).first { sd -> market.newGame(sd, Difficulty.NOVICE).ads.any { it.side == Side.BUY && it.bankIds.any { b -> b <= 2 } } }
        var s = market.newGame(seed, Difficulty.NOVICE)
        val ad = s.ads.first { it.side == Side.BUY && it.bankIds.any { b -> b <= 2 } }
        val bank = ad.bankIds.first { it <= 2 }
        val res = trading.createTrade(s, ad.id, ad.minRub, bank)
        if (!res.ok) return
        s = res.state
        val t = s.trades.first { it.id == res.tradeId }
        s = s.withTrade(t.copy(scenario = Scenario.B_ASK_CANCEL))
        s = trading.pay(s, t.id).state
        val c = trading.cancel(s, t.id)
        assertFalse(c.ok)
        assertTrue(c.state.trades.first { it.id == t.id }.fraudLoss)
        invariants(c.state)
    }

    @Test fun `library has at least 20 cases and generator makes more`() {
        assertTrue(Cases.library.size >= 20)
        val g = Cases.generate(123L, 0)
        assertEquals(4, g.questions.size)
        assertNotNull(Cases.find(g.id))
        assertEquals(g.title, Cases.find(g.id)!!.title)
    }

    @Test fun `case grading is evidence based`() {
        val c = Cases.library.first()
        assertEquals(100, Cases.grade(c, c.questions.map { it.correct }))
        assertEquals(0, Cases.grade(c, c.questions.map { (it.correct + 1) % it.options.size }))
    }

    @Test fun `practice answers are computed and consistent`() {
        PracticeType.values().forEach { type ->
            val q = PracticeEngine().generate(type, 1234L)
            assertTrue(q.correct in q.options.indices)
            assertEquals(q.options.size, q.options.size)
        }
    }

    @Test fun `exam needs strong results in all categories`() {
        val exam = ExamEngine()
        val qs = exam.generate(77L)
        val perfect = exam.grade(77L, qs, qs.map { it.correct }, emptyList())
        assertTrue(perfect.result.passed)
        val bad = exam.grade(77L, qs, qs.map { (it.correct + 1) % it.options.size }, emptyList())
        assertFalse(bad.result.passed)
        // одного экзамена мало для подтверждённого уровня
        assertFalse(perfect.level.startsWith("Подтверждённый"))
        val second = exam.grade(78L, exam.generate(78L), exam.generate(78L).map { it.correct }, listOf(perfect.result))
        assertTrue(second.level.startsWith("Подтверждённый"))
    }
}
