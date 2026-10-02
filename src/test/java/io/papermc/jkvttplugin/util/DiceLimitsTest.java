package io.papermc.jkvttplugin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #234: hostile dice expressions are refused before anything is rolled. The count used to be added
 * into an int before the 1,000-die check, so a second huge group wrapped it negative and slipped past;
 * an oversized number threw out of /roll.
 */
class DiceLimitsTest {

    @Test
    @Timeout(2) // the old code would sit in a billions-long loop here
    void aCountThatWouldOverflowIsRefused() {
        assertTrue(DiceRoller.roll("1d6+2147483647d6").isEmpty());
        assertTrue(DiceRoller.roll("999d6+999d6").isEmpty(), "1,998 dice over two groups is over the limit");
    }

    @Test
    void oversizedNumbersAreInvalidNotAnException() {
        assertDoesNotThrow(() -> DiceRoller.roll("99999999999d6"));
        assertTrue(DiceRoller.roll("99999999999d6").isEmpty());
        assertTrue(DiceRoller.roll("1d99999999999").isEmpty());
        assertTrue(DiceRoller.roll("1d6+99999999999").isEmpty());
        assertTrue(DiceRoller.roll("1d6*99999999999").isEmpty());
    }

    @Test
    void realRollsStillWork() {
        var r = DiceRoller.roll("1d8+1d6+3").orElseThrow();
        assertEquals(2, r.dice().size());
        assertTrue(r.total() >= 5 && r.total() <= 17);
        assertEquals(1000, DiceRoller.roll("1000d1").orElseThrow().total(), "exactly the limit is fine");
        assertTrue(DiceRoller.roll("1d4-10").orElseThrow().total() < 0, "a negative result is allowed");
        assertEquals(10, DiceRoller.roll("5d1*2").orElseThrow().total());
    }
}
