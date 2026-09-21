package io.papermc.jkvttplugin.data.model.enums;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.parser.LanguageParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** One id per tool and per language, whatever the spelling. */
class RegistryTest {

    @Test
    void toolSpellingsFoldToOneId() {
        assertEquals("navigators_tools", ToolRegistry.idOf("Navigator's Tools"));
        assertEquals("navigators_tools", ToolRegistry.idOf("navigators_tools"));
        assertEquals("vehicles_land", ToolRegistry.idOf("vehicles(land)"));
        assertEquals("vehicles_water", ToolRegistry.idOf("Vehicles (Water)"));
        assertEquals("three_dragon_ante_set", ToolRegistry.idOf("Three-Dragon Ante Set"));
        assertEquals("thieves_tools", ToolRegistry.idOf("Thieves' Tools"));
        assertEquals("thieves_tools", ToolRegistry.idOf("Thieves’ Tools")); // curly apostrophe
    }

    @Test
    void toolsAreItemsPlusVehicles() {
        TestContent.load();
        assertEquals(17, ToolRegistry.getByCategory(ToolRegistry.Category.ARTISAN_TOOL).size(), "all 17 artisan's tools");
        assertEquals(10, ToolRegistry.getByCategory(ToolRegistry.Category.MUSICAL_INSTRUMENT).size(), "10 instruments");
        assertTrue(ToolRegistry.getByCategory(ToolRegistry.Category.GAMING_SET)
                .containsAll(List.of("dice_set", "playing_card_set", "dragonchess_set", "three_dragon_ante_set")));
        for (String v : List.of("vehicles_land", "vehicles_water", "vehicles_air", "vehicles_space")) {
            assertTrue(ToolRegistry.isRegistered(v), v);
        }
        assertEquals("Navigator's Tools", ToolRegistry.displayName("navigators_tools"));
    }

    /** The Healer's Kit is gear anyone can use, not a tool proficiency. */
    @Test
    void healersKitIsNotATool() {
        TestContent.load();
        assertFalse(ToolRegistry.isRegistered("healers_kit"));
    }

    @Test
    void categoryTagsExpand() {
        TestContent.load();
        assertTrue(ToolRegistry.expandTag("artisan_tool").contains("smiths_tools"));
        assertTrue(ToolRegistry.expandTag("artisans_tools").contains("smiths_tools"), "plural/possessive spelling");
        assertTrue(ToolRegistry.expandTag("musical_instrument").contains("lute"));
        assertNull(ToolRegistry.expandTag("smiths_tools"), "a tool id isn't a tag");
    }

    /** Picking a lock is DEX + thieves' tools (PHB p.154); the default comes from the item YAML. */
    @Test
    void thievesToolsDefaultToDexterity() {
        TestContent.load();
        assertEquals(Ability.DEXTERITY, ToolRegistry.get("thieves_tools").checkAbility());
        assertNull(ToolRegistry.get("smiths_tools").checkAbility(), "other tools make the DM name one");
    }

    @Test
    void languageSpellingsFoldToOneId() {
        assertEquals("deep_speech", LanguageRegistry.idOf("Deep Speech"));
        assertEquals("deep_speech", LanguageRegistry.idOf("deep speech"));
        assertEquals("Deep Speech", LanguageRegistry.displayName("deep_speech"));
        assertEquals(List.of("common", "elvish"), LanguageParser.parseLanguages(List.of("Common", "elvish", "COMMON")));
    }

    @Test
    void unknownLanguageFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> LanguageParser.parseLanguages(List.of("Klingon")));
    }

    @Test
    void homebrewLanguagesComeFromLanguagesYml(@TempDir Path dir) throws Exception {
        File yml = dir.resolve("Languages.yml").toFile();
        Files.writeString(yml.toPath(), "- Thieves' Cant\n- Druidic\n");
        LanguageRegistry.load(yml);
        assertTrue(LanguageRegistry.isRegistered("thieves cant"));
        assertTrue(LanguageRegistry.isRegistered("Druidic"));
        assertTrue(LanguageRegistry.isRegistered("Common"), "built-ins stay");
    }

    @AfterEach
    void restoreDefaultLanguages() {
        LanguageRegistry.load(new File("DMContent/Languages.yml")); // absent in the repo → defaults
    }
}
