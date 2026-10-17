package com.swipeclean.app.ui

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Date
import java.util.Locale

private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

fun formatDate(millis: Long): String =
    if (millis <= 0L) "Sin fecha" else dateFormat.format(Date(millis))

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun monthName(month: Int): String {
    val name = DateFormatSymbols.getInstance().months.getOrNull(month - 1) ?: month.toString()
    return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"
