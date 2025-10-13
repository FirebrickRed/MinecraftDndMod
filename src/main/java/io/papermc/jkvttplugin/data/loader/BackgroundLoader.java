package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndBackground;
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

        DndBackground.Builder builder = DndBackground.builder()
                .id(key)
                .name((String) data.getOrDefault("name", "unknown"))
                .description((String) data.get("description"))
                .skills(LoaderUtils.normalizeStringList(data.get("skill_proficiencies")))
                .languages(LoaderUtils.parseLanguages(data.get("languages")))
                .tools(LoaderUtils.normalizeStringList(data.get("tool_proficiencies")))
                .equipment(LoaderUtils.parseEquipment((List<Object>) data.get("starting_equipment")))
                .feature((String) data.get("feature"))
                .traits(LoaderUtils.parseTraits(data.get("traits")))
                .links(LoaderUtils.normalizeStringList(data.get("links")))
                .playerChoices(LoaderUtils.parsePlayerChoices(data.get("player_choices")))
                .icon((String) data.get("icon_name"));

        DndBackground dndBackground = builder.build();
        return dndBackground;
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
