package com.corlang.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import kotlinx.coroutines.launch

/**
 * A short check across all levels. Walking from easiest to hardest, the learner's placement is
 * the last question they answer correctly before their first miss (a "you're solid up to here"
 * rule). The result sets the current lesson day and level, so nobody has to start at day 1.
 */
@Composable
fun PlacementScreen(container: AppContainer, lang: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val test = remember(lang) { container.content.placement(lang) }
    if (test == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            com.corlang.app.ui.components.CorlangLogo(
                variant = com.corlang.app.ui.components.LogoVariant.ORBIT,
                size = 56.dp,
                brand = MaterialTheme.colorScheme.onSurfaceVariant,
                core = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "No placement test for this language yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(onClick = onDone, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
        }
        return
    }
    val questions = remember(lang) { test.questions.sortedBy { it.difficulty } }

    var index by remember(lang) { mutableIntStateOf(0) }
    var chosen by remember(lang) { mutableStateOf<String?>(null) }
    var checked by remember(lang) { mutableStateOf(false) }
    // Placement so far: the last correct answer's day/level before the first miss.
    var placeDay by remember(lang) { mutableIntStateOf(1) }
    var placeLevel by remember(lang) { mutableStateOf("A0") }
    var stopped by remember(lang) { mutableStateOf(false) }   // hit first miss
    var finished by remember(lang) { mutableStateOf(false) }

    if (finished) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text("You're placed at", style = MaterialTheme.typography.titleMedium)
            Text(
                "$placeLevel · Day $placeDay",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                "Your lessons will start here. Earlier days stay available to review any time, and " +
                    "you can retake this test from Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    scope.launch { container.progress.setPlacement(lang, placeDay, placeLevel) }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) { Text("Start at $placeLevel · Day $placeDay") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
        return
    }

    val q = questions[index]
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (index == 0) {
            Text(test.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(test.intro, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp))
        }
        LinearProgressIndicator(
            progress = { (index + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text("Question ${index + 1} / ${questions.size}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(q.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp))

        val shown = remember(index) { q.options.shuffled() }
        shown.forEach { option ->
            val isChosen = chosen == option
            val border = when {
                !checked && isChosen -> MaterialTheme.colorScheme.primary
                checked && option == q.answer -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .border(2.dp, border, RoundedCornerShape(10.dp))
                    .clickable(enabled = !checked) { chosen = option }
            ) { Text(option, modifier = Modifier.padding(12.dp)) }
        }

        Button(
            onClick = {
                if (!checked) {
                    val correct = chosen == q.answer
                    if (correct && !stopped) { placeDay = q.startDay; placeLevel = q.level }
                    if (!correct) stopped = true   // stop advancing placement at the first miss
                    checked = true
                } else if (index + 1 >= questions.size) {
                    finished = true
                } else {
                    index++; chosen = null; checked = false
                }
            },
            enabled = checked || chosen != null,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text(
                when {
                    !checked -> "Check"
                    index + 1 >= questions.size -> "See placement"
                    else -> "Next"
                }
            )
        }
        // Let them bail out early once placed; remaining questions only confirm the ceiling.
        if (checked && stopped) {
            OutlinedButton(
                onClick = { finished = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Skip the rest, place me now") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
