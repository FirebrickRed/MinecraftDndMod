package io.papermc.jkvttplugin.data.model.enums;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Every language a character can know, keyed by one canonical id ({@code deep_speech}) with a
 * display name ("Deep Speech"). The PHB standard + exotic languages are built in;
 * {@code DMContent/Languages.yml} (optional, a plain list of names) adds homebrew ones on every
 * load. YAML can spell a language either way — {@link #idOf} folds "Deep Speech", "deep speech"
 * and {@code deep_speech} to the same id, so a character never "knows" Common twice.
 */
public final class LanguageRegistry {
    private static final List<String> DEFAULT_LANGUAGES = List.of(
            "Common", "Dwarvish", "Elvish", "Giant", "Gnomish", "Goblin", "Halfling",
            "Orc", "Abyssal", "Celestial", "Draconic", "Deep Speech", "Infernal",
            "Primordial", "Sylvan", "Undercommon"
    );

    /** id → display name, in registration order. */
    private static final Map<String, String> languages = new LinkedHashMap<>();

    static { resetToDefault(); }

    private LanguageRegistry() {}

    /** The canonical id for any spelling of a language. */
    public static String idOf(String raw) {
        return Util.normalize(raw).replace("'", "");
    }

    public static void register(String name) {
        if (name == null || name.isBlank()) return;
        languages.putIfAbsent(idOf(name), name.trim());
    }

    public static boolean isRegistered(String raw) {
        return raw != null && languages.containsKey(idOf(raw));
    }

    /** "Deep Speech" for {@code deep_speech}; a prettified id if the language isn't registered. */
    public static String displayName(String raw) {
        String name = languages.get(idOf(raw));
        return name != null ? name : Util.prettify(idOf(raw));
    }

    /** Every registered language id. */
    public static List<String> getAllLanguages() {
        return new ArrayList<>(languages.keySet());
    }

    public static void resetToDefault() {
        languages.clear();
        DEFAULT_LANGUAGES.forEach(LanguageRegistry::register);
    }

    /**
     * Resets to the built-in languages, then adds every name in {@code DMContent/Languages.yml}
     * (a YAML list). A missing file is fine — the defaults stand.
     */
    public static void load(File yamlFile) {
        resetToDefault();
        if (yamlFile == null || !yamlFile.exists()) return;
        try (FileReader reader = new FileReader(yamlFile)) {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof List<?> list) {
                for (Object o : list) if (o instanceof String s) register(s);
            } else if (loaded != null) {
                JkVttPlugin.logger().warning("[Languages] " + yamlFile.getName()
                        + " should be a list of language names; ignoring it.");
            }
        } catch (Exception e) {
            JkVttPlugin.logger().warning("[Languages] Failed to read " + yamlFile.getName() + ": " + e.getMessage());
        }
    }
}
