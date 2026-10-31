package ca.udes.reparecampus.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.CANADA_FRENCH)
        .withZone(ZoneId.systemDefault())

/** "2026-10-29T19:21:33.123Z" -> "29 oct. 2026, 15 h 21" (fuseau de l'appareil). */
fun formatDateTime(iso: String?): String =
    iso?.let { runCatching { dateTimeFormatter.format(Instant.parse(it)) }.getOrNull() } ?: "—"

fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis))

fun formatMoney(amount: Double): String = String.format(Locale.CANADA_FRENCH, "%.2f $", amount)
