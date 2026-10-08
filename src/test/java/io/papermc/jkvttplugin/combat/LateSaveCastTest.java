package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #269: a save answered late. Bane leaves each target a save to make; if the caster casts Bane again,
 * loses concentration, or starts concentrating on something else before one of them answers, that
 * target's Bane has nothing left to hang on and doesn't go on. The spell's id can't tell that (it's
 * still "bane"): the cast's identity does, carried on the pending save ({@code SpellSave.Facts.castId})
 * and held by the caster's concentration.
 *
 * <p>Only the concentration-dependent effect is withheld. The save's damage and its condition are its
 * own and still happen.
 */
class LateSaveCastTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private final List<CharacterSheet> registered = new ArrayList<>();

    @AfterEach
    void unregister() {
        for (CharacterSheet s : registered) CharacterPersistenceLoader.removeCharacter(s.getPlayerId(), s.getCharacterId());
    }

    private CharacterSheet live(String cls) {
        CharacterSheet s = character("human", null, cls, "acolyte", scores(Ability.WISDOM, 16));
        CharacterPersistenceLoader.storeCharacterInMemory(s);
        s.setSavable(false);
        registered.add(s);
        return s;
    }

    private static DndSpell bane() { return SpellLoader.getSpell("bane"); }

    /** One cast of a save spell by {@code caster}: it completes, and leaves this save pending on each target. */
    private static SpellSave.Facts cast(CharacterSheet caster, DndSpell spell) {
        long castId = CastCompletion.finish(UUID.randomUUID(), caster, spell, SpellCost.of(caster, spell), null).castId();
        return SpellSave.Facts.of(spell, caster.getPlayerId(), caster.getCharacterId(), 13,
                Ability.fromString(spell.getSaveType()), SpellSave.tagsFor(spell), castId);
    }

    /** A target, through their real sheet. */
    private static SpellSave.Subject subject(CharacterSheet sheet) {
        return new SpellSave.Subject() {
            @Override public String name() { return sheet.getCharacterName(); }
            @Override public boolean isImmuneTo(String conditionId) { return false; }
            @Override public boolean addCondition(String conditionId) { return sheet.addCondition(conditionId); }
            @Override public boolean cannotAct() { return false; }
            @Override public void give(ActiveEffect effect) { sheet.addEffect(effect); }
            @Override public void showCondition(DndCondition condition) {}
            @Override public void endConcentration(String why) { sheet.breakConcentration(); }
        };
    }

    private static boolean baned(CharacterSheet s) { return s.hasEffect("spell:bane"); }

    private static String text(List<Component> lines) {
        StringBuilder sb = new StringBuilder();
        for (Component c : lines) sb.append(PlainTextComponentSerializer.plainText().serialize(c)).append('\n');
        return sb.toString();
    }

    // ---------- the cast that's still held ----------

    @Test
    void theTargetsOfTheCurrentCastAnswerIndependently() {
        CharacterSheet cleric = live("cleric"), a = live("fighter"), b = live("fighter"), c = live("fighter");
        SpellSave.Facts save = cast(cleric, bane()); // one cast, three saves: the same facts for each

        assertFalse(SpellSave.apply(save, subject(a), false).saved());
        assertTrue(baned(a), "a failed");
        SpellSave.apply(save, subject(b), true);
        assertFalse(baned(b), "b saved");
        assertTrue(baned(a), "which didn't undo a's");
        SpellSave.apply(save, subject(c), false); // answered last, some time later
        assertTrue(baned(c), "c failed too: the cast is still held, so it still lands");
        assertSame(bane(), cleric.getConcentratingOn());
    }

    // ---------- the same spell cast again ----------

    @Test
    void anOldBaneSaveAnsweredAfterBaneIsRecastDoesNotLand() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter"), fresh = live("fighter");
        cleric.setSpellSlotsRemaining(1, 2);
        SpellSave.Facts first = cast(cleric, bane());   // "slow" hasn't answered yet
        SpellSave.Facts second = cast(cleric, bane());  // Bane again, on someone else
        assertNotEquals(first.castId(), second.castId());
        assertEquals("bane", first.effectSpellId(), "the spell's id is the same for both: it can't tell them apart");

        SpellSave.Outcome late = SpellSave.apply(first, subject(slow), false);
        assertFalse(baned(slow), "the cast that asked for this save isn't the one being concentrated on any more");
        assertTrue(text(late.effectLines()).contains("no longer being held"), text(late.effectLines()));
        assertFalse(late.saved(), "they still failed");

        SpellSave.apply(second, subject(fresh), false);
        assertTrue(baned(fresh), "the current cast's save lands as usual");
        assertEquals(second.castId(), fresh.getActiveEffects().get(0).getCastId());
    }

    /** The late save mustn't knock out the new cast either: it's refused, it doesn't re-take anything. */
    @Test
    void theLateSaveLeavesTheNewCastsTargetsAlone() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter"), fresh = live("fighter");
        cleric.setSpellSlotsRemaining(1, 2);
        SpellSave.Facts first = cast(cleric, bane());
        SpellSave.Facts second = cast(cleric, bane());
        SpellSave.apply(second, subject(fresh), false);
        SpellSave.apply(first, subject(slow), false);
        assertTrue(baned(fresh));
        assertEquals(second.castId(), cleric.getConcentrationCastId(), "concentration is still the second cast's");
    }

    // ---------- concentration ended, or moved to another spell ----------

    @Test
    void aSaveAnsweredAfterConcentrationEndedDoesNotLand() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter");
        SpellSave.Facts save = cast(cleric, bane());
        cleric.breakConcentration(); // they took a hit and failed the CON save
        SpellSave.apply(save, subject(slow), false);
        assertFalse(baned(slow));
    }

    @Test
    void aSaveAnsweredAfterAnotherSpellTookTheirConcentrationDoesNotLand() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter");
        cleric.setSpellSlotsRemaining(1, 2);
        SpellSave.Facts save = cast(cleric, bane());
        DndSpell bless = SpellLoader.getSpell("bless");
        CastCompletion.finish(null, cleric, bless, SpellCost.of(cleric, bless), null);
        SpellSave.apply(save, subject(slow), false);
        assertFalse(baned(slow));
        assertSame(bless, cleric.getConcentratingOn(), "and the Bless isn't disturbed");
    }

    /** Dropping the spell and casting it afresh is a new cast as well: the old save belongs to neither concentration. */
    @Test
    void concentrationThatEndedAndCameBackOnTheSameSpellIsANewCast() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter");
        cleric.setSpellSlotsRemaining(1, 2);
        SpellSave.Facts old = cast(cleric, bane());
        cleric.breakConcentration();
        cast(cleric, bane());
        SpellSave.apply(old, subject(slow), false);
        assertFalse(baned(slow));
    }

    // ---------- other casters ----------

    @Test
    void anotherCastersBaneIsUntouched() {
        CharacterSheet cleric = live("cleric"), other = live("cleric"), theirs = live("fighter"), slow = live("fighter");
        cleric.setSpellSlotsRemaining(1, 2);
        SpellSave.Facts othersSave = cast(other, bane());
        SpellSave.apply(othersSave, subject(theirs), false);
        assertTrue(baned(theirs));

        SpellSave.Facts first = cast(cleric, bane());
        cast(cleric, bane()); // this cleric recasts
        SpellSave.apply(first, subject(slow), false);

        assertFalse(baned(slow), "this cleric's old cast is over");
        assertTrue(baned(theirs), "the other cleric's Bane is still on their target");
        assertEquals(othersSave.castId(), other.getConcentrationCastId());

        // And the other cleric's own late save still lands: their cast is still held.
        CharacterSheet theirSecond = live("fighter");
        SpellSave.apply(othersSave, subject(theirSecond), false);
        assertTrue(baned(theirSecond));
    }

    // ---------- only the concentration-dependent effect is withheld ----------

    @Test
    void theDamageAndTheConditionOfALateSaveStillHappen() {
        CharacterSheet cleric = live("cleric"), slow = live("fighter");
        long castId = CastCompletion.finish(null, cleric, bane(), null, null).castId();
        // A save that carries Bane's effect AND damage AND a condition, from that cast.
        SpellSave.Facts facts = new SpellSave.Facts("Cursed Blast", cleric.getPlayerId(), cleric.getCharacterId(), 13, Ability.CHARISMA,
                "2d6", "necrotic", "half", "prone", Set.of("magic"), "bane", castId);
        cleric.breakConcentration();

        SpellSave.Outcome failed = SpellSave.apply(facts, subject(slow), false);
        assertFalse(baned(slow), "the effect needed the concentration");
        assertEquals(SpellSave.Owed.FULL, failed.damage(), "the damage didn't");
        assertTrue(slow.getConditions().contains("prone"), "nor did the condition");
        assertTrue(failed.conditionApplied());
        assertNotNull(ConditionLoader.get("prone"));

        CharacterSheet lucky = live("fighter");
        assertEquals(SpellSave.Owed.HALF, SpellSave.apply(facts, subject(lucky), true).damage(), "a late success is still half");
    }

    /**
     * A spell that isn't in any file: Bane's effect on a failed save, with or without concentration. No
     * shipped spell has an effect on a save without concentration today, so the rule is pinned with one
     * made here instead of with whatever the content happens to hold.
     */
    private static DndSpell jinx(String id, boolean concentration) {
        java.util.Map<String, Object> yaml = new java.util.LinkedHashMap<>();
        yaml.put("name", concentration ? "Held Jinx" : "Jinx");
        yaml.put("level", 1);
        yaml.put("school", "enchantment");
        yaml.put("casting_time", "1 action");
        yaml.put("range", "30 feet");
        yaml.put("components", "V");
        yaml.put("duration", "1 minute");
        yaml.put("description", "A test curse.");
        yaml.put("concentration", concentration);
        yaml.put("save_type", "Charisma");
        yaml.put("effect", java.util.Map.of("effects",
                java.util.Map.of("roll_bonus", java.util.Map.of("dice", "-1d4", "to", List.of("attacks", "saves")))));
        DndSpell spell = SpellLoader.parseSpell(id, yaml);
        spell.setId(id);
        return spell;
    }

    private static SpellSave.Facts saveAgainst(DndSpell spell, UUID casterCharacterId, long castId) {
        return new SpellSave.Facts(spell.getName(), UUID.randomUUID(), casterCharacterId, 13, Ability.CHARISMA,
                null, null, null, null, Set.of("magic"), spell.getId(), castId);
    }

    /** An effect that doesn't depend on concentration isn't asked who holds what: it lands whatever the caster is doing. */
    @Test
    void anEffectThatNeedsNoConcentrationLandsWhoeverHoldsWhat() {
        DndSpell jinx = jinx("jinx", false);
        assertTrue(jinx.hasEffect(), "the synthetic spell really has an effect");
        assertFalse(jinx.isConcentration(), "and really needs no concentration");
        java.util.function.Function<String, DndSpell> spells = id -> id.equals("jinx") ? jinx : SpellLoader.getSpell(id);
        String source = jinx.getEffect().getSourceId();

        // The caster is concentrating on something else entirely.
        CharacterSheet cleric = live("cleric"), a = live("fighter");
        CastCompletion.finish(null, cleric, SpellLoader.getSpell("bless"), null, null);
        long cast = SpellEffects.newCast();
        SpellSave.Outcome o = SpellSave.apply(saveAgainst(jinx, cleric.getCharacterId(), cast), subject(a), false, spells);
        assertTrue(a.hasEffect(source), "it landed");
        ActiveEffect onA = a.getActiveEffects().stream().filter(e -> e.getSourceId().equalsIgnoreCase(source)).findFirst().orElseThrow();
        assertEquals("-1d4", onA.rollBonusFor(ActiveEffect.ATTACKS));
        assertEquals(cast, onA.getCastId(), "and still knows its cast");
        assertEquals(cleric.getCharacterId(), onA.getCasterId());
        assertTrue(text(o.effectLines()).contains("is under Jinx"), text(o.effectLines()));
        assertSame(SpellLoader.getSpell("bless"), cleric.getConcentratingOn(), "the caster's Bless is their own business");

        // The caster isn't concentrating on anything.
        CharacterSheet b = live("fighter");
        cleric.breakConcentration();
        SpellSave.apply(saveAgainst(jinx, cleric.getCharacterId(), SpellEffects.newCast()), subject(b), false, spells);
        assertTrue(b.hasEffect(source));

        // The caster can't even be found (a creature's ability, say): there's no one to ask, and no need to.
        CharacterSheet c = live("fighter");
        SpellSave.apply(saveAgainst(jinx, UUID.randomUUID(), SpellEffects.newCast()), subject(c), false, spells);
        assertTrue(c.hasEffect(source));

        // A successful save still applies nothing.
        CharacterSheet d = live("fighter");
        SpellSave.apply(saveAgainst(jinx, cleric.getCharacterId(), SpellEffects.newCast()), subject(d), true, spells);
        assertFalse(d.hasEffect(source));
    }

    /** The very same spell made a concentration spell is held to the rule: with nobody holding that cast, it doesn't land. */
    @Test
    void theSameSyntheticSpellWithConcentrationIsHeldToItsCast() {
        DndSpell held = jinx("held_jinx", true);
        assertTrue(held.isConcentration());
        java.util.function.Function<String, DndSpell> spells = id -> id.equals("held_jinx") ? held : SpellLoader.getSpell(id);
        String source = held.getEffect().getSourceId();
        CharacterSheet cleric = live("cleric"), a = live("fighter"), b = live("fighter");

        long cast = CastCompletion.finish(null, cleric, held, null, null).castId();
        SpellSave.apply(saveAgainst(held, cleric.getCharacterId(), cast), subject(a), false, spells);
        assertTrue(a.hasEffect(source), "held: it lands");

        cleric.breakConcentration();
        SpellSave.Outcome late = SpellSave.apply(saveAgainst(held, cleric.getCharacterId(), cast), subject(b), false, spells);
        assertFalse(b.hasEffect(source), "no longer held: it doesn't");
        assertTrue(text(late.effectLines()).contains("no longer being held"), text(late.effectLines()));
    }
    // ---------- who holds a cast ----------

    @Test
    void owningACastMeansThisCastOfThisSpellByThisCaster() {
        CharacterSheet cleric = live("cleric");
        long castId = CastCompletion.finish(null, cleric, bane(), null, null).castId();
        assertTrue(SpellEffects.ownsConcentration(cleric.getCharacterId(), bane(), castId));
        assertFalse(SpellEffects.ownsConcentration(cleric.getCharacterId(), bane(), castId + 1000), "another cast");
        assertFalse(SpellEffects.ownsConcentration(cleric.getCharacterId(), SpellLoader.getSpell("bless"), castId), "another spell");
        assertFalse(SpellEffects.ownsConcentration(UUID.randomUUID(), bane(), castId), "a caster the game can't find");
        assertFalse(SpellEffects.ownsConcentration(null, bane(), castId));

        cleric.setConcentratingOn(bane()); // set outside a cast (a restart, a test): no cast is known to own it
        assertEquals(0, cleric.getConcentrationCastId());
        assertFalse(SpellEffects.ownsConcentration(cleric.getCharacterId(), bane(), castId));
        assertFalse(SpellEffects.ownsConcentration(cleric.getCharacterId(), bane(), 0), "and 0 is never a cast");
    }
}
