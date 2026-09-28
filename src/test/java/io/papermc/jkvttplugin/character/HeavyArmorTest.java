package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** #34: armor too heavy for your Strength slows you by 10 ft (PHB p.144) but still protects you. */
class HeavyArmorTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void chainMailWithoutTheStrengthCostsTenFeetButKeepsItsAc() {
        CharacterSheet weak = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 10));
        weak.equipArmor(ArmorLoader.getArmor("chain_mail"));
        assertEquals(20, weak.getSpeed(), "30 ft, less 10 for chain mail at STR 10 (it needs 13)");
        assertEquals(16, weak.getArmorClass(), "chain mail still gives its AC 16; it used to fall back to 10 + DEX");

        CharacterSheet strong = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 15));
        strong.equipArmor(ArmorLoader.getArmor("chain_mail"));
        assertEquals(30, strong.getSpeed());
    }

    @Test
    void aDwarfIsNotSlowedByHeavyArmor() {
        CharacterSheet dwarf = character("dwarf", "hill_dwarf", "fighter", "soldier", scores(Ability.STRENGTH, 10));
        dwarf.equipArmor(ArmorLoader.getArmor("plate"));
        assertEquals(25, dwarf.getSpeed());
    }

    @Test
    void lightArmorNeverSlows() {
        CharacterSheet weak = character("human", null, "rogue", "criminal", scores(Ability.STRENGTH, 8));
        weak.equipArmor(ArmorLoader.getArmor("leather_armor"));
        assertEquals(30, weak.getSpeed());
    }
}
