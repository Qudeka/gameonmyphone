package com.p2pacademy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.p2pacademy.domain.*
import com.p2pacademy.education.*
import com.p2pacademy.investigations.CaseFile
import com.p2pacademy.investigations.Cases
import com.p2pacademy.ui.theme.P2PColors

@Composable
fun LearnScreen(s: GameState, go: (String) -> Unit) {
    val rec = EducationManager().recommend(s).map { it.id }.toSet()
    ScreenColumn {
        H1("Академия")
        Muted("Пройдено ${s.lessonsDone.size} из ${Lessons.all.size}. Уроки подбираются по вашим слабым навыкам.")
        Lessons.all.forEach { l ->
            Panel(onClick = { go("lesson/${l.id}") }) {
                Text(l.title)
                Muted("Навык: ${l.skill} · ${s.skill(l.skill)}/100")
                if (l.id in s.lessonsDone) Pill("Пройден", P2PColors.Profit)
                else if (l.id in rec) Pill("Рекомендуем", P2PColors.Warning)
            }
        }
    }
}

@Composable
fun LessonScreen(vm: GameViewModel, id: String, back: () -> Unit) {
    val lesson = Lessons.all.find { it.id == id }
    ScreenColumn {
        if (lesson == null) { Muted("Урок не найден"); return@ScreenColumn }
        val practiceQ = remember(id) { lesson.practice?.let { PracticeEngine().generate(it, System.nanoTime()) } }
        val questions = remember(id) { listOfNotNull(practiceQ) + lesson.quiz }
        val answers = remember(id) { mutableStateListOf<Int?>().also { l -> repeat(questions.size) { l.add(null) } } }
        var reveal by remember(id) { mutableStateOf(false) }

        H1(lesson.title)
        H2("Теория"); Text(lesson.theory)
        H2("Пример"); Text(lesson.example)
        H2(if (practiceQ != null) "Практика и проверка знаний" else "Проверка знаний")
        questions.forEachIndexed { i, q -> QuestionBlock(i, q, answers[i], reveal) { answers[i] = it } }
        if (!reveal) {
            PrimaryBtn("Проверить", enabled = answers.all { it != null }) {
                reveal = true
                val right = questions.indices.count { answers[it] == questions[it].correct }
                vm.completeLesson(lesson.id, right, questions.size)
            }
        } else {
            val right = questions.indices.count { answers[it] == questions[it].correct }
            Text("Результат: $right из ${questions.size}")
            Muted(if (right * 3 >= questions.size * 2) "Урок засчитан." else "Урок не засчитан: изучите разбор ошибок выше и пройдите снова.", 13)
            SecondaryBtn("Назад") { back() }
        }
    }
}

@Composable
fun PracticeScreen(vm: GameViewModel, s: GameState) {
    var type by remember { mutableIntStateOf(0) }
    var nonce by remember { mutableStateOf(System.nanoTime()) }
    var sel by remember { mutableStateOf<Int?>(null) }
    var reveal by remember { mutableStateOf(false) }
    val types = PracticeType.values()
    val q = remember(type, nonce) { PracticeEngine().generate(types[type], nonce) }
    ScreenColumn {
        H1("Практика")
        Muted("Задания генерируются заново с вычисляемыми ответами. Решено верно: ${s.practiceRight} из ${s.practiceTotal}.")
        Chips(types.map { it.title }, type) { type = it; nonce = System.nanoTime(); sel = null; reveal = false }
        QuestionBlock(0, q, sel, reveal) { sel = it }
        if (!reveal) PrimaryBtn("Ответить", enabled = sel != null) {
            reveal = true
            vm.recordPractice(types[type].skill, sel == q.correct)
        } else PrimaryBtn("Следующее задание") { nonce = System.nanoTime(); sel = null; reveal = false }
    }
}

