package io.papermc.jkvttplugin.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@code /dm rest <character|all> <short|long> [time passed]}, read from the end so names can have spaces. */
class RestArgsTest {

    private static RestCommand.Args parse(String line) {
        return RestCommand.parse(line.split(" "));
    }

    @Test
    void nameAndType() {
        assertEquals(new RestCommand.Args("Bob", "long", null), parse("Bob long"));
        assertEquals(new RestCommand.Args("Mira Stoneheart", "short", null), parse("Mira Stoneheart SHORT"));
    }

    @Test
    void timePassed() {
        assertEquals(new RestCommand.Args("all", "long", 480), parse("all long 8h"));
        assertEquals(new RestCommand.Args("Mira Stoneheart", "short", 90), parse("Mira Stoneheart short 1h30m"));
    }

    @Test
    void refused() {
        assertNull(parse("Bob"));
        assertNull(parse("long"));
        assertNull(parse("long 8h"), "no name");
        assertNull(parse("Bob nap"));
    }
}
