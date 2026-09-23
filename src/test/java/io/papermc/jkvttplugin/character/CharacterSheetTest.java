package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.model.AcAdjustment.Until;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Character math on a real sheet: tools, expertise, armor proficiency. */
class CharacterSheetTest {

    private static DndArmor armor(String id) {
        TestContent.load();
        DndArmor a = ArmorLoader.getArmor(id);
        assertNotNull(a, id);
        return a;
    }

    // ---------- conditions and the DM's AC adjustment live on the sheet (#175) ----------

    /** Poisoned outside a fight: checks at disadvantage. Restrained: DEX saves too. */
    @Test
    void conditionsPutRollsAtDisadvantageOutOfCombat() {
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        assertNull(c.conditionDisadvantageOn(false, Ability.WISDOM));
        c.addCondition("poisoned");
        assertEquals("Poisoned", c.conditionDisadvantageOn(false, Ability.WISDOM), "checks");
        assertNull(c.conditionDisadvantageOn(true, Ability.DEXTERITY), "poison doesn't touch saves");

        c.removeCondition("poisoned");
        c.addCondition("restrained");
        assertEquals("Restrained", c.conditionDisadvantageOn(true, Ability.DEXTERITY));
        assertNull(c.conditionDisadvantageOn(true, Ability.WISDOM));
    }

    @Test
    void aDmAcAdjustmentAddsAndEndsWhenItSays() {
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        int gear = c.getArmorClass();
        c.setAcAdjustment(new io.papermc.jkvttplugin.data.model.AcAdjustment(2,
                io.papermc.jkvttplugin.data.model.AcAdjustment.Until.LONG_REST));
        assertEquals(gear + 2, c.getArmorClass());
        assertEquals(gear, c.getGearArmorClass(), "the base stays what the armor gives");

        c.shortRest();
        assertEquals(gear + 2, c.getArmorClass(), "a short rest doesn't end a long-rest adjustment");
        c.longRest();
        assertEquals(gear, c.getArmorClass(), "a long rest does");

        c.setAcAdjustment(new io.papermc.jkvttplugin.data.model.AcAdjustment(-1,
                io.papermc.jkvttplugin.data.model.AcAdjustment.Until.REMOVED));
        c.longRest();
        assertEquals(gear - 1, c.getArmorClass(), "'until you remove it' outlasts rests, and can be negative");
    }

    /** How long a DM AC change lasts, however it's typed. */
    @Test
    void acAdjustmentDurationsParse() {
        assertEquals(Until.LONG_REST, Until.parse("long"));
        assertEquals(Until.LONG_REST, Until.parse("long_rest"));
        assertEquals(Until.SHORT_REST, Until.parse("Short Rest"));
        assertEquals(Until.NEXT_TURN, Until.parse("next"));
        assertEquals(Until.REMOVED, Until.parse("removed"));
        assertNull(Until.parse("forever"));
    }

    // ---------- racial spells ----------

    /** A tiefling rogue's Thaumaturgy is theirs to cast, with CHA; the cast paths only checked class lists. */
    @Test
    void innateSpellsAreKnownAndUseTheirOwnAbility() {
        CharacterSheet teef = character("tiefling", null, "rogue", "urchin", scores(Ability.CHARISMA, 14));
        var thaumaturgy = io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell("thaumaturgy");
        assertTrue(teef.knowsSpell(thaumaturgy));
        assertEquals(Ability.CHARISMA, teef.castingAbilityFor(thaumaturgy));

        var rebuke = io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell("hellish_rebuke");
        assertFalse(teef.knowsSpell(rebuke), "Hellish Rebuke comes at level 3");
        assertNull(teef.castingAbilityFor(rebuke), "a rogue has no class spellcasting");
    }

    /** A high elf rogue's wizard cantrip is an innate spell cast with INT, not a (nonexistent) class cantrip. */
    @Test
    void highElfRogueCastsTheirCantripWithInt() {
        CharacterSheet elf = character("elf", "high_elf", "rogue", "sage", scores(Ability.INTELLIGENCE, 14));
        elf.restoreChosenInnateSpells(java.util.Map.of("fire_bolt", Ability.INTELLIGENCE));
        var fireBolt = io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell("fire_bolt");
        assertTrue(elf.knowsSpell(fireBolt));
        assertEquals(Ability.INTELLIGENCE, elf.castingAbilityFor(fireBolt));
        assertTrue(elf.getKnownCantrips().isEmpty(), "not a class cantrip");
    }

