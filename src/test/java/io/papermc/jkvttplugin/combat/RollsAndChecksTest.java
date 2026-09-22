package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.dm.CheckManager;
import io.papermc.jkvttplugin.util.DiceRoller;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Dice, the roll resolver, advantage stacking, and contested checks. No content needed. */
class RollsAndChecksTest {

    // ---------- dice (#109) ----------

    @RepeatedTest(50)
    void diceStayInRange() {
        int r = DiceRoller.rollDice(2, 6);
        assertTrue(r >= 2 && r <= 12, "2d6 = " + r);
    }

    /** A valid roll can be zero or negative; only malformed input is "empty". Never a -1 sentinel. */
    @Test
    void negativeDiceResultsAreValid() {
        OptionalInt r = DiceRoller.parseDiceRoll("1d4-10");
        assertTrue(r.isPresent());
        assertTrue(r.getAsInt() >= -9 && r.getAsInt() <= -6, "1d4-10 = " + r.getAsInt());
        assertTrue(DiceRoller.parseDiceRoll("2d6+3").isPresent());
        assertFalse(DiceRoller.parseDiceRoll("banana").isPresent());
        assertFalse(DiceRoller.parseDiceRoll("").isPresent());
    }

    /** /roll shows the work: every die, the modifier, the total — and they add up. */
    @RepeatedTest(20)
    void aRollKeepsEveryDie() {
        DiceRoller.Rolled r = DiceRoller.roll("2d6+3").orElseThrow();
        assertEquals(2, r.dice().size());
        assertEquals(r.dice().get(0) + r.dice().get(1) + 3, r.total());
        assertEquals("[" + r.dice().get(0) + ", " + r.dice().get(1) + "] +3 = " + r.total(), r.breakdown());
        assertTrue(DiceRoller.roll("1d4-1").orElseThrow().breakdown().contains(" -1 = "));
    }

    /** Whenever the GAME rolls, it shows the dice: one helper, so no caller can print a bare total. */
    @Test
    void aGameRollShowsItsDice() {
        DiceRoller.Rolled r = DiceRoller.rollOrFlat("2d8+1");
        assertNotNull(r);
        assertEquals(2, r.dice().size());
        assertTrue(r.display().startsWith("🎲 2d8+1: ["), r.display());
        assertTrue(r.display().endsWith("= " + r.total()));
    }

    /** A flat amount is allowed and shows as itself, so callers don't need a second path for "5". */
    @Test
    void aFlatAmountIsItsOwnRoll() {
        DiceRoller.Rolled flat = DiceRoller.rollOrFlat("5");
        assertNotNull(flat);
        assertEquals(5, flat.total());
        assertEquals("🎲 5", flat.display());
        assertNull(DiceRoller.rollOrFlat("banana"));
        assertNull(DiceRoller.rollOrFlat(null));
    }

    @Test
    void aDieWithNoSidesIsMalformedNotACrash() {
        assertTrue(DiceRoller.roll("1d0").isEmpty());
        assertFalse(DiceRoller.parseDiceRoll("2d0").isPresent());
    }

    // ---------- the roll resolver ----------

    @Test
    void manualRollAddsTheModifier() {
        RollService.RollResult r = RollService.resolve(14, null, 5, "+5[Deception]", false, Advantage.NONE, false);
        assertEquals(19, r.total());
        assertEquals(14, r.d20());
        assertEquals("d20(14) +5[Deception] = 19", r.breakdown());
    }

    @Test
    void providedTotalIsTakenAsIs() {
        RollService.RollResult r = RollService.resolve(null, 22, 5, "+5", false, Advantage.DISADVANTAGE, false);
        assertEquals(22, r.total());
        assertTrue(r.providedTotal());
    }

    @Test
    void naturalTwentyAndOneAreFlagged() {
        assertTrue(RollService.resolve(20, null, 0, "", false, Advantage.NONE, false).nat20());
        assertTrue(RollService.resolve(1, null, 0, "", false, Advantage.NONE, false).nat1());
    }

    @Test
    void rollWordsParse() {
        assertEquals(12, RollService.parseInput(new String[]{"insight", "manualRoll", "12"}).providedRoll());
        assertEquals(18, RollService.parseInput(new String[]{"total", "18"}).providedTotal());
        assertTrue(RollService.parseInput(new String[]{"autoRoll"}).forceAuto());
        assertTrue(RollService.parseInput(new String[]{"insight", "deception"}).isEmpty());
    }

    // ---------- advantage stacking (5e) ----------

    @Test
    void advantageAndDisadvantageCancel() {
        assertFalse(Advantage.ADVANTAGE.combine(Advantage.DISADVANTAGE).affectsRoll());
        assertEquals(Advantage.ADVANTAGE, Advantage.ADVANTAGE.combine(Advantage.ADVANTAGE), "two advantages are still one");
        assertEquals(Advantage.DISADVANTAGE, Advantage.NONE.with(false));
    }

    /**
     * PHB p.173: "...even if multiple circumstances impose disadvantage and only one grants advantage."
     * Pairwise folding used to turn adv + dis + dis back into disadvantage.
     */
    @Test
    void cancellationSticksNoMatterHowManyMoreSources() {
        Advantage a = Advantage.NONE.with(true).with(false).with(false);
        assertEquals(Advantage.CANCELLED, a);
        assertFalse(a.affectsRoll(), "rolls one die");
        assertEquals(Advantage.CANCELLED, Advantage.NONE.with(false).with(false).with(true).with(true).with(true));
        // A cancelled roll rolls exactly like a plain one.
        assertEquals(15, RollService.resolve(12, null, 3, "+3", false, Advantage.CANCELLED, false).total());
    }

    // ---------- contested checks (either side a creature) ----------

    @Test
    void contestResolvesWhenBothSidesRoll() {
        UUID guard = UUID.randomUUID(), balin = UUID.randomUUID();
        CheckManager.Contest c = CheckManager.registerContest(null,
                new CheckManager.Side(guard, "Guard", "Perception", true, 2, "Perception"),
                new CheckManager.Side(balin, "Balin", "Stealth", true, 0, "DEX"));
        assertNull(CheckManager.recordContestRoll(c.id, guard, 14), "still waiting on Balin");
        assertNotNull(CheckManager.getContest(c.id));

        CheckManager.Contest done = CheckManager.recordContestRoll(c.id, balin, 11);
        assertNotNull(done);
        assertEquals(14, done.sides.get(0).total);
        assertEquals(11, done.sides.get(1).total);
        assertNull(CheckManager.getContest(c.id), "a resolved contest is cleared");
    }

    /** A character side waits on their own roll (a pending check); a creature side doesn't. */
    @Test
    void onlyCharacterSidesGetAPendingCheck() {
        UUID zek = UUID.randomUUID(), balin = UUID.randomUUID();
        CheckManager.Contest c = CheckManager.registerContest(null,
                new CheckManager.Side(zek, "Zek", "Insight"),
                new CheckManager.Side(balin, "Balin", "Deception", true, 1, "CHA"));
        assertEquals(c.id, CheckManager.peekPending(zek).contestId());
        assertNull(CheckManager.peekPending(balin));
        CheckManager.takePending(zek);
    }
}
