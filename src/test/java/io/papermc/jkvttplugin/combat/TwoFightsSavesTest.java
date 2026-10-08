package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #273: a fight's pending saves belong to that fight. {@code clearInFight()} used to remove every
 * fight's, so one DM typing {@code /combat finished} wiped the saves still owed in another DM's fight;
 * the confirming test, before the fix, failed with "fight B's bear still owes its save ==> expected:
 * true but was: false".
 *
 * <p>Each request carries its fight's session id, and listing, choosing, validating and clearing are
 * all scoped to it.
 */
class TwoFightsSavesTest {

    private static final UUID FIGHT_A = UUID.randomUUID(), FIGHT_B = UUID.randomUUID();

    @AfterEach
    void tidy() { SaveOutcome.clear(); }

    private static String inFight(UUID fight, UUID saver, int dc, Ability ability, String spell, List<Boolean> ran) {
        return SaveOutcome.awaitInFight(fight, saver, dc, ability, Set.of("magic"), spell, ran::add);
    }

    // ---------- ending one fight ----------

    /** Two fights at once, and a spell cast out of any fight: ending A takes A's saves and nothing else. */
    @Test
    void endingOneFightClearsOnlyItsOwnSaves() {
        UUID wolf = UUID.randomUUID(), bear = UUID.randomUUID(), bystander = UUID.randomUUID();
        List<Boolean> a = new ArrayList<>(), b = new ArrayList<>(), outside = new ArrayList<>();
        String wolfsInA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", a);
        String bearsInB = inFight(FIGHT_B, bear, 14, Ability.DEXTERITY, "Sacred Flame", b);
        String outOfCombat = SaveOutcome.await(bystander, 12, Ability.CONSTITUTION, Set.of("poison"), outside::add);

        SaveOutcome.clearInFight(FIGHT_A); // fight A's DM: /combat finished

        assertFalse(SaveOutcome.isWaiting(wolfsInA), "fight A's save went with fight A");
        assertEquals("That save has already been settled.", SpellCastHandler.pickSave(FIGHT_A, wolf, "Wolf", wolfsInA).refusal());
        assertFalse(SpellCastHandler.hasPendingSave(FIGHT_A, wolf));

        assertTrue(SaveOutcome.isWaiting(bearsInB), "fight B's bear still owes its save");
        SpellCastHandler.Picked stillOwed = SpellCastHandler.pickSave(FIGHT_B, bear, "Bear", bearsInB);
        assertNull(stillOwed.refusal());
        assertEquals(14, stillOwed.save().dc());
        assertTrue(SaveOutcome.graded(stillOwed.save().id(), false), "and can still be answered");
        assertEquals(List.of(false), b);

        assertTrue(SaveOutcome.isWaiting(outOfCombat), "the out-of-combat request survived too");
        assertNull(SaveOutcome.refusal(outOfCombat, bystander, true, Ability.CONSTITUTION, 12));
        assertTrue(SaveOutcome.graded(outOfCombat, true));
        assertEquals(List.of(true), outside);

        assertTrue(a.isEmpty(), "clearing ran nobody's outcome");
    }

    @Test
    void clearingAFightWithNothingPendingTouchesNothing() {
        UUID bear = UUID.randomUUID();
        String bearsInB = inFight(FIGHT_B, bear, 14, Ability.DEXTERITY, "Sacred Flame", new ArrayList<>());
        SaveOutcome.clearInFight(FIGHT_A);
        SaveOutcome.clearInFight(null);
        SaveOutcome.clearInFight(UUID.randomUUID());
        assertTrue(SaveOutcome.isWaiting(bearsInB));
    }

    // ---------- answering through the wrong fight ----------

    @Test
    void aSaveCannotBeAnsweredThroughAnotherFight() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        String wolfsInA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", ran);

