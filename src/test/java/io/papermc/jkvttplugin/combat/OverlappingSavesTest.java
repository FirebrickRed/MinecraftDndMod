package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #273: in a fight, two save spells at one creature are two saves. They used to share one slot per
 * target, so the second replaced the first; the confirming test, before the fix, failed with "the wolf
 * owes two saves: Bane's and Sacred Flame's ==> expected: 2 but was: 1".
 *
 * <p>A fight's pending save is now a save request ({@link SaveOutcome#awaitInFight}), whose id rides in
 * the {@code /combat save} buttons. These walk what {@code SpellCastHandler} does with one: leave it
 * ({@code awaitInFight}), choose which is being answered ({@code pickSave}), and grade it. The roll, the
 * chat and the damage prompt need a server (TEST_PLAN).
 */
class OverlappingSavesTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    /** The one fight these saves are left in (two fights at once: TwoFightsSavesTest). */
    private static final UUID FIGHT = UUID.randomUUID();

    private final List<CharacterSheet> registered = new ArrayList<>();

    @AfterEach
    void tidy() {
        for (CharacterSheet s : registered) CharacterPersistenceLoader.removeCharacter(s.getPlayerId(), s.getCharacterId());
        SaveOutcome.clear();
    }

    private CharacterSheet live(String race, String subrace, String cls) {
        CharacterSheet s = character(race, subrace, cls, "acolyte", scores(Ability.WISDOM, 16));
        CharacterPersistenceLoader.storeCharacterInMemory(s);
        s.setSavable(false);
        registered.add(s);
        return s;
    }

    /** A spell leaves {@code target} a save, as SpellCastHandler.leavePending does; {@code ran} hears its outcome. */
    private static String leave(UUID target, SpellSave.Facts facts, List<Boolean> ran) {
        return SaveOutcome.awaitInFight(FIGHT, target, facts.dc(), facts.ability(), facts.saveTags(), facts.spellName(), ran::add);
    }

    private static SpellSave.Facts facts(String spellId, int dc) {
        DndSpell spell = SpellLoader.getSpell(spellId);
        UUID caster = UUID.randomUUID();
        return SpellSave.Facts.of(spell, caster, caster, dc, Ability.fromString(spell.getSaveType()), SpellSave.tagsFor(spell), SpellEffects.newCast());
    }

    // ---------- two saves on one creature ----------

    @Test
    void aSecondSpellAtTheSameCreatureDoesNotOverwriteItsUnansweredSave() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> bane = new ArrayList<>(), flame = new ArrayList<>();
        String first = leave(wolf, facts("bane", 13), bane);
        String second = leave(wolf, facts("sacred_flame", 14), flame);

        assertEquals(2, SaveOutcome.inFightFor(FIGHT, wolf).size(), "the wolf owes two saves: Bane's and Sacred Flame's");
        assertNotEquals(first, second);
        assertTrue(SpellCastHandler.hasPendingSave(FIGHT, wolf));
        assertEquals(List.of("Bane", "Sacred Flame"), SaveOutcome.inFightFor(FIGHT, wolf).stream().map(SaveOutcome.Request::label).toList(), "oldest first, each named");
    }

    @Test
    void eachIsAnsweredByItsOwnIdInEitherOrder() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> bane = new ArrayList<>(), flame = new ArrayList<>();
        String first = leave(wolf, facts("bane", 13), bane);
        String second = leave(wolf, facts("sacred_flame", 14), flame);

        // The newer one first.
        SpellCastHandler.Picked p = SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", second);
        assertNull(p.refusal());
        assertEquals(Ability.DEXTERITY, p.save().ability(), "Sacred Flame's ability,");
        assertEquals(14, p.save().dc(), "its DC,");
        assertEquals(Set.of("magic", "radiant"), p.save().tags(), "and what it's against");
        assertTrue(SaveOutcome.graded(p.save().id(), false));
        assertEquals(List.of(false), flame);
        assertTrue(bane.isEmpty(), "Bane's save wasn't touched");

        // Then the older one, still there with its own facts.
        SpellCastHandler.Picked q = SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", first);
        assertEquals(Ability.CHARISMA, q.save().ability());
        assertEquals(13, q.save().dc());
        assertEquals(Set.of("magic"), q.save().tags());
        assertTrue(SaveOutcome.graded(q.save().id(), true));
        assertEquals(List.of(true), bane);
        assertEquals(List.of(false), flame, "and Sacred Flame's didn't run again");
        assertFalse(SpellCastHandler.hasPendingSave(FIGHT, wolf));
    }

    @Test
    void theOlderOneFirstWorksTheSame() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> bane = new ArrayList<>(), flame = new ArrayList<>();
        String first = leave(wolf, facts("bane", 13), bane);
        String second = leave(wolf, facts("sacred_flame", 14), flame);
        SaveOutcome.graded(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", first).save().id(), false);
        assertEquals(List.of(false), bane);
        assertTrue(flame.isEmpty());
        SaveOutcome.graded(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", second).save().id(), true);
        assertEquals(List.of(true), flame);
    }

    /** Two casts of the same spell with the same ability and DC are still two saves. */
    @Test
    void twoIdenticalLookingSavesAreStillTwo() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> one = new ArrayList<>(), two = new ArrayList<>();
        String first = leave(wolf, facts("sacred_flame", 13), one);
        String second = leave(wolf, facts("sacred_flame", 13), two);
        SaveOutcome.graded(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", second).save().id(), false);
        assertTrue(one.isEmpty());
        assertEquals(List.of(false), two);
        assertNotNull(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", first).save());
    }

    // ---------- stale answers, and the wrong creature ----------

    @Test
    void aStaleAnswerResolvesNothing() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> bane = new ArrayList<>(), flame = new ArrayList<>();
        String first = leave(wolf, facts("bane", 13), bane);
        SaveOutcome.graded(first, false);
        String second = leave(wolf, facts("sacred_flame", 14), flame); // a new save arrives afterwards

        SpellCastHandler.Picked again = SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", first); // the old buttons, clicked again
        assertNull(again.save(), "it isn't quietly taken as the answer to the newer save");
        assertEquals("That save has already been settled.", again.refusal());
        assertFalse(SaveOutcome.graded(first, false));
        assertEquals(List.of(false), bane, "nothing ran twice");
        assertTrue(flame.isEmpty(), "and the newer save is untouched");
        assertNotNull(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", second).save());
    }

    @Test
    void anotherCreaturesIdIsRefused() {
        UUID wolf = UUID.randomUUID(), bear = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        String wolfs = leave(wolf, facts("bane", 13), ran);
        SpellCastHandler.Picked p = SpellCastHandler.pickSave(FIGHT, bear, "Bear", wolfs);
        assertNull(p.save());
        assertEquals("That save is someone else's.", p.refusal());
        assertEquals("That save has already been settled.", SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", "rmadeup").refusal());
        assertTrue(ran.isEmpty());
    }

    /** A request a spell left out of a fight is answered by /dm check, not by /combat save. */
    @Test
    void anOutOfCombatRequestIsNotAFightsSave() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        String outside = SaveOutcome.await(wolf, 13, Ability.CHARISMA, Set.of("magic"), ran::add);
        assertNull(SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", outside).save());
        assertEquals("That save isn't one a fight is waiting on.", SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", outside).refusal());
        assertFalse(SpellCastHandler.hasPendingSave(FIGHT, wolf));
        assertEquals("Wolf has no pending save.", SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", null).refusal());
        assertTrue(SaveOutcome.isWaiting(outside), "and it's still waiting on its own answer");
    }

    // ---------- a bare /combat save ----------

    @Test
    void aBareSaveStillWorksWhenOnlyOneIsOwed() {
        UUID wolf = UUID.randomUUID();
        assertEquals("Wolf has no pending save.", SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", null).refusal(), "none owed");

        String only = leave(wolf, facts("bane", 13), new ArrayList<>());
        SpellCastHandler.Picked one = SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", null);
        assertEquals(only, one.save().id(), "an ordinary /combat save, typed by hand");

        leave(wolf, facts("sacred_flame", 14), new ArrayList<>());
        SpellCastHandler.Picked two = SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", null);
        assertNull(two.save(), "with two owed it doesn't guess");
        assertNull(two.refusal());
        assertEquals(2, two.several().size(), "it asks which");
    }

    // ---------- what each save carries ----------

    /** Tags stay with their own save: a stout halfling has advantage against the poison one, not the other. */
    @Test
    void eachSaveKeepsItsOwnTags() {
        CharacterSheet halfling = live("halfling", "stout", "fighter");
        UUID id = halfling.getPlayerId();
        String poison = leave(id, facts("poison_spray", 13), new ArrayList<>());
        String flame = leave(id, facts("sacred_flame", 13), new ArrayList<>());

        SaveOutcome.Request p = SpellCastHandler.pickSave(FIGHT, id, "Zek", poison).save(), f = SpellCastHandler.pickSave(FIGHT, id, "Zek", flame).save();
        assertEquals("against poison", halfling.saveAdvantageSourceVs(p.ability(), p.tags()));
        assertNull(halfling.saveAdvantageSourceVs(f.ability(), f.tags()), "Sacred Flame isn't poison, however the other save reads");
    }

    /** Cast identity and concentration ownership ride with each save's own outcome, whatever order they're answered in. */
    @Test
    void castIdentityAndConcentrationOwnershipSurviveOverlap() {
        CharacterSheet cleric = live("human", null, "cleric"), other = live("human", null, "cleric"), target = live("human", null, "fighter");
        DndSpell bane = SpellLoader.getSpell("bane");
        SpellSave.Subject subject = new SpellSave.Subject() {
            @Override public String name() { return "Target"; }
            @Override public boolean isImmuneTo(String conditionId) { return false; }
            @Override public boolean addCondition(String conditionId) { return target.addCondition(conditionId); }
            @Override public boolean cannotAct() { return false; }
            @Override public void give(ActiveEffect effect) { target.addEffect(effect); }
            @Override public void showCondition(DndCondition condition) {}
            @Override public void endConcentration(String why) {}
        };
        // Two clerics each cast Bane at the same target: two saves, two casts, two concentrations.
        long castA = CastCompletion.finish(null, cleric, bane, SpellCost.of(cleric, bane), null).castId();
        long castB = CastCompletion.finish(null, other, bane, SpellCost.of(other, bane), null).castId();
        SpellSave.Facts a = SpellSave.Facts.of(bane, cleric.getPlayerId(), cleric.getCharacterId(), 13, Ability.CHARISMA, SpellSave.tagsFor(bane), castA);
        SpellSave.Facts b = SpellSave.Facts.of(bane, other.getPlayerId(), other.getCharacterId(), 14, Ability.CHARISMA, SpellSave.tagsFor(bane), castB);
        UUID id = target.getPlayerId();
        String first = SaveOutcome.awaitInFight(FIGHT, id, a.dc(), a.ability(), a.saveTags(), a.spellName(), saved -> SpellSave.apply(a, subject, saved));
        String second = SaveOutcome.awaitInFight(FIGHT, id, b.dc(), b.ability(), b.saveTags(), b.spellName(), saved -> SpellSave.apply(b, subject, saved));

        cleric.breakConcentration(); // the first cleric loses their Bane before its save is answered

        SaveOutcome.graded(SpellCastHandler.pickSave(FIGHT, id, "Target", second).save().id(), false);
        assertTrue(target.hasEffect("spell:bane"), "the second cleric's Bane lands: their cast is held");
        assertEquals(castB, target.getActiveEffects().get(0).getCastId());
        assertEquals(other.getCharacterId(), target.getActiveEffects().get(0).getCasterId());

        SaveOutcome.graded(SpellCastHandler.pickSave(FIGHT, id, "Target", first).save().id(), false);
        assertEquals(1, target.getActiveEffects().size(), "the first cleric's doesn't: nobody holds that cast");
        assertEquals(castB, target.getActiveEffects().get(0).getCastId(), "and it didn't displace the one that's held");
    }

    // ---------- the end of the fight ----------

    @Test
    void theFightEndingClearsItsSavesAndOnlyThose() {
        UUID wolf = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        String inFight = leave(wolf, facts("bane", 13), ran);
        String outside = SaveOutcome.await(wolf, 13, Ability.DEXTERITY, Set.of(), ran::add);

        SaveOutcome.clearInFight(FIGHT);
        assertFalse(SaveOutcome.isWaiting(inFight), "an unanswered save used to outlive its fight");
        assertEquals("That save has already been settled.", SpellCastHandler.pickSave(FIGHT, wolf, "Wolf", inFight).refusal());
        assertTrue(SaveOutcome.isWaiting(outside), "a spell cast out of a fight is still waiting on its save");
        assertTrue(ran.isEmpty(), "and clearing ran nobody's outcome");
    }

    // ---------- the command carries the id, and settles it before rolling ----------

    @Test
    void theCombatSaveCommandCarriesTheRequestAndChecksItFirst() throws Exception {
        String base = "src/main/java/io/papermc/jkvttplugin/combat/";
        String handler = Files.readString(Path.of(base + "SpellCastHandler.java")).replace("\r\n", "\n");
        String command = Files.readString(Path.of(base + "CombatCommand.java")).replace("\r\n", "\n");

        assertFalse(handler.contains("Map<UUID, SpellSave.Facts> pendingSaves"), "one slot per target is back");
        assertTrue(handler.contains("\"/combat save request \" + save.id()"), "a player's buttons carry the id");
        assertTrue(handler.contains("\" request \" + save.id() + \" \""), "and the DM's, for a creature");
        assertTrue(command.contains("roll.forceAuto(), request);"), "which /combat save hands on");

        int resolve = handler.indexOf("public static void resolveSave(");
        int pick = handler.indexOf("pickSave(session.getSessionId(), target.getId(), target.getDisplayName(), requestId)", resolve);
        int roll = handler.indexOf("RollService.resolve(", resolve);
        int useUp = handler.indexOf("SpellEffects.useUp(", resolve);
        assertTrue(pick > resolve && pick < roll && pick < useUp, "which save is settled before the roll and before a one-use effect is spent");
        // Inspiration is still offered where it was: after the result line, before the outcome.
        int inspiration = handler.indexOf("InspirationPrompt.offer(", resolve);
        int graded = handler.indexOf("SaveOutcome.graded(ps.id(), success)", resolve);
        assertTrue(inspiration > roll && inspiration < graded, "Inspiration timing is unchanged");
    }
}
