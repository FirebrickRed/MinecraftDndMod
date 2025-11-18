package io.papermc.jkvttplugin.util;

/**
 * Utility methods for command parsing and handling.
 */
public class CommandUtil {

    /**
     * Merges quoted arguments into a single string.
     *
     * Bukkit's command system splits arguments by spaces BEFORE we receive them,
     * so quoted strings like "Marcus the Brave" become separate args.
     * This method reconstructs the original quoted string.
     *
     * Examples:
     * - Input: ["spawn", "guard", "\"Marcus", "the", "Brave\"", "100"]
     * - Returns: {name: "Marcus the Brave", nextIndex: 5}
     *
     * - Input: ["spawn", "guard", "\"SingleWord\"", "100"]
     * - Returns: {name: "SingleWord", nextIndex: 3}
     *
     * - Input: ["spawn", "guard", "NoQuotes", "100"]
     * - Returns: null (no quotes found)
     *
     * @param args Original argument array from command
     * @param startIndex Index to start looking for quotes
     * @return QuotedStringResult with merged name and next index, or null if no quotes
     */
    public static QuotedStringResult parseQuotedString(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return null;
        }

        String firstArg = args[startIndex];

        // Check if this argument starts with a quote
        if (!firstArg.startsWith("\"")) {
            return null; // No quotes, return null
        }

        // Check if quote closes in the same argument: "Name"
        if (firstArg.endsWith("\"") && firstArg.length() > 1) {
            String name = firstArg.substring(1, firstArg.length() - 1);
            return new QuotedStringResult(name, startIndex + 1);
        }

        // Multi-word quoted string: "Marcus the Brave"
        StringBuilder merged = new StringBuilder();
        merged.append(firstArg.substring(1)); // Remove opening quote

        int currentIndex = startIndex + 1;
        while (currentIndex < args.length) {
            String arg = args[currentIndex];

            if (arg.endsWith("\"")) {
                // Found closing quote
                merged.append(" ").append(arg, 0, arg.length() - 1);
                return new QuotedStringResult(merged.toString(), currentIndex + 1);
            } else {
                // Still inside quoted string
                merged.append(" ").append(arg);
            }
            currentIndex++;
        }

        // Unclosed quote - return what we have
        return new QuotedStringResult(merged.toString(), currentIndex);
    }

    /**
     * Result object for parseQuotedString.
     */
    public static class QuotedStringResult {
        private final String value;
        private final int nextIndex;

        public QuotedStringResult(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }

        public String getValue() {
            return value;
        }

        public int getNextIndex() {
            return nextIndex;
        }
    }
}