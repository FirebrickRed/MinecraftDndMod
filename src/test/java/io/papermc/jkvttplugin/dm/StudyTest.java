package io.papermc.jkvttplugin.dm;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Study checks (#231): which tiers a total clears, which rolls count, and that results survive a
 * save. The click, the roll buttons and the DM's [Tell them] need a server (TEST_PLAN.md).
 */
class StudyTest {

    private static Study library() {
        Study s = new Study();
        s.mode = Study.Mode.ROLLED;
        s.checks.add("history");
        s.checks.add("arcana");
        s.setTiers(List.of(
                new Study.Tier(20, "The binding is Netherese."),
                new Study.Tier(10, "Old histories of the region."),
                new Study.Tier(15, "One volume is missing.")));
        return s;
    }

    @Test
    void eachTierClearedAddsItsLine() {
        Study s = library();
        assertEquals(0, s.tierFor(9), "below every DC: just the description");
        assertEquals(1, s.tierFor(10), "meeting the DC clears it");
        assertEquals(2, s.tierFor(19));
        assertEquals(3, s.tierFor(25));
        assertEquals(List.of("Old histories of the region.", "One volume is missing."), s.linesUpTo(2),
                "tiers are sorted by DC whatever order they were typed in");
        assertTrue(s.linesUpTo(0).isEmpty());
    }

    @Test
    void blankTiersAreDroppedAndThereAreAtMostThree() {
        Study s = new Study();
        s.setTiers(List.of(new Study.Tier(10, "  "), new Study.Tier(12, "a"), new Study.Tier(5, "b"),
                new Study.Tier(30, "c"), new Study.Tier(25, "d")));
        assertEquals(3, s.tiers().size());
        assertEquals(List.of(5, 12, 25), s.tiers().stream().map(Study.Tier::dc).toList(), "the hardest extra one goes");
    }

    @Test
    void onlyAStudyWithASkillIsOn() {
        Study s = new Study();
        s.mode = Study.Mode.PASSIVE;
        assertFalse(s.active(), "no skill picked");
        s.checks.add("investigation");
        assertTrue(s.active());
        s.mode = Study.Mode.OFF;
        assertFalse(s.active());
    }

    @Test
    void checkNamesFoldToOneId() {
        assertEquals("sleight_of_hand", Study.normalizeCheck("Sleight of Hand"));
        assertEquals("history", Study.normalizeCheck("HISTORY"));
        assertEquals("intelligence", Study.normalizeCheck("INT"));
        assertEquals("wisdom", Study.normalizeCheck("wisdom"));
        assertNull(Study.normalizeCheck("none"));
        assertNull(Study.normalizeCheck("thieves_tools"));
        assertEquals("Intelligence check", Study.displayName("intelligence"));
        assertEquals("History", Study.displayName("history"));
    }

    /** The roll that answers a study prompt comes back from /character check as a type and value. */
    @Test
    void skillsAndAbilityChecksCountButSavesDoNot() {
        assertEquals("history", Study.checkOfRoll("SKILL", "HISTORY"));
        assertEquals("intelligence", Study.checkOfRoll("CHECK", "INTELLIGENCE"));
        assertNull(Study.checkOfRoll("SAVE", "INTELLIGENCE"), "a save isn't studying");
        assertNull(Study.checkOfRoll("TOOL", "DEXTERITY:thieves_tools"));
        assertNull(Study.checkOfRoll("SKILL", "INTELLIGENCE"));
    }

    @Test
    void resultsAndTiersSurviveASave() {
        Study s = library();
        s.dmFirst = true;
        UUID told = UUID.randomUUID(), waiting = UUID.randomUUID();
        s.results.put(told, new Study.Result("Mira", "history", 17, Study.How.TYPED, 2, 1));
        s.results.put(waiting, new Study.Result("Tobin", "arcana", 23, Study.How.GAME, 3, null));

        YamlConfiguration yaml = new YamlConfiguration();
        s.save(yaml.createSection("study"));
        Study back = Study.load(YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml.saveToString()))
                .getConfigurationSection("study"));

        assertEquals(Study.Mode.ROLLED, back.mode);
        assertEquals(List.of("history", "arcana"), back.checks);
        assertTrue(back.dmFirst);
        assertEquals(s.tiers(), back.tiers());
        assertEquals(s.results.get(told), back.results.get(told), "the DM told Mira tier 1 though she earned 2");
        assertNull(back.results.get(waiting).shown(), "still waiting on the DM after a restart");
    }

    @Test
    void anUnsetStudyLoadsAsOff() {
        Study s = Study.load(null);
        assertEquals(Study.Mode.OFF, s.mode);
        assertFalse(s.active());
    }
}
