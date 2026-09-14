package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.DndSubClass;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses subrace and subclass definitions (and the {@code size} field) from YAML, delegating
 * to the focused parsers ({@link AbilityParser}, {@link LanguageParser}, {@link ChoiceParser},
 * {@link InnateSpellParser}). Split out of {@code LoaderUtils} (issue #12).
 */
public final class RaceClassParser {

    private RaceClassParser() {}

    /** A resolved size, or a "choose your size" player-choice when the YAML defers it. */
    public static class SizeParseResult {
        public final Size size;
        public final PlayersChoice<String> sizeChoice;
        public SizeParseResult(Size size, PlayersChoice<String> sizeChoice) {
            this.size = size;
            this.sizeChoice = sizeChoice;
        }
    }

    @SuppressWarnings("unchecked")
    public static SizeParseResult parseSize(Object sizeObj) {
        if (sizeObj instanceof String s) {
            return new SizeParseResult(Size.fromString(s), null);
        } else if (sizeObj instanceof Map<?, ?> sizeMap && sizeMap.containsKey("players_choice")) {
            PlayersChoice<String> sizeChoice = PlayersChoice.fromMap((Map<String, Object>) sizeMap.get("players_choice"), String.class, PlayersChoice.ChoiceType.CUSTOM);
            return new SizeParseResult(null, sizeChoice);
        }
        return new SizeParseResult(Size.MEDIUM, null); // Default to medium if no valid size found
    }

    public static Map<String, DndSubRace> parseSubraces(Object rawData) {
        Map<String, DndSubRace> result = new HashMap<>();

        if (rawData instanceof Map<?, ?> subraceMap) {
            for (Map.Entry<?, ?> entry : subraceMap.entrySet()) {
                if (entry.getKey() instanceof String id &&
                        entry.getValue() instanceof Map<?, ?> rawSubraceData) {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> subraceData = (Map<String, Object>) rawSubraceData;

                    DndSubRace subrace = parseSubRace(id, subraceData);
                    result.put(id, subrace);
                }
            }
        }

        return result;
    }

    public static DndSubRace parseSubRace(String id, Map<String, Object> data) {
        // Ability scores (fixed and choice-based)
        AbilityParser.AbilityScoreParseResult abilityScores = AbilityParser.parseAbilityScores(data.get("ability_scores"));

        // Languages
        LanguageParser.LanguageParseResults langResult = LanguageParser.parseLanguagesAndChoices(data.get("languages"));

        return DndSubRace.builder()
                .id(id)
                .name((String) data.getOrDefault("name", id))
                .description((String) data.getOrDefault("description", ""))
                .fixedAbilityScores(abilityScores.fixedBonuses)
                .abilityScoreChoice(abilityScores.choiceBonuses)
                .traits(ParseUtil.parseTraits(data.get("traits")))
                .languages(langResult.languages)
                .playerChoices(ChoiceParser.parsePlayerChoices(data.get("player_choices")))
                .icon((String) data.getOrDefault("custom_model", "")) // resource-pack model name
                // Parse mechanical trait fields (Issue #51)
                .speed((int) data.getOrDefault("speed", 0))
                .swimmingSpeed((int) data.getOrDefault("swimming_speed", 0))
                .flyingSpeed((int) data.getOrDefault("flying_speed", 0))
                .climbingSpeed((int) data.getOrDefault("climbing_speed", 0))
                .burrowingSpeed((int) data.getOrDefault("burrowing_speed", 0))
                .darkvision((Integer) data.get("darkvision"))
                .damageResistances(ParseUtil.parseStringList(data.get("damage_resistances")))
                .skillProficiencies(ParseUtil.parseStringList(data.get("skill_proficiencies")))
                .weaponProficiencies(ParseUtil.parseStringList(data.get("weapon_proficiencies")))
                .armorProficiencies(ParseUtil.parseStringList(data.get("armor_proficiencies")))
                .innateSpells(InnateSpellParser.parseInnateSpells(data.get("innate_spells")))
                .build();
    }

    public static Map<String, DndSubClass> parseSubclasses(Object rawData, String className) {
        Map<String, DndSubClass> result = new HashMap<>();

        if (rawData instanceof Map<?, ?> subclassMap) {
            for (Map.Entry<?, ?> entry : subclassMap.entrySet()) {
                if (entry.getKey() instanceof String id &&
                        entry.getValue() instanceof Map<?, ?> rawSubclassData) {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> subclassData = (Map<String, Object>) rawSubclassData;

                    DndSubClass subclass = parseSubClass(id, subclassData, className);
                    result.put(id, subclass);
                }
            }
        }

        return result;
    }

    public static DndSubClass parseSubClass(String id, Map<String, Object> data, String className) {
        DndSubClass subclass = new DndSubClass();
        subclass.setId(id);
        subclass.setName((String) data.getOrDefault("name", id));
        subclass.setParentClass(className);
        subclass.setDescription((String) data.getOrDefault("description", ""));
        subclass.setIcon((String) data.get("custom_model")); // resource-pack model name

        // Parse features by level
        subclass.setFeaturesByLevel(ParseUtil.parseLevelStringListMap(data.get("features_by_level")));

        // Parse bonus spells (domain spells, expanded spell list, etc.) with validation
        List<String> bonusSpells = ParseUtil.normalizeStringList(data.get("bonus_spells"));
        validateSpells(bonusSpells, className, id, "bonus_spells");
        subclass.setBonusSpells(bonusSpells);

        // Parse additional spells (cantrips always known) with validation
        List<String> additionalSpells = ParseUtil.normalizeStringList(data.get("additional_spells"));
        validateSpells(additionalSpells, className, id, "additional_spells");
        subclass.setAdditionalSpells(additionalSpells);

        // Parse proficiencies and languages
        subclass.setSkillProficiencies(ParseUtil.normalizeStringList(data.get("skill_proficiencies")));
        subclass.setArmorProficiencies(ParseUtil.normalizeStringList(data.get("armor_proficiencies")));
        subclass.setWeaponProficiencies(ParseUtil.normalizeStringList(data.get("weapon_proficiencies")));
        subclass.setToolProficiencies(ParseUtil.normalizeStringList(data.get("tool_proficiencies")));
        subclass.setLanguages(LanguageParser.parseLanguages(data.get("languages")));

        // Parse special movement speeds
        subclass.setSwimmingSpeed(ParseUtil.asInt(data.get("swimming_speed"), 0));
        subclass.setDarkvision(ParseUtil.asInt(data.get("darkvision"), 0));

        // Parse player choices (e.g., Knowledge Domain skill choices, Genie patron type)
        subclass.setPlayerChoices(ChoiceParser.parsePlayerChoices(data.get("player_choices")));

        // Parse conditional advantages (e.g., advantage on saves vs disease)
        subclass.setConditionalAdvantages(parseConditionalAdvantages(data.get("conditional_advantages")));

        // Parse conditional bonus spells (e.g., Genie patron spells based on genie kind)
        subclass.setConditionalBonusSpells(parseConditionalBonusSpells(data.get("conditional_bonus_spells"), className, id));

        return subclass;
    }

    /**
     * Parses conditional advantages from YAML.
     * Format: [{type: "saving_throw", condition: "poison", description: "..."}, ...]
     */
    public static List<Map<String, String>> parseConditionalAdvantages(Object input) {
        if (!(input instanceof List<?> list)) return List.of();

        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, String> advantage = new HashMap<>();
                advantage.put("type", ParseUtil.asString(map.get("type"), ""));
                advantage.put("condition", ParseUtil.asString(map.get("condition"), ""));
                advantage.put("description", ParseUtil.asString(map.get("description"), ""));
                result.add(advantage);
            }
        }
        return result;
    }

    /**
     * Parses conditional bonus spells from YAML with spell validation.
     * Format: {dao: [spell1, spell2], djinni: [spell3, spell4], ...}
     */
    private static Map<String, List<String>> parseConditionalBonusSpells(Object input, String className, String subclassId) {
        if (!(input instanceof Map<?, ?> map)) return Map.of();

        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String choiceOption) {
                List<String> spells = ParseUtil.normalizeStringList(entry.getValue());

                // Validate spells
                validateSpells(spells, className, subclassId, "conditional_bonus_spells[" + choiceOption + "]");

                result.put(choiceOption, spells);
            }
        }
        return result;
    }

    /**
     * Validates a list of spell IDs and logs warnings for any that don't exist.
     * Does not crash - gracefully warns about missing spells.
     */
    private static void validateSpells(List<String> spellIds, String className, String subclassId, String fieldName) {
        if (spellIds == null || spellIds.isEmpty()) return;

        for (String spellId : spellIds) {
            if (SpellLoader.getSpell(spellId) == null) {
                JkVttPlugin.logger().warning("[RaceClassParser] " + className + " subclass '" + subclassId +
                    "' references unknown spell '" + spellId + "' in " + fieldName);
            }
        }
    }
}
