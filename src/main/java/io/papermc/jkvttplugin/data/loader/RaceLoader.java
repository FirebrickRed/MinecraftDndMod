package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.enums.CreatureType;
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
        LoaderUtils.SizeParseResult sizeResult = LoaderUtils.parseSize(data.get("size"));
        LoaderUtils.LanguageParseResults langResult = LoaderUtils.parseLanguagesAndChoices(data.get("languages"));
        Object abilityScoresRaw = data.get("ability_scores");
        LoaderUtils.AbilityScoreParseResult abilityScores = LoaderUtils.parseAbilityScores(abilityScoresRaw);

        DndRace.Builder builder = DndRace.builder()
                .id(normalize((String) data.getOrDefault("name", "Unknown")))
                .name((String) data.getOrDefault("name", "Unknown"))
                .sourceUrl((String) data.get("source_url"))
                .description((String) data.get("description"))
                .creatureType(CreatureType.fromString((String) data.getOrDefault("creature_type", "Humanoid")))
                .size(sizeResult.size)
                .speed((int) data.getOrDefault("speed", 30))
                .fixedAbilityScores(abilityScores.fixedBonuses)
                .abilityScoreChoice(abilityScores.choiceBonuses)
                .traits(LoaderUtils.parseTraits(data.get("traits")))
                .languages(langResult.languages)
                .subraces(LoaderUtils.parseSubraces(data.get("subraces")))
                .playerChoices(LoaderUtils.parsePlayerChoices(data.get("player_choices")))
                .icon((String) data.getOrDefault("icon_name", null))
            // Parse new mechanical trait fields (Issue #51)
                .swimmingSpeed((int) data.getOrDefault("swimming_speed", 0))
                .flyingSpeed((int) data.getOrDefault("flying_speed", 0))
                .climbingSpeed((int) data.getOrDefault("climbing_speed", 0))
                .burrowingSpeed((int) data.getOrDefault("burrowing_speed", 0))
                .darkvision((Integer) data.get("darkvision"))
                .damageResistances(LoaderUtils.parseStringList(data.get("damage_resistances")))
                .skillProficiencies(LoaderUtils.parseStringList(data.get("skill_proficiencies")))
                .weaponProficiencies(LoaderUtils.parseStringList(data.get("weapon_proficiencies")))
                .armorProficiencies(LoaderUtils.parseStringList(data.get("armor_proficiencies")))
                .innateSpells(LoaderUtils.parseInnateSpells(data.get("innate_spells")));


        DndRace dndRace = builder.build();
        return dndRace;
    }



    public static DndRace getRace(String name) {
        return loadedRaces.get(name);
    }

    public static Collection<DndRace> getAllRaces() {
        return Collections.unmodifiableCollection(loadedRaces.values());
    }

    /**
     * Clears all loaded races. Called before reloading data.
     */
    public static void clear() {
        loadedRaces.clear();
        LOGGER.info("Cleared all loaded races");
    }
}
