/*
 * "Edited ..." labels for the project list: pure classification of elapsed time, then localized formatting.
 */
package com.framecraft.editor.home

import android.content.res.Resources
import android.text.format.DateFormat
import com.framecraft.editor.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How long ago a project was edited, at the precision the list shows. */
sealed interface EditedAgo {
    /** Less than a minute ago. */
    data object JustNow : EditedAgo

    /** [count] whole minutes ago, under an hour. */
    data class Minutes(
        val count: Int,
    ) : EditedAgo

    /** [count] whole hours ago, under a day. */
    data class Hours(
        val count: Int,
    ) : EditedAgo

    /** Between one and two days ago. */
    data object Yesterday : EditedAgo

    /** Two or more days ago, on [date]; [sameYear] when that is the current year. */
    data class OnDate(
        val date: LocalDate,
        val sameYear: Boolean,
    ) : EditedAgo
}

/** Classifies edit times for the project list. */
object EditedTime {
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** Classifies an edit at [editedMillis] as seen at [nowMillis] in [zone]; future times count as just now. */
    fun classify(
        nowMillis: Long,
        editedMillis: Long,
        zone: ZoneId,
    ): EditedAgo {
        val elapsed = (nowMillis - editedMillis).coerceAtLeast(0L)
        return when {
            elapsed < MINUTE_MILLIS -> EditedAgo.JustNow
            elapsed < HOUR_MILLIS -> EditedAgo.Minutes((elapsed / MINUTE_MILLIS).toInt())
            elapsed < DAY_MILLIS -> EditedAgo.Hours((elapsed / HOUR_MILLIS).toInt())
            elapsed < 2 * DAY_MILLIS -> EditedAgo.Yesterday
            else -> {
                val date = Instant.ofEpochMilli(editedMillis).atZone(zone).toLocalDate()
                val currentYear = Instant.ofEpochMilli(nowMillis).atZone(zone).year
                EditedAgo.OnDate(date, date.year == currentYear)
            }
        }
    }
}

/** Formats [ago] as the list's "Edited ..." label, with dates in [locale]'s month-day order. */
fun editedLabel(
    resources: Resources,
    locale: Locale,
    ago: EditedAgo,
): String =
    when (ago) {
        EditedAgo.JustNow -> resources.getString(R.string.home_edited_just_now)
        is EditedAgo.Minutes -> resources.getQuantityString(R.plurals.home_edited_minutes, ago.count, ago.count)
        is EditedAgo.Hours -> resources.getQuantityString(R.plurals.home_edited_hours, ago.count, ago.count)
        EditedAgo.Yesterday -> resources.getString(R.string.home_edited_yesterday)
        is EditedAgo.OnDate -> {
            val skeleton = if (ago.sameYear) "MMMd" else "yMMMd"
            val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
            val date = DateTimeFormatter.ofPattern(pattern, locale).format(ago.date)
            resources.getString(R.string.home_edited_on, date)
        }
    }
