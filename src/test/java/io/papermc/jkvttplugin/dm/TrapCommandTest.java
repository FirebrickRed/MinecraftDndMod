package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.SaveOutcome;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code /dm trap} (#202): what a trap line says, and what the damage becomes once the save is graded.
 * Calling the save and landing the damage need a server (TEST_PLAN).
 */
class TrapCommandTest {

    private static TrapCommand.Spec parse(String line) {
        List<String> problems = new ArrayList<>();
        TrapCommand.Spec spec = TrapCommand.parse(line.split(" "), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        return spec;
    }

    private static String problemWith(String line) {
        List<String> problems = new ArrayList<>();
        assertNull(TrapCommand.parse(line.isEmpty() ? new String[0] : line.split(" "), problems), line);
        assertEquals(1, problems.size(), "one clear complaint: " + problems);
        return problems.get(0);
    }

    @Test
    void aFullTrapLine() {
        TrapCommand.Spec s = parse("dexterity dc 13 2d10 type fire half name Flame jet");
        assertEquals(Ability.DEXTERITY, s.save());
        assertEquals(13, s.dc());
        assertEquals("2d10", s.damage());
        assertEquals("fire", s.type());
        assertTrue(s.halfOnSave());
        assertEquals("Flame jet", s.name(), "the name runs to the end of the line");
    }

    @Test
    void theShortestTrapAndItsDefaults() {
        TrapCommand.Spec s = parse("con dc 12 8");
        assertEquals(Ability.CONSTITUTION, s.save(), "an abbreviation works");
        assertEquals("8", s.damage(), "a number is the DM's own roll");
        assertNull(s.type());
        assertFalse(s.halfOnSave(), "a save takes nothing unless it says half");
        assertNull(s.name());
    }

    @Test
    void theOrderOfTheOptionsDoesNotMatter() {
        TrapCommand.Spec s = parse("wisdom half type psychic 3d6 dc 15");
        assertEquals(15, s.dc());
        assertEquals("3d6", s.damage());
        assertEquals("psychic", s.type());
        assertTrue(s.halfOnSave());
    }

    @Test
    void whatIsMissingIsSaid() {
        assertTrue(problemWith("").contains("which save"));
        assertTrue(problemWith("luck dc 13 2d10").contains("isn't an ability"));
        assertTrue(problemWith("dexterity 2d10").contains("needs a DC"));
        assertTrue(problemWith("dexterity dc 13").contains("needs its damage"));
        assertTrue(problemWith("dexterity dc lots 2d10").contains("wants a number"));
        assertTrue(problemWith("dexterity dc 13 2d10 fire").contains("'fire'"), "a damage type needs the word type");
    }

    @Test
    void theSaveDecidesTheDamage() {
        assertEquals(11, TrapCommand.damageAfterSave(11, false, true), "failed: all of it");
        assertEquals(11, TrapCommand.damageAfterSave(11, false, false));
        assertEquals(5, TrapCommand.damageAfterSave(11, true, true), "saved for half, rounded down");
        assertEquals(0, TrapCommand.damageAfterSave(11, true, false), "saved: nothing");
    }

    /** The trap rides on the ordinary save: graded at its DC, the damage step runs exactly once. */
    @Test
    void theDamageWaitsForTheGradedSave() {
        UUID victim = UUID.randomUUID();
        List<Boolean> ran = new ArrayList<>();
        SaveOutcome.await(victim, 13, ran::add);
        assertFalse(SaveOutcome.graded(victim, 15, false), "some other save at another DC isn't the trap's");
        assertTrue(ran.isEmpty());
        assertTrue(SaveOutcome.graded(victim, 13, false));
        assertEquals(List.of(false), ran);
        assertFalse(SaveOutcome.graded(victim, 13, false), "it doesn't fire twice");
    }
}
