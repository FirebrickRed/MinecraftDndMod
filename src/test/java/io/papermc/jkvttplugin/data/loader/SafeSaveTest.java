package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.SafeFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.papermc.jkvttplugin.TestContent.character;
import static io.papermc.jkvttplugin.TestContent.scores;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #242: a save never leaves a half-written file, and one broken save can't stop the others loading
 * or get buried by the next save.
 */
class SafeSaveTest {

    @TempDir Path dir;

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void aSaveKeepsThePreviousVersionAndLeavesNoTemp() throws Exception {
        File f = dir.resolve("a.yml").toFile();
        SafeFile.write(f, "first: 1\n");
        SafeFile.write(f, "second: 2\n");
        assertEquals("second: 2\n", Files.readString(f.toPath()));
        assertEquals("first: 1\n", Files.readString(SafeFile.backupOf(f).toPath()), "the last good version is kept");
        assertFalse(new File(f.getPath() + ".tmp").exists());
    }

    private static String saved(CharacterSheet sheet) {
        return new Yaml().dump(CharacterPersistenceLoader.serializeCharacterSheet(sheet));
    }

    @Test
    void aBrokenCharacterFallsBackToItsBackupAndIsSetAside() throws Exception {
        CharacterSheet zek = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 15));
        File f = dir.resolve(zek.getCharacterId() + ".yml").toFile();
        SafeFile.write(f, saved(zek));
        SafeFile.write(f, saved(zek)); // now there's a .bak
        Files.writeString(f.toPath(), "characterName: [unclosed\n  : : :"); // a crash mid-write, the old way

        CharacterSheet back = CharacterPersistenceLoader.readOrRecover(new Yaml(), f);
        assertNotNull(back, "loaded from the backup");
        assertEquals(zek.getCharacterId(), back.getCharacterId());
        assertFalse(f.exists(), "the broken file was moved aside, so the next save can't bury it");
        try (var listing = Files.list(dir)) {
            assertTrue(listing.anyMatch(p -> p.getFileName().toString().contains(".broken-")), "and kept for a look");
        }
    }

    @Test
    void aBrokenCharacterWithNoBackupIsSkippedNotThrownAndLeftAlone() throws Exception {
        File f = dir.resolve("broken.yml").toFile();
        Files.writeString(f.toPath(), "characterName: [unclosed\n  : : :");
        assertDoesNotThrow(() -> assertNull(CharacterPersistenceLoader.readOrRecover(new Yaml(), f)));
        assertTrue(f.exists(), "nothing to fall back on: the file stays exactly where it was");
    }
}
