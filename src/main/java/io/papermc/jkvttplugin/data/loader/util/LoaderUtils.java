package io.papermc.jkvttplugin.data.loader.util;

import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoaderUtils {

    public static Map<Ability, Integer> parseAbilityScoreMap(Object rawObject) {
        Map<Ability, Integer> result = new HashMap<>();

        if (rawObject instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String keyString && entry.getValue() instanceof Number num) {
                    try {
                        Ability ability = Ability.valueOf(keyString.toUpperCase());
                        result.put(ability, num.intValue());
                    } catch (IllegalArgumentException ignored) {
                        // Log or skip unknown ability
                    }
                }
            }
        }

        return result;
    }

    public static List<Ability> parseAbilityList(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<Ability> abilities = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof String str) {
                try {
                    abilities.add(Ability.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Optionally log the invalid entry
                }
            }
        }
        return abilities;
    }

    public static PlayersChoice<Ability> parseAbilityPlayersChoice(Object input) {
        if (!(input instanceof Map<?, ?> choiceMap)) return null;

        Object chooseObj = choiceMap.get("choose");
        Object optionsObj = choiceMap.get("options");

        if (!(chooseObj instanceof Number chooseNum) || !(optionsObj instanceof List<?> optionList)) {
            return null;
        }

        int choose = chooseNum.intValue();
        List<Ability> options = new ArrayList<>();

        for (Object option : optionList) {
            if (option instanceof String str) {
                try {
                    options.add(Ability.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Optionally log or ignore invalid entries
                }
            }
        }

        return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.ABILITY_SCORE);
    }

    public static List<String> parseLanguages(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<String> validated = new ArrayList<>();
        for (Object lang : inputList) {
            if (lang instanceof String str) {
                if (!LanguageRegistry.isRegistered(str)) {
                    throw new IllegalArgumentException("Invalid language: " + str);
                }
                validated.add(str);
            }
        }
        return validated;
    }

    public static PlayersChoice<String> parseLanguagePlayersChoice(Object input) {
        if (!(input instanceof Map<?, ?> choiceMap)) return null;

        Object chooseObj = choiceMap.get("choose");
        Object optionsObj = choiceMap.get("options");

        if (!(chooseObj instanceof Number chooseNum) || !(optionsObj instanceof List<?> optionList)) {
            return null;
        }

        int choose = chooseNum.intValue();
        List<String> options = new ArrayList<>();

        for (Object option : optionList) {
            if (option instanceof String str) {
                if (!LanguageRegistry.isRegistered(str)) {
                    throw new IllegalArgumentException("Invalid language option: " + str);
                }
                options.add(str);
            }
        }

        return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.LANGUAGE);
    }

    public static List<String> parseTraits(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<String> traits = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof String str && !str.isBlank()) {
                traits.add(str.trim());
            }
        }
        return traits;
    }

    public static List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> rawList)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String str && !str.isBlank()) {
                result.add(str.trim().toLowerCase());
            }
        }

        return result;
    }

    public static Map<Integer, List<String>> parseLevelStringListMap(Object raw) {
        Map<Integer, List<String>> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> rawMap)) return result;

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            try {
                int level = Integer.parseInt(entry.getKey().toString().trim());
                List<String> list = normalizeStringList(entry.getValue());
                result.put(level, list);
            } catch (NumberFormatException ignored) {}
        }

        return result;
    }

    public static Map<String, DndSubRace> parseSubraces(Object rawData) {
        Map<String, DndSubRace> result = new HashMap<>();

        if (rawData instanceof Map<?, ?> subraceMap) {
            for (Map.Entry<?, ?> entry : subraceMap.entrySet()) {
                if (entry.getKey() instanceof String id &&
                        entry.getValue() instanceof Map<?, ?> rawSubraceData) {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> subraceData = (Map<String, Object>) rawSubraceData;

                    DndSubRace subrace = parseSubRace(id, subraceData); // assuming it's public or move it to utils
                    result.put(id, subrace);
                }
            }
        }

        return result;
    }

    public static DndSubRace parseSubRace(String id, Map<String, Object> data) {
        String name = (String) data.getOrDefault("name", id);
        String description = (String) data.getOrDefault("description", "");
        List<String> traits = LoaderUtils.parseTraits(data.get("traits"));

        // Fixed ability scores
        Map<Ability, Integer> fixedAbilityScores = LoaderUtils.parseAbilityScoreMap(data.get("ability_scores"));

        // Ability score choices
        PlayersChoice<Ability> abilityScoreChoices = LoaderUtils.parseAbilityPlayersChoice(data.get("players_choice_ability_scores"));

        List<String> languages = LoaderUtils.parseLanguages(data.get("languages"));
        PlayersChoice<String> languageChoices = LoaderUtils.parseLanguagePlayersChoice(data.get("players_choice_languages"));

        String iconName = (String) data.getOrDefault("icon_name", "");

        return new DndSubRace(id, name, description, fixedAbilityScores, abilityScoreChoices, traits, languages, languageChoices, iconName);
    }

    public static PlayersChoice<String> parseSkillChoice(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return PlayersChoice.fromMap(map, String.class, PlayersChoice.ChoiceType.SKILL);
        }
        return null;
    }

    public static List<String> parseEquipment(List<?> equipmentList) {
        List<String> result = new ArrayList<>();
        if (equipmentList == null) return result;

        for (Object entry : equipmentList) {
            if (entry instanceof String) {
                result.add((String) entry);
            } else if (entry instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) entry;
                Object itemObj = map.get("item");
                if (itemObj instanceof String) {
                    result.add((String) itemObj);
                } else {
                    System.out.println("[Warning] Equipment entry object missing valid 'item' key: " + entry);
                }
            } else {
                System.out.println("[Warning] Unexpected equipment entry type: " + entry);
            }
        }
        return result;
    }

    public static List<PlayersChoice<String>> parseEquipmentChoicesList(Object obj) {
        if (obj instanceof List<?> list) {
            List<PlayersChoice<String>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(PlayersChoice.fromMap(map, String.class, PlayersChoice.ChoiceType.EQUIPMENT));
                }
            }
            return result;
        }
        return List.of();
    }

    public static <T> List<T> castList(Object obj, Class<T> clazz) {
        if (obj instanceof List<?> list) {
            List<T> result = new ArrayList<>();
            for (Object item : list) {
                if (clazz.isInstance(item)) {
                    result.add(clazz.cast(item));
                }
            }
            return result;
        }
        return List.of();
    }

    public static <K, V> Map<K, V> castMap(Object obj, Class<K> keyClass, Class<V> valueClass) {
        if (obj instanceof Map<?, ?> map) {
            Map<K, V> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (keyClass.isInstance(entry.getKey()) && valueClass.isInstance(entry.getValue())) {
                    result.put(keyClass.cast(entry.getKey()), valueClass.cast(entry.getValue()));
                }
            }
            return result;
        }
        return Map.of();
    }

}
