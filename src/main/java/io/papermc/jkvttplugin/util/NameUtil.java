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

    /** Like {@link #joinArgs} but skips {@code --flags}. */
    public static String joinArgsExcludingFlags(String[] args, int from) {
        if (args == null || from >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = from; i < args.length; i++) {
            if (args[i].startsWith("--")) continue;
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
