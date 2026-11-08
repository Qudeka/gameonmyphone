package com.p2pacademy.market

import com.p2pacademy.banking.Banks
import com.p2pacademy.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

class PriceEngine {
    private val anchor = BigDecimal("92.00")

    fun initial(r: Random): BigDecimal = BigDecimal(r.nextInt(8800, 9600)).movePointLeft(2)

    /** Новый курс: шум, давление действий пользователя, шок события, возврат к «якорю». */
    fun step(mid: BigDecimal, volBp: Int, pressureBp: Int, shockBp: Int, r: Random): BigDecimal {
        val noise = r.nextInt(-volBp, volBp + 1)
        val rev = anchor.subtract(mid).multiply(BigDecimal(10000)).divide(mid, 0, RoundingMode.HALF_UP).toInt() / 25
        val bp = noise + pressureBp + shockBp + rev
        val next = mid.multiply(BigDecimal(10000 + bp)).divide(BigDecimal(10000), 2, RoundingMode.HALF_UP)
        return next.max(BigDecimal("70.00")).min(BigDecimal("125.00"))
    }
}

class TraderGenerator {
    private val prefixes = listOf("Crypto", "Alex", "Mira", "Volt", "Nord", "Lion", "Fox", "Delta", "Orbit", "Kvant", "Sigma", "Maks", "Anna", "Boris", "Lena", "Titan", "Vlad", "Olga")
    private val suffixes = listOf("Trade", "Pay", "Fx", "Exch", "Desk", "Pro", "Cash", "Hub", "Dealer", "Market")

    fun generate(id: Int, type: TraderType, r: Random): Trader {
        val name = prefixes[r.nextInt(prefixes.size)] + suffixes[r.nextInt(suffixes.size)] + r.nextInt(10, 99)
        fun cancelsFor(trades: Int, success: Int) = (trades * (100 - success) / 100 / 2)
        return when (type) {
            TraderType.EXP_SELLER, TraderType.EXP_BUYER -> {
                val tr = r.nextInt(800, 5000); val sp = r.nextInt(97, 100)
                Trader(id, name, type, r.nextInt(600, 2000), tr, sp, cancelsFor(tr, sp), r.nextInt(0, 4), r.nextInt(3, 10), r.nextInt(85, 99))
            }
            TraderType.BIG -> {
                val tr = r.nextInt(5000, 20000); val sp = r.nextInt(98, 100)
                Trader(id, name, type, r.nextInt(800, 2500), tr, sp, cancelsFor(tr, sp), r.nextInt(0, 6), r.nextInt(2, 6), r.nextInt(90, 100))
            }
            TraderType.NEWBIE -> {
                val tr = r.nextInt(0, 25); val sp = r.nextInt(70, 100)
                Trader(id, name, type, r.nextInt(3, 40), tr, sp, cancelsFor(tr, sp), r.nextInt(0, 2), r.nextInt(8, 25), r.nextInt(40, 60))
            }
            TraderType.SLOW -> {
                val tr = r.nextInt(100, 1000); val sp = r.nextInt(95, 98)
                Trader(id, name, type, r.nextInt(100, 800), tr, sp, cancelsFor(tr, sp), r.nextInt(0, 3), r.nextInt(25, 60), r.nextInt(65, 80))
            }
            TraderType.UNRELIABLE -> {
                val tr = r.nextInt(30, 300); val sp = r.nextInt(80, 90)
                Trader(id, name, type, r.nextInt(30, 300), tr, sp, cancelsFor(tr, sp) + r.nextInt(3, 15), r.nextInt(2, 7), r.nextInt(15, 40), r.nextInt(35, 55))
            }
            TraderType.SUSPICIOUS -> {
                val tr = r.nextInt(10, 80); val sp = r.nextInt(60, 100)
                Trader(id, name, type, r.nextInt(5, 60), tr, sp, cancelsFor(tr, sp) + r.nextInt(2, 8), r.nextInt(3, 11), r.nextInt(3, 20), r.nextInt(20, 45))
            }
            TraderType.AGGRESSIVE -> {
                val tr = r.nextInt(300, 2000); val sp = r.nextInt(90, 96)
                Trader(id, name, type, r.nextInt(200, 900), tr, sp, cancelsFor(tr, sp), r.nextInt(3, 12), r.nextInt(5, 15), r.nextInt(55, 70))
            }
            TraderType.MANIPULATOR -> {
                val tr = r.nextInt(50, 400); val sp = r.nextInt(85, 96)
                Trader(id, name, type, r.nextInt(20, 200), tr, sp, cancelsFor(tr, sp), r.nextInt(2, 9), r.nextInt(5, 12), r.nextInt(30, 60))
            }
        }
    }
}

