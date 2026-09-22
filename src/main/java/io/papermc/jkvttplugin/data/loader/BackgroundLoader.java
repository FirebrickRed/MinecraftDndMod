package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.loader.parser.LanguageParser;
import io.papermc.jkvttplugin.data.loader.parser.EquipmentParser;
import io.papermc.jkvttplugin.data.loader.parser.ChoiceParser;
import io.papermc.jkvttplugin.data.loader.parser.AbilityParser;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class BackgroundLoader {
    private static final Map<String, DndBackground> loadedBackgrounds = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("BackgroundLoader");

    public static void loadAllBackgrounds(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No background files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> fileMap = yaml.load(reader);
                if (fileMap == null) continue;
                for (Map.Entry<String, Object> entry : fileMap.entrySet()) {
                    // Per entry, so one bad background doesn't take the rest of its file down with it.
                    try {
                        Map<String, Object> data = (Map<String, Object>) entry.getValue();
                        DndBackground background = parseBackground(entry.getKey(), data);
                        // Keyed by id: the creation menu and saved sheets both store the id.
                        loadedBackgrounds.put(normalize(background.getId()), background);
                        LOGGER.fine("Loaded background: " + background.getName());
                    } catch (Exception e) {
                        LOGGER.severe("Failed to load background '" + entry.getKey() + "' from " + file.getName() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load background from " + file.getName() + ": " + e.getMessage());
            }
        }
        LOGGER.info("Loaded " + loadedBackgrounds.size() + " backgrounds.");
    }

    private static DndBackground parseBackground(String key, Map<String, Object> data) {
        // Only `starting_equipment:` is read. `equipment:` looks right and silently grants nothing.
        if (data.containsKey("equipment") && !data.containsKey("starting_equipment")) {
            LOGGER.warning("Background '" + key + "' uses `equipment:` — rename it to `starting_equipment:`,"
                    + " or the background grants no gear.");
        }

        return DndBackground.builder()
                .id(key)
                .name(ParseUtil.asString(data.get("name"), Util.prettify(key)))
                .description((String) data.get("description"))
                .skills(ParseUtil.normalizeStringList(data.get("skill_proficiencies")))
                .languages(LanguageParser.parseLanguages(data.get("languages")))
                .tools(ToolRegistry.idsOf(ParseUtil.normalizeStringList(data.get("tool_proficiencies"))))
                .equipment(EquipmentParser.parseEquipment((List<Object>) data.get("starting_equipment")))
                .feature(parseFeature(data.get("feature")))
                // 2024-rules slots: carried and displayed, not applied (see DndBackground).
                .feat(ParseUtil.asString(data.get("feat"), null))
                .abilityScoreOptions(AbilityParser.parseAbilityList(data.get("ability_scores")))
                .links(ParseUtil.normalizeStringList(data.get("links")))
                .playerChoices(ChoiceParser.parsePlayerChoices(data.get("player_choices")))
                .customModel((String) data.get("custom_model")) // resource-pack model name
                .build();
    }

    /** {@code feature: {name, description}}; a bare string is shorthand for a name with no text. */
    private static DndBackground.Feature parseFeature(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            String name = ParseUtil.asString(m.get("name"), "");
            if (name.isBlank()) return null;
            return new DndBackground.Feature(name, ParseUtil.asString(m.get("description"), null));
        }
        if (raw instanceof String s && !s.isBlank()) return new DndBackground.Feature(s.trim(), null);
        return null;
    }

    public static DndBackground getBackground(String name) {
        return loadedBackgrounds.get(normalize(name));
    }

    public static Collection<DndBackground> getAllBackgrounds() {
        return Collections.unmodifiableCollection(loadedBackgrounds.values());
    }

    /**
     * Clears all loaded backgrounds. Called before reloading data.
     */
    public static void clear() {
        loadedBackgrounds.clear();
        LOGGER.info("Cleared all loaded backgrounds");
    }
}
