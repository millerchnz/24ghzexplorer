package com.millerchnz.ghzexplorer.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.RadioText

data class SignalRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val trailing: String,
    val stale: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalList(
    title: String,
    intro: String,
    status: String,
    rows: List<SignalRow>,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    mapModifier: Modifier = Modifier,
    exportModifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (onMap != null) {
                        TextButton(onClick = onMap, modifier = mapModifier) { Text("Map") }
                    }
                    if (onExport != null) {
                        TextButton(onClick = onExport, modifier = exportModifier) { Text("Export") }
                    }
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction, modifier = actionModifier) {
                            Text(actionLabel)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { banner() }
            item { header() }
            item {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text(text = emptyMessage, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                items(rows, key = { it.key }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(row.key) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val rowColor = if (row.stale) {
                                MaterialTheme.colorScheme.error
                            } else {
                                null
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = rowColor ?: MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    row.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = rowColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (row.trailing.isNotBlank()) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    row.trailing,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = rowColor ?: MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDetail(
    title: String,
    fields: List<ApiField>,
    onBack: () -> Unit,
    missingMessage: String = "This reading is no longer in the latest scan.",
    note: String? = null,
    lastHeardMillis: Long? = null,
    onExport: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
    stale: Boolean = false,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SelectionContainer {
                        Text(
                            title,
                            maxLines = 1,
                            color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onMap != null) {
                        TextButton(onClick = onMap) { Text("Map") }
                    }
                    if (onExport != null && fields.isNotEmpty()) {
                        TextButton(onClick = onExport) { Text("Export") }
                    }
                },
            )
        },
    ) { padding ->
        // Item keys stay put when a field's value changes, so a refresh does not jump the page.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (fields.isNotEmpty()) {
                item(key = "last-heard") {
                    val heard = lastHeardMillis?.takeIf { it > 0L }?.let(RadioText::formatDateTime) ?: "—"
                    SelectionContainer {
                        Text(
                            text = "Last heard: $heard",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (stale) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            if (note != null && fields.isNotEmpty()) {
                item(key = "note") {
                    SelectionContainer {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stale) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            item(key = "header") { header() }
            if (fields.isEmpty()) {
                item(key = "missing") {
                    SelectionContainer {
                        Text(missingMessage, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                fields.groupBy { it.section }.forEach { (section, sectionFields) ->
                    item(key = "section-$section") {
                        SectionCard(title = section) {
                            sectionFields.forEach { field ->
                                FieldLine(field, stale)
                            }
                        }
                    }
                }
            }
            item(key = "footer") { footer() }
            item(key = "end") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FieldLine(field: ApiField, stale: Boolean) {
    val color = when {
        stale && field.raw != null -> MaterialTheme.colorScheme.error
        field.raw == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    SelectionContainer {
        FieldLineText(field, color, stale)
    }
}

@Composable
private fun FieldLineText(field: ApiField, color: androidx.compose.ui.graphics.Color, stale: Boolean) {
    if ('\n' in field.display) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = field.display,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.9f),
            )
            Text(
                text = field.display,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                modifier = Modifier.weight(1.1f),
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun PermissionBanner(
    permissions: Array<String>,
    message: String,
    onChanged: () -> Unit = {},
) {
    if (permissions.isEmpty()) return
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val missing = remember(tick, permissions) { AppPermissions.missing(context, permissions) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        onChanged()
    }
    if (missing.isEmpty()) return
    SectionCard(
        title = "Permission needed",
        subtitle = message,
    ) {
        Text(
            missing.joinToString(", ") { AppPermissions.label(it) },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch(missing.toTypedArray()) }) { Text("Grant") }
            OutlinedButton(onClick = { context.openAppSettings() }) { Text("Settings") }
        }
    }
}

@Composable
fun LocationBanner() {
    val context = LocalContext.current
    if (AppPermissions.locationEnabled(context)) return
    SectionCard(
        title = "Location is off",
        subtitle = "Android withholds Wi-Fi scan results and cell identity until system Location is on. " +
            "Find device also uses it to plot the walk.",
    ) {
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text("Location settings") }
    }
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
