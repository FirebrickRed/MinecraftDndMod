package io.papermc.jkvttplugin.util;

import java.text.Normalizer;

public final class NameNormalizer {
    private NameNormalizer() {}

    public static String toSnakeCase(String raw) {
        if (raw == null) return null;

        return Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{Alnum}\\s-]", "")
                .trim()
                .toLowerCase()
                .replaceAll("[\\s-]+", "_");
    }

    public static String toDisplayName(String snake) {
        if (snake == null) return null;
        String[] parts = snake.split("_");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return b.toString().trim();
    }
}
