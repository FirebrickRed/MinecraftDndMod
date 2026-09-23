package io.papermc.jkvttplugin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Names with spaces (#140). Bukkit splits arguments before we see them, so every command that takes
 * a name has to put it back together the same way — one helper, not a copy per command.
 */
class NameUtilTest {

    private static String[] args(String line) {
        return line.split(" ");
    }

    @Test
    void aQuotedNameIsTakenWhole() {
        NameUtil.TakenName n = NameUtil.takeName(args("spawn guard \"Marcus the Brave\" 100"), 2);
        assertEquals("Marcus the Brave", n.value());
        assertTrue(n.quoted());
        assertEquals(5, n.nextIndex(), "the rest of the command starts after the closing quote");
    }

    @Test
    void oneQuotedWordLosesItsQuotes() {
        NameUtil.TakenName n = NameUtil.takeName(args("rename \"Balin\" Bob"), 1);
        assertEquals("Balin", n.value());
        assertTrue(n.quoted());
        assertEquals(2, n.nextIndex());
    }

    @Test
    void anUnquotedNameIsOneWord() {
        NameUtil.TakenName n = NameUtil.takeName(args("shop add Balin shortsword 10"), 2);
        assertEquals("Balin", n.value());
        assertFalse(n.quoted(), "so a command can tell 'they meant this exactly' from 'one word'");
        assertEquals(3, n.nextIndex());
    }

    /** An unclosed quote takes the rest rather than dropping the command on the floor. */
    @Test
    void anUnclosedQuoteTakesWhatIsThere() {
        NameUtil.TakenName n = NameUtil.takeName(args("rename \"Balin Ironforge"), 1);
        assertEquals("Balin Ironforge", n.value());
        assertEquals(3, n.nextIndex());
    }

    @Test
    void nothingThereIsNull() {
        assertNull(NameUtil.takeName(args("rename"), 1));
        assertNull(NameUtil.takeName(null, 0));
    }

    @Test
    void joinArgsRunsToTheEndAndStripsQuotes() {
        assertEquals("Balin Ironforge", NameUtil.joinArgs(args("view \"Balin Ironforge\""), 1));
        assertEquals("Balin Ironforge", NameUtil.joinArgs(args("view Balin Ironforge"), 1));
        assertEquals("", NameUtil.joinArgs(args("view"), 1));
    }

    @Test
    void stripQuotesTakesOnePairOnly() {
        assertEquals("Balin", NameUtil.stripQuotes("\"Balin\""));
        assertEquals("Balin", NameUtil.stripQuotes("'Balin'"));
        assertEquals("\"Balin", NameUtil.stripQuotes("\"Balin"));
        assertEquals("Balin's Blade", NameUtil.stripQuotes("Balin's Blade"));
    }

    // ---------- readName / collapseName: the one reader ----------

    private static final java.util.List<String> HP_ACTIONS = java.util.List.of("damage", "heal", "temp", "set", "full");

    /** The playtest bug: /dm hp "The Kindler" damage 5 read the name as "The. */
    @Test
    void aQuotedNameReadsToItsClosingQuote() {
        String[] a = NameUtil.collapseName(args("\"The Kindler\" damage 5"), 0, HP_ACTIONS);
        assertArrayEquals(new String[]{"The Kindler", "damage", "5"}, a);
    }

    @Test
    void anUnquotedNameReadsUpToTheCommandsNextKeyword() {
        String[] a = NameUtil.collapseName(args("The Kindler DAMAGE 5"), 0, HP_ACTIONS);
        assertArrayEquals(new String[]{"The Kindler", "DAMAGE", "5"}, a, "stop words ignore case");
        assertArrayEquals(new String[]{"Meepo", "heal", "3"}, NameUtil.collapseName(args("Meepo heal 3"), 0, HP_ACTIONS));
    }

    @Test
    void theFirstWordIsAlwaysTheName() {
        NameUtil.TakenName n = NameUtil.readName(args("heal heal 3"), 0, HP_ACTIONS);
        assertEquals("heal", n.value());
        assertEquals(1, n.nextIndex());
    }

    @Test
    void collapseKeepsWhatComesBefore() {
        String[] a = NameUtil.collapseName(args("clear Balin Ironforge stealth"), 1, java.util.List.of("stealth", "all"));
        assertArrayEquals(new String[]{"clear", "Balin Ironforge", "stealth"}, a);
    }

    @Test
    void noStopWordsReadsToTheEnd() {
        assertEquals("Balin Ironforge", NameUtil.readName(args("Balin Ironforge"), 0, java.util.List.of()).value());
    }

    // ---------- matchByName: one match or none ----------

    private record Named(String name, String base) {}

    private static final java.util.List<Named> GOBLINS = java.util.List.of(
            new Named("Goblin #1", "Goblin"), new Named("Goblin #2", "Goblin"), new Named("Balin Ironforge", "Balin"));

    private static Named match(String q) {
        return NameUtil.matchByName(GOBLINS, q, Named::name, Named::base);
    }

    @Test
    void anExactOrNumberedNameMatches() {
        assertEquals("Goblin #2", match("goblin #2").name());
        assertEquals("Goblin #2", match("Goblin 2").name(), "'Goblin 2' = 'Goblin #2'");
        assertEquals("Balin Ironforge", match("Balin").name(), "a unique base name");
        assertEquals("Balin Ironforge", match("Bal").name(), "a unique prefix");
    }

    /** It used to take the first hit, so a command could act on whichever goblin came first. */
    @Test
    void anAmbiguousNameMatchesNothing() {
        assertNull(match("Goblin"), "two goblins share the base name");
        assertNull(match("Gob"), "two goblins share the prefix");
        assertNull(match("Nobody"));
    }

    /** Two creatures really named "Meepo": the name can't tell them apart, so neither is picked. */
    @Test
    void twoIdenticalNamesMatchNothing() {
        var meepos = java.util.List.of(new Named("Meepo", "Kobold"), new Named("Meepo", "Kobold"));
        assertNull(NameUtil.matchByName(meepos, "Meepo", Named::name, Named::base));
    }
}
