package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
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
                    String backgroundKey = entry.getKey();
                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
                    DndBackground background = parseBackground(backgroundKey, data);
                    loadedBackgrounds.put(normalize(background.getName()), background);
                    LOGGER.info("Loaded background: " + background.getName());
                }
            } catch (Exception e) {
                System.err.println("Failed to load background from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static DndBackground parseBackground(String key, Map<String, Object> data) {
        String name = (String) data.getOrDefault("name", key);
        System.out.println("Name: " + name);
        String description = (String) data.getOrDefault("description", "");
        System.out.println("Description: " + description);

        // Skills
        List<String> skills = LoaderUtils.normalizeStringList(data.get("skill_proficiencies"));
        System.out.println("Skills: " + skills);

        // languages
        List<String> languages = LoaderUtils.parseLanguages(data.get("languages"));
        System.out.println("Languages: " + languages);
        PlayersChoice<String> languageChoices = LoaderUtils.parseLanguagePlayersChoice(data.get("players_choice_languages"));
        System.out.println("Language Choices: " + languageChoices);

        // tools
        List<String> tools = LoaderUtils.normalizeStringList(data.get("tool_proficiencies"));
        System.out.println("Tools: " + tools);

        // equipment
        List<String> equipment = LoaderUtils.parseEquipment((List<Object>) data.get("starting_equipment"));
        System.out.println("Equipment: " + equipment);

        // feature
        String feature = (String) data.getOrDefault("feature", "");
        System.out.println("Feature: " + feature);

        // Traits
        List<String> traits = LoaderUtils.parseTraits(data.get("traits"));
        System.out.println("Traits: " + traits);

        // links
        List<String> links = LoaderUtils.normalizeStringList(data.get("links"));
        System.out.println("Links: " + links);

        // Icon name
        String iconName = (String) data.getOrDefault("icon_name", null);
        System.out.println("Icon Name: " + iconName);

        return new DndBackground(key, name, description, skills, languages, languageChoices, tools, equipment, feature, traits, links, iconName);
    }

    public static DndBackground getBackground(String name) {
        return loadedBackgrounds.get(normalize(name));
    }

    public static Collection<DndBackground> getAllBackgrounds() {
        return Collections.unmodifiableCollection(loadedBackgrounds.values());
    }
}
