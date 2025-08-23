package io.papermc.jkvttplugin.util;

import java.util.*;
import java.util.logging.Logger;

// ToDo: look over this to make sure if it's needed or not
/** Temporary, in-memory tag → items mapping. Swap to ItemRegistry later. */
public final class TagRegistry {
    private static final Logger LOG = Logger.getLogger("TagRegistry");
    private static Map<String, List<String>> TAGS = defaultTags();

    private TagRegistry() {}

    public static List<String> itemsFor(String tag) {
        if (tag == null) return List.of();
        String key = Util.normalize(tag);
        List<String> list = TAGS.get(key);
        if (list == null) {
            LOG.warning("Unknown tag: " + key + " (add it to TagRegistry or ItemRegistry YAML)");
            return List.of();
        }
        return list;
    }

    /** Call this once you have an ItemRegistry to replace the internal map. */
    public static void install(Map<String, List<String>> newTags) {
        TAGS = newTags != null ? copyUnmodifiable(newTags) : defaultTags();
    }

    // ----- internals -----
    private static Map<String, List<String>> defaultTags() {
        Map<String, List<String>> m = new HashMap<>();
        m.put("simple_weapon", List.of(
                "club","dagger","greatclub","handaxe","javelin","light_hammer","mace",
                "quarterstaff","sickle","spear","light_crossbow","dart","shortbow","sling"
        ));
        m.put("martial_melee_weapon", List.of(
                "battleaxe","flail","glaive","greataxe","greatsword","halberd","lance",
                "longsword","maul","morningstar","pike","rapier","scimitar","shortsword",
                "trident","war_pick","warhammer","whip"
        ));
        return copyUnmodifiable(m);
    }

    private static Map<String, List<String>> copyUnmodifiable(Map<String, List<String>> src) {
        Map<String, List<String>> out = new HashMap<>();
        for (var e : src.entrySet()) {
            out.put(Util.normalize(e.getKey()), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }
}
