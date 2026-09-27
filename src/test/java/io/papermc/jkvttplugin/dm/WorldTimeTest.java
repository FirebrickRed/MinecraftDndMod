package io.papermc.jkvttplugin.dm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The DM's clock: durations typed in, the time shown, and moving it without drifting off the minute. */
class WorldTimeTest {

    @Test
    void durationsParse() {
        assertEquals(10, WorldTime.parseMinutes("10m"));
        assertEquals(60, WorldTime.parseMinutes("1h"));
        assertEquals(480, WorldTime.parseMinutes("8H"));
        assertEquals(90, WorldTime.parseMinutes("1h30m"));
        assertEquals(90, WorldTime.parseMinutes("1.5h"));
        assertEquals(-60, WorldTime.parseMinutes("-1h"));
    }

    @Test
    void notDurations() {
        assertNull(WorldTime.parseMinutes("8"), "a bare number could be hours or minutes");
        assertNull(WorldTime.parseMinutes(""));
        assertNull(WorldTime.parseMinutes("h"));
        assertNull(WorldTime.parseMinutes("long"));
        assertNull(WorldTime.parseMinutes("Bob"));
        assertNull(WorldTime.parseMinutes(null));
    }

    @Test
    void describesInWords() {
        assertEquals("10 minutes", WorldTime.describe(10));
        assertEquals("1 hour", WorldTime.describe(60));
        assertEquals("8 hours", WorldTime.describe(-480));
        assertEquals("1 hour 30 minutes", WorldTime.describe(90));
    }

    @Test
    void tickZeroIsDawnOfDayOne() {
        assertEquals("Day 1, 06:00", WorldTime.format(0));
        assertEquals("Day 1, 12:00", WorldTime.format(6000));
        assertEquals("Day 2, 00:00", WorldTime.format(18000));
        assertEquals("Day 2, 06:00", WorldTime.format(24000));
    }

    /** Six +10 min steps land exactly where one +1 hour does (10 min isn't a whole number of ticks). */
    @Test
    void tenMinuteStepsDontDrift() {
        long t = 0;
        for (int i = 0; i < 6; i++) t = WorldTime.ticksOf(WorldTime.minutesOf(t) + 10);
        assertEquals(1000, t);
        assertEquals("Day 1, 07:00", WorldTime.format(t));
        for (int i = 0; i < 144; i++) t = WorldTime.ticksOf(WorldTime.minutesOf(t) + 10);
        assertEquals(25000, t, "a whole day of 10-minute steps");
    }

    @Test
    void neverBeforeTheStart() {
        assertEquals(0, WorldTime.ticksOf(WorldTime.minutesOf(0) - 60));
    }

    @Test
    void invisibilityYouHadCountsDownWhilePossessing() {
        assertEquals(-1, PossessionManager.remainingTicks(-1, 600_000), "infinite stays infinite");
        assertEquals(3600 - 200, PossessionManager.remainingTicks(3600, 10_000), "10 s = 200 ticks used");
        assertEquals(0, PossessionManager.remainingTicks(100, 60_000), "ran out while possessing");
    }
}
