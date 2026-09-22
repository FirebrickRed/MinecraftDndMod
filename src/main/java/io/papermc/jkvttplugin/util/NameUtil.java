package io.papermc.jkvttplugin.util;

import java.util.Collection;
import java.util.function.Function;

/**
 * Shared name-resolution helpers (Issue #140). Joining tokenized args into a name, stripping
 * surrounding quotes, and a consistent match cascade all live here so every command/tool that
 * resolves a player, entity, combatant, or character by name applies the same rules — spaces,
 * quotes, case-insensitivity, partial match, and ambiguity — instead of re-implementing them.
 */
public final class NameUtil {

    private NameUtil() {}

    /** Remove a single pair of surrounding double or single quotes, if present. */
    public static String stripQuotes(String input) {
        if (input == null || input.length() < 2) return input;
        char first = input.charAt(0), last = input.charAt(input.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    /**
     * A name taken off the front of the arguments, where the rest of the command continues, and
     * whether it was quoted — some commands treat "a quoted name" as the DM being explicit.
     */
    public record TakenName(String value, int nextIndex, boolean quoted) {}

    /**
     * Take ONE name at {@code args[from]}, for commands that have arguments after the name
     * ({@code /dm entity spawn <id> "Marcus the Brave" 100}). A quoted span is read to its closing
     * quote; anything else is the single word. Null when there's nothing there.
     *
     * <p>Bukkit splits arguments on spaces before we see them, so a multi-word name only survives in
     * quotes. Use this wherever a name is followed by more arguments; use {@link #joinArgs} when the
     * name runs to the end of the command.
     */
    public static TakenName takeName(String[] args, int from) {
        if (args == null || from < 0 || from >= args.length) return null;
        String first = args[from];
        if (!first.startsWith("\"")) return new TakenName(first, from + 1, false);
        if (first.length() > 1 && first.endsWith("\"")) {
            return new TakenName(first.substring(1, first.length() - 1), from + 1, true);
        }
        StringBuilder merged = new StringBuilder(first.substring(1));
        for (int i = from + 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.endsWith("\"")) {
                merged.append(" ").append(arg, 0, arg.length() - 1);
                return new TakenName(merged.toString(), i + 1, true);
            }
            merged.append(" ").append(arg);
        }
        return new TakenName(merged.toString(), args.length, true); // unclosed quote: take what is there
    }

    /** Join {@code args[from..]} into one space-separated name, stripping surrounding quotes. */
    public static String joinArgs(String[] args, int from) {
        if (args == null || from >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(" ");
            sb.append(args[i]);
        }
        return stripQuotes(sb.toString());
    }

    /**
     * Joins the leading positional args (a name, possibly with spaces) and STOPS at the first
     * {@code --flag}. Everything from the first flag on is flag-land — the flag and any value it
     * carries (e.g. {@code --roll 1d20}) — so a value like {@code 1d20} never leaks into the name.
     * All callers put the name before any flags, matching the command usage convention.
     */
    public static String joinArgsExcludingFlags(String[] args, int from) {
        if (args == null || from >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = from; i < args.length; i++) {
            if (args[i].startsWith("--")) break; // reached the flags; the name is complete
            // Bare roll keywords (autoRoll / manualRoll / total) also end the name (#183).
            String lower = args[i].toLowerCase();
            if (lower.equals("autoroll") || lower.equals("manualroll") || lower.equals("total")) break;
            if (!first) sb.append(" ");
            sb.append(args[i]);
            first = false;
        }
        return stripQuotes(sb.toString());
    }

    /**
     * Match one item from a collection by name using a consistent cascade: exact (case-insensitive)
     * on the primary name, then with '#' spacing normalized (so "Wolf 2" == "Wolf #2"), then exact on
     * the secondary/base name, then startsWith on the primary, then a UNIQUE contains. Returns null
     * if nothing matches — or if a contains-match is ambiguous (more than one item contains the term).
     */
    public static <T> T matchByName(Collection<T> items, String query,
                                    Function<T, String> primaryName, Function<T, String> secondaryName) {
        if (items == null || query == null) return null;
        String q = stripQuotes(query.trim());
        String ql = q.toLowerCase();
        if (ql.isEmpty()) return null;

        // 1. exact on primary
        for (T it : items) if (q.equalsIgnoreCase(primaryName.apply(it))) return it;

        // 2. '#'-normalized on primary
        String qn = ql.replaceAll("\\s*#\\s*", "#");
        for (T it : items) {
            String p = primaryName.apply(it);
            if (p != null && p.toLowerCase().replaceAll("\\s*#\\s*", "#").equals(qn)) return it;
        }

        // 3. exact on the secondary/base name
        if (secondaryName != null) {
            for (T it : items) if (q.equalsIgnoreCase(secondaryName.apply(it))) return it;
        }

        // 4. startsWith on primary
        for (T it : items) {
            String p = primaryName.apply(it);
            if (p != null && p.toLowerCase().startsWith(ql)) return it;
        }

        // 5. unique contains on primary
        T contains = null;
        for (T it : items) {
            String p = primaryName.apply(it);
            if (p != null && p.toLowerCase().contains(ql)) {
                if (contains != null) return null; // ambiguous
                contains = it;
            }
        }
        return contains;
    }

    /** Match convenience for items that have only one name. */
    public static <T> T matchByName(Collection<T> items, String query, Function<T, String> name) {
        return matchByName(items, query, name, null);
    }
}
