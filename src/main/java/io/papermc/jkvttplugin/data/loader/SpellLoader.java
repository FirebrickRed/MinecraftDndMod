package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellComponents;
import io.papermc.jkvttplugin.data.model.enums.SpellSchool;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpellLoader {
    private static final Map<String, DndSpell> spells = new HashMap<>();
    private static boolean loaded = false;
    private static final Logger LOGGER = Logger.getLogger("SpellLoader");

    public static void loadAllSpells(File folder) {
        if (loaded) return;

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No spell files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection spellsSection = config.getConfigurationSection("spells");

                if (spellsSection == null) {
                    LOGGER.warning("No 'spells' section found in: " + file.getName());
                    continue;
                }

                for (String spellKey : spellsSection.getKeys(false)) {
                    ConfigurationSection spellSection = spellsSection.getConfigurationSection(spellKey);
                    if (spellSection == null) continue;

                    try {
                        DndSpell spell = parseSpell(spellKey, spellSection);
                        spells.put(spellKey.toLowerCase(), spell);
                    } catch (Exception e) {
                        LOGGER.severe("Failed to load spell: " + spellKey + " from " + file.getName());
                    }
                }

//                Map<String, Object>  fileMap = yaml.load(reader);
//                if (fileMap == null) continue;
//                for (Map.Entry<String, Object> entry : fileMap.entrySet()) {
//                    String spellKey = entry.getKey();
//                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
//                    DndSpell spell = parseSpell(spellKey, data);
//                    loadedSpells.put(normalize(spell.getName(), spell));
//                    LOGGER.info("Loaded spell: " + spell.getName());
//                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load spell file: " + file.getName());
                e.printStackTrace();
            }
        }

        loaded = true;
        LOGGER.info("Loaded spells");
    }

    private static DndSpell parseSpell(String key, ConfigurationSection section) {
        SpellComponents components = parseComponents(section);

        String range = section.getString("range", "Self");

        String castingTime = section.getString("casting_time", "1 action");

        List<String> classes = section.getStringList("classes");
        if (classes.isEmpty()) {
            String singleClass = section.getString("classes");
            if (singleClass != null) {
                classes = Arrays.asList(singleClass.split(",\\s*"));
            }
        }

        return DndSpell.builder()
                .name(section.getString("name", key))
                .level(section.getInt("level", 0))
                .school(SpellSchool.fromString(section.getString("school")))
                .classes(classes)
                .castingTime(castingTime)
                .range(range)
                .components(components)
                .duration(section.getString("duration", "Instantaneous"))
                .description(section.getString("description", ""))
                .concentration(section.getBoolean("concentration", false))
                .ritual(section.getBoolean("ritual", false))
                .icon(parseIcon(section.getString("icon", "ENCHANGED_BOOK")))
                .higherLevels(section.getString("high_levels", null))
                .attackType(section.getString("attack_type", null))
                .saveType(section.getString("save_type", null))
                .damageType(section.getString("damage_type", null))
                .build();
    }

    private static SpellComponents parseComponents(ConfigurationSection section) {
        Object componentsObj = section.get("components");

        if (componentsObj instanceof String) {
            return SpellComponents.fromString((String) componentsObj);
        } else if (componentsObj instanceof Map) {
            Map<String, Object> compMap = (Map<String, Object>) componentsObj;

            boolean verbal = (Boolean) compMap.getOrDefault("verbal", false);
            boolean somatic = (Boolean) compMap.getOrDefault("somatic", false);
            boolean material = (Boolean) compMap.getOrDefault("material", false);
            String materialDescription = (String) compMap.get("material_description");
            boolean materialcnsumed = (Boolean) compMap.getOrDefault("material_consumed", false);
            Integer materialCost = compMap.containsKey("material_cost") ? ((Number) compMap.get("material_cost")).intValue() : null;

            return new SpellComponents(verbal, somatic, material, materialDescription, materialcnsumed, materialCost);
        }

        return SpellComponents.fromString("V, S");
    }

    private static Material parseIcon(String iconString) {
        try {
            return Material.valueOf(iconString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.ENCHANTED_BOOK;
        }
    }

    public static DndSpell getSpell(String spellKey) {
        return spells.get(spellKey.toLowerCase());
    }

    public static Collection<DndSpell> getAllSpells() {
        return spells.values();
    }

    public static List<DndSpell> getSpellsForClass(String className) {
        return spells.values().stream()
                .filter(spell -> spell.isAvailableToClass(className))
                .toList();
    }

    public static List<DndSpell> getCantripsForClass(String className) {
        return getSpellsForClass(className).stream()
                .filter(DndSpell::isCantrip)
                .toList();
    }

    public static List<DndSpell> getSpellsByLevel(String className, int level) {
        return getSpellsForClass(className).stream()
                .filter(spell -> spell.getLevel() == level)
                .toList();
    }

    public static Map<Integer, List<DndSpell>> getSpellsByLevelForClass(String className) {
        return getSpellsForClass(className).stream()
                .collect(Collectors.groupingBy(DndSpell::getLevel));
    }
}
