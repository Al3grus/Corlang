package com.corlang.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corlang.app.data.model.LanguageMeta

/**
 * Top bar showing the app title and a language picker (flag + name) on the right.
 *
 * [pickerEnabled] = false locks the picker while the learner is mid-session (lesson, review,
 * quiz, exam…): the current language stays visible as a static badge, but the dropdown can't
 * open — a mid-session switch would tear down the session and lose partial work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageTopBar(
    languages: List<LanguageMeta>,
    selected: String,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit = {},
    pickerEnabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val current = languages.firstOrNull { it.code == selected } ?: languages.firstOrNull()

    // Dark-only app: the bar sits on surface so it stays quiet against the ink background.
    val barColor = MaterialTheme.colorScheme.surface
    val onBar = MaterialTheme.colorScheme.onSurface

    TopAppBar(
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                // Brand mark in the header, per the logo usage checklist. Rings take the bar's
                // text color so they read on the blue/surface bar; the core keeps its red pop.
                CorlangLogo(variant = LogoVariant.ORBIT, size = 26.dp, brand = onBar)
                Text("Corlang", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            titleContentColor = onBar,
            actionIconContentColor = onBar
        ),
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = onBar)
            }
            if (languages.size <= 1 || !pickerEnabled) {
                // Single language, or picker locked mid-session: a static badge, no dropdown.
                Text(
                    (current?.flagEmoji ?: "") + "  " +
                        (if (languages.size <= 1) current?.nativeName ?: "" else current?.name ?: ""),
                    color = onBar,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            } else {
                Row {
                    // TextButton, not IconButton: IconButton clips its content to a 40dp circle,
                    // which would crop the flag+name label the moment a 2nd language ships.
                    TextButton(onClick = { expanded = true }) {
                        Text(
                            (current?.flagEmoji ?: "") + "  " + (current?.name ?: ""),
                            color = onBar,
                            fontSize = 15.sp
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose language", tint = onBar)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.flagEmoji}  ${lang.name}  (${lang.nativeName})") },
                                onClick = {
                                    onSelect(lang.code)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}