@Composable
fun ExamScreen(vm: GameViewModel, s: GameState) {
    var started by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val answers = remember { mutableStateListOf<Int?>() }
    var outcome by remember { mutableStateOf<ExamEngine.Outcome?>(null) }
    ScreenColumn {
        H1("Итоговый экзамен")
        val o = outcome
        when {
            !started -> {
                Muted("14 заданий без подсказок: анализ рынка, расчёты, проверка платежей, безопасность, капитал и стресс. Задания генерируются заново при каждой попытке.")
                Muted("Для высокого уровня нужны устойчивые навыки: не менее 80% и не менее 60% в каждой категории, и так в двух разных экзаменах. Простое прохождение сертификата не даёт.")
                s.examResults.takeLast(3).reversed().forEach { Muted("Прошлая попытка: ${it.scorePct}% — ${if (it.passed) "сдан" else "не сдан"}", 13) }
                PrimaryBtn("Начать экзамен") {
                    questions = vm.startExam(); answers.clear(); repeat(questions.size) { answers.add(null) }
                    outcome = null; started = true
                }
            }
            o == null -> {
                questions.forEachIndexed { i, q -> QuestionBlock(i, q, answers[i], false) { answers[i] = it } }
                PrimaryBtn("Завершить экзамен", enabled = answers.all { it != null }) {
                    outcome = vm.finishExam(answers.map { it ?: -1 })
                }
            }
            else -> {
                H2("Результат: ${o.result.scorePct}% — ${if (o.result.passed) "СДАН" else "НЕ СДАН"}")
                Text(o.level)
                H2("По категориям")
                o.result.byCategory.forEach { (k, v) ->
                    KV(k, "$v%", if (v >= 70) P2PColors.Profit else P2PColors.Loss)
                    LinearProgressIndicator(progress = { v / 100f }, modifier = Modifier.fillMaxWidth())
                }
                if (o.weak.isNotEmpty()) { H2("Слабые места"); o.weak.forEach { Text("• $it") } }
                H2("Разбор")
                questions.forEachIndexed { i, q -> QuestionBlock(i, q, answers[i], true) {} }
                SecondaryBtn("Вернуться") { started = false; outcome = null }
            }
        }
    }
}

@Composable
fun CasesScreen(s: GameState, go: (String) -> Unit) {
    var gen by remember { mutableIntStateOf(0) }
    ScreenColumn {
        H1("Расследования")
        Muted("Изучите улики и определите, где проблема, кто виноват, есть ли мошенничество и какое решение безопаснее.")
        PrimaryBtn("Сгенерировать новое дело") {
            val seed = System.nanoTime() % 1_000_000L
            go("case/g$seed-$gen"); gen++
        }
        Cases.library.forEach { c ->
            Panel(onClick = { go("case/${c.id}") }) {
                Text(c.title)
                Muted("Сложность: ${"★".repeat(c.level)}")
                s.casesDone[c.id]?.let { Pill("Лучший результат: $it%", if (it >= 75) P2PColors.Profit else P2PColors.Warning) }
            }
        }
    }
}

@Composable
fun CaseScreen(vm: GameViewModel, id: String, back: () -> Unit) {
    val case: CaseFile? = remember(id) { Cases.find(id) }
    ScreenColumn {
        if (case == null) { Muted("Дело не найдено"); return@ScreenColumn }
        val answers = remember(id) { mutableStateListOf<Int?>().also { l -> repeat(case.questions.size) { l.add(null) } } }
        var score by remember(id) { mutableStateOf<Int?>(null) }
        H1(case.title)
        Muted("Сложность: ${"★".repeat(case.level)}")
        Panel { H2("Материалы дела"); case.evidence.forEach { Text("• $it") } }
        case.questions.forEachIndexed { i, q -> QuestionBlock(i, q, answers[i], score != null) { answers[i] = it } }
        val sc = score
        if (sc == null) PrimaryBtn("Закрыть дело", enabled = answers.all { it != null }) {
            score = vm.finishCase(case, answers.map { it ?: -1 })
        } else {
            Text("Результат: $sc%", color = if (sc >= 75) P2PColors.Profit else P2PColors.Warning)
            SecondaryBtn("К списку дел") { back() }
        }
    }
}
