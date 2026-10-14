package io.papermc.jkvttplugin.combat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #274: in a fight, a save's damage used to be written onto the caster's turn as the one pending hit, so
 * Burning Hands catching two goblins that both failed left only the second's damage to roll, a save for
 * half set the half flag on whatever hit was pending, and a save answered after the caster's turn had
 * nowhere to go. Each target's damage is now its own {@link OwedDamage} entry, answered by its id; one cast's
 * damage is rolled once for everyone it caught (PHB p.196), each taking all or half by its own save.
 *
 * <p>The chat, the buttons and the HP change need a server (TEST_PLAN, Cleric / Warlock).
 */
class OwedDamageTest {

    private static final UUID FIGHT = UUID.randomUUID();
    private static final UUID CASTER = UUID.randomUUID();
    private static final UUID GOBLIN_A = UUID.randomUUID();
    private static final UUID GOBLIN_B = UUID.randomUUID();

    @AfterEach
    void tidy() { OwedDamage.clear(); }

    /** Burning Hands (3d6 fire, cast {@code cast}) owed to {@code target}, as SpellCastHandler.oweDamage leaves it. */
    private static OwedDamage.Arrival burningHands(UUID fight, UUID target, boolean half, long cast) {
        return OwedDamage.arrive(fight, CASTER, target, "Burning Hands", "3d6", "", 0, "", "fire", half, cast);
    }

    @Test
    void twoFailedSavesFromOneCastAreBothDealtAnsweredFirstToLast() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.Owed b = burningHands(FIGHT, GOBLIN_B, false, 1).owed();
        assertNotEquals(a.id(), b.id());
        assertEquals(2, OwedDamage.owedIn(FIGHT, CASTER, null).size(), "the second goblin's damage replaced the first's");