class MarketGenerator(private val traders: TraderGenerator = TraderGenerator()) {

    private fun typePool(d: Difficulty): List<TraderType> {
        val risky = when (d) { Difficulty.NOVICE -> 0; Difficulty.MIDDLE -> 1; Difficulty.ADVANCED -> 2; Difficulty.HARDCORE -> 3 }
        val pool = mutableListOf<TraderType>()
        fun add(t: TraderType, n: Int) { repeat(n) { pool += t } }
        add(TraderType.EXP_SELLER, 4); add(TraderType.EXP_BUYER, 4); add(TraderType.NEWBIE, 3)
        add(TraderType.BIG, 2); add(TraderType.SLOW, 2); add(TraderType.UNRELIABLE, 1 + risky)
        add(TraderType.SUSPICIOUS, risky); add(TraderType.AGGRESSIVE, risky); add(TraderType.MANIPULATOR, risky)
        return pool
    }

    private fun bps(mid: BigDecimal, bp: Int): BigDecimal =
        mid.multiply(BigDecimal(10000 + bp)).divide(BigDecimal(10000), 2, RoundingMode.HALF_UP)

    fun makeAd(id: Int, trader: Trader, mid: BigDecimal, r: Random): Ad {
        val side = when (trader.type) {
            TraderType.EXP_SELLER -> Side.BUY
            TraderType.EXP_BUYER -> Side.SELL
            else -> if (r.nextBoolean()) Side.BUY else Side.SELL
        }
        var bp = if (side == Side.BUY) r.nextInt(-20, 90) else r.nextInt(-90, 25)
        // Подозрительные и манипуляторы «заманивают» слишком выгодным курсом
        if (trader.type == TraderType.SUSPICIOUS || trader.type == TraderType.MANIPULATOR) {
            bp += if (side == Side.BUY) -r.nextInt(60, 130) else r.nextInt(60, 130)
        }
        val big = trader.type == TraderType.BIG
        val minRub = BigDecimal(r.nextInt(5, 50) * 100)
        val maxRub = BigDecimal(if (big) r.nextInt(200, 600) * 1000 else r.nextInt(15, 150) * 1000)
        val reserve = BigDecimal(if (big) r.nextInt(3000, 12000) else r.nextInt(150, 3500)).setScale(4)
        val banks = Banks.all.indices.shuffled(r).take(r.nextInt(1, 4))
        return Ad(id, trader.id, side, bps(mid, bp), minRub, maxRub, reserve, banks)
    }

    fun populate(s: GameState, r: Random, count: Int = 26): GameState {
        var next = s.nextId
        val pool = typePool(s.difficulty)
        val newTraders = mutableListOf<Trader>()
        val newAds = mutableListOf<Ad>()
        repeat(count) {
            val tr = traders.generate(next++, pool[r.nextInt(pool.size)], r)
            newTraders += tr
            newAds += makeAd(next++, tr, s.mid, r)
        }
        return s.copy(nextId = next, traders = s.traders + newTraders, ads = newAds)
    }
}

