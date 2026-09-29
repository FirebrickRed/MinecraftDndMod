package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** #225: Bless, Bane, Guidance and Shield of Faith as timed effects on their targets. */
class SpellEffectsTest {

    private final List<CharacterSheet> registered = new ArrayList<>();

    @BeforeAll
    static void load() { TestContent.load(); }

    @AfterEach
    void unregister() {
        for (CharacterSheet s : registered) CharacterPersistenceLoader.removeCharacter(s.getPlayerId(), s.getCharacterId());
    }

    /** A character the game can find (concentration and the clock look through every loaded sheet), never saved to disk. */
    private CharacterSheet live(String race, String cls) {
        CharacterSheet s = character(race, null, cls, "acolyte", scores(Ability.WISDOM, 16));
        CharacterPersistenceLoader.storeCharacterInMemory(s);
        s.setSavable(false);
        registered.add(s);
        return s;
    }

    private static ActiveEffect from(DndSpell spell, CharacterSheet caster) {
        ActiveEffect e = spell.getEffect().copy();
        e.setCasterId(caster.getCharacterId());
        return e;
    }

    @Test
    void spellDurationsReadAsRounds() {
        assertEquals(10, DndSpell.durationRounds("1 minute"));
        assertEquals(10, DndSpell.durationRounds("Concentration, up to 1 minute"));
        assertEquals(100, DndSpell.durationRounds("10 minutes"));
        assertEquals(600, DndSpell.durationRounds("1 hour"));
        assertEquals(1, DndSpell.durationRounds("1 round"));
        assertEquals(-1, DndSpell.durationRounds("Instantaneous"));
    }

    @Test
    void blessLoadsWithItsTargetsAndMinute() {
        DndSpell bless = SpellLoader.getSpell("bless");
        assertTrue(bless.hasEffect());
        assertEquals(3, bless.effectTargetsAt(1));
        assertEquals(4, bless.effectTargetsAt(2), "one more target per slot level above 1st");
        assertEquals(10, bless.getEffect().getRoundsRemaining());
        assertEquals("1d4", bless.getEffect().rollBonusFor(ActiveEffect.ATTACKS));
        assertNull(bless.getEffect().rollBonusFor(ActiveEffect.CHECKS));
        assertEquals("-1d4", SpellLoader.getSpell("bane").getEffect().rollBonusFor(ActiveEffect.SAVES));
    }

    @Test
    void blessShowsOnSavesAndSpellAttacksButNotChecks() {
        CharacterSheet cleric = live("human", "cleric");
        cleric.addEffect(from(SpellLoader.getSpell("bless"), cleric));
        assertTrue(cleric.getSaveBreakdown(Ability.WISDOM).endsWith(" +1d4[Bless]"), cleric.getSaveBreakdown(Ability.WISDOM));
        assertTrue(cleric.getSpellAttackBreakdown(SpellLoader.getSpell("sacred_flame")).contains("+1d4[Bless]"));
        assertFalse(cleric.getSkillBonusBreakdown(Skill.PERCEPTION).contains("Bless"));
    }

    @Test
    void theRollerRollsTheDiceInTheBonus() {
        for (int i = 0; i < 50; i++) {
            RollService.LabelDice d = RollService.rollLabelDice("+3[STR] +2[Prof] +1d4[Bless] -1d4[Bane]");
            assertTrue(d.sum() >= -3 && d.sum() <= 3, "1d4 - 1d4");
            assertTrue(d.label().startsWith("+3[STR] +2[Prof] +"), d.label());
            assertTrue(d.label().contains("[Bless 1d4]") && d.label().contains("[Bane 1d4]"), d.label());
        }
        assertEquals(new RollService.LabelDice(0, "+3[STR] +2[Prof]"), RollService.rollLabelDice("+3[STR] +2[Prof]"));

        RollService.parseInput(new String[]{"autoRoll"});
        RollService.RollResult auto = RollService.resolve(null, null, 3, "+3[STR] +1d4[Bless]", false, Advantage.NONE, true);
        assertTrue(auto.total() >= 5 && auto.total() <= 27, "an autoRoll rolls the d4 too: " + auto.total());
        RollService.RollResult given = RollService.resolve(null, 20, 3, "+3[STR] +1d4[Bless]", false);
        assertEquals(20, given.total(), "a total you give is final");
    }

    @Test
    void guidanceIsUsedUpByOneCheckAndNotBySaves() {
        CharacterSheet cleric = live("human", "cleric");
        cleric.addEffect(from(SpellLoader.getSpell("guidance"), cleric));
        assertTrue(cleric.getSkillBonusBreakdown(Skill.PERCEPTION).contains("+1d4[Guidance]"));
        SpellEffects.useUp(cleric, ActiveEffect.SAVES);
        assertTrue(cleric.hasEffect("spell:guidance"), "a save doesn't use Guidance");
        SpellEffects.useUp(cleric, ActiveEffect.CHECKS);
        assertFalse(cleric.hasEffect("spell:guidance"));
    }

