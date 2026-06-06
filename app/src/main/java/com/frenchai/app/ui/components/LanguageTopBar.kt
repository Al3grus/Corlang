package com.frenchai.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frenchai.app.data.model.LanguageMeta

/** Top bar showing the app title and a language picker (flag + name) on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageTopBar(
    languages: List<LanguageMeta>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = languages.firstOrNull { it.code == selected } ?: languages.firstOrNull()

    TopAppBar(
        title = { Text("Frenchai", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
            Row {
                IconButton(onClick = { expanded = true }) {
                    Row(modifier = Modifier.padding(end = 4.dp)) {
                        Text(
                            (current?.flagEmoji ?: "") + "  " + (current?.name ?: ""),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose language")
                    }
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
    )
}
