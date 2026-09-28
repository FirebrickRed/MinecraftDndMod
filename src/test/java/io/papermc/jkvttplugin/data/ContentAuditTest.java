package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.parser.EquipmentParser;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The race/class/background audit against the PHB (2026-09): each test is a mistake that was in
 * the YAML or the loader, so it can't come back.
 */
class ContentAuditTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    /** The paladin had shield proficiency only, so its own starting chain mail put it at disadvantage. */
    @Test
    void paladinsWearTheirChainMail() {
        CharacterSheet p = character("human", null, "paladin", "soldier", scores());
        assertTrue(p.isProficientWithArmor(ArmorLoader.getArmor("chain_mail")));
    }

    /** The paladin had no skill pick, and its equipment choices had no type:, so they were dropped. */
    @Test
    void paladinsPickSkillsAndWeapons() {
        var s = session("human", null, "paladin", "soldier");
        choice(s, "class_skills");
        choice(s, "class_equipment_1");
        choice(s, "class_equipment_2");
    }

    /** "martial_weapon x2" is two weapons; the quantity on a tag used to be dropped (one weapon). */
    @Test
    void aTagWithAQuantityIsThatManyPicks() {
        List<EquipmentOption> opts = EquipmentParser.parseEquipmentOptions(List.of("martial_weapon x2", "bolt x20"));
        assertEquals(EquipmentOption.Kind.BUNDLE, opts.get(0).getKind());
        assertEquals(2, opts.get(0).getParts().size());
        assertEquals(EquipmentOption.Kind.ITEM, opts.get(1).getKind(), "an item keeps its quantity as one stack");
        assertEquals(20, opts.get(1).getQuantity());
    }

    /**
     * "Two martial weapons" is picked one at a time and can be two different weapons; a pick with a
     * weapon still to choose doesn't count as done (they all used to become the first pick).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void twoWeaponsArePickedOneAtATime() {
        var s = session("human", null, "fighter", "soldier");
        var pc = (io.papermc.jkvttplugin.data.model.PendingChoice) choice(s, "class_equipment_1");
        EquipmentOption two = ((List<EquipmentOption>) pc.getPlayersChoice().getOptions()).stream()
                .filter(o -> o.openSlots() == 2).findFirst().orElseThrow();

        EquipmentOption half = two.fillFirstOpenSlot(EquipmentOption.item("longsword"));
        assertTrue(half.hasOpenSlot());
        assertTrue(two.isPartlyFilledBy(half));
        pc.toggleOption(half, java.util.Set.of());
        assertFalse(pc.isComplete(), "one weapon picked, one to go");

        EquipmentOption both = half.fillFirstOpenSlot(EquipmentOption.item("warhammer"));
        assertEquals("Longsword + Warhammer", both.prettyLabel());
        assertTrue(two.isPartlyFilledBy(both));
        pc.getChosen().remove(half);
        pc.toggleOption(both, java.util.Set.of());
        assertTrue(pc.isComplete());
        assertFalse(two.isPartlyFilledBy(half.fillFirstOpenSlot(EquipmentOption.item("dagger"))), "a dagger isn't a martial weapon");
    }

    /** "Half your level, rounded down": 0 at level 1, so a level-1 artificer prepares INT mod spells. */
    @Test
    void halfCastersRoundDown() {
        var formula = ClassLoader.getClass("artificer").getSpellcastingInfo().getSpellsPreparedFormula();
        assertNotNull(formula, "the artificer had no formula and fell back to a full caster's");
        assertEquals(3, formula.calculate(scores(Ability.INTELLIGENCE, 16), 1));
        assertEquals(4, formula.calculate(scores(Ability.INTELLIGENCE, 16), 2));
    }

    /** Every class had its cantrip count one level late (a cleric's 4th cantrip is at level 4). */
    @Test
    void cantripsGrowAtLevelFourAndTen() {
        var cleric = ClassLoader.getClass("cleric").getSpellcastingInfo().getCantripsKnownByLevel();
        assertEquals(3, cleric.get(2), "level 3");
        assertEquals(4, cleric.get(3), "level 4");
        assertEquals(5, cleric.get(9), "level 10");
    }

    /** Druidic and Thieves' Cant are known through the class, and never offered as a pick. */
    @Test
    void classSecretLanguages() {
        CharacterSheet d = character("human", null, "druid", "hermit", scores());
        assertTrue(d.getLanguages().stream().anyMatch(l -> LanguageRegistry.idOf(l).equals("druidic")));
        assertFalse(LanguageRegistry.getAllLanguages().contains("druidic"));
        assertFalse(LanguageRegistry.getAllLanguages().contains(LanguageRegistry.idOf("Thieves' Cant")));
    }

    /** Divine Sense is 1 + CHA; Bardic Inspiration is CHA but at least once. */
    @Test
    void resourcesWithAPlusAndAMinimum() {
        CharacterSheet p = character("human", null, "paladin", "soldier", scores(Ability.CHARISMA, 14));
        assertEquals(3, p.getResource("Divine Sense").getMax());
        assertEquals(5, p.getResource("Lay on Hands").getMax(), "5 x level");
        CharacterSheet b = character("human", null, "bard", "entertainer", scores(Ability.CHARISMA, 10));
        assertNotNull(b.getResource("Bardic Inspiration"), "CHA 10 used to mean no Bardic Inspiration at all");
        assertEquals(1, b.getResource("Bardic Inspiration").getMax());
    }

    /** Hex Warrior's proficiencies were only in the description text. */
    @Test
    void hexbladesAreArmed() {
        CharacterSheet h = CharacterSheet.loadFromData(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Test",
                "human", null, "warlock", "the_hexblade", "acolyte", scores(), new java.util.HashSet<>(),
                java.util.Set.of(), java.util.Set.of(), 10, 10, 10);
        assertTrue(h.isProficientWithArmor(ArmorLoader.getArmor("shield")));
    }

    /** A giff has advantage on every STR check and save (Hippo Build), not just when shoving things. */
    @Test
    void hippoBuild() {
        CharacterSheet g = character("giff", null, "fighter", "soldier", scores());
        assertNotNull(g.effectAdvantageSource(List.of("str_saves")));
        assertNotNull(g.effectAdvantageSource(List.of("str_checks")));
    }
}
