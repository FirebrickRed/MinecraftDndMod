package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.parser.ShopParser;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

public class WeaponLoader {
    private static final Map<String, DndWeapon> loadedWeapons = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("WeaponLoader");

    public static void loadAllWeapons(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No weapon files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        // Pass 1: every entry's raw YAML, so a magic weapon can name a base defined in another file.
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                if (data == null) continue;
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> weaponData) {
                        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) weaponData;
                        raw.put(Util.normalize(entry.getKey()), m);
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load weapons from " + file.getName() + ": " + e.getMessage());
            }
        }

        // Pass 2: resolve `base:` and parse. One bad entry doesn't take the others down.
        for (String id : raw.keySet()) {
            try {
                Map<String, Object> merged = resolveBase(id, raw, new LinkedHashSet<>());
                if (merged == null) continue;
                DndWeapon weapon = parseWeapon(id, merged);
                loadedWeapons.put(id, weapon);
                LOGGER.fine("Loaded weapon: " + weapon.getName());
            } catch (Exception e) {
                LOGGER.severe("Failed to load weapon '" + id + "': " + e.getMessage());
            }
        }
        LOGGER.info("Loaded " + loadedWeapons.size() + " weapons.");
        installWeaponTags();
    }

    /** Keys a magic weapon never inherits from its base: its own identity and price, not a longsword's. */
    private static final Set<String> NOT_INHERITED = Set.of("name", "cost", "description", "base", "rarity", "magic");

    /**
     * A weapon's YAML with its {@code base:} chain merged in: the base's stats first, this entry's
     * keys on top. A magic weapon authors only what differs ("Longsword +2" = base: longsword + a
     * magic block). A missing base or a cycle is logged and the entry skipped.
     */
    private static Map<String, Object> resolveBase(String id, Map<String, Map<String, Object>> raw, Set<String> visiting) {
        Map<String, Object> own = raw.get(id);
        if (own == null) return null;
        if (!(own.get("base") instanceof String baseRef)) return own;
        String baseId = Util.normalize(baseRef);
        if (!visiting.add(id)) {
            LOGGER.warning("Weapon '" + id + "' has a base: cycle (" + String.join(" → ", visiting) + ") — skipped.");
            return null;
        }
        Map<String, Object> base = resolveBase(baseId, raw, visiting);
        if (base == null) {
            LOGGER.warning("Weapon '" + id + "' has base: " + baseRef + ", which isn't a weapon — skipped.");
            return null;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : base.entrySet()) {
            if (!NOT_INHERITED.contains(e.getKey())) merged.put(e.getKey(), e.getValue());
        }
        merged.putAll(own);
        merged.put("__baseName", base.get("name")); // for proficiency matching and the default name
        return merged;
    }

    /**
     * Derive weapon tags from the loaded weapons and register them (#54). A weapon of category
     * {@code simple}/{@code martial} and type {@code melee}/{@code ranged} joins {@code <cat>_weapon}
     * and {@code <cat>_<type>_weapon} (e.g. martial + melee → martial_weapon and martial_melee_weapon).
     * Homebrew weapons auto-join their tags with no hardcoding. Runs on every load/reload.
     */
    private static void installWeaponTags() {
        Map<String, List<String>> tags = new java.util.HashMap<>();
        for (Map.Entry<String, DndWeapon> entry : loadedWeapons.entrySet()) {
            String id = entry.getKey(); // already normalized (the lookup key used everywhere)
            DndWeapon w = entry.getValue();
            // Magic weapons stay out of the category tags, or every "choose any martial weapon"
            // starting-kit pick would offer a Longsword +3.
            if (w.isMagic()) continue;
            String cat = w.getCategory() == null ? "" : Util.normalize(w.getCategory());
            String type = w.getType() == null ? "" : Util.normalize(w.getType());
            if (cat.isBlank()) continue;
            tags.computeIfAbsent(cat + "_weapon", k -> new java.util.ArrayList<>()).add(id);
            if (!type.isBlank()) {
                tags.computeIfAbsent(cat + "_" + type + "_weapon", k -> new java.util.ArrayList<>()).add(id);
            }
        }
        io.papermc.jkvttplugin.util.TagRegistry.merge(tags);
    }

    @SuppressWarnings("unchecked")
    private static DndWeapon parseWeapon(String id, Map<?, ?> data) {
        DndWeapon weapon = new DndWeapon();

        weapon.setId(id);
        // ToDo: getOrDefault weirdness here too
//        weapon.setName((String) data.getOrDefault("name", id.replace('_', ' ')));
//        weapon.setCategory((String) data.getOrDefault("category", "simple"));
//        weapon.setType((String) data.getOrDefault("type", "melee"));
//        weapon.setDamage((String) data.getOrDefault("damage", "1d4"));
        weapon.setName((String) data.get("name"));
        weapon.setCategory((String) data.get("category"));
        weapon.setType((String) data.get("type"));
        weapon.setDamage((String) data.get("damage"));
        weapon.setDamageType((String) data.get("damage_type"));
        weapon.setWeight((String) data.get("weight"));
        weapon.setCost(ShopParser.parseCost(data.get("cost"), id));
        weapon.setDescription((String) data.get("description"));
        weapon.setMaterial((String) data.get("material"));         // vanilla Minecraft item
        weapon.setAmmunition((String) data.get("ammunition")); // item id this weapon fires (#128)
        if (data.get("reach") instanceof Number reach) weapon.setReach(reach.intValue()); // melee reach in feet
        if (data.get("recovery_chance") instanceof Number rc) weapon.setRecoveryChance(rc.intValue()); // #191
        weapon.setCustomModel((String) data.get("custom_model"));  // optional resource-pack model

        // Magic (#188): base weapon, rarity, and the magic block's bonuses.
        if (data.get("base") instanceof String baseRef) {
            weapon.setBaseId(Util.normalize(baseRef));
            if (data.get("__baseName") instanceof String baseName) weapon.setBaseName(baseName);
        }
        if (data.get("rarity") instanceof String rarity) weapon.setRarity(Util.normalize(rarity));
        if (data.get("magic") instanceof Map<?, ?> magic) {
            weapon.setAttackBonus(io.papermc.jkvttplugin.data.loader.util.ParseUtil.asInt(magic.get("attack_bonus"), 0));
            weapon.setDamageBonus(io.papermc.jkvttplugin.data.loader.util.ParseUtil.asInt(magic.get("damage_bonus"), 0));
            weapon.setCritBonusDamage(io.papermc.jkvttplugin.data.loader.util.ParseUtil.asInt(magic.get("crit_bonus_damage"), 0));
            // "bonus: 2" is shorthand for the usual Weapon +2 (same to attack and damage).
            int both = io.papermc.jkvttplugin.data.loader.util.ParseUtil.asInt(magic.get("bonus"), 0);
            if (both != 0) {
                if (weapon.getAttackBonus() == 0) weapon.setAttackBonus(both);
                if (weapon.getDamageBonus() == 0) weapon.setDamageBonus(both);
            }
        }
        // No name on a +N weapon → "Longsword +2", from the base's name.
        if (weapon.getName() == null && data.get("__baseName") instanceof String baseName) {
            int n = weapon.getAttackBonus();
            weapon.setName(n != 0 && n == weapon.getDamageBonus() ? baseName + " +" + n : baseName);
        }

        // Parse properties
        Object propertiesObj = data.get("properties");
        if (propertiesObj instanceof List<?> propsList) {
            Set<String> properties = new HashSet<>();
            for (Object prop : propsList) {
                if (prop instanceof String) {
                    properties.add(((String) prop).toLowerCase());
                }
            }
            weapon.setProperties(properties);
        }

        // Parse range for ranged weapons
        Object rangeObj = data.get("range");
        if (rangeObj instanceof Map<?, ?> rangeMap) {
            Object normal = rangeMap.get("normal");
            Object longRange = rangeMap.get("long");

            if (normal instanceof Integer) {
                weapon.setNormalRange((Integer) normal);
            }
            if (longRange instanceof Integer) {
                weapon.setLongRange((Integer) longRange);
            }
        } else if (rangeObj instanceof String rangeStr) {
            // Handle "80/320" format
            String[] parts = rangeStr.split("/");
            try {
                weapon.setNormalRange(Integer.parseInt(parts[0].trim()));
                if (parts.length > 1) {
                    weapon.setLongRange(Integer.parseInt(parts[1].trim()));
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid range format for weapon " + id + ": " + rangeStr);
            }
        } else if (rangeObj instanceof Integer) {
            weapon.setNormalRange((Integer) rangeObj);
        }

        return weapon;
    }

    public static DndWeapon getWeapon(String id) {
        if (id == null) return null;
        return loadedWeapons.get(Util.normalize(id));
    }

    public static Collection<DndWeapon> getAllWeapons() {
        return Collections.unmodifiableCollection(loadedWeapons.values());
    }

    public static List<DndWeapon> getWeaponsByCategory(String category) {
        return loadedWeapons.values().stream()
                .filter(weapon -> category.equalsIgnoreCase(weapon.getCategory()))
                .toList();
    }

    public static List<DndWeapon> getSimpleWeapons() {
        return getWeaponsByCategory("simple");
    }

    public static List<DndWeapon> getMartialWeapons() {
        return getWeaponsByCategory("martial");
    }

    /**
     * Clears all loaded weapons. Called before reloading data.
     */
    public static void clear() {
        loadedWeapons.clear();
        LOGGER.info("Cleared all loaded weapons");
    }
}
