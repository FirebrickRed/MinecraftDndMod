package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry of a class's or subclass's {@code features_by_level}, as players read it: a name and
 * what it does (#65). Display text only; mechanics live in {@code features:} (the Effect Engine).
 *
 * <p>Both YAML shapes are read, case kept:
 * <pre>
 * features_by_level:
 *   1:
 *     - name: Rage                         # a class: a map
 *       description: Enter a rage as a bonus action…
 *     - "Dark One's Blessing: When you…"   # a subclass: "Name: description" (or "Name. …" / "Name- …")
 * </pre>
 */
public record FeatureText(String name, String description) {

    public static Map<Integer, List<FeatureText>> parseByLevel(Object raw) {
        Map<Integer, List<FeatureText>> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> byLevel)) return out;
        for (Map.Entry<?, ?> e : byLevel.entrySet()) {
            int level;
            try { level = Integer.parseInt(String.valueOf(e.getKey()).trim()); } catch (NumberFormatException ex) { continue; }
            List<FeatureText> list = new ArrayList<>();
            if (e.getValue() instanceof List<?> items) {
                for (Object item : items) {
                    FeatureText f = parseOne(item);
                    if (f != null) list.add(f);
                }
            }
            out.put(level, list);
        }
        return out;
    }

    static FeatureText parseOne(Object item) {
        if (item instanceof Map<?, ?> m) {
            Object name = m.get("name");
            if (name == null || String.valueOf(name).isBlank()) return null;
            Object desc = m.get("description");
            return new FeatureText(String.valueOf(name).trim(), desc == null ? "" : String.valueOf(desc).trim());
        }
        if (item instanceof String s && !s.isBlank()) {
            String t = s.trim();
            // "Name: text", "Name. text", "Name- text": the first separator within a short head.
            int cut = -1;
            for (String sep : new String[]{": ", ". ", "- "}) {
                int i = t.indexOf(sep);
                if (i > 0 && i <= 40 && (cut < 0 || i < cut)) cut = i;
            }
            return cut > 0 ? new FeatureText(t.substring(0, cut).trim(), t.substring(cut + 2).trim()) : new FeatureText(t, "");
        }
        return null;
    }
}
