package io.papermc.jkvttplugin.util;

import java.util.*;
import java.util.logging.Logger;

/**
 * In-memory tag → item-id mapping used by equipment choices (#54). Weapon tags
 * ({@code simple_weapon}, {@code martial_weapon}, {@code simple_melee_weapon}, …) are DERIVED from
 * the loaded weapons' {@code category}/{@code type} at load time (see WeaponLoader), so homebrew
 * weapons auto-join their tags with no hardcoding. Non-derivable groupings (e.g. {@code gaming_set})
 * live in {@link #defaultTags()} until an item-tag YAML replaces them.
 */
public final class TagRegistry {
    private static final Logger LOG = Logger.getLogger("TagRegistry");
    private static final Map<String, List<String>> TAGS = new HashMap<>(defaultTags());

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

    /** True if the (normalized) name is a known tag. Used to classify equipment tokens without a
     *  warning (a plain item id is simply "not a tag"). */
    public static boolean isTag(String name) {
        return name != null && TAGS.containsKey(Util.normalize(name));
    }

    /**
     * Merge derived tags in (normalized keys, replacing any existing list for the same tag). Used by
     * WeaponLoader to install the weapon tags it derives from item data on every load/reload.
     */
    public static void merge(Map<String, List<String>> more) {
        if (more == null) return;
        for (var e : more.entrySet()) {
            TAGS.put(Util.normalize(e.getKey()), List.copyOf(e.getValue()));
        }
    }

    // ----- internals -----
    private static Map<String, List<String>> defaultTags() {
        Map<String, List<String>> m = new HashMap<>();
        // Non-weapon groupings that can't be derived from item fields yet.
        m.put(Util.normalize("gaming_set"), List.of("dice_set", "playing_card_set"));
        return m;
    }
}