        SpellCastHandler.Picked viaB = SpellCastHandler.pickSave(FIGHT_B, wolf, "Wolf", wolfsInA);
        assertNull(viaB.save(), "fight B's /combat save, with fight A's id");
        assertEquals("That save belongs to another fight.", viaB.refusal());
        assertEquals("That save belongs to another fight.",
                SaveOutcome.refusalInFight(FIGHT_B, wolfsInA, wolf, Ability.CHARISMA, 13));
        assertTrue(ran.isEmpty());
        assertTrue(SaveOutcome.isWaiting(wolfsInA), "it's still fight A's to answer");
        assertNull(SpellCastHandler.pickSave(FIGHT_A, wolf, "Wolf", wolfsInA).refusal());
    }

    /** The same creature id in two fights (it can't be in two at once, but a bare command mustn't find the other's save). */
    @Test
    void aBareSaveOnlySeesItsOwnFightsSaves() {
        UUID wolf = UUID.randomUUID();
        String inA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", new ArrayList<>());
        assertEquals(inA, SpellCastHandler.pickSave(FIGHT_A, wolf, "Wolf", null).save().id());
        assertEquals("Wolf has no pending save.", SpellCastHandler.pickSave(FIGHT_B, wolf, "Wolf", null).refusal());
        assertTrue(SaveOutcome.inFightFor(FIGHT_B, wolf).isEmpty());
        assertEquals(1, SaveOutcome.inFightFor(FIGHT_A, wolf).size());
        assertFalse(SpellCastHandler.hasPendingSave(FIGHT_B, wolf));
        assertTrue(SaveOutcome.inFightFor(null, wolf).isEmpty(), "no fight, no fight's saves");
    }

    /** Two saves owed in two fights are not "several": each fight sees its one. */
    @Test
    void savesInTwoFightsAreNotCountedTogether() {
        UUID wolf = UUID.randomUUID();
        String inA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", new ArrayList<>());
        String inB = inFight(FIGHT_B, wolf, 14, Ability.DEXTERITY, "Sacred Flame", new ArrayList<>());
        assertEquals(inA, SpellCastHandler.pickSave(FIGHT_A, wolf, "Wolf", null).save().id(), "not 'which one?'");
        assertEquals(inB, SpellCastHandler.pickSave(FIGHT_B, wolf, "Wolf", null).save().id());
    }

    // ---------- the out-of-combat door and a fight's door are different doors ----------

    @Test
    void aFightsSaveIsNotAnsweredByAnOutOfCombatCheckNorTheReverse() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        String inA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", ran);
        String outside = SaveOutcome.await(wolf, 13, Ability.CHARISMA, Set.of("magic"), ran::add);

        // /dm check Wolf save cha dc 13 request <the fight's id>: right creature, ability and DC, wrong door.
        String viaCheck = SaveOutcome.refusal(inA, wolf, true, Ability.CHARISMA, 13);
        assertNotNull(viaCheck);
        assertTrue(viaCheck.contains("a fight's"), viaCheck);

        assertEquals("That save isn't one a fight is waiting on.", SaveOutcome.refusalInFight(FIGHT_A, outside, wolf, Ability.CHARISMA, 13));
        assertNull(SaveOutcome.refusal(outside, wolf, true, Ability.CHARISMA, 13), "each is fine through its own");
        assertNull(SaveOutcome.refusalInFight(FIGHT_A, inA, wolf, Ability.CHARISMA, 13));
        assertTrue(ran.isEmpty());
    }

    /** The other mismatches are still caught first, in a fight as out of one. */
    @Test
    void aFightsSaveStillHasToBeTheRightSave() {
        UUID wolf = UUID.randomUUID(), bear = UUID.randomUUID();
        String inA = inFight(FIGHT_A, wolf, 13, Ability.CHARISMA, "Bane", new ArrayList<>());
        assertEquals("That save is someone else's.", SaveOutcome.refusalInFight(FIGHT_A, inA, bear, Ability.CHARISMA, 13));
        assertNotNull(SaveOutcome.refusalInFight(FIGHT_A, inA, wolf, Ability.DEXTERITY, 13));
        assertNotNull(SaveOutcome.refusalInFight(FIGHT_A, inA, wolf, Ability.CHARISMA, 15));
        assertEquals("That save has already been settled.", SaveOutcome.refusalInFight(FIGHT_A, "rmadeup", wolf, Ability.CHARISMA, 13));
    }

    // ---------- the fight clears its own ----------

    @Test
    void endingAFightClearsThatSessionsSaves() throws Exception {
        String session = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/CombatSession.java"));
        assertTrue(session.contains("SaveOutcome.clearInFight(sessionId);"), "endCombat clears its own session's saves");
        String handler = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/SpellCastHandler.java"));
        assertTrue(handler.contains("SaveOutcome.awaitInFight(session.getSessionId(), target.getId()"), "a save is left in its own fight");
        assertTrue(handler.contains("pickSave(session.getSessionId(), target.getId()"), "and looked for in the fight it's answered in");
    }
}