    // ---------- tool checks + expertise (#207) ----------

    @Test
    void thievesToolsCheckAddsProficiencyAndDoublesWithExpertise() {
        CharacterSheet rogue = character("human", null, "rogue", "urchin", scores(Ability.DEXTERITY, 16), Skill.STEALTH);
        assertEquals(5, rogue.getToolCheckBonus(Ability.DEXTERITY, "thieves_tools"), "+3 DEX +2 prof");
        assertEquals(3, rogue.getToolCheckBonus(Ability.DEXTERITY, "smiths_tools"), "not proficient: DEX only");

        rogue.restoreExpertise(List.of("thieves_tools", "stealth"));
        assertEquals(7, rogue.getToolCheckBonus(Ability.DEXTERITY, "THIEVES_TOOLS"), "expertise doubles; any spelling");
        assertEquals(7, rogue.getSkillBonus(Skill.STEALTH));
        assertEquals("+3[DEX] +4[Prof ×2]", rogue.getSkillBonusBreakdown(Skill.STEALTH));
        assertEquals(3, rogue.getSkillBonus(Skill.ACROBATICS), "no proficiency, no expertise");
    }

    @Test
    void chosenToolsAndLanguagesRestoreOnLoad() {
        CharacterSheet c = character("human", null, "fighter", "sage", scores());
        c.restoreChosenProficiencies(List.of("Smith's Tools"), List.of("Deep Speech"));
        assertTrue(c.isProficientWithTool("smiths_tools"));
        assertTrue(c.getLanguages().contains("deep_speech"));
        assertTrue(c.getChosenLanguages().contains("deep_speech"), "kept apart from grants so it saves");
    }

    @Test
    void backgroundToolsAreCanonical() {
        CharacterSheet c = character("human", null, "fighter", "sailor", scores());
        assertTrue(c.getToolProficiencies().containsAll(List.of("navigators_tools", "vehicles_water")));
    }

    // ---------- armor proficiency (#209, PHB p.144) ----------

    @Test
    void fighterInPlateHasNoPenalty() {
        CharacterSheet fighter = character("human", null, "fighter", "soldier", scores());
        fighter.equipArmor(armor("plate"));
        fighter.equipShield(armor("shield"));
        assertNull(fighter.armorPenaltyReason());
    }

    @Test
    void wizardInChainMailIsPenalisedOnStrAndDexOnly() {
        CharacterSheet wizard = character("human", null, "wizard", "sage", scores());
        wizard.equipArmor(armor("chain_mail"));
        assertEquals("not proficient with Chain Mail", wizard.armorPenaltyReason());
        assertTrue(wizard.armorPenaltyApplies(Ability.STRENGTH));
        assertTrue(wizard.armorPenaltyApplies(Ability.DEXTERITY));
        assertFalse(wizard.armorPenaltyApplies(Ability.WISDOM));
        assertFalse(wizard.armorPenaltyApplies(Ability.CONSTITUTION));

        wizard.unequipArmor();
        assertNull(wizard.armorPenaltyReason(), "off again, no penalty");
    }

    @Test
    void wizardWithAShieldIsPenalised() {
        CharacterSheet wizard = character("human", null, "wizard", "sage", scores());
        wizard.equipShield(armor("shield"));
        assertEquals("not proficient with Shield", wizard.armorPenaltyReason());
    }

    /** Race armor proficiencies count: Dwarven Armor Training is light + medium, not heavy. */
    @Test
    void mountainDwarfWizardCanWearMediumButNotHeavy() {
        CharacterSheet dwarf = character("dwarf", "mountain_dwarf", "wizard", "sage", scores());
        dwarf.equipArmor(armor("scale_mail"));
        assertNull(dwarf.armorPenaltyReason(), "scale mail is medium");
        dwarf.equipArmor(armor("chain_mail"));
        assertNotNull(dwarf.armorPenaltyReason(), "chain mail is heavy");
    }
}
