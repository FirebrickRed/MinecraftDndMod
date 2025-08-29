package io.papermc.jkvttplugin.data.loader.util;

import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Size;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.jkvttplugin.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoaderUtils {

    public static class LanguageParseResults {
        public final List<String> languages;
        public final PlayersChoice<String> playersChoice;

        public LanguageParseResults(List<String> languages, PlayersChoice<String> playersChoice) {
            this.languages = languages;
            this.playersChoice = playersChoice;
        }
    }

    public static class SizeParseResult {
        public final Size size;
        public final PlayersChoice<String> sizeChoice;
        public SizeParseResult(Size size, PlayersChoice<String> sizeChoice) {
            this.size = size;
            this.sizeChoice = sizeChoice;
        }
    }

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

    public static LanguageParseResults parseLanguagesAndChoices(Object input) {
        List<String> langs = new ArrayList<>();
        PlayersChoice<String> playersChoice = null;

        if (input instanceof List<?> inputList) {
            for (Object lang : inputList) {
                if (lang instanceof String str) {
                    if (!LanguageRegistry.isRegistered(str)) {
                        throw new IllegalArgumentException("Invalid language: " + str);
                    }
                    System.out.println("Adding language: " + str);
                    langs.add(str);
                } else if (lang instanceof Map<?, ?> choiceMap && choiceMap.containsKey("players_choice")) {
                    Object pcObj = choiceMap.get("players_choice");
                    System.out.println("Found players choice for languages: " + pcObj);
                    playersChoice = parseLanguagePlayersChoice(pcObj);
                }
            }
        }

        return new LanguageParseResults(langs, playersChoice);
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

        int choose;
        if (chooseObj instanceof Number n) {
            choose = n.intValue();
        } else if (chooseObj instanceof String s) {
            try {
                choose = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                choose = 1;
            }
        } else {
            choose = 1;
        }

        List<String> options = new ArrayList<>();

        if (optionsObj instanceof List<?> optionList) {
            if (optionList.isEmpty()) {
                options.addAll(LanguageRegistry.getAllLanguages());
            } else {
                for (Object option : optionList) {
                    if (option instanceof String str) {
                        if (!LanguageRegistry.isRegistered(str)) {
                            throw new IllegalArgumentException("Invalid language option: " + str);
                        }
                        options.add(str);
                    }
                }
            }
        }

        return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.LANGUAGE);
    }

    public static SizeParseResult parseSize(Object sizeObj) {
        if (sizeObj instanceof String s) {
            return new SizeParseResult(Size.fromString(s), null);
        } else if (sizeObj instanceof Map<?, ?> sizeMap && sizeMap.containsKey("players_choice")) {
            PlayersChoice<String> sizeChoice = PlayersChoice.fromMap((Map<String, Object>)sizeMap.get("players_choice"), String.class, PlayersChoice.ChoiceType.CUSTOM);
            return new SizeParseResult(null, sizeChoice);
        }
        return new SizeParseResult(Size.MEDIUM, null); // Default to medium if no valid size found
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

    public static PlayersChoice<String> parseSkillChoice(Object node) {
        if (!(node instanceof List<?> blocks)) return null;

        for (Object block : blocks) {
            if (!(block instanceof Map<?, ?> m)) continue;
            Object pcNode = m.get("players_choice");
            if (pcNode instanceof Map<?, ?> pm) {
                int choose = asInt(pm.get("choose"), 0);
                List<String> options = normalizeStringList(pm.get("options"));
                if (choose > 0 && !options.isEmpty()) {
                    return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.SKILL);
                }
            }
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

    public static List<EquipmentOption> parseEquipmentOptions(Object node) {
        List<EquipmentOption> out = new ArrayList<>();
        if (!(node instanceof List<?> raw)) return out;

        for (Object opt : raw) {
            if (opt instanceof List<?> bundle) {
                List<EquipmentOption> parts = new ArrayList<>();
                for (Object part : bundle) {
                    EquipmentOption p = parseEquipmentElement(part);
                    if (p != null) {
                        parts.add(p);
                    }
                }
                if (!parts.isEmpty()) {
                    out.add(EquipmentOption.bundle(parts));
                }
                continue;
            }
            EquipmentOption single = parseEquipmentElement(opt);
            if (single != null) out.add(single);
        }
        return out;
    }

    private static EquipmentOption parseEquipmentElement(Object node) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey("item")) {
                String id = Util.normalize(asString(m.get("item"), ""));
                int qty = asInt(m.get("quantity"), 1);
                System.out.println("id: " + id + " quantity: " + qty);
                if (!id.isBlank()) {
                    return EquipmentOption.item(id, qty);
                }
            }
            if (m.containsKey("tag")) {
                String tag = Util.normalize(asString(m.get("tag"), ""));
                if (!tag.isBlank()) {
                    return EquipmentOption.tag(tag);
                }
            }
        } else if (node instanceof String s) {
            String id = Util.normalize(s);
            if (!id.isBlank()) {
                return EquipmentOption.item(id, 1);
            }
        }
        return null;
    }

    private static List<EquipmentOption> expandTagsForChoices(List<EquipmentOption> options) {
        List<EquipmentOption> out = new ArrayList<>();
        for (var opt : options) {
            if (opt.getKind() == EquipmentOption.Kind.TAG) {
                for (String id : TagRegistry.itemsFor(opt.getIdOrTag())) {
                    out.add(EquipmentOption.item(id));
                }
            } else {
                out.add(opt);
            }
        }
        return out;
    }

    public static List<ChoiceEntry> parsePlayerChoicesForClass(Object node) {
        if (!(node instanceof List<?> arr)) return List.of();
        List<ChoiceEntry> out = new ArrayList<>();

        for (Object raw : arr) {
            if (!(raw instanceof Map<?, ?> m)) continue;

            String id = asString(m.get("id"), "");
            String title = asString(m.get("title"), id);
            String typeString = asString(m.get("type"), "").toUpperCase();
            int choose = asInt(m.get("choose"), 0);

            PlayersChoice<?> pc = null;
            PlayersChoice.ChoiceType type;

            switch(typeString) {
                case "SKILL" -> {
                    type = PlayersChoice.ChoiceType.SKILL;
                    var opts = normalizeStringList(m.get("options"));
                    pc = new PlayersChoice<>(choose, opts, type);
                }
                case "EQUIPMENT" -> {
                    type = PlayersChoice.ChoiceType.EQUIPMENT;
                    var opts = parseEquipmentOptions(m.get("options"));
//                    opts = expandTagsForChoices(opts);
                    pc = new PlayersChoice<>(choose, opts, type);
                }
                default -> { continue; }
            }

            if (choose > 0 && pc.getOptions() != null && !pc.getOptions().isEmpty()) {
                out.add(new ChoiceEntry(id, title, pc.getType(), pc));
            }
        }
        return out;
    }

    public static String asString(Object o, String def) {
        return (o instanceof String s) ? s : def;
    }

    public static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return def;
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
