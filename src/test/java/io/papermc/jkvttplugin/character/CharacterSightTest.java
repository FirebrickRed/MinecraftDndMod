package io.papermc.jkvttplugin.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #148: when it's dark enough for the darkvision view limit, and how far that is. */
class CharacterSightTest {

    @Test
    void darknessCountsTorchesAndNight() {
        assertFalse(CharacterSight.isDark(0, 15, true), "open sky by day");
        assertTrue(CharacterSight.isDark(0, 15, false), "open sky at night");
        assertFalse(CharacterSight.isDark(14, 15, false), "next to a torch at night");
        assertTrue(CharacterSight.isDark(0, 0, true), "a cave at noon");
        assertTrue(CharacterSight.isDark(7, 0, true), "dim light still counts");
        assertFalse(CharacterSight.isDark(8, 0, true));
    }

    @Test
    void viewChunksNeverGoBelowMinecraftsFloor() {
        assertEquals(2, CharacterSight.viewChunks(60));
        assertEquals(2, CharacterSight.viewChunks(120));
        assertEquals(4, CharacterSight.viewChunks(300));
    }
}
