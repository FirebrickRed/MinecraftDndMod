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
}