class MarketEngine(
    private val price: PriceEngine = PriceEngine(),
    private val gen: MarketGenerator = MarketGenerator(),
) {
    fun newGame(seed: Long, difficulty: Difficulty): GameState {
        val r = rng(seed, 1L)
        val mid = price.initial(r)
        val base = GameState(
            seed = seed,
            difficulty = difficulty,
            bankBalances = listOf(BigDecimal("40000.00"), BigDecimal("30000.00"), BigDecimal("30000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            mid = mid,
            priceHistory = listOf(mid),
            skills = Skill.all.associateWith { 30 },
            equityHistory = listOf(BigDecimal("100000.00")),
        )
        return gen.populate(base, r).note("Добро пожаловать! Начальный капитал 100 000 ₽ — виртуальный.")
    }

    /** Новый торговый день: новый рынок, баланс и статистика сохраняются. */
    fun newDay(s: GameState): GameState {
        val completed = s.trades.count { it.success }
        val summary = DaySummary(s.day, s.equity, completed - s.dayStartCompleted, s.equity - s.dayStartEquity)
        val nextDay = s.day + 1
        val seed = s.seed * 6364136223846793005L + nextDay
        val r = rng(seed, nextDay.toLong())
        val gap = r.nextInt(-80, 81) * (s.difficulty.volBp.coerceAtLeast(8)) / 15
        val newMid = price.step(s.mid, s.difficulty.volBp, 0, gap, r)
        val keepIds = s.activeTrades.map { it.traderId }.toSet()
        var n = s.copy(
            seed = seed, day = nextDay, clockMin = (nextDay - 1) * 1440 + 540,
            mid = newMid, priceHistory = (s.priceHistory + newMid).takeLast(96),
            pressureBp = 0, events = s.events.filter { it.untilMin > (nextDay - 1) * 1440 },
            traders = s.traders.filter { it.id in keepIds }, ads = emptyList(),
            dayHistory = s.dayHistory + summary, dayStartEquity = s.equity, dayStartCompleted = completed,
        )
        n = gen.populate(n, r)
        n = n.copy(traders = n.traders.map { it }) // кэш не нужен, список уже обновлён
        n = event(n, r, force = true)
        return n.note("Начался торговый день $nextDay. Рынок обновлён.").recordEquity()
    }

    private fun event(s: GameState, r: Random, force: Boolean): GameState {
        if (!force && r.nextInt(100) >= 8) return s
        return when (r.nextInt(6)) {
            0 -> shock(s, r.nextInt(60, 120), "Всплеск спроса на USDT: курс растёт")
            1 -> shock(s, -r.nextInt(60, 120), "Приток предложения: курс снижается")
            2 -> shock(s, r.nextInt(120, 220), "Новости о регулировании: резкий скачок курса")
            3 -> shock(s, -r.nextInt(100, 200), "Резкая распродажа на рынке")
            4 -> {
                val b = r.nextInt(Banks.all.size)
                val e = MarketEvent(s.clockMin, "Сбой у банка ${Banks.all[b].name}: переводы задерживаются", 0, b, s.clockMin + 120)
                s.copy(events = s.events + e).note(e.title, b)
            }
            else -> {
                val cut = s.ads.filter { it.side == Side.BUY }.sortedBy { it.rate }.take(3).map { it.id }.toSet()
                val e = MarketEvent(s.clockMin, "Крупный продавец покинул рынок: выгодные объявления пропали", 0)
                s.copy(ads = s.ads.filter { it.id !in cut }, events = s.events + e).note(e.title)
            }
        }
    }

    private fun shock(s: GameState, bp: Int, title: String): GameState {
        val newMid = s.mid.multiply(BigDecimal(10000 + bp)).divide(BigDecimal(10000), 2, RoundingMode.HALF_UP)
        val ratio = newMid
        val moved = s.ads.map { it.copy(rate = it.rate.multiply(ratio).divide(s.mid, 2, RoundingMode.HALF_UP)) }
        return s.copy(mid = newMid, ads = moved, events = s.events + MarketEvent(s.clockMin, title, bp)).note(title)
    }

    /** Каждые 15 минут: цена, давление действий пользователя, объявления, события. */
    fun tick(s: GameState): GameState {
        val r = rng(s.seed, s.clockMin.toLong())
        val newMid = price.step(s.mid, s.difficulty.volBp, s.pressureBp, 0, r)
        var ads = s.ads.mapNotNull { ad ->
            if (r.nextInt(100) < 6) null
            else {
                val drift = r.nextInt(-5, 6)
                val rate = ad.rate.multiply(newMid).divide(s.mid, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(10000 + drift)).divide(BigDecimal(10000), 2, RoundingMode.HALF_UP)
                val reserve = if (r.nextInt(100) < 15) ad.reserveUsdt.multiply(BigDecimal(r.nextInt(60, 111))).divide(BigDecimal(100), 4, RoundingMode.HALF_UP) else ad.reserveUsdt
                ad.copy(rate = rate, reserveUsdt = reserve)
            }
        }
        var n = s.copy(
            mid = newMid, priceHistory = (s.priceHistory + newMid).takeLast(96),
            pressureBp = s.pressureBp * 6 / 10, ads = ads,
        )
        // Убираем участников, у которых не осталось объявлений и активных сделок
        val liveIds = ads.map { it.traderId }.toSet() + s.activeTrades.map { it.traderId }
        n = n.copy(traders = n.traders.filter { it.id in liveIds })
        // Новые участники приходят на рынок
        if (n.ads.size < 22 || r.nextInt(100) < 25) {
            val g = MarketGenerator()
            val tg = TraderGenerator()
            val pool = TraderType.values().toList()
            val tid = n.nextId
            val tr = tg.generate(tid, pool[r.nextInt(pool.size)], r)
            val ad = g.makeAd(tid + 1, tr, n.mid, r)
            n = n.copy(nextId = tid + 2, traders = n.traders + tr, ads = n.ads + ad)
        }
        return event(n, r, force = false)
    }
}
