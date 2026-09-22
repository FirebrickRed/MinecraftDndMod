package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.character.CharacterSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Character files: deleting archives instead of erasing, and a hand-over re-files the character. */
class CharacterFilesTest {

    @TempDir Path dir;

    @Test
    void deletedCharacterIsArchivedNotErased() {
        CharacterPersistenceLoader.setDataFolder(dir.toFile());
        CharacterSheet zek = character("human", null, "fighter", "soldier", scores());
        CharacterPersistenceLoader.saveCharacter(zek);
        CharacterPersistenceLoader.storeCharacterInMemory(zek);
        File live = new File(dir.toFile(), zek.getCharacterId() + ".yml");
        assertTrue(live.exists());

        CharacterPersistenceLoader.removeCharacter(zek.getPlayerId(), zek.getCharacterId());

        assertFalse(live.exists(), "out of play");
        assertTrue(new File(dir.toFile(), "Deleted/" + zek.getCharacterId() + ".yml").exists(), "but kept");
        assertNull(CharacterPersistenceLoader.getCharacter(zek.getPlayerId(), zek.getCharacterId()));
    }

    /** /character give to a different player transfers the character: it moves to the new owner. */
    @Test
    void transferMovesTheCharacterToItsNewOwner() {
        CharacterPersistenceLoader.setDataFolder(dir.toFile());
        CharacterSheet zek = character("human", null, "fighter", "soldier", scores());
        CharacterPersistenceLoader.storeCharacterInMemory(zek);
        java.util.UUID oldOwner = zek.getPlayerId();
        java.util.UUID brother = java.util.UUID.randomUUID();

        CharacterPersistenceLoader.transferCharacter(zek, brother);

        assertEquals(brother, zek.getPlayerId());
        assertNull(CharacterPersistenceLoader.getCharacter(oldOwner, zek.getCharacterId()), "gone from the old owner");
        assertSame(zek, CharacterPersistenceLoader.getCharacter(brother, zek.getCharacterId()));
        assertEquals(brother.toString(), CharacterPersistenceLoader.serializeCharacterSheet(zek).get("playerId"),
                "and the file says so, so it survives a restart");
        assertTrue(new File(dir.toFile(), zek.getCharacterId() + ".yml").exists());
    }
}
