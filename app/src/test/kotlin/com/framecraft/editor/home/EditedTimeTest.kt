/*
 * Unit tests for classifying how long ago a project was edited.
 */
package com.framecraft.editor.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Verifies each band of the "Edited ..." rules. */
class EditedTimeTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 10, 29).atTime(12, 0).toInstant(zone).toEpochMilli()

    /** Returns the classification of an edit [agoMillis] before now. */
    private fun ago(agoMillis: Long) = EditedTime.classify(now, now - agoMillis, zone)

    /** Recent edits use minutes and hours. */
    @Test
    fun recentBands() {
        assertEquals(EditedAgo.JustNow, ago(59_000L))
        assertEquals(EditedAgo.JustNow, ago(-5_000L))
        assertEquals(EditedAgo.Minutes(5), ago(5 * 60_000L))
        assertEquals(EditedAgo.Hours(23), ago(23 * 3_600_000L))
        assertEquals(EditedAgo.Yesterday, ago(30 * 3_600_000L))
    }

    /** Older edits show the date, with the year only when it differs. */
    @Test
    fun olderEditsShowDates() {
        assertEquals(EditedAgo.OnDate(LocalDate.of(2026, 10, 24), sameYear = true), ago(5 * 86_400_000L))
        assertEquals(EditedAgo.OnDate(LocalDate.of(2025, 10, 29), sameYear = false), ago(365 * 86_400_000L))
    }
}
