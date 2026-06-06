package com.frenchai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenchai.app.AppContainer
import com.frenchai.app.data.model.FeynmanConcept
import com.frenchai.app.ui.components.InfoCard
import com.frenchai.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * Feynman teach-back loop: read a plain-English explanation, re-explain it in your own words,
 * then self-check against the rubric. Missed points reveal a re-teach snippet so you can loop
 * until you can explain the concept cleanly on your own.
 */
@Composable
fun TeachScreen(container: AppContainer, lang: String) {
    val concepts = remember(lang) { container.content.feynman(lang).concepts }
    var active by remember(lang) { mutableStateOf<FeynmanConcept?>(null) }

    if (active == null) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Teach it back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "The fastest way to know you understand something: explain it simply. " +
                    "Read, re-explain in your own words, then check what you missed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (concepts.isEmpty()) Text("No concepts for this language yet — coming soon.")
            concepts.forEach { c ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { active = c }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${c.levelId} · ${c.title}", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    } else {
        FeynmanRunner(container, lang, active!!) { active = null }
    }
}

@Composable
private fun FeynmanRunner(
    container: AppContainer,
    lang: String,
    concept: FeynmanConcept,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var myExplanation by remember(concept.id) { mutableStateOf("") }
    var revealed by remember(concept.id) { mutableStateOf(false) }
    // Which rubric points the learner says they covered.
    val covered = remember(concept.id) { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("${concept.levelId} · ${concept.title}",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        InfoCard {
            SectionTitle("📖 In the simplest terms")
            Text(concept.simpleExplanation, style = MaterialTheme.typography.bodyMedium)
            SectionTitle("💡 Analogy")
            Text(concept.analogy, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }

        SectionTitle("🗣️ Now explain it back in your own words")
        OutlinedTextField(
            value = myExplanation,
            onValueChange = { myExplanation = it },
            label = { Text("Type your explanation (or say it aloud, then jot the gist)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        if (!revealed) {
            Button(
                onClick = { revealed = true },
                enabled = myExplanation.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Check what I missed") }
        } else {
            SectionTitle("✅ Did you cover these? Tick the ones you got")
            concept.rubricPoints.forEachIndexed { i, rp ->
                val got = covered[i] ?: false
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = got, onCheckedChange = { covered[i] = it })
                            Text(rp.point, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                        if (!got) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    "Re-teach: ${rp.reTeach}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            val gotCount = concept.rubricPoints.indices.count { covered[it] == true }
            val total = concept.rubricPoints.size
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Text(
                    if (gotCount == total)
                        "🎉 You covered all $total points — you can explain this on your own."
                    else
                        "You covered $gotCount / $total. Re-read the missed points above, then explain again.",
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        // Loop: clear and try again, keeping the same concept.
                        myExplanation = ""
                        revealed = false
                        covered.clear()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Explain again") }
                Button(
                    onClick = {
                        scope.launch { container.progress.recordFeynman(lang, concept.id, gotCount, total) }
                        onExit()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save & finish") }
            }
        }

        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
            Text("Back to concepts")
        }
    }
}
