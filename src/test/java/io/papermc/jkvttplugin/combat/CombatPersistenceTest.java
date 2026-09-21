package io.papermc.jkvttplugin.combat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Combat survives a restart (#105/#165): what's saved comes back. The shutdown path itself
 * (suspendForShutdown keeping the file) needs a server; the save file's round trip doesn't.
 */
class CombatPersistenceTest {

    @TempDir Path dir;

    @BeforeEach
    void useTempFolder() { CombatPersistence.setFolder(dir.toFile()); }

    private static Combatant zek() {
        Combatant c = Combatant.fromSavedData(UUID.randomUUID(), Combatant.CombatantType.PLAYER, "Zek", "Zek",
                17, 3, false, false, true, false, List.of("prone", "poisoned"), 1, 2, false, false);
        return c;
    }

    private static Combatant kobold() {
        Combatant c = Combatant.fromSavedData(UUID.randomUUID(), Combatant.CombatantType.ENTITY, "Kobold 2", "Kobold",
                12, 2, true, true, false, false, List.of(), 0, 0, false, true);
        c.beginRitual("alarm", "Alarm", 3);
        return c;
    }

    @Test
    void everythingSavedComesBack() throws Exception {
        UUID session = UUID.randomUUID(), dm = UUID.randomUUID();
        Combatant zek = zek(), kobold = kobold();
        Map<String, Object> data = CombatPersistence.snapshot(session, dm, 3, 1, false, List.of(zek, kobold));
        CombatPersistence.write(new File(dir.toFile(), session + ".yml"), data);

        List<CombatPersistence.Saved> all = CombatPersistence.readAll();
        assertEquals(1, all.size());
        CombatPersistence.Saved s = all.get(0);
        assertEquals(session, s.sessionId());
        assertEquals(dm, s.dmId());
        assertEquals(3, s.roundNumber());
        assertEquals(1, s.currentTurnIndex(), "whose turn it was");
        assertFalse(s.isSetupPhase());

        Combatant z = s.combatants().get(0);
        assertEquals(zek.getId(), z.getId());
        assertEquals(Combatant.CombatantType.PLAYER, z.getType());
        assertEquals(17, z.getInitiative());
        assertTrue(z.isUnconscious());
        assertEquals(1, z.getDeathSaveSuccesses());
        assertEquals(2, z.getDeathSaveFailures());
        assertEquals(List.of("prone", "poisoned"), List.copyOf(z.getConditions()));
        assertFalse(z.isReactionAvailable());

        Combatant k = s.combatants().get(1);
        assertEquals("Kobold 2", k.getDisplayName());
        assertEquals("Kobold", k.getBaseName());
        assertTrue(k.isSurprised());
        assertTrue(k.isHidden());
        assertTrue(k.isChanneling(), "a channelled ritual survives");
        assertEquals("alarm", k.getRitualSpellId());
        assertEquals(3, k.getRitualRoundsLeft());
    }

    /** Nothing to resume: the file is cleared rather than restoring an empty fight every boot. */
    @Test
    void aSaveWithNoCombatantsIsRemoved() throws Exception {
        UUID session = UUID.randomUUID();
        File f = new File(dir.toFile(), session + ".yml");
        CombatPersistence.write(f, CombatPersistence.snapshot(session, UUID.randomUUID(), 1, 0, true, List.of()));
        assertTrue(CombatPersistence.readAll().isEmpty());
        assertFalse(f.exists());
    }

    /** A file we can't read is kept (a DM can look at it; a fixed build can still restore it). */
    @Test
    void anUnreadableSaveIsKept() throws Exception {
        File f = dir.resolve(UUID.randomUUID() + ".yml").toFile();
        Files.writeString(f.toPath(), "sessionId: not-a-uuid\ndmId: also-not\ncombatants: []\n");
        assertTrue(CombatPersistence.readAll().isEmpty());
        assertTrue(f.exists());
    }

    /** One bad combatant entry doesn't lose the rest of the fight. */
    @Test
    void aBadCombatantEntryIsSkipped() {
        Map<String, Object> data = CombatPersistence.snapshot(UUID.randomUUID(), UUID.randomUUID(), 2, 0, false, List.of(zek()));
        @SuppressWarnings("unchecked") List<Object> list = (List<Object>) data.get("combatants");
        list.add(Map.of("id", "garbage", "type", "PLAYER"));
        assertEquals(1, CombatPersistence.parse(data).combatants().size());
    }

    /** Clean end (/combat finished) is the only thing that deletes the file. */
    @Test
    void deleteRemovesTheFile() throws Exception {
        UUID session = UUID.randomUUID();
        File f = new File(dir.toFile(), session + ".yml");
        CombatPersistence.write(f, CombatPersistence.snapshot(session, UUID.randomUUID(), 1, 0, false, List.of(zek())));
        CombatPersistence.delete(session);
        assertFalse(f.exists());
    }
}
