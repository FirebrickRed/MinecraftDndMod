package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** How /dm entity reads names off a line: spawn's custom name, and several names for remove. */
class EntityCommandNamesTest {

    private static String[] words(String line) { return line.split(" "); }

    @Test
    void spawnKeepsAWholeUnquotedName() {
        assertEquals(new EntityNames.SpawnName("Meepo the Bold", 5), EntityNames.parseSpawnName(words("spawn kobold Meepo the Bold")));
    }

    @Test
    void spawnReadsTrailingCoordinatesAfterTheName() {
        assertEquals(new EntityNames.SpawnName("Meepo the Bold", 5),
                EntityNames.parseSpawnName(words("spawn kobold Meepo the Bold ~ 64 ~5")));
    }

    @Test
    void aNameEndingInANumberIsStillAName() {
        assertEquals("Guard 3", EntityNames.parseSpawnName(words("spawn guard Guard 3")).name());
        assertEquals("Guard 3", EntityNames.parseSpawnName(words("spawn guard Guard 3 10 64 10")).name());
    }

    @Test
    void spawnWithOnlyCoordinatesOrNothingHasNoName() {
        assertNull(EntityNames.parseSpawnName(words("spawn kobold ~ 64 ~")).name());
        assertNull(EntityNames.parseSpawnName(words("spawn kobold")).name());
        assertEquals("Marcus the Brave", EntityNames.parseSpawnName(words("spawn guard \"Marcus the Brave\" ~ ~ ~")).name());
    }

    // ---------- remove: several names on one line ----------

    private final List<DndEntityInstance> spawned = new ArrayList<>();

    private void spawn(String name) {
        TestContent.load();
        spawned.add(new DndEntityInstance(EntityLoader.getEntity("balin_blacksmith"), null, name, 10));
    }

    @AfterEach
    void despawn() { spawned.forEach(DndEntityInstance::unregister); }

    @Test
    void removeReadsTwoNumberedWolvesAsTwoNames() {
        spawn("Wolf");
        spawn("Wolf #2");
        assertEquals(List.of("wolf #1", "wolf #2"), EntityNames.splitCreatureNames(words("remove wolf #1 wolf #2"), 1));
    }

    @Test
    void removeReadsANameWithSpacesQuotedOrNot() {
        spawn("The Kindler");
        assertEquals(List.of("The Kindler"), EntityNames.splitCreatureNames(words("remove The Kindler"), 1));
        assertEquals(List.of("The Kindler"), EntityNames.splitCreatureNames(words("remove \"The Kindler\""), 1));
    }

    @Test
    void aWordThatNamesNothingIsKeptSoItCanBeReported() {
        spawn("Wolf");
        assertEquals(List.of("Wolf", "nobody"), EntityNames.splitCreatureNames(words("remove Wolf nobody"), 1));
    }
}
