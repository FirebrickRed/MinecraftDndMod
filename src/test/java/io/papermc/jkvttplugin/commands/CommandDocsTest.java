package io.papermc.jkvttplugin.commands;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The help, the tab completion and COMMANDS.md say the same thing the code does. They're four copies
 * of one list, and they drift: a removed command stays documented (/dm entity cleanup), or a new one
 * is never offered by Tab. Reads the sources, like a reviewer would.
 */
class CommandDocsTest {

    private static final Path DM = Path.of("src/main/java/io/papermc/jkvttplugin/dm/DmCommand.java");
    private static final Path ENTITY = Path.of("src/main/java/io/papermc/jkvttplugin/commands/DmEntityCommand.java");
    private static final Path DOCS = Path.of("COMMANDS.md");

    /** The main names in the first {@code switch (subcommand)}: "check" of {@code case "check", "promptcheck"}. */
    private static Set<String> dispatched(Path source) throws IOException {
        String src = Files.readString(source);
        int start = src.indexOf("switch (subcommand) {");
        int end = src.indexOf("default ->", start);
        assertTrue(start >= 0 && end > start, "no subcommand switch in " + source);
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("case \"([a-z]+)\"").matcher(src.substring(start, end));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Every alias in the switch ("promptcheck", "goto"): documented through its main name. */
    private static Set<String> aliases(Path source) throws IOException {
        String src = Files.readString(source);
        int start = src.indexOf("switch (subcommand) {");
        int end = src.indexOf("default ->", start);
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("case \"[a-z]+\"((?:, \"[a-z]+\")+)").matcher(src.substring(start, end));
        while (m.find()) {
            Matcher a = Pattern.compile("\"([a-z]+)\"").matcher(m.group(1));
            while (a.find()) out.add(a.group(1));
        }
        return out;
    }

    /** First word of every row in a COMMANDS.md table, from the heading containing {@code heading} to the next heading. */
    private static Set<String> documented(String heading) throws IOException {
        String docs = Files.readString(DOCS);
        int start = docs.indexOf(heading);
        assertTrue(start >= 0, "COMMANDS.md has no section " + heading);
        int end = docs.indexOf("\n#", start + 1);
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("(?m)^\\| `([a-z]+)((?:\\\\\\|[a-z]+)*)").matcher(docs.substring(start, end < 0 ? docs.length() : end));
        while (m.find()) {
            out.add(m.group(1));
            for (String more : m.group(2).split("\\\\\\|")) if (!more.isBlank()) out.add(more);
        }
        return out;
    }

    @Test
    void dmEntityDispatchTabAndDocsAgree() throws IOException {
        Set<String> code = dispatched(ENTITY);

        String src = Files.readString(ENTITY);
        Matcher tab = Pattern.compile("return List\\.of\\((\"spawn\"[^)]*)\\)").matcher(src);
        assertTrue(tab.find(), "no subcommand tab list in DmEntityCommand");
        Set<String> tabbed = new TreeSet<>();
        Matcher w = Pattern.compile("\"([a-z]+)\"").matcher(tab.group(1));
        while (w.find()) tabbed.add(w.group(1));

        assertEquals(code, tabbed, "Tab completion for /dm entity doesn't match what it dispatches");
        assertEquals(code, documented("Entities & items"), "COMMANDS.md's /dm entity section doesn't match the code");

        // /dm's help line lists them too (the unimplemented stub may be left out).
        Matcher help = Pattern.compile("/dm entity <([a-z|]+)>").matcher(Files.readString(DM));
        assertTrue(help.find(), "no /dm entity line in /dm's help");
        Set<String> helped = new TreeSet<>(Set.of(help.group(1).split("\\|")));
        Set<String> expected = new TreeSet<>(code);
        expected.remove("spawngroup"); // #79: a stub, not advertised
        assertEquals(expected, helped, "/dm's help line for /dm entity doesn't match the code");
    }

    @Test
    void dmDispatchAndDocsAgree() throws IOException {
        Set<String> code = dispatched(DM);
        Set<String> docs = new LinkedHashSet<>(documented("DM admin"));
        docs.add("entity"); // documented as its own section
        Set<String> missing = new TreeSet<>(code);
        missing.removeAll(docs);
        Set<String> stale = new TreeSet<>(docs);
        stale.removeAll(code);
        stale.removeAll(aliases(DM));
        assertTrue(missing.isEmpty(), "/dm subcommands with no row in COMMANDS.md: " + missing);
        assertTrue(stale.isEmpty(), "COMMANDS.md documents /dm subcommands that don't exist: " + stale);
    }
}
