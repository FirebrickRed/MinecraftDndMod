package io.papermc.jkvttplugin.data.model.enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LanguageRegistry {
    private static final Set<String> registeredLanguages = new HashSet<>();

    static {
        // Default D&D languages
        Collections.addAll(registeredLanguages,
                "Common", "Elvish", "Dwarvish", "Draconic", "Halfling", "Orc",
                "Gnomish", "Goblin", "Sylvan", "Celestial", "Infernal", "Abyssal"
        );
    }

    public static void register(String language) {
        if (language != null && !language.isBlank()) {
            registeredLanguages.add(language.trim());
        }
    }

    public static void registerAll(Iterable<String> languages) {
        for (String lang : languages) {
            register(lang);
        }
    }

    public static boolean isRegistered(String language) {
        return language != null && registeredLanguages.contains(language.trim());
    }

    public static Set<String> getAll() {
        return Collections.unmodifiableSet(registeredLanguages);
    }

    public static void clear() {
        registeredLanguages.clear();
    }
}
