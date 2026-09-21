package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.DndBackground;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole of DMContent loads, and the content check is clean. "A clean load prints nothing" was a
 * convention you had to read the console to keep; this makes it one the build enforces.
 */
class ContentLoadTest {

    @Test
    void contentCheckIsClean() {
        List<String> warnings = TestContent.load();
        assertTrue(warnings.isEmpty(), "Content check warnings:\n  " + String.join("\n  ", warnings));
    }

    @Test
    void everyContentTypeLoads() {
        TestContent.load();
        assertFalse(SpellLoader.getAllSpells().isEmpty(), "spells");
        assertFalse(WeaponLoader.getAllWeapons().isEmpty(), "weapons");
        assertFalse(ArmorLoader.getAllArmors().isEmpty(), "armor");
        assertFalse(ItemLoader.getAllItems().isEmpty(), "items");
        assertFalse(RaceLoader.getAllRaces().isEmpty(), "races");
        assertFalse(ClassLoader.getAllClasses().isEmpty(), "classes");
        assertFalse(EntityLoader.getAllEntities().isEmpty(), "entities");
    }

    /** All 13 PHB backgrounds plus the extras — one bad entry used to drop its whole file. */
    @Test
    void allBackgroundsLoad() {
        TestContent.load();
        for (String id : List.of("acolyte", "charlatan", "criminal", "entertainer", "folk_hero", "guild_artisan",
                "hermit", "noble", "outlander", "sage", "sailor", "soldier", "urchin",
                "anthropologist", "archaeologist", "athlete", "wildspacer", "haunted_one")) {
            assertNotNull(BackgroundLoader.getBackground(id), "background " + id);
        }
    }

    @Test
    void everyBackgroundHasAFeature() {
        TestContent.load();
        for (DndBackground b : BackgroundLoader.getAllBackgrounds()) {
            assertNotNull(b.getFeature(), b.getId() + " has no feature");
            assertFalse(b.getFeature().name().isBlank(), b.getId() + " feature has no name");
        }
    }

    /** Every class YAML loads — a missing ritual_casting: used to drop a whole class. */
    @Test
    void allClassesLoad() {
        TestContent.load();
        for (String id : List.of("artificer", "barbarian", "bard", "cleric", "druid", "fighter", "monk",
                "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard")) {
            assertNotNull(ClassLoader.getClass(id), "class " + id);
        }
    }
}
