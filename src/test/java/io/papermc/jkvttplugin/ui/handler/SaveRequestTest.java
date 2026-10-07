package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.Advantage;
import io.papermc.jkvttplugin.combat.SaveOutcome;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.dm.CheckManager;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #272: a save a spell or a trap is waiting on is a request with its own id. It used to be matched by
 * who was saving and the DC, so an unrelated save by the same player at the same DC inherited its tags
 * (#266) and set off its damage. The confirming test, before the fix, failed with "the unrelated save
 * inherits the spell's tags ==> expected: [] but was: [magic, poison]".
 *
 * <p>These walk the same steps the commands do: {@code SaveOutcome.await} (the spell or trap),
 * {@code CheckManager.register} (the DM's check being called), and {@code RollOptionsMenuHandler.answering}
 * / {@code withPenalties} (the player's prompt and their answer). The chat itself needs a server.
 */
class SaveRequestTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static final UUID DM = UUID.randomUUID();

    private static CharacterSheet stout() {
        CharacterSheet s = character("halfling", "stout", "fighter", "soldier", scores());
        s.setSavable(false);
        return s;
    }

    /** A spell's or trap's save: returns its request id, and adds to {@code ran} when its outcome runs. */
    private static String waiting(CharacterSheet on, int dc, Ability ability, Set<String> tags, List<Boolean> ran) {
        return SaveOutcome.await(on.getPlayerId(), dc, ability, tags, ran::add);
    }

    /** The DM's check being called on that player, as CheckCommand registers it ({@code request} null = an ordinary check). */
    private static void called(CharacterSheet on, int dc, String request) {
        CheckManager.register(on.getPlayerId(), DM, dc, "save", Advantage.NONE, request);
    }

    private static RollMode promptMode(CharacterSheet s, Ability save, String request) {
        return RollOptionsMenuHandler.withPenalties(s, "SAVE", save.name(), RollMode.NORMAL, request);
    }

    /** What the player's answer is taken as: the pending check it answers, and (when it does) grade it. */
    private static CheckManager.Pending answer(CharacterSheet s, Ability save, String answeredRequest, boolean saved) {
        RollOptionsMenuHandler.Answering a = RollOptionsMenuHandler.answering(s, "SAVE", save.name(), answeredRequest);
        if (a.settled() || a.pending() == null) return null;
        CheckManager.take(s.getPlayerId(), a.pending());
        if (a.pending().requestId() != null) SaveOutcome.graded(a.pending().requestId(), saved); // as resolvePhysical does
        return a.pending();
    }

    // ---------- unrelated saves at the same DC ----------

    /** The reported case: Poison Spray is waiting; the DM calls an unrelated CON save at the same DC. */
    @Test
    void anUnrelatedSaveWithTheSameAbilityAndDcInheritsNothing() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        String poisonSpray = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("magic", "poison"), spell);

        called(halfling, 13, null); // "/dm check <them> save con dc 13": a ledge, nothing to do with the spell
        assertEquals(RollMode.NORMAL, promptMode(halfling, Ability.CONSTITUTION, null), "no advantage against poison on the ledge save");
        CheckManager.Pending answered = answer(halfling, Ability.CONSTITUTION, null, false);
        assertNotNull(answered, "the ordinary check is answered as always");
        assertNull(answered.requestId());
        assertTrue(spell.isEmpty(), "failing the ledge save didn't deal Poison Spray's damage");
        assertTrue(SaveOutcome.isWaiting(poisonSpray), "the spell is still waiting on its own save");

        // Now the spell's own save is called and answered.
        called(halfling, 13, poisonSpray);
        assertEquals(RollMode.ADVANTAGE, promptMode(halfling, Ability.CONSTITUTION, poisonSpray), "Stout Resilience, on the right save");
        assertNotNull(answer(halfling, Ability.CONSTITUTION, poisonSpray, false));
        assertEquals(List.of(false), spell, "and its outcome runs, once");
    }

    @Test
    void anUnrelatedSaveWithADifferentAbilityAtTheSameDcInheritsNothing() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        waiting(halfling, 13, Ability.CONSTITUTION, Set.of("magic", "poison"), spell);
        called(halfling, 13, null); // a WIS save, DC 13
        assertEquals(RollMode.NORMAL, promptMode(halfling, Ability.WISDOM, null));
        assertNotNull(answer(halfling, Ability.WISDOM, null, false));
        assertTrue(spell.isEmpty());
    }

    /** A save rolled from the sheet (no request id) while a request's check is pending is the player's own roll. */
    @Test
    void aRollWithNoRequestIdNeverAnswersARequest() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        String request = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("poison"), spell);
        called(halfling, 13, request);

        RollOptionsMenuHandler.Answering own = RollOptionsMenuHandler.answering(halfling, "SAVE", "CONSTITUTION", null);
        assertNull(own.pending(), "same ability, same DC, but not that request's answer");
        assertFalse(own.settled(), "it's simply a roll of their own");
        assertEquals(RollMode.NORMAL, promptMode(halfling, Ability.CONSTITUTION, null), "and it gets none of the request's tags");
        assertTrue(spell.isEmpty());
        assertNotNull(CheckManager.peekRequest(request), "the request's check is still there to be answered");
        assertNotNull(answer(halfling, Ability.CONSTITUTION, request, true));
        assertEquals(List.of(true), spell);
    }

    // ---------- replacement requests ----------

    /** A second save is called for before the first is answered: each is answered by its own prompt. */
    @Test
    void aSecondRequestDoesNotReplaceTheFirst() {
        CharacterSheet halfling = stout();
        List<Boolean> poison = new ArrayList<>(), fire = new ArrayList<>();
        String first = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("magic", "poison"), poison);
        called(halfling, 13, first);
        String second = waiting(halfling, 13, Ability.DEXTERITY, Set.of("magic", "fire"), fire);
        called(halfling, 13, second); // takes the player's one "current check" slot
        assertNotEquals(first, second);

        // Each prompt keeps its own tags.
        assertEquals(RollMode.ADVANTAGE, promptMode(halfling, Ability.CONSTITUTION, first));
        assertEquals(RollMode.NORMAL, promptMode(halfling, Ability.DEXTERITY, second), "fire isn't poison");

        // Answer the OLDER prompt first: it resolves the older request, not the newer one.
        assertNotNull(answer(halfling, Ability.CONSTITUTION, first, false));
        assertEquals(List.of(false), poison);
        assertTrue(fire.isEmpty(), "the newer save wasn't touched");
        assertNotNull(CheckManager.peekPending(halfling.getPlayerId()), "and it's still the player's current check");

        assertNotNull(answer(halfling, Ability.DEXTERITY, second, true));
        assertEquals(List.of(true), fire);
        assertNull(CheckManager.peekPending(halfling.getPlayerId()));
    }

    /** An ordinary DM check called after a request takes the current slot; the request stays answerable by its id. */
    @Test
    void anOrdinaryCheckCalledAfterwardsDoesNotSwallowTheRequest() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        String request = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("poison"), spell);
        called(halfling, 13, request);
        called(halfling, 15, null); // "/dm check <them> skill perception dc 15", say

        CheckManager.Pending ordinary = RollOptionsMenuHandler.answering(halfling, "SKILL", "PERCEPTION", null).pending();
        assertNotNull(ordinary);
        assertEquals(15, ordinary.dc());
        assertNull(ordinary.requestId(), "the ordinary check is the ordinary check");

        assertNotNull(answer(halfling, Ability.CONSTITUTION, request, false));
        assertEquals(List.of(false), spell);
        assertNotNull(CheckManager.peekPending(halfling.getPlayerId()), "answering the request left the ordinary check pending");
    }

    // ---------- stale answers ----------

    @Test
    void anAnswerToASettledRequestIsRefused() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        String request = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("poison"), spell);
        called(halfling, 13, request);
        assertNotNull(answer(halfling, Ability.CONSTITUTION, request, false));

        // The same button clicked again, and a new unrelated check now pending.
        called(halfling, 13, null);
        RollOptionsMenuHandler.Answering again = RollOptionsMenuHandler.answering(halfling, "SAVE", "CONSTITUTION", request);
        assertTrue(again.settled(), "that save is over");
        assertNull(again.pending(), "and it isn't quietly taken as the answer to the new check");
        assertEquals(List.of(false), spell, "nothing ran twice");
        assertNotNull(CheckManager.peekPending(halfling.getPlayerId()), "the new check is untouched");
    }

    @Test
    void anAnswerToARuledRequestIsRefused() {
        CharacterSheet halfling = stout();
        List<Boolean> spell = new ArrayList<>();
        String request = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("poison"), spell);
        called(halfling, 13, request);
        assertTrue(SaveOutcome.rule(request, true), "the DM ruled it without a roll");
        assertTrue(RollOptionsMenuHandler.answering(halfling, "SAVE", "CONSTITUTION", request).settled());
        assertEquals(List.of(true), spell);
        assertEquals(RollMode.NORMAL, promptMode(halfling, Ability.CONSTITUTION, request), "a settled request has no tags left to give");
    }

    @Test
    void someoneElsesRequestIdOrTheWrongSaveIsNotAnAnswer() {
        CharacterSheet halfling = stout(), other = stout();
        List<Boolean> spell = new ArrayList<>();
        String request = waiting(halfling, 13, Ability.CONSTITUTION, Set.of("poison"), spell);
        called(halfling, 13, request);

        assertTrue(RollOptionsMenuHandler.answering(other, "SAVE", "CONSTITUTION", request).settled(), "another player typing the id");
        assertTrue(RollOptionsMenuHandler.answering(halfling, "SAVE", "STRENGTH", request).settled(), "the id on a different save");
        assertTrue(RollOptionsMenuHandler.answering(halfling, "SKILL", "ATHLETICS", request).settled(), "or on a skill check");
        assertTrue(RollOptionsMenuHandler.answering(halfling, "SAVE", "CONSTITUTION", "rmadeup").settled(), "or an id nobody issued");
        assertTrue(spell.isEmpty());
        assertNotNull(answer(halfling, Ability.CONSTITUTION, request, false), "the real answer still works");
    }

    // ---------- ordinary DM checks ----------

    @Test
    void ordinaryDmChecksWorkAsBefore() {
        CharacterSheet halfling = stout();
        called(halfling, 12, null);
        CheckManager.Pending p = RollOptionsMenuHandler.answering(halfling, "SKILL", "STEALTH", null).pending();
        assertNotNull(p, "whatever they roll next answers it, as it always has");
        assertEquals(12, p.dc());
        CheckManager.take(halfling.getPlayerId(), p);
        assertNull(RollOptionsMenuHandler.answering(halfling, "SKILL", "STEALTH", null).pending(), "answered once");
        assertFalse(RollOptionsMenuHandler.answering(halfling, "SKILL", "STEALTH", null).settled(), "then it's just their own roll");
    }
}
