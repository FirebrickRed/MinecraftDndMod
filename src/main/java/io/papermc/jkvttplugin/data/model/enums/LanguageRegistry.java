package io.papermc.jkvttplugin.data.model.enums;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class LanguageRegistry {
    private static final List<String> DEFAULT_LANGUAGES = List.of(
            "Common", "Dwarvish", "Elvish", "Giant", "Gnomish", "Goblin", "Halfling",
            "Orc", "Abyssal", "Celestial", "Draconic", "Deep Speech", "Infernal",
            "Primordial", "Sylvan", "Undercommon"
    );

    private static final Set<String> registeredLanguages = new LinkedHashSet<>(DEFAULT_LANGUAGES);

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

    public static List<String> getAllLanguages() {
        return new ArrayList<>(registeredLanguages);
    }

    public static void clear() {
        registeredLanguages.clear();
    }

    public static void resetToDefault() {
        registeredLanguages.clear();
        registeredLanguages.addAll(DEFAULT_LANGUAGES);
    }

    public static void loadLangagesFromYaml(File yamlFile) {
        try (FileReader reader = new FileReader(yamlFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(reader);
            if (loaded instanceof List<?> langList) {
                clear();
                for (Object obj : langList) {
                    if (obj instanceof String lang) {
                        register(lang);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[LanguageRegistry] Failed to load languages from YAML: " + e.getMessage() + ". Using default languages.");
            resetToDefault();
        }
    }
}
