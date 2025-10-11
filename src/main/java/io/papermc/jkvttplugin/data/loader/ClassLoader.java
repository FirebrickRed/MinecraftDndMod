package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.SpellsPreparedFormula;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class ClassLoader {
    private static final Map<String, DndClass> loadedClasses = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ClassLoader");

    public static void loadAllClasses(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No class files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                DndClass dndClass = parseClass(data);
                loadedClasses.put(dndClass.getId(), dndClass);
                LOGGER.info("Loaded class: " + dndClass.getName());
            } catch (Exception e) {
                System.err.println("Failed to load class from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static DndClass parseClass(Map<String, Object> data) {
        String name = (String) data.getOrDefault("name", "Unknown");
        DndClass.Builder builder = DndClass.builder()
                .id(normalize(name))
                .name(name)
                .hitDie((int) data.getOrDefault("hit_die", 6))
                .savingThrows(LoaderUtils.parseAbilityList(data.get("saving_throws")))

                .armorProficiencies(LoaderUtils.normalizeStringList(data.get("armor_proficiencies")))
                .weaponProficiencies(LoaderUtils.normalizeStringList(data.get("weapon_proficiencies")))
                .toolProficiencies(LoaderUtils.normalizeStringList(data.get("tool_proficiencies")))
                .languages(LoaderUtils.normalizeStringList(data.get("languages")))

                .skills(LoaderUtils.normalizeStringList(data.get("skills")))
                .startingEquipment(LoaderUtils.normalizeStringList(data.get("starting_equipment")))

                .asiLevels(LoaderUtils.castList(data.get("asi_levels"), Integer.class))
                .spellcastingAbility(Ability.fromString((String) data.get("spellcasting_ability")))
                .spellcasting(parseSpellcasting(data.get("spellcasting")))
                .featuresByLevel(LoaderUtils.parseLevelStringListMap(data.get("features_by_level")))
                .subclasses(LoaderUtils.castList(data.getOrDefault("subclasses", List.of()), String.class))
                .multiclassRequirements(LoaderUtils.castMap(data.get("multiclass_requirements"), String.class, Integer.class))
//                .classResources()

                .allowFeats((boolean) data.getOrDefault("allow_feats", true))
                .icon((String) data.get("icon"));

        DndClass dndClass = builder.build();
        var pcs = LoaderUtils.parsePlayerChoicesForClass(data.get("player_choices"));
        dndClass.setPlayerChoices(pcs);

        return dndClass;
    }

    public static DndClass getClass(String name) {
        return loadedClasses.get(name);
    }

    public static Collection<DndClass> getAllClasses() {
        return Collections.unmodifiableCollection(loadedClasses.values());
    }

    private static SpellcastingInfo parseSpellcasting(Object spellcastingData) {
        if (!(spellcastingData instanceof Map<?,?> map)) return null;

        SpellcastingInfo spellcasting = new SpellcastingInfo();

//        spellcasting.setType((String) map.get("type"));
        spellcasting.setCastingAbility((String) map.get("casting_ability"));
        spellcasting.setPreparationType((String) map.get("preparation_type"));
        spellcasting.setRitualCasting((boolean) map.get("ritual_casting"));
        spellcasting.setSpellcastingFocusType((String) map.get("spellcasting_focus_type"));
//        spellcasting.setSpellList((String) map.get("spell_list"));
        spellcasting.setSpellcastingLevel((Integer) map.get("spellcasting_level"));
        spellcasting.setCantripsKnownByLevel(LoaderUtils.castList(map.get("cantrips_known_by_level"), Integer.class));
        spellcasting.setSpellsKnownByLevel(LoaderUtils.castList(map.get("spells_known_by_level"), Integer.class));
        spellcasting.setSlotRecovery((String) map.get("slot_recovery"));

        Object formulaData = map.get("spells_prepared_formula");
        if (formulaData instanceof Map<?, ?> formulaMap) {
            SpellsPreparedFormula formula = parseSpellsPreparedFormula(formulaMap);
            spellcasting.setSpellsPreparedFormula(formula);
        }

        Object slotsData = map.get("spell_slots_by_level");
        if (slotsData instanceof Map<?, ?> slotsMap) {
            Map<Integer, List<Integer>> slotsByLevel = new HashMap<>();
            for (Map.Entry<?, ?> entry : slotsMap.entrySet()) {
                try {
                    Integer spellLevel = Integer.valueOf(entry.getKey().toString());
                    List<Integer> slots = LoaderUtils.castList(entry.getValue(), Integer.class);
                    if (slots != null) {
                        slotsByLevel.put(spellLevel, slots);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid spell level in spell_slots_by_level: " + entry.getKey());
                }
            }
            spellcasting.setSpellSlotsByLevel(slotsByLevel);
        }
        return spellcasting;
    }

    private static SpellsPreparedFormula parseSpellsPreparedFormula(Map<?, ?> formulaMap) {
        SpellsPreparedFormula formula = new SpellsPreparedFormula();

        String typeStr = (String) formulaMap.get("type");
        if (typeStr != null) {
            try {
                formula.setType(SpellsPreparedFormula.Type.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid formula type: " + typeStr + ". Defaulting to ability_plus_level");
                formula.setType(SpellsPreparedFormula.Type.ABILITY_PLUS_LEVEL);
            }
        }

        String abilityStr = (String) formulaMap.get("ability");
        if (abilityStr != null) {
            formula.setAbility(Ability.fromString(abilityStr));
        }

        String levelTypeStr = (String) formulaMap.get("level_type");
        if (levelTypeStr != null) {
            try {
                formula.setLevelType(SpellsPreparedFormula.LevelType.valueOf(levelTypeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid level type: " + levelTypeStr + ". Defaulting to 'full', other options include 'half' and 'third'");
                formula.setLevelType(SpellsPreparedFormula.LevelType.FULL);
            }
        }

        Object minimum = formulaMap.get("minimum");
        if (minimum instanceof  Integer) {
            formula.setMinimum((Integer) minimum);
        }

        Object value = formulaMap.get("value");
        if (value instanceof Integer) {
            formula.setValue((Integer) value);
        }

        Object base = formulaMap.get("base");
        if (base instanceof Integer) {
            formula.setBase((Integer) base);
        }

        Object levelMultiplier = formulaMap.get("level_multiplier");
        if (levelMultiplier instanceof Number) {
            formula.setLevelMultiplier(((Number) levelMultiplier).doubleValue());
        }

        return formula;
    }
}
