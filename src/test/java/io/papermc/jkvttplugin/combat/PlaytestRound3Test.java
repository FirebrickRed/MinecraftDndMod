package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.character;
import static io.papermc.jkvttplugin.TestContent.scores;
import static org.junit.jupiter.api.Assertions.*;

/** The third playtest round: typed sums, a save that decides its own outcome (#245), the DC spelled out. */
class PlaytestRound3Test {

    @BeforeAll
    static void load() { TestContent.load(); }

    @BeforeEach
    void reset() { SaveOutcome.clear(); }

    // ---- "manualRoll 1+4": several dice added up for you ----

    @Test
    void aSumTypedAsSeparateWordsBecomesOneWord() {
        assertArrayEquals(new String[]{"damage", "Goblin", "manualRoll", "1+4"},
                RollService.joinSums(new String[]{"damage", "Goblin", "manualRoll", "1", "+", "4"}));
        assertArrayEquals(new String[]{"manualRoll", "1+4+6"}, RollService.joinSums(new String[]{"manualRoll", "1+", "4", "+6"}));
        assertArrayEquals(new String[]{"manualRoll", "14", "3"}, RollService.joinSums(new String[]{"manualRoll", "14", "3"}),
                "the d20 and a bonus die (#225) are not a sum");
    }

    @Test
    void manualRollAndTotalReadASum() {
        assertEquals(5, RollService.parseInput(new String[]{"attack", "Goblin", "manualRoll", "1+4"}).providedRoll());
        assertEquals(19, RollService.parseInput(new String[]{"total", "12+7"}).providedTotal());
        assertEquals(14, RollService.parseInput(new String[]{"manualRoll", "14", "3"}).providedRoll(), "still 14, then a bonus die");
        assertEquals(5, RollService.sumOrNull("1+4"));
        assertEquals(7, RollService.sumOrNull("7"));
        assertNull(RollService.sumOrNull("1+"));
        assertNull(RollService.sumOrNull("abc"));
    }

    // ---- #245: the save's result runs the spell's outcome ----

    @Test
    void theGradedSaveRunsTheOutcomeOnce() {
        UUID goblin = UUID.randomUUID();
        List<Boolean> seen = new ArrayList<>();
        SaveOutcome.await(goblin, 13, seen::add);

        assertFalse(SaveOutcome.graded(goblin, 15, false), "a save against some other DC (a trap) isn't this spell's");
        assertFalse(SaveOutcome.graded(UUID.randomUUID(), 13, false), "nor is someone else's save");
        assertTrue(seen.isEmpty());

        assertTrue(SaveOutcome.graded(goblin, 13, false));
        assertEquals(List.of(false), seen, "failed: the damage is offered");
        assertFalse(SaveOutcome.graded(goblin, 13, false), "only once");
    }

    @Test
    void theDmCanRuleItWithoutARoll() {
        UUID goblin = UUID.randomUUID();
        List<Boolean> seen = new ArrayList<>();
        SaveOutcome.await(goblin, 13, seen::add);
        assertTrue(SaveOutcome.rule(goblin, true));
        assertEquals(List.of(true), seen);
        assertFalse(SaveOutcome.isWaiting(goblin));
    }

    @Test
    void theSaveDcIsSpelledOut() {
        CharacterSheet cleric = character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16));
        var sacredFlame = SpellLoader.getSpell("sacred_flame");
        assertEquals(13, cleric.getSpellSaveDc(sacredFlame));
        assertEquals("13 (8 +2[Prof] +3[WIS])", cleric.getSpellSaveDcBreakdown(sacredFlame));
    }
}
