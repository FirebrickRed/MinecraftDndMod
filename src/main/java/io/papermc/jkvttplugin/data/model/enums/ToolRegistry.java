package io.papermc.jkvttplugin.data.model.enums;

import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.util.Util;

import java.util.*;

/**
 * Every tool a character can be proficient with, keyed by one canonical id.
 * <p>
 * <b>A tool proficiency is an item id.</b> Tools are real gear in 5e, so the registry is built from
 * items: anything in {@code DMContent/Items} tagged with a tool category ({@code artisan_tool},
 * {@code musical_instrument}, {@code gaming_set}, or {@code tool} for the other kits) registers
 * itself on load. Proficiency in "Thieves' Tools" and the Thieves' Tools item are the same id, and
 * a homebrew instrument becomes choosable just by giving its item the tag — no code.
 * <p>
 * Vehicles are the one exception: you're proficient with "Vehicles (Water)", but a ship isn't an
 * item you carry, so those few are built in here.
 * <p>
 * YAML may spell a tool any way a person would ({@code "Navigator's Tools"},
 * {@code navigators_tools}, {@code vehicles(land)}, {@code "Vehicles (Land)"}); {@link #idOf}
 * folds them all to the same id at load, so nothing downstream compares two spellings.
 */
public final class ToolRegistry {

    public enum Category {
        ARTISAN_TOOL("artisan_tool"),
        MUSICAL_INSTRUMENT("musical_instrument"),
        GAMING_SET("gaming_set"),
        VEHICLE("vehicle"),
        OTHER("tool");

        /** The item tag (and choice tag) that means this category. */
        public final String tag;

        Category(String tag) { this.tag = tag; }

        /** The category an item tag names, accepting the plural/possessive spellings; null if none. */
        public static Category fromTag(String raw) {
            if (raw == null) return null;
            return switch (idOf(raw)) {
                case "artisan_tool", "artisan_tools", "artisans_tool", "artisans_tools" -> ARTISAN_TOOL;
                case "musical_instrument", "musical_instruments", "instrument", "instruments" -> MUSICAL_INSTRUMENT;
                case "gaming_set", "gaming_sets" -> GAMING_SET;
                case "vehicle", "vehicles" -> VEHICLE;
                case "tool", "tools" -> OTHER;
                default -> null;
            };
        }
    }

    /** {@code checkAbility}: the ability a check with it defaults to (item `check_ability:`), or null. */
    public record Tool(String id, String name, Category category, Ability checkAbility) {}

    /** Vehicles aren't items, so they're the only tools defined here. */
    private static final List<Tool> VEHICLES = List.of(
            new Tool("vehicles_land", "Vehicles (Land)", Category.VEHICLE, null),
            new Tool("vehicles_water", "Vehicles (Water)", Category.VEHICLE, null),
            new Tool("vehicles_air", "Vehicles (Air)", Category.VEHICLE, null),
            new Tool("vehicles_space", "Vehicles (Space)", Category.VEHICLE, null)
    );

    private static final Map<String, Tool> tools = new LinkedHashMap<>();

    static { reset(); }

    private ToolRegistry() {}

    /**
     * The canonical id for any spelling of a tool: lowercase, apostrophes dropped, everything else
     * that isn't a letter or digit becomes one underscore. {@code "Navigator's Tools"} →
     * {@code navigators_tools}, {@code "vehicles(land)"} → {@code vehicles_land},
     * {@code "Three-Dragon Ante Set"} → {@code three_dragon_ante_set}.
     */
    public static String idOf(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
        s = s.replaceAll("[^a-z0-9]+", "_");
        return s.replaceAll("^_+|_+$", "");
    }

    /** {@link #idOf} over a list, dropping blanks and duplicates, keeping order. */
    public static List<String> idsOf(Collection<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            String id = idOf(r);
            if (!id.isEmpty()) out.add(id);
        }
        return List.copyOf(out);
    }

    /** Drops everything but the built-in vehicles. Called before a reload re-registers the items. */
    public static void reset() {
        tools.clear();
        for (Tool v : VEHICLES) tools.put(v.id(), v);
    }

    /**
     * Registers every item that carries a tool-category tag. Runs after {@code ItemLoader} on every
     * load/reload, before races/classes/backgrounds parse their tool choices.
     */
    public static void registerItems(Collection<DndItem> items) {
        for (DndItem item : items) {
            Category category = categoryOf(item.getTags());
            if (category == null) continue;
            String id = idOf(item.getId());
            String name = (item.getName() == null || item.getName().isBlank()) ? Util.prettify(id) : item.getName();
            tools.put(id, new Tool(id, name, category, item.getCheckAbility()));
        }
    }

    private static Category categoryOf(List<String> tags) {
        if (tags == null) return null;
        // A specific category beats the generic `tool` tag if an item lists both.
        Category found = null;
        for (String tag : tags) {
            Category c = Category.fromTag(tag);
            if (c == null) continue;
            if (c != Category.OTHER) return c;
            found = c;
        }
        return found;
    }

    public static boolean isRegistered(String raw) {
        return tools.containsKey(idOf(raw));
    }

    public static Tool get(String raw) {
        return tools.get(idOf(raw));
    }

    /** The tool's display name ("Navigator's Tools"); falls back to a prettified id if unknown. */
    public static String displayName(String raw) {
        Tool t = get(raw);
        return t != null ? t.name() : Util.prettify(idOf(raw));
    }

    /** Every registered tool id. */
    public static List<String> getAllTools() {
        return new ArrayList<>(tools.keySet());
    }

    /** Ids of every registered tool in a category. */
    public static List<String> getByCategory(Category category) {
        List<String> out = new ArrayList<>();
        for (Tool t : tools.values()) if (t.category() == category) out.add(t.id());
        return out;
    }

    /**
     * Expands a category tag ({@code artisan_tool}, {@code musical_instrument}, {@code gaming_set},
     * {@code vehicle}, {@code tool}) into its tool ids, or null if the string isn't a category.
     */
    public static List<String> expandTag(String tag) {
        Category c = Category.fromTag(tag);
        return c == null ? null : getByCategory(c);
    }

    public static boolean isTag(String str) {
        return Category.fromTag(str) != null;
    }
}
