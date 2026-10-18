package com.millerchnz.ghzexplorer.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.millerchnz.ghzexplorer.data.AppGraph

fun Map<String, String>.displayName(id: String, reported: String): String =
    this[id]?.takeIf { it.isNotBlank() } ?: reported

/** Edits the name saved for [deviceId]. An empty save clears it. */
@Composable
fun AliasCard(deviceId: String) {
    val saved by AppGraph.aliases.names.collectAsStateWithLifecycle()
    var draft by remember(deviceId) { mutableStateOf(saved[deviceId].orEmpty()) }
    SectionCard(
        title = "Alias",
        subtitle = "Shown instead of the name the radio reported. Saved for this id, so it returns when that id is seen again.",
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Alias") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { AppGraph.aliases.set(deviceId, draft) }),
        )
        Spacer(Modifier.height(8.dp))
        Text(deviceId, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { AppGraph.aliases.set(deviceId, draft) }) {
            Text(if (draft.isBlank()) "Clear alias" else "Save alias")
        }
    }
}
