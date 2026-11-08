package com.p2pacademy.chat

import com.p2pacademy.domain.*
import kotlin.random.Random

enum class Quick(val label: String) {
    STATUS("Как статус?"),
    PROOF("Пришлите подтверждение"),
    WAIT("Подожду ещё немного"),
    NO_RESEND("Повторно переводить не буду"),
    STAY("Общаемся только здесь"),
    ARBITRATION("Предлагаю арбитраж"),
    WHY("Объясните причину"),
}

class ChatEngine {

    private fun rush(trader: Trader) = trader.type == TraderType.AGGRESSIVE || trader.type == TraderType.MANIPULATOR

    /** Сообщения, которые придут сами после создания заявки (пользователь продаёт). */
    fun onCreate(t: Trade, trader: Trader, r: Random): List<SchedMsg> {
        val c = t.createdMin
        val list = mutableListOf<SchedMsg>()
        when {
            t.side == Side.SELL && t.scenario == Scenario.S_NO_SHOW ->
                list += SchedMsg(c + 20, "Извините, не получается оплатить, банк глючит.")
            t.side == Side.SELL && rush(trader) ->
                list += SchedMsg(c + 2, "Я уже готов платить, не тяните время.")
        }
        return list
    }

    /** Сообщения после оплаты контрагентом (пользователь продаёт). */
    fun afterBuyerPaid(t: Trade, trader: Trader, base: Int, r: Random): List<SchedMsg> {
        val list = mutableListOf<SchedMsg>()
        list += SchedMsg(base, "Оплатил(а), чек в чате. Отпускайте.")
        when (t.scenario) {
            Scenario.S_HONEST -> if (rush(trader)) list += SchedMsg(base + 2, "Быстрее, пожалуйста, я спешу!")
            Scenario.S_PENDING_OK -> list += SchedMsg(base + 3, "Банк пишет, что платёж в обработке, скоро придёт.")
            Scenario.S_BANK_HOLD -> list += SchedMsg(base + 3, "Мне позвонили из банка, просят подтвердить операцию. Сейчас решу.")
            Scenario.S_FAKE_RECEIPT -> list += SchedMsg(base + 2, "Деньги ушли! Смотрите чек, чего вы ждёте? Отпускайте!")
            Scenario.S_FAKE_NOTIFY -> list += SchedMsg(base + 2, "Вам должна прийти смс от банка. Вот скрин уведомления, отпускайте.")
            Scenario.S_WRONG_AMOUNT -> list += SchedMsg(base + 3, "Там чуть меньше, но это комиссия банка. Отпускайте, всё ок.")
            Scenario.S_SPLIT -> list += SchedMsg(base + 2, "Перевёл частями, остаток дойдёт позже. Отпускайте уже.")
            Scenario.S_NAME_MISMATCH -> list += SchedMsg(base + 2, "Платил с карты друга, так получилось. Неважно, деньги же пришли.")
            Scenario.S_OLD_RECEIPT -> list += SchedMsg(base + 2, "Чек во вложении. Отпускайте, я тороплюсь!")
            Scenario.S_WRONG_DATE -> list += SchedMsg(base + 2, "Чек у меня сохранился, всё оплачено, отпускайте.")
            Scenario.S_CANCELLED -> list += SchedMsg(base + 3, "Платёж прошёл, банк подтвердил. Отпускайте.")
            Scenario.S_PENDING_REJECT -> list += SchedMsg(base + 3, "Статус «принят в обработку» — это нормально, отпускайте.")
            else -> {}
        }
        if (trader.type == TraderType.MANIPULATOR || trader.type == TraderType.SUSPICIOUS) {
            list += SchedMsg(base + 6, "Давайте напишем в Telegram, там удобнее решить быстро.")
        }
        return list
    }

