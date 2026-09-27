package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.AcFormula;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unarmored Defense and other AC formulas come from YAML (#220): the best formula that applies
 * beats armor / 10 + DEX, and never makes AC worse. Genasi is used for the monks because its
 * bonuses are the player's to place, so the scores below are the scores the sheet has.
 */
class ArmorClassFormulaTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndArmor armor(String id) {
        DndArmor a = ArmorLoader.getArmor(id);
        assertNotNull(a, id);
        return a;
    }

    private static CharacterSheet monk(int dex, int wis) {
        return character("genasi", "fire", "monk", "acolyte", scores(Ability.DEXTERITY, dex, Ability.WISDOM, wis));
    }

    @Test
    void monkUnarmoredIsTenPlusDexPlusWis() {
        CharacterSheet m = monk(16, 14);
        assertEquals(15, m.getArmorClass(), "10 + 3 + 2");
        assertEquals("Unarmored Defense: 10 + DEX + WIS", m.getAcFormulaSource());
    }

    @Test
    void monkInArmorUsesTheArmor() {
        CharacterSheet m = monk(16, 14);
        m.equipArmor(armor("leather_armor"));
        assertEquals(14, m.getArmorClass(), "leather 11 + DEX 3; Unarmored Defense needs no armor");
        assertNull(m.getAcFormulaSource());
        m.unequipArmor();
        assertEquals(15, m.getArmorClass(), "back to Unarmored Defense");
    }

    /** A monk's Unarmored Defense forbids a shield, so picking one up gives 10 + DEX + 2 instead. */
    @Test
    void monkWithShieldLosesUnarmoredDefense() {
        CharacterSheet m = monk(16, 16);
        assertEquals(16, m.getArmorClass());
        m.equipShield(armor("shield"));
        assertEquals(15, m.getArmorClass(), "10 + DEX 3 + shield 2");
        assertNull(m.getAcFormulaSource());
    }

    /** A barbarian's Unarmored Defense allows a shield (PHB p.48): it adds on top. */
    @Test
    void barbarianKeepsUnarmoredDefenseWithAShield() {
        CharacterSheet b = character("genasi", "fire", "barbarian", "acolyte",
                scores(Ability.DEXTERITY, 14, Ability.CONSTITUTION, 16));
        assertEquals(15, b.getArmorClass(), "10 + DEX 2 + CON 3");
        b.equipShield(armor("shield"));
        assertEquals(17, b.getArmorClass(), "plus the shield");
        assertEquals("Unarmored Defense: 10 + DEX + CON", b.getAcFormulaSource());
    }

    /** A formula only ever helps: with WIS 8 the monk's 10 + DEX is better, so that's the AC. */
    @Test
    void aWorseFormulaIsIgnored() {
        CharacterSheet m = monk(14, 8);
        assertEquals(12, m.getArmorClass(), "10 + 2 beats 10 + 2 - 1");
        assertNull(m.getAcFormulaSource());
    }

    @Test
    void classesWithoutItAreUnchanged() {
        CharacterSheet f = character("genasi", "fire", "fighter", "acolyte", scores(Ability.DEXTERITY, 16, Ability.WISDOM, 16));
        assertEquals(13, f.getArmorClass());
    }

    @Test
    void typosAreReportedNotGuessed() {
        AcFormula f = AcFormula.parse(Map.of("base", 13, "add", List.of("dex", "dexterity"), "requires", List.of("no_boots")));
        assertEquals(13, f.getBase());
        assertEquals(List.of(Ability.DEXTERITY), f.getAdd(), "the full name is understood, the abbreviation isn't");
        assertEquals(2, f.getProblems().size(), f.getProblems().toString());
        assertEquals("13 + DEX", f.describe());
    }
}
