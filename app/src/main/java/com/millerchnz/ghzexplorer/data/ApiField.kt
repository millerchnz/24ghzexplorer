package com.millerchnz.ghzexplorer.data

/**
 * One value an Android radio API reported. A null [raw] means the platform did not
 * report it, which the UI draws as an em dash so a missing reading never looks like zero.
 */
data class ApiField(
    val section: String,
    val label: String,
    val raw: String?,
    val unit: String? = null,
) {
    val display: String
        get() = when {
            raw == null -> "—"
            unit.isNullOrBlank() -> raw
            else -> "$raw $unit"
        }
}

fun MutableList<ApiField>.reported(
    section: String,
    label: String,
    raw: String?,
    unit: String? = null,
) {
    add(ApiField(section, label, raw?.takeIf { it.isNotBlank() }, unit))
}

fun MutableList<ApiField>.reportedInt(
    section: String,
    label: String,
    value: Int,
    unit: String? = null,
    unavailable: Int = Int.MAX_VALUE,
) {
    reported(section, label, value.takeUnless { it == unavailable }?.toString(), unit)
}

fun MutableList<ApiField>.reportedLong(
    section: String,
    label: String,
    value: Long,
    unit: String? = null,
) {
    reported(section, label, value.takeUnless { it == Long.MAX_VALUE }?.toString(), unit)
}

fun MutableList<ApiField>.reportedBool(section: String, label: String, value: Boolean) {
    add(ApiField(section, label, if (value) "yes" else "no"))
}
