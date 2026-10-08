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

    /** An effect that doesn't depend on concentration isn't asked the question at all. */
    @Test
    void anEffectThatNeedsNoConcentrationLandsWhoeverHoldsWhat() {
        DndSpell effectSpell = SpellLoader.getAllSpells().stream()
                .filter(s -> s.hasEffect() && !s.isConcentration()).findFirst().orElse(null);
        if (effectSpell == null) return; // none shipped today: nothing to pin
        CharacterSheet cleric = live("cleric"), target = live("fighter");
        SpellSave.Facts facts = new SpellSave.Facts(effectSpell.getName(), cleric.getPlayerId(), cleric.getCharacterId(), 13, Ability.WISDOM,
                null, null, null, null, Set.of(), effectSpell.getId(), SpellEffects.newCast());
        SpellSave.apply(facts, subject(target), false);
        assertTrue(target.hasEffect(effectSpell.getEffect().getSourceId()));
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
