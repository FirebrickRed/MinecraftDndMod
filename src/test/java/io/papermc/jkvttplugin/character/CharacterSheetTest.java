package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
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

    // ---------- tool checks + expertise (#207) ----------

    @Test
    void thievesToolsCheckAddsProficiencyAndDoublesWithExpertise() {
        CharacterSheet rogue = character("human", null, "rogue", "urchin", scores(Ability.DEXTERITY, 16), Skill.STEALTH);
        assertEquals(5, rogue.getToolCheckBonus(Ability.DEXTERITY, "thieves_tools"), "+3 DEX +2 prof");
        assertEquals(3, rogue.getToolCheckBonus(Ability.DEXTERITY, "smiths_tools"), "not proficient: DEX only");

        rogue.restoreExpertise(List.of("thieves_tools", "stealth"));
        assertEquals(7, rogue.getToolCheckBonus(Ability.DEXTERITY, "THIEVES_TOOLS"), "expertise doubles; any spelling");
        assertEquals(7, rogue.getSkillBonus(Skill.STEALTH));
        assertEquals("+3[DEX] +4[Expertise]", rogue.getSkillBonusBreakdown(Skill.STEALTH));
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
