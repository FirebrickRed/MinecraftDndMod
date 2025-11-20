package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
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

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);

                // Each YAML can contain multiple weapons
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String weaponId = entry.getKey();

                    if (entry.getValue() instanceof Map<?, ?> weaponData) {
                        DndWeapon weapon = parseWeapon(weaponId, weaponData);
                        loadedWeapons.put(Util.normalize(weaponId), weapon);
                        LOGGER.info("Loaded weapon: " + weapon.getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load weapons from " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
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
        weapon.setCost(LoaderUtils.parseCost(data.get("cost"), id));
        weapon.setDescription((String) data.get("description"));
        weapon.setIcon((String) data.get("icon"));

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
