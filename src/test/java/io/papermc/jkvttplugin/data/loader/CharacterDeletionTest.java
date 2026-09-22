package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.character.CharacterSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deleting a character never erases its file: it's archived, so a DM can bring it back. */
class CharacterDeletionTest {

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
}
