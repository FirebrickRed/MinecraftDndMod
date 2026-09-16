package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellComponents;
import io.papermc.jkvttplugin.data.model.enums.SpellSchool;
import org.bukkit.Material;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpellLoader {
    private static final Map<String, DndSpell> spells = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("SpellLoader");

    public static void loadAllSpells(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No spell files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> fileData = yaml.load(reader);
                if (fileData == null) continue;

                // Spell files have a top-level "spells:" section
                Object spellsSection = fileData.get("spells");
                if (!(spellsSection instanceof Map<?, ?> spellsMap)) {
                    LOGGER.warning("No 'spells' section found in: " + file.getName());
                    continue;
                }

                for (Map.Entry<?, ?> entry : spellsMap.entrySet()) {
                    if (!(entry.getKey() instanceof String spellKey)) continue;
                    if (!(entry.getValue() instanceof Map<?, ?> spellData)) continue;

                    try {
                        DndSpell spell = parseSpell(spellKey, spellData);
                        String id = spellKey.toLowerCase();
                        spell.setId(id);
                        // Warn on a duplicate id: two files defining the same spell silently let
                        // whichever loads last win, which is how frostbite ended up an attack instead
                        // of a save. Keep the first, skip the rest, and say so.
                        if (spells.containsKey(id)) {
                            LOGGER.warning("Duplicate spell id '" + id + "' in " + file.getName()
                                    + " — keeping the earlier definition, ignoring this one.");
                            continue;
                        }
                        spells.put(id, spell);
                        LOGGER.fine("Loaded spell: " + spell.getName());

                        // A spell that declares a damage TYPE but no damage DICE is almost certainly
                        // missing its dice (the Frostbite bug → "Apply damage (1)"). A condition-only
                        // save spell (Hold Person) has neither, so it isn't flagged. See
                        // docs/authoring-spells.md.
                        if (spell.getDamageType() != null && !spell.getDamageType().isBlank()
                                && (spell.getDamage() == null || spell.getDamage().isBlank())) {
                            LOGGER.warning("Spell '" + id + "' (" + file.getName() + ") has damage_type '"
                                    + spell.getDamageType() + "' but no damage dice — add a `damage:` value,"
                                    + " or remove damage_type if it deals no damage.");
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Failed to load spell: " + spellKey + " from " + file.getName() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load spell file: " + file.getName());
                e.printStackTrace();
            }
        }

        LOGGER.info("Loaded " + spells.size() + " spells total");
    }

    @SuppressWarnings("unchecked")
    private static DndSpell parseSpell(String key, Map<?, ?> data) {
        String name = ParseUtil.asString(data.get("name"), key);
        int level = ParseUtil.asInt(data.get("level"), 0);
        SpellSchool school = SpellSchool.fromString(ParseUtil.asString(data.get("school"), "evocation"));

        // Parse classes list
        List<String> classes = ParseUtil.normalizeStringList(data.get("classes"));
        // Handle legacy comma-separated string format if normalizeStringList returns empty
        if (classes.isEmpty() && data.get("classes") instanceof String classString) {
            classes = Arrays.asList(classString.split(",\\s*"));
        }

        String castingTime = ParseUtil.asString(data.get("casting_time"), "1 action");
        String range = ParseUtil.asString(data.get("range"), "Self");
        SpellComponents components = parseComponents(data.get("components"));
        String duration = ParseUtil.asString(data.get("duration"), "Instantaneous");
        String description = ParseUtil.asString(data.get("description"), "");
        boolean concentration = ParseUtil.asBoolean(data.get("concentration"), false);
        boolean ritual = ParseUtil.asBoolean(data.get("ritual"), false);
        // Icons follow the shared convention: `material:` is the vanilla item to render; absent → a
        // level-based default (chosen in createItemStack). `custom_model:` is optional and opt-in.
        Material material = parseMaterial(ParseUtil.asString(data.get("material"), null)); // null → level default
        String higherLevels = ParseUtil.asString(data.get("higher_levels"), null);
        String attackType = ParseUtil.asString(data.get("attack_type"), null);
        String saveType = ParseUtil.asString(data.get("save_type"), null);
        String damageType = ParseUtil.asString(data.get("damage_type"), null);

        DndSpell spell = DndSpell.builder()
                .name(name)
                .level(level)
                .school(school)
                .classes(classes)
                .castingTime(castingTime)
                .range(range)
                .components(components)
                .duration(duration)
                .description(description)
                .concentration(concentration)
                .ritual(ritual)
                .material(material)
                .higherLevels(higherLevels)
                .attackType(attackType)
                .saveType(saveType)
                .damageType(damageType)
                .build();
        // Combat resolution fields (#123).
        spell.setDamage(ParseUtil.asString(data.get("damage"), null));
        // Default: cantrips deal nothing on a successful save; leveled spells deal half. Override in YAML.
        spell.setSaveEffect(ParseUtil.asString(data.get("save_effect"), spell.isCantrip() ? "none" : "half"));
        spell.setConditionOnFail(ParseUtil.asString(data.get("condition_on_fail"), null));
        spell.setCastChoice(ParseUtil.asString(data.get("cast_choice"), null));
        spell.setMarkDamage(ParseUtil.asString(data.get("mark_damage"), null));
        // Area of effect (#149).
        spell.setAoeShape(ParseUtil.asString(data.get("aoe_shape"), null));
        if (data.get("aoe_size") instanceof Number n) spell.setAoeSize(n.intValue());
        spell.setAoeTargets(ParseUtil.asString(data.get("aoe_targets"), "all"));
        // Social / roleplay spells (#151).
        spell.setSocialType(ParseUtil.asString(data.get("social_type"), null));
        if (data.get("word_limit") instanceof Number wl) spell.setWordLimit(wl.intValue());
        // Ritual-in-combat per-spell channel length (#156); 0/absent = global default.
        if (data.get("ritual_rounds") instanceof Number rr) spell.setRitualRounds(rr.intValue());
        // Healing / temporary HP (#123).
        spell.setHealing(ParseUtil.asString(data.get("healing"), null));
        spell.setTempHp(ParseUtil.asString(data.get("temp_hp"), null));
        // Optional resource-pack model overlay (only applied if the pack provides it).
        spell.setCustomModel(ParseUtil.asString(data.get("custom_model"), null));
        return spell;
    }

    private static SpellComponents parseComponents(Object componentsObj) {
        if (componentsObj instanceof String componentsStr) {
            return SpellComponents.fromString(componentsStr);
        } else if (componentsObj instanceof Map<?, ?> compMap) {
            boolean verbal = ParseUtil.asBoolean(compMap.get("verbal"), false);
            boolean somatic = ParseUtil.asBoolean(compMap.get("somatic"), false);
            boolean material = ParseUtil.asBoolean(compMap.get("material"), false);
            String materialDescription = ParseUtil.asString(compMap.get("material_description"), null);
            boolean materialConsumed = ParseUtil.asBoolean(compMap.get("material_consumed"), false);
            Integer materialCost = ParseUtil.asInt(compMap.get("material_cost"), 0);
            if (materialCost == 0) materialCost = null; // Treat 0 as null

            return new SpellComponents(verbal, somatic, material, materialDescription, materialConsumed, materialCost);
        }

        return SpellComponents.fromString("V, S");
    }

    /** Parse a vanilla Material for the spell's base item; null if absent or invalid (→ level default). */
    private static Material parseMaterial(String materialString) {
        if (materialString == null || materialString.isBlank()) return null;
        try {
            return Material.valueOf(materialString.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown spell material '" + materialString + "' — using the level-based default.");
            return null;
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

    /**
     * Clears all loaded spells. Called before reloading data.
     */
    public static void clear() {
        spells.clear();
        LOGGER.info("Cleared all loaded spells");
    }
}
