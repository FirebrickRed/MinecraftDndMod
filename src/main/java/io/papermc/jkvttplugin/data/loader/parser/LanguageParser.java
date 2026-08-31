package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses language lists and language player-choices from YAML, validating against
 * {@link LanguageRegistry}. Split out of {@code LoaderUtils} (issue #12).
 */
public final class LanguageParser {

    private LanguageParser() {}

    /** A fixed list of languages plus an optional "choose N languages" player-choice. */
    public static class LanguageParseResults {
        public final List<String> languages;
        public final PlayersChoice<String> playersChoice;

        public LanguageParseResults(List<String> languages, PlayersChoice<String> playersChoice) {
            this.languages = languages;
            this.playersChoice = playersChoice;
        }
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
                    langs.add(str);
                } else if (lang instanceof Map<?, ?> choiceMap && choiceMap.containsKey("players_choice")) {
                    Object pcObj = choiceMap.get("players_choice");
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
}
