package io.papermc.jkvttplugin.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Names go through one reader. A raw argument is one word, so a command that hands {@code args[i]}
 * straight to a name finder breaks on every multi-word name ("The Kindler" arrives as {@code "The}),
 * and each command had to be playtested separately to find out. This fails the build instead.
 *
 * <p>A file that passes {@code args[i]} to a finder must read its names through {@link NameUtil}
 * ({@code collapseName}, {@code readName} or {@code takeName}) somewhere, which is what makes that
 * {@code args[i]} already a whole name. Minecraft usernames ({@code Bukkit.getPlayerExact}) can't
 * contain spaces, so they're not checked.
 */
class NameReadingTest {

    /** A name finder called with a raw argument: resolveOrError(x, args[..]), findEntity(args[..]), … */
    private static final Pattern RAW_TO_FINDER = Pattern.compile(
            "(resolveOrError\\([^,]+,\\s*args\\[|findEntity\\(args\\[|findByName\\(args\\["
                    + "|getCombatantByName\\(args\\[|findCombatantByName\\([^,]+,\\s*args\\[)");

    private static final Pattern READS_NAMES = Pattern.compile("(collapseName|readName|takeName)\\(");

    @Test
    void commandsReadNamesThroughTheSharedReader() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                if (RAW_TO_FINDER.matcher(src).find() && !READS_NAMES.matcher(src).find()) {
                    offenders.add(file.toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "These pass a raw one-word argument to a name finder; read the name with "
                + "NameUtil.collapseName / readName first, or multi-word names break: " + offenders);
    }
}
