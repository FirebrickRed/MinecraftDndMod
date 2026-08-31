package io.papermc.jkvttplugin.data.loader.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic YAML-value primitives shared by the focused parsers in
 * {@code io.papermc.jkvttplugin.data.loader.parser}. Kept deliberately small and
 * dependency-free: no D&D model types, just Object -> primitive/collection coercion.
 *
 * <p>Split out of the old grab-bag {@code LoaderUtils} (issue #12). Domain parsing now
 * lives in the {@code parser} package; this class holds only the cross-cutting helpers.
 */
public final class ParseUtil {

    private ParseUtil() {}

    public static String asString(Object o, String def) {
        return (o instanceof String s) ? s : def;
    }

    public static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static boolean asBoolean(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    public static <T> List<T> castList(Object obj, Class<T> clazz) {
        if (obj instanceof List<?> list) {
            List<T> result = new ArrayList<>();
            for (Object item : list) {
                if (clazz.isInstance(item)) {
                    result.add(clazz.cast(item));
                }
            }
            return result;
        }
        return List.of();
    }

    public static <K, V> Map<K, V> castMap(Object obj, Class<K> keyClass, Class<V> valueClass) {
        if (obj instanceof Map<?, ?> map) {
            Map<K, V> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (keyClass.isInstance(entry.getKey()) && valueClass.isInstance(entry.getValue())) {
                    result.put(keyClass.cast(entry.getKey()), valueClass.cast(entry.getValue()));
                }
            }
            return result;
        }
        return Map.of();
    }

    /**
     * Lower-cases and trims each string in a YAML list, dropping blanks.
     */
    public static List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> rawList)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String str && !str.isBlank()) {
                result.add(str.trim().toLowerCase());
            }
        }
        return result;
    }

    /**
     * Trims each string in a YAML list, dropping blanks (preserves case).
     * Used for damage resistances, skill proficiencies, etc.
     */
    public static List<String> parseStringList(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof String str && !str.isBlank()) {
                result.add(str.trim());
            }
        }
        return result;
    }

    /**
     * Trims each string in a YAML list, dropping blanks (preserves case).
     * Historically distinct from {@link #parseStringList}; kept for callers that read "traits".
     */
    public static List<String> parseTraits(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<String> traits = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof String str && !str.isBlank()) {
                traits.add(str.trim());
            }
        }
        return traits;
    }

    /**
     * Parses a { level -> [strings] } YAML map (e.g. features_by_level), lower-casing values.
     */
    public static Map<Integer, List<String>> parseLevelStringListMap(Object raw) {
        Map<Integer, List<String>> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> rawMap)) return result;

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            try {
                int level = Integer.parseInt(entry.getKey().toString().trim());
                List<String> list = normalizeStringList(entry.getValue());
                result.put(level, list);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
