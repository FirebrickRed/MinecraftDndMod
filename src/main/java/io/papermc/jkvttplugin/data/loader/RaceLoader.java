package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.CreatureType;
import io.papermc.jkvttplugin.data.model.enums.Size;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class RaceLoader {
    private static final Map<String, DndRace> loadedRaces = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("RaceLoader");

    public static void loadAllRaces(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No race files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                DndRace race = parseRace(data);
                loadedRaces.put(race.getId(), race);
                LOGGER.info("Loaded race: " + race.getName());
            } catch (Exception e) {
                System.err.println("Failed to load race from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static DndRace parseRace(Map<String, Object> data) {
        String name = (String) data.getOrDefault("name", "Unknown");
        String id = normalize(name);
        String description = (String) data.getOrDefault("description", "");
        String sourceUrl = (String) data.getOrDefault("source_url", "");
        CreatureType creatureType = CreatureType.fromString((String) data.getOrDefault("creature_type", "Humanoid"));

        LoaderUtils.SizeParseResult sizeResult = LoaderUtils.parseSize(data.get("size"));
        Size size = sizeResult.size;
        PlayersChoice<String> sizeChoice = sizeResult.sizeChoice;

        int speed = (int) data.getOrDefault("speed", 30);

        // Languages
        LoaderUtils.LanguageParseResults langResult = LoaderUtils.parseLanguagesAndChoices(data.get("languages"));
        List<String> languages = langResult.languages;
        PlayersChoice<String> languageChoices = langResult.playersChoice;

        // Ability scores (fixed and choice-based)
        Object abilityScoresRaw = data.get("ability_scores");
        LoaderUtils.AbilityScoreParseResult abilityScores = LoaderUtils.parseAbilityScores(abilityScoresRaw);

        // Traits
        List<String> traits = LoaderUtils.parseTraits(data.get("traits"));

        // Subraces (supporting map of subrace name -> subrace data)
        Map<String, DndSubRace> subraces = LoaderUtils.parseSubraces(data.get("subraces"));

        // Icon Name
        String iconName = (String) data.getOrDefault("icon_name", null);

        return new DndRace(id, name, sourceUrl, description, creatureType, size, sizeChoice, speed, abilityScores.fixedBonuses, abilityScores.choiceBonuses, traits, languages, languageChoices, subraces, iconName);
    }



    public static DndRace getRace(String name) {
        return loadedRaces.get(name);
    }

    public static Collection<DndRace> getAllRaces() {
        return Collections.unmodifiableCollection(loadedRaces.values());
    }
}
