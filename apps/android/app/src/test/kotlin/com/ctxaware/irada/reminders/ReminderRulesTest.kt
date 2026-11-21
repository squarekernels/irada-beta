package com.ctxaware.irada.reminders

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRulesTest {
    @Test fun nineAmFollowsTheLocalZone() {
        val local = "2026-12-01T09:00:00"
        val karachi = reminderInstant(local, ZoneId.of("Asia/Karachi"))
        val london = reminderInstant(local, ZoneId.of("Europe/London"))
        assertEquals(5, Duration.between(karachi, london).toHours())
        assertEquals(9, london.atZone(ZoneId.of("Europe/London")).hour)
    }
    @Test fun daylightSavingGapMovesForwardAndOverlapUsesFirstOffset() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), reminderInstant("2026-03-08T02:30:00", zone))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), reminderInstant("2026-11-01T01:30:00", zone))
    }
}