        List<OwedDamage.Landing> landed = OwedDamage.answer(a.id(), 12);
        assertEquals(List.of(GOBLIN_A, GOBLIN_B), landed.stream().map(l -> l.owed().targetId()).toList(),
                "one roll for everyone the cast caught, the answered target first");
        assertEquals(List.of(12, 12), landed.stream().map(OwedDamage.Landing::amount).toList());
        assertTrue(OwedDamage.owedIn(FIGHT, null, null).isEmpty());
    }

    @Test
    void twoFailedSavesAnsweredTheOtherWayRound() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.Owed b = burningHands(FIGHT, GOBLIN_B, false, 1).owed();
        List<OwedDamage.Landing> landed = OwedDamage.answer(b.id(), 9);
        assertEquals(List.of(GOBLIN_B, GOBLIN_A), landed.stream().map(l -> l.owed().targetId()).toList());
        assertTrue(OwedDamage.answer(a.id(), 9).isEmpty(), "A's button, after the roll already reached A, deals nothing more");
    }

    @Test
    void aSaveForHalfOnlyHalvesItsOwnTarget() {
        OwedDamage.Owed failed = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        burningHands(FIGHT, GOBLIN_B, true, 1);
        List<OwedDamage.Landing> landed = OwedDamage.answer(failed.id(), 13);
        assertEquals(13, landed.get(0).amount(), "the failed save takes it all");
        assertEquals(6, landed.get(1).amount(), "the saved one takes half, rounded down");
        assertFalse(landed.get(0).owed().half());
        assertTrue(landed.get(1).owed().half());
    }

    @Test
    void halfIsAlsoItsOwnWhenTheSavedTargetIsAnswered() {
        burningHands(FIGHT, GOBLIN_A, false, 1);
        OwedDamage.Owed saved = burningHands(FIGHT, GOBLIN_B, true, 1).owed();
        List<OwedDamage.Landing> landed = OwedDamage.answer(saved.id(), 11);
        assertEquals(5, landed.get(0).amount());
        assertEquals(11, landed.get(1).amount(), "the half on the answered entry leaked onto the failed one");
    }

    @Test
    void aSaveThatComesInAfterTheRollTakesTheSameRoll() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.answer(a.id(), 10);
        // Goblin B answers its save later (another turn): nothing to roll, the cast's damage was 10.
        OwedDamage.Arrival late = burningHands(FIGHT, GOBLIN_B, true, 1);
        assertNull(late.owed(), "nothing left to answer");
        assertEquals(10, late.landing().full());
        assertEquals(5, late.landing().amount());
        assertTrue(OwedDamage.owedIn(FIGHT, null, null).isEmpty());
    }

    @Test
    void aSaveAnsweredOffTheCastersTurnIsStillTheirsToRoll() {
        // Nothing about it lives on a turn: the caster answers it whenever, the DM too, nobody else.
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        assertNull(OwedDamage.refusal(FIGHT, a.id(), CASTER, false));
        assertNull(OwedDamage.refusal(FIGHT, a.id(), UUID.randomUUID(), true));
        assertEquals("That isn't your damage to roll.", OwedDamage.refusal(FIGHT, a.id(), UUID.randomUUID(), false));
    }

    @Test
    void twoCastsAreTwoRolls() {
        // Two Sacred Flames (or Bane and then a damaging spell) at one creature: separate casts, separate rolls.
        OwedDamage.Owed first = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.Owed second = burningHands(FIGHT, GOBLIN_A, false, 2).owed();
        assertEquals(1, OwedDamage.answer(first.id(), 8).size());
        assertNotNull(OwedDamage.find(second.id()), "answering one cast dealt another cast's damage");
        assertEquals(1, OwedDamage.owedIn(FIGHT, CASTER, GOBLIN_A).size());
    }

    @Test
    void withoutACastEachIsItsOwnRoll() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 0).owed();
        OwedDamage.Owed b = burningHands(FIGHT, GOBLIN_B, false, 0).owed();
        assertEquals(1, OwedDamage.answer(a.id(), 8).size());
        assertNotNull(OwedDamage.find(b.id()));
        assertNotNull(burningHands(FIGHT, GOBLIN_B, false, 0).owed(), "no cast, no shared roll to land on arrival");
    }

    @Test
    void anAnsweredOrStaleIdDealsNothingTwice() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        assertEquals(1, OwedDamage.answer(a.id(), 7).size());
        assertTrue(OwedDamage.answer(a.id(), 7).isEmpty(), "a second click dealt the damage again");
        assertEquals("That damage has already been dealt (or its fight is over).", OwedDamage.refusal(FIGHT, a.id(), CASTER, false));
        assertNotNull(OwedDamage.refusal(FIGHT, "hdeadbeef00", CASTER, true), "an attack hit's old button isn't owed damage");
    }

    @Test
    void anAnswerWithTheDmIsNotAnsweredAgainUntilTheyRule() {
        OwedDamage.Owed a = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.hold(a.id());
        assertNotNull(OwedDamage.refusal(FIGHT, a.id(), CASTER, false));
        OwedDamage.release(a.id()); // [Deny]: roll again
        assertNull(OwedDamage.refusal(FIGHT, a.id(), CASTER, false));
    }

    @Test
    void aFightEndingTakesOnlyItsOwnOwedDamage() {
        UUID other = UUID.randomUUID();
        OwedDamage.Owed mine = burningHands(FIGHT, GOBLIN_A, false, 1).owed();
        OwedDamage.answer(burningHands(FIGHT, GOBLIN_B, false, 3).owed().id(), 10); // cast 3's roll, in this fight
        OwedDamage.Owed theirs = burningHands(other, GOBLIN_A, false, 4).owed();
        assertEquals("That damage belongs to another fight.", OwedDamage.refusal(FIGHT, theirs.id(), CASTER, true));

        OwedDamage.clearInFight(FIGHT);
        assertNull(OwedDamage.find(mine.id()));
        assertNotNull(OwedDamage.find(theirs.id()), "ending one fight cleared another's damage");
        assertNotNull(burningHands(FIGHT, GOBLIN_A, false, 3).owed(), "a finished fight's roll still landed on a new arrival");
    }

    @Test
    void eachHitHasItsOwnId() {
        // An ordinary attack keeps its one hit per turn; its buttons now carry the hit's id, so a button from a
        // hit already dealt can't answer the next one.
        TurnState ts = new TurnState(30, null);
        assertNull(ts.getPendingDamageId());
        ts.markAttackHit(GOBLIN_A, 3, "+3[STR]", false);
        String first = ts.getPendingDamageId();
        assertNotNull(first);
        ts.clearDamagePending();
        assertNull(ts.getPendingDamageId());
        ts.markAttackHit(GOBLIN_A, 3, "+3[STR]", false);
        assertNotEquals(first, ts.getPendingDamageId());
    }
}
