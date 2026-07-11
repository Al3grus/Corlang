package com.corlang.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.model.Question
import com.corlang.app.data.model.QuestionType
import com.corlang.app.data.model.Quiz
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.theme.CorlangColors
import kotlinx.coroutines.launch

/**
 * Practice hub: level quizzes and the official-format mock exam, switched with a
 * segmented control (same pattern as the Learn tab).
 */
@Composable
fun QuizScreen(container: AppContainer, lang: String) {
    var tab by rememberSaveable(lang) { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Quizzes") }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Mock exam") }
        }
        when (tab) {
            0 -> QuizzesTab(container, lang)
            else -> ExamScreen(container, lang)
        }
    }
}

@Composable
private fun QuizzesTab(container: AppContainer, lang: String) {
    val quizzes = remember(lang) { container.content.quizzes(lang).quizzes }
    // Store only the id so an in-progress quiz survives rotation/process recreation.
    var activeId by rememberSaveable(lang) { mutableStateOf<String?>(null) }
    val active = quizzes.firstOrNull { it.id == activeId }

    if (active == null) {
        QuizList(container, lang, quizzes) { activeId = it.id }
    } else {
        QuizRunner(container, lang, active) { activeId = null }
    }
}

@Composable
private fun QuizList(
    container: AppContainer,
    lang: String,
    quizzes: List<Quiz>,
    onPick: (Quiz) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Quizzes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "10 questions each, easy → hard. Graded with explanations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (quizzes.isEmpty()) {
            Text("No quizzes for this language yet — coming soon.")
        }
        quizzes.forEach { quiz ->
            val best by container.progress.bestQuizScore(lang, quiz.id)
                .collectAsState(initial = null)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onPick(quiz) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${quiz.levelId} · ${quiz.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${quiz.questions.size} questions" +
                            (best?.let { " · best ${it}/${quiz.questions.size}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizRunner(
    container: AppContainer,
    lang: String,
    quiz: Quiz,
    onExit: () -> Unit
) {
    val questions = remember(quiz.id) { quiz.questions.sortedBy { it.difficulty } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var index by rememberSaveable(quiz.id) { mutableStateOf(0) }
    var score by rememberSaveable(quiz.id) { mutableStateOf(0) }
    var checked by rememberSaveable(quiz.id) { mutableStateOf(false) }
    var lastCorrect by rememberSaveable(quiz.id) { mutableStateOf(false) }
    var finished by rememberSaveable(quiz.id) { mutableStateOf(false) }

    // Per-question response state.
    var selectedOption by rememberSaveable(quiz.id) { mutableStateOf<String?>(null) }
    var fillText by rememberSaveable(quiz.id) { mutableStateOf("") }
    val reorderAssembled = remember(quiz.id) { mutableStateListOf<String>() }
    val matchMapping = remember(quiz.id) { mutableStateMapOf<String, String>() }

    fun resetResponse() {
        selectedOption = null
        fillText = ""
        reorderAssembled.clear()
        matchMapping.clear()
        checked = false
        lastCorrect = false
    }

    if (finished) {
        QuizSummary(quiz, score, onExit)
        return
    }

    val q = questions[index]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        Text(
            "${quiz.levelId} · ${quiz.title}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        LinearProgressIndicator(
            progress = { (index + 1f) / questions.size },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        Text(
            "Question ${index + 1} / ${questions.size}  ·  difficulty ${q.difficulty}/10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        InfoCard {
            Text(q.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        when (q.type) {
            QuestionType.MCQ -> {
                val feedback = CorlangColors.feedback
                // Shuffle per question so the correct option isn't positionally predictable.
                val shownOptions = remember(q.prompt) { q.options.shuffled() }
                shownOptions.forEach { option ->
                    val isChosen = selectedOption == option
                    val border = when {
                        !checked && isChosen -> MaterialTheme.colorScheme.primary
                        checked && option == q.answer -> feedback.correct
                        checked && isChosen -> feedback.wrong
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(2.dp, border, RoundedCornerShape(10.dp))
                            .clickable(enabled = !checked) { selectedOption = option }
                    ) {
                        Text(option, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            QuestionType.FILL -> {
                OutlinedTextField(
                    value = fillText,
                    onValueChange = { if (!checked) fillText = it },
                    label = { Text("Your answer") },
                    enabled = !checked,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            QuestionType.REORDER -> {
                // Scramble the tokens for display — content lists them in the correct order,
                // and an already-solved puzzle is no exercise.
                val scrambled = remember(q.prompt) {
                    var s = q.options.shuffled()
                    var tries = 0
                    while (s == q.ordered && q.options.distinct().size > 1 && tries < 10) {
                        s = q.options.shuffled(); tries++
                    }
                    s
                }
                Text(
                    "Tap the words in the correct order:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    scrambled.forEach { token ->
                        val used = reorderAssembled.count { it == token } >=
                            q.options.count { it == token }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (used) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable(enabled = !checked && !used) {
                                    reorderAssembled.add(token)
                                }
                        ) { Text(token, modifier = Modifier.padding(10.dp)) }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable(enabled = !checked && reorderAssembled.isNotEmpty()) {
                            if (reorderAssembled.isNotEmpty()) reorderAssembled.removeAt(reorderAssembled.lastIndex)
                        }
                ) {
                    Text(
                        if (reorderAssembled.isEmpty()) "(tap words above — tap here to undo)"
                        else reorderAssembled.joinToString(" "),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            QuestionType.MATCH -> {
                val feedback = CorlangColors.feedback
                // Shuffle the right-hand options once per question so the answer order isn't given away.
                val rights = remember(q.prompt) { q.pairs.map { it.right }.distinct().shuffled() }
                Text(
                    "Tap to match each item with its pair:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                q.pairs.forEach { pair ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(pair.left, fontWeight = FontWeight.SemiBold)
                        FlowRow(modifier = Modifier.fillMaxWidth()) {
                            rights.forEach { right ->
                                val selected = matchMapping[pair.left] == right
                                val isThisPairCorrect = pair.right == right
                                val color = when {
                                    checked && selected && isThisPairCorrect -> feedback.correctContainer
                                    checked && selected && !isThisPairCorrect -> feedback.wrongContainer
                                    checked && isThisPairCorrect -> feedback.correctContainer
                                    selected -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val onColor = when {
                                    checked && isThisPairCorrect -> feedback.onCorrectContainer
                                    checked && selected -> feedback.onWrongContainer
                                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = color,
                                    contentColor = onColor,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable(enabled = !checked) { matchMapping[pair.left] = right }
                                ) { Text(right, modifier = Modifier.padding(10.dp)) }
                            }
                        }
                    }
                }
            }
        }

        // Feedback after checking.
        if (checked) {
            val feedback = CorlangColors.feedback
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (lastCorrect) feedback.correctContainer else feedback.wrongContainer,
                contentColor = if (lastCorrect) feedback.onCorrectContainer else feedback.onWrongContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        if (lastCorrect) "✅ Correct" else "❌ Not quite",
                        fontWeight = FontWeight.Bold
                    )
                    if (!lastCorrect) {
                        val correct = when (q.type) {
                            QuestionType.REORDER -> q.ordered.joinToString(" ")
                            QuestionType.MATCH -> q.pairs.joinToString("; ") { "${it.left} → ${it.right}" }
                            else -> q.answer
                        }
                        Text("Answer: $correct", fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(q.explanation, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Action button: Check -> Next/Finish.
        Button(
            onClick = {
                if (!checked) {
                    val correct = when (q.type) {
                        QuestionType.MCQ -> selectedOption?.let { Grading.gradeMcq(q, it) } ?: false
                        QuestionType.FILL -> Grading.gradeFill(q, fillText)
                        QuestionType.REORDER -> Grading.gradeReorder(q, reorderAssembled.toList())
                        QuestionType.MATCH -> Grading.gradeMatch(q, matchMapping.toMap())
                    }
                    lastCorrect = correct
                    if (correct) { score++; Haptics.confirm(context) } else Haptics.reject(context)
                    checked = true
                } else {
                    if (index + 1 >= questions.size) {
                        scope.launch {
                            container.progress.recordQuiz(lang, quiz.id, score, questions.size)
                        }
                        finished = true
                    } else {
                        index++
                        resetResponse()
                    }
                }
            },
            enabled = checked || when (q.type) {
                QuestionType.MCQ -> selectedOption != null
                QuestionType.FILL -> fillText.isNotBlank()
                QuestionType.REORDER -> reorderAssembled.isNotEmpty()
                QuestionType.MATCH -> matchMapping.size == q.pairs.size
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Text(
                when {
                    !checked -> "Check answer"
                    index + 1 >= questions.size -> "See results"
                    else -> "Next question"
                }
            )
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Exit quiz")
        }
    }
}

@Composable
private fun QuizSummary(quiz: Quiz, score: Int, onExit: () -> Unit) {
    val total = quiz.questions.size
    val pct = if (total == 0) 0 else score * 100 / total
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quiz complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "$score / $total  ($pct%)",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Text(
            when {
                pct >= 90 -> "Excellent — you've got this cold."
                pct >= 70 -> "Solid. Review the ones you missed and move on."
                pct >= 50 -> "Getting there. Redo this quiz after the cheatsheet."
                else -> "Worth re-studying this level before continuing."
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Back to quizzes")
        }
    }
}