    /** Сообщения после оплаты пользователем (пользователь покупает). */
    fun afterUserPaid(t: Trade, trader: Trader, op: BankOp, r: Random): List<SchedMsg> {
        val base = t.createdMin
        val now = op.createdMin
        val list = mutableListOf<SchedMsg>()
        when (t.scenario) {
            Scenario.B_HONEST -> list += SchedMsg(op.tFinal + 1, "Вижу поступление, отпускаю.")
            Scenario.B_SLOW -> {
                list += SchedMsg(now + 5, "Секунду, отойду, проверю позже.")
                list += SchedMsg(op.tFinal + 30, "Вернулся, вижу деньги, отпускаю.")
            }
            Scenario.B_BANK_HOLD -> list += SchedMsg(now + 6, "Ваш банк держит платёж на проверке, подожду.")
            Scenario.B_REJECTED -> list += SchedMsg(now + 6, "Деньги не пришли, у вас точно прошёл перевод?")
            Scenario.B_NO_RELEASE -> {
                list += SchedMsg(op.tFinal + 2, "Не вижу поступления. Что-то не так.")
                list += SchedMsg(op.tFinal + 8, "Деньги не пришли, я проверил дважды!")
            }
            Scenario.B_REPEAT -> {
                list += SchedMsg(op.tFinal + 2, "Платёж потерялся в банке. Переведите ещё раз, первый вернётся.")
                list += SchedMsg(op.tFinal + 9, "Ну что, переводите? Время идёт.")
            }
            Scenario.B_ASK_CANCEL -> list += SchedMsg(now + 4, "Ой, реквизиты указал неверно. Отмените заявку, я верну деньги сам.")
            Scenario.B_OFF_PLATFORM -> list += SchedMsg(now + 4, "Напишите мне в Telegram, там быстрее отпущу. Тут всё медленно.")
            else -> {}
        }
        if (rush(trader)) list += SchedMsg(now + 3, "Давайте быстрее, у меня очень мало времени!")
        return list
    }

    fun reply(t: Trade, trader: Trader, q: Quick, r: Random): String {
        val sc = t.scenario
        val rushy = rush(trader)
        return when (q) {
            Quick.STATUS -> when (sc) {
                Scenario.B_NO_RELEASE -> "Платежа нет. Я ничего не получал."
                Scenario.B_REPEAT -> "Пришлось ждать банк, переводите повторно — так быстрее."
                Scenario.B_ASK_CANCEL -> "Я же написал: отменяйте, верну деньги."
                Scenario.B_OFF_PLATFORM -> "Давайте в Telegram, тут не отвечу."
                Scenario.B_HONEST, Scenario.B_SLOW, Scenario.B_BANK_HOLD -> "Проверяю банк, чуть позже отпущу."
                Scenario.S_FAKE_RECEIPT, Scenario.S_FAKE_NOTIFY -> "Всё оплачено, вы просто не умеете проверять."
                Scenario.S_NO_SHOW -> "Сейчас оплачу, подождите."
                else -> if (rushy) "Отпускайте скорее!" else "Платёж отправлен, проверяйте банк."
            }
            Quick.PROOF -> when (sc) {
                Scenario.S_FAKE_RECEIPT, Scenario.S_FAKE_NOTIFY, Scenario.S_OLD_RECEIPT, Scenario.S_WRONG_DATE ->
                    "Я уже всё прислал выше. Сверяйте, а не задавайте вопросы."
                Scenario.S_NAME_MISMATCH -> "Это платёж со счёта друга. Он мой знакомый, всё нормально."
                else -> "Чек выше в чате, проверьте его по истории банка."
            }
            Quick.WAIT -> if (rushy) "Ждать? Я теряю время! Отпускайте." else "Хорошо, подождём."
            Quick.NO_RESEND -> when (sc) {
                Scenario.B_REPEAT -> "Тогда я ничего не отпущу. Решайте сами."
                else -> "Ладно, как скажете."
            }
            Quick.STAY -> when (sc) {
                Scenario.B_OFF_PLATFORM -> "Тогда не буду отвечать здесь."
                else -> "Как скажете, общаемся здесь."
            }
            Quick.ARBITRATION -> if (trader.type == TraderType.MANIPULATOR || sc.fraud)
                "Арбитраж? Не надо, давайте решим между собой!" else "Конечно, арбитраж всё покажет."
            Quick.WHY -> when (sc) {
                Scenario.S_WRONG_AMOUNT -> "Разницу удержал банк, мелочь."
                Scenario.S_NAME_MISMATCH -> "Какая разница, чей это счёт."
                Scenario.B_ASK_CANCEL -> "Ошибся, бывает. Отменяйте."
                else -> "Ничего особенного, просто хочу быстрее закончить."
            }
        }
    }
}
