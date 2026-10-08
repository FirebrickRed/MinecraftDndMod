package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #267: what a graded spell save does, the same in a fight and out of one. The targets here are a real
 * sheet and a real stat block behind {@link SpellSave.Subject}; the live adapter over a Combatant, the
 * damage prompts and the chat need a server (TEST_PLAN).
 */
class SpellSaveTest {

    /** The caster of every save here: a real character the game can find, since a concentration spell's effect asks who holds it. */
    private static CharacterSheet casterSheet;
    private static final UUID CASTER = UUID.randomUUID();
    private static UUID CASTER_CHARACTER;

    @BeforeAll
    static void load() {
        TestContent.load();
        casterSheet = character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16));
        io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader.storeCharacterInMemory(casterSheet);
        casterSheet.setSavable(false);
        CASTER_CHARACTER = casterSheet.getCharacterId();
    }

    @org.junit.jupiter.api.AfterAll
    static void forget() {
        io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader.removeCharacter(casterSheet.getPlayerId(), casterSheet.getCharacterId());
    }

    /** The save a fresh cast of the spell calls for; a concentration spell's cast completes first, as it does in play. */
    private static SpellSave.Facts factsOf(String spellId) {
        DndSpell spell = SpellLoader.getSpell(spellId);
        assertNotNull(spell, spellId);
        long castId = CastCompletion.finish(null, casterSheet, spell, null, null).castId();
        return SpellSave.Facts.of(spell, CASTER, CASTER_CHARACTER, 13, Ability.fromString(spell.getSaveType()), Set.of("magic"), castId);
    }

    /** A save with just a condition on a fail, for conditions no level-1 spell applies. */
    private static SpellSave.Facts condition(String conditionId) {
        return new SpellSave.Facts("Test Spell", CASTER, CASTER_CHARACTER, 13, Ability.WISDOM, null, null, null, conditionId, Set.of(), null, 0);
    }

    private static String text(List<Component> lines) {
        StringBuilder sb = new StringBuilder();
        for (Component c : lines) sb.append(PlainTextComponentSerializer.plainText().serialize(c)).append('\n');
        return sb.toString();
    }

    /** A character, through their real sheet: conditions, effects and concentration are the sheet's own. */
    private static final class OnSheet implements SpellSave.Subject {
        final CharacterSheet sheet;
        final List<String> shown = new ArrayList<>();
        String concentrationEndedBecause;
        OnSheet(CharacterSheet sheet) { this.sheet = sheet; sheet.setSavable(false); }
        @Override public String name() { return sheet.getCharacterName(); }
        @Override public boolean isImmuneTo(String conditionId) { return false; }
        @Override public boolean addCondition(String conditionId) { return sheet.addCondition(conditionId); }
        @Override public boolean cannotAct() {
            for (String id : sheet.getConditions()) {
                DndCondition c = ConditionLoader.get(id);
                if (c != null && c.isNoActions()) return true;
            }
            return false;
        }
        @Override public void give(ActiveEffect effect) { sheet.addEffect(effect); }
        @Override public void showCondition(DndCondition condition) { shown.add(condition.getId()); }
        @Override public void endConcentration(String why) { concentrationEndedBecause = why; sheet.breakConcentration(); }
    }

    /** A creature, through its real stat block: the immunities are the template's. */
    private static final class OfTemplate implements SpellSave.Subject {
        final DndEntity template;
        final Set<String> conditions = new LinkedHashSet<>();
        final List<ActiveEffect> effects = new ArrayList<>();
        OfTemplate(String entityId) { template = EntityLoader.getEntity(entityId); assertNotNull(template, entityId); }
        @Override public String name() { return template.getName(); }
        @Override public boolean isImmuneTo(String conditionId) { return template.getConditionImmunities().contains(conditionId.toLowerCase()); }
        @Override public boolean addCondition(String conditionId) { return conditions.add(conditionId); }
        @Override public boolean cannotAct() { return false; }
        @Override public void give(ActiveEffect effect) { effects.add(effect); }
        @Override public void showCondition(DndCondition condition) {}
        @Override public void endConcentration(String why) {}
    }

    private static OnSheet fighter() {
        return new OnSheet(character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 16)));
    }

    // ---------- grading ----------

    @Test
    void meetingTheDcSaves() {
        assertTrue(SpellSave.saved(13, 13));
        assertFalse(SpellSave.saved(12, 13));
    }

    // ---------- the spell's effect (Bane) ----------

    @Test
    void aFailedSaveAgainstBanePutsBaneOnThem() {
        OnSheet target = fighter();
        SpellSave.Outcome o = SpellSave.apply(factsOf("bane"), target, false);

        assertEquals(1, target.sheet.getActiveEffects().size(), "Bane is on them");
        ActiveEffect bane = target.sheet.getActiveEffects().get(0);
        assertEquals("-1d4", bane.rollBonusFor(ActiveEffect.ATTACKS));
        assertEquals("-1d4", bane.rollBonusFor(ActiveEffect.SAVES));
        assertEquals(CASTER_CHARACTER, bane.getCasterId(), "it hangs on the caster's concentration");
        assertEquals(casterSheet.getConcentrationCastId(), bane.getCastId(), "and on the cast that called for the save");
        assertTrue(text(o.effectLines()).contains("is under Bane"), text(o.effectLines()));
        assertEquals(SpellSave.Owed.NONE, o.damage(), "Bane deals no damage");
        assertFalse(o.saved());
    }

    @Test
    void asuccessfulSaveAgainstBaneDoesNothing() {
        OnSheet target = fighter();
        SpellSave.Outcome o = SpellSave.apply(factsOf("bane"), target, true);
        assertTrue(target.sheet.getActiveEffects().isEmpty());
        assertTrue(o.effectLines().isEmpty() && o.conditionLines().isEmpty());
        assertEquals(SpellSave.Owed.NONE, o.damage());
        assertTrue(o.saved());
    }

    @Test
    void aCreatureGetsTheEffectToo() {
        OfTemplate skeleton = new OfTemplate("skeleton");
        SpellSave.apply(factsOf("bane"), skeleton, false);
        assertEquals(1, skeleton.effects.size());
    }

    // ---------- the condition ----------

    @Test
    void aFailedSaveAppliesTheCondition() {
        OnSheet target = fighter();
        SpellSave.Outcome o = SpellSave.apply(factsOf("entangle"), target, false);
        assertTrue(target.sheet.getConditions().contains("restrained"), "applied to the sheet itself: no fight needed");
        assertTrue(o.conditionApplied());
        assertEquals(List.of("restrained"), target.shown, "and shown");
        assertTrue(text(o.conditionLines()).contains("is now Restrained!"), text(o.conditionLines()));
        assertEquals(SpellSave.Owed.NONE, o.damage(), "Entangle deals no damage");
    }

    @Test
    void aSuccessfulSaveAppliesNoCondition() {
        OnSheet target = fighter();
        SpellSave.Outcome o = SpellSave.apply(factsOf("entangle"), target, true);
        assertTrue(target.sheet.getConditions().isEmpty());
        assertFalse(o.conditionApplied());
    }

    @Test
    void alreadyHavingTheConditionIsNotAppliedTwice() {
        OnSheet target = fighter();
        target.sheet.addCondition("restrained");
        SpellSave.Outcome o = SpellSave.apply(factsOf("entangle"), target, false);
        assertFalse(o.conditionApplied());
        assertTrue(o.conditionLines().isEmpty(), "nothing new to announce");
    }

    // ---------- immunity ----------

    @Test
    void anImmuneTargetIsToldSoAndDoesNotGetIt() {
        OfTemplate skeleton = new OfTemplate("skeleton"); // condition_immunities: [poisoned]
        SpellSave.Outcome o = SpellSave.apply(condition("poisoned"), skeleton, false);
        assertTrue(skeleton.conditions.isEmpty());
        assertFalse(o.conditionApplied());
        assertTrue(text(o.conditionLines()).contains("is immune to being Poisoned"), text(o.conditionLines()));

        SpellSave.Outcome prone = SpellSave.apply(condition("prone"), skeleton, false);
        assertTrue(skeleton.conditions.contains("prone"), "it's only immune to what its stat block lists");
        assertTrue(prone.conditionApplied());
    }

    // ---------- damage owed ----------

    @Test
    void damageOwedIsFullHalfOrNone() {
        // Burning Hands: half on a save.
        assertEquals(SpellSave.Owed.FULL, SpellSave.apply(factsOf("burning_hands"), fighter(), false).damage());
        assertEquals(SpellSave.Owed.HALF, SpellSave.apply(factsOf("burning_hands"), fighter(), true).damage());
        // Sacred Flame: a save takes nothing.
        assertEquals(SpellSave.Owed.FULL, SpellSave.apply(factsOf("sacred_flame"), fighter(), false).damage());
        assertEquals(SpellSave.Owed.NONE, SpellSave.apply(factsOf("sacred_flame"), fighter(), true).damage());
        // No damage at all: nothing owed either way, even if it says "half".
        SpellSave.Facts halfOfNothing = new SpellSave.Facts("Odd", CASTER, CASTER_CHARACTER, 13, Ability.DEXTERITY,
                " ", null, "half", null, Set.of(), null, 0);
        assertEquals(SpellSave.Owed.NONE, SpellSave.apply(halfOfNothing, fighter(), false).damage());
        assertEquals(SpellSave.Owed.NONE, SpellSave.apply(halfOfNothing, fighter(), true).damage());
    }

    @Test
    void damageAndAConditionTogether() {
        // Thunderwave-like: damage on a fail, plus a condition.
        SpellSave.Facts f = new SpellSave.Facts("Shove Wave", CASTER, CASTER_CHARACTER, 13, Ability.CONSTITUTION,
                "2d8", "thunder", "half", "prone", Set.of(), null, 0);
        OnSheet failed = fighter();
        SpellSave.Outcome o = SpellSave.apply(f, failed, false);
        assertEquals(SpellSave.Owed.FULL, o.damage());
        assertTrue(failed.sheet.getConditions().contains("prone"));

        OnSheet saved = fighter();
        assertEquals(SpellSave.Owed.HALF, SpellSave.apply(f, saved, true).damage());
        assertTrue(saved.sheet.getConditions().isEmpty(), "half damage, no condition");
    }

    // ---------- concentration ----------

    @Test
    void aConditionThatIncapacitatesEndsTheirConcentration() {
        OnSheet target = new OnSheet(character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16)));
        target.sheet.setConcentratingOn(SpellLoader.getSpell("bless"));
        assertTrue(target.sheet.isConcentrating());

        SpellSave.apply(condition("paralyzed"), target, false);
        assertTrue(target.sheet.getConditions().contains("paralyzed"));
        assertFalse(target.sheet.isConcentrating(), "paralyzed: concentration ends outright, no save");
        assertEquals("they were paralyzed", target.concentrationEndedBecause);
    }

    @Test
    void aConditionThatLeavesThemActingDoesNotEndConcentration() {
        OnSheet target = new OnSheet(character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16)));
        target.sheet.setConcentratingOn(SpellLoader.getSpell("bless"));
        SpellSave.apply(condition("restrained"), target, false);
        assertTrue(target.sheet.isConcentrating(), "restrained can still act");
        assertNull(target.concentrationEndedBecause);

        SpellSave.apply(condition("paralyzed"), target, true);
        assertTrue(target.sheet.isConcentrating(), "they saved");
    }

    // ---------- both callers use it ----------

    /**
     * The fight and the out-of-combat path each keep their own damage step and their own audience, and
     * neither applies a save's consequences by hand any more.
     */
    @Test
    void bothPathsGoThroughTheSharedOutcome() throws Exception {
        String base = "src/main/java/io/papermc/jkvttplugin/combat/";
        String fight = Files.readString(Path.of(base + "SpellCastHandler.java"));
        String outside = Files.readString(Path.of(base + "OutOfCombatAttack.java"));

        assertTrue(fight.contains("SpellSave.apply(") && outside.contains("SpellSave.apply("));
        assertTrue(fight.contains("SpellSave.saved(r.total(), ps.dc())"), "grading stays its own step (#268 will pause after it)");
        // Each path's own damage step and audience.
        assertTrue(fight.contains("AttackHandler.promptDamage(session, damageSource, target, ps.damage()"));
        assertTrue(outside.contains("offerDamage(player, spell, false, aim.target, null, true)"), "half, out of a fight");
        // Nothing left that applies or hands off a condition outside SpellSave.
        assertFalse(fight.contains("target.addCondition(cond.getId())"), "the fight applies the condition itself again");
        assertFalse(outside.contains("/dm adjust applies it"), "out of a fight the condition is the DM's chore again");
    }
}
