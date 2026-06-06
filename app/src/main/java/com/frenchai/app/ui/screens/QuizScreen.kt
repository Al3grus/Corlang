package com.frenchai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenchai.app.AppContainer
import com.frenchai.app.data.model.Question
import com.frenchai.app.data.model.QuestionType
import com.frenchai.app.data.model.Quiz
import com.frenchai.app.ui.components.InfoCard
import kotlinx.coroutines.launch

/**
 * Quiz flow: pick a quiz -> answer 10 questions ordered easy->hard -> each answer is graded
 * instantly with an explanation of what was missed -> final score is recorded per language.
 */
@Composable
fun QuizScreen(container: AppContainer, lang: String) {
    val quizzes = remember(lang) { container.content.quizzes(lang).quizzes }
    var active by remember(lang) { mutableStateOf<Quiz?>(null) }

    if (active == null) {
        QuizList(container, lang, quizzes) { active = it }
    } else {
        QuizRunner(container, lang, active!!) { active = null }
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

    var index by remember(quiz.id) { mutableStateOf(0) }
    var score by remember(quiz.id) { mutableStateOf(0) }
    var checked by remember(quiz.id) { mutableStateOf(false) }
    var lastCorrect by remember(quiz.id) { mutableStateOf(false) }
    var finished by remember(quiz.id) { mutableStateOf(false) }

    // Per-question response state.
    var selectedOption by remember(quiz.id) { mutableStateOf<String?>(null) }
    var fillText by remember(quiz.id) { mutableStateOf("") }
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
                q.options.forEach { option ->
                    val isChosen = selectedOption == option
                    val border = when {
                        !checked && isChosen -> MaterialTheme.colorScheme.primary
                        checked && option == q.answer -> Color(0xFF2E7D32)
                        checked && isChosen -> Color(0xFFC62828)
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
                Text(
                    "Tap the words in the correct order:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    q.options.forEach { token ->
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
                                    checked && selected && isThisPairCorrect -> Color(0xFFC8E6C9)
                                    checked && selected && !isThisPairCorrect -> Color(0xFFFFCDD2)
                                    checked && isThisPairCorrect -> Color(0xFFC8E6C9)
                                    selected -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = color,
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
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (lastCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        if (lastCorrect) "✅ Correct" else "❌ Not quite",
                        fontWeight = FontWeight.Bold,
                        color = if (lastCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
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
                    if (correct) score++
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