    @Test
    void blessEndsOnEveryoneWhenTheCasterStopsConcentrating() {
        DndSpell bless = SpellLoader.getSpell("bless");
        CharacterSheet cleric = live("human", "cleric");
        CharacterSheet fighter = live("human", "fighter");
        fighter.addEffect(from(bless, cleric));
        cleric.addEffect(from(bless, cleric));
        cleric.setConcentratingOn(bless);

        cleric.setConcentratingOn(bless); // recasting it keeps it going
        assertTrue(fighter.hasEffect("spell:bless"));

        cleric.breakConcentration();
        assertFalse(fighter.hasEffect("spell:bless"));
        assertFalse(cleric.hasEffect("spell:bless"));
    }

    @Test
    void theDmsClockRunsItDown() {
        DndSpell bless = SpellLoader.getSpell("bless");
        CharacterSheet cleric = live("human", "cleric");
        CharacterSheet fighter = live("human", "fighter");
        fighter.addEffect(from(bless, cleric));
        cleric.setConcentratingOn(bless);
        fighter.addEffect(from(SpellLoader.getSpell("shield_of_faith"), cleric)); // 10 minutes; not concentrated on here

        SpellEffects.passTime(1);
        assertFalse(fighter.hasEffect("spell:bless"), "a minute later, Bless is gone");
        assertFalse(cleric.isConcentrating(), "and its caster isn't concentrating on it any more");
        assertTrue(fighter.hasEffect("spell:shield_of_faith"), "Shield of Faith lasts 10 minutes");
    }

    @Test
    void shieldOfFaithRaisesTheTargetsAc() {
        CharacterSheet cleric = live("human", "cleric");
        CharacterSheet fighter = live("human", "fighter");
        int before = fighter.getArmorClass();
        fighter.addEffect(from(SpellLoader.getSpell("shield_of_faith"), cleric));
        assertEquals(before + 2, fighter.getArmorClass());
    }

    @Test
    void aSavedBlessComesBackWithItsRoundsAndCaster() {
        CharacterSheet cleric = live("human", "cleric");
        CharacterSheet fighter = live("human", "fighter");
        assertTrue(fighter.restoreActiveEffect("spell:bless", 7, false, cleric.getCharacterId()));
        ActiveEffect e = fighter.getActiveEffects().get(0);
        assertEquals(7, e.getRoundsRemaining());
        assertEquals(cleric.getCharacterId(), e.getCasterId());
        assertFalse(fighter.restoreActiveEffect("spell:no_such_spell", 7, false, null));
    }

    @Test
    void youCanRollTheBonusDieYourself() {
        RollService.parseInput(new String[]{"manualRoll", "14", "3"});
        RollService.RollResult r = RollService.resolve(14, null, 3, "+3[STR] +1d4[Bless]", false);
        assertEquals(20, r.total(), "14 + 3 + your 3 on the d4");
        assertTrue(r.breakdown().contains("+3[Bless 1d4]"), r.breakdown());

        // A manualRoll never rolls for you: a missing or impossible d4 is refused, and the re-prompt says why.
        RollService.parseInput(new String[]{"manualRoll", "14", "9"});
        assertNull(RollService.resolve(14, null, 3, "+3[STR] +1d4[Bless]", false));
        assertTrue(RollService.takeBonusDiceError().contains("isn't a 1d4 roll"));
        RollService.parseInput(new String[]{"manualRoll", "14"});
        assertNull(RollService.resolve(14, null, 3, "+3[STR] +1d4[Bless]", false));
        assertTrue(RollService.takeBonusDiceError().startsWith("Bless adds 1d4"));

        assertEquals("/combat attack goblin ", RollPrompt.baseLine("combat", new String[]{"attack", "goblin", "manualRoll", "14", "3"}),
                "a re-prompt drops the bonus dice typed after the d20 too");
    }

    @Test
    void bardicInspirationWaitsUntilTheHolderChoosesTheRoll() {
        CharacterSheet bard = live("human", "bard");
        var feature = bard.getFeature("bardic_inspiration");
        assertNotNull(feature);
        assertTrue(feature.givesToOther());
        assertEquals(60, feature.getRange());

        CharacterSheet fighter = live("human", "fighter");
        fighter.addEffect(feature.getApplyTemplate().copy());
        assertFalse(fighter.getSaveBreakdown(Ability.DEXTERITY).contains("Bardic"), "held: not on any roll yet");
        SpellEffects.useUp(fighter, ActiveEffect.SAVES);
        assertTrue(fighter.hasEffect("bardic_inspiration"), "a roll made while it's held doesn't spend it");

        assertEquals(1, SpellEffects.arm(fighter).size());
        assertTrue(fighter.getSaveBreakdown(Ability.DEXTERITY).contains("+1d6[Bardic Inspiration]"));
        assertTrue(fighter.getSkillBonusBreakdown(Skill.ATHLETICS).contains("+1d6[Bardic Inspiration]"));
        SpellEffects.useUp(fighter, ActiveEffect.ATTACKS);
        assertFalse(fighter.hasEffect("bardic_inspiration"), "the roll it was armed for spends it");
    }

    @Test
    void severalClickedTargetsFillOneCommand() {
        DndSpell bless = SpellLoader.getSpell("bless");
        assertEquals("/character cast bless Zek, Borin, me ", SpellTargeting.castCommand(false, bless, "Zek, Borin, me", 0));
        assertEquals("/combat cast bless Zek, Borin level 2 ", SpellTargeting.castCommand(true, bless, "Zek, Borin", 2));
    }
}
