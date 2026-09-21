package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parses a fixed {@code languages:} list into canonical language ids, validated against
 * {@link LanguageRegistry}. Split out of {@code LoaderUtils} (issue #12).
 * <p>
 * A language the player picks is a {@code player_choices} entry with {@code type: language}
 * (see {@link ChoiceParser}), the same as for skills and tools.
 */
public final class LanguageParser {

    private LanguageParser() {}

    /**
     * {@code [Common, "Deep Speech"]} → {@code [common, deep_speech]}. An unregistered language
     * throws, which fails that one file's load with the language named — add it to
     * {@code DMContent/Languages.yml} if it's homebrew.
     */
    public static List<String> parseLanguages(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Object lang : inputList) {
            if (lang instanceof String str) {
                if (!LanguageRegistry.isRegistered(str)) {
                    throw new IllegalArgumentException("Unknown language '" + str
                            + "' (add it to DMContent/Languages.yml if it's homebrew)");
                }
                ids.add(LanguageRegistry.idOf(str));
            }
        }
        return List.copyOf(ids);
    }
}
