package com.millerchnz.ghzexplorer.data

import java.util.Locale

data class ExportDevice(
    val title: String,
    val subtitle: String,
    val fields: List<ApiField>,
    val seen: SeenSpan? = null,
)

/** Plain-text copy of one device's fields, suitable for a .txt share. */
object ExportText {
    fun fileName(kind: String, title: String): String {
        val safe = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "device" }
        return "$kind-$safe.txt"
    }

    fun document(
        kind: String,
        title: String,
        fields: List<ApiField>,
        stopped: Boolean,
        atMillis: Long = System.currentTimeMillis(),
        seen: SeenSpan? = null,
    ): String = buildString {
        appendLine("2.4 GHz Explorer")
        appendLine(kind)
        appendLine(title)
        appendLine("Exported: ${RadioText.formatTime(atMillis)}")
        if (stopped) {
            appendLine("Refresh stopped because this device was no longer heard. This file is the last reading.")
        }
        appendLine()
        appendSeen(seen)
        appendFields(fields)
    }

    fun listDocument(
        kind: String,
        devices: List<ExportDevice>,
        atMillis: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("2.4 GHz Explorer")
        appendLine(kind)
        appendLine("Exported: ${RadioText.formatTime(atMillis)}")
        appendLine("${devices.size} devices")
        appendLine()
        devices.forEach { device ->
            appendLine("==== ${device.title} ====")
            if (device.subtitle.isNotBlank()) appendLine(device.subtitle)
            appendLine()
            appendSeen(device.seen)
            appendFields(device.fields)
        }
    }

    private fun StringBuilder.appendSeen(seen: SeenSpan?) {
        appendLine("[Seen]")
        appendLine("First location: ${seen?.first?.let(::formatLocation) ?: "—"}")
        appendLine("First strength: ${seen?.first?.let { "${it.rssiDbm} dBm" } ?: "—"}")
        appendLine("Last location: ${seen?.last?.let(::formatLocation) ?: "—"}")
        appendLine("Last strength: ${seen?.last?.let { "${it.rssiDbm} dBm" } ?: "—"}")
        appendLine()
    }

    private fun formatLocation(place: SeenPlace): String =
        String.format(Locale.US, "%.6f, %.6f", place.latitude, place.longitude)

    private fun StringBuilder.appendFields(fields: List<ApiField>) {
        fields.groupBy { it.section }.forEach { (section, rows) ->
            appendLine("[$section]")
            rows.forEach { field ->
                val value = field.display
                if ('\n' in value) {
                    appendLine("${field.label}:")
                    appendLine(value)
                } else {
                    appendLine("${field.label}: $value")
                }
            }
            appendLine()
        }
    }
}
