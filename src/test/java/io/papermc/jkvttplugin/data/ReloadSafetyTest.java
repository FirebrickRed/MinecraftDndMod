package io.papermc.jkvttplugin.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #243: a YAML file that won't parse loads nothing, so /dm reload checks every file first and refuses
 * to clear what's loaded. (That loader warnings count as content warnings is held by
 * ContentLoadTest.contentCheckIsClean, which now sees them.)
 */
class ReloadSafetyTest {

    @TempDir Path dir;

    @Test
    void theRealContentParses() {
        assertEquals(List.of(), new DataManager(new File("DMContent")).syntaxErrors());
    }

    @Test
    void aBrokenFileIsNamedWithItsLine() throws Exception {
        Files.createDirectories(dir.resolve("Spells"));
        Files.writeString(dir.resolve("Spells/good.yml"), "spells:\n  light:\n    name: Light\n");
        Files.writeString(dir.resolve("Spells/bad.yml"), "spells:\n  fire_bolt:\n    name: Fire Bolt\n   level: 0\n");
        List<String> errors = new DataManager(dir.toFile()).syntaxErrors();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("Spells/bad.yml line 4"), errors.get(0));
    }
}
