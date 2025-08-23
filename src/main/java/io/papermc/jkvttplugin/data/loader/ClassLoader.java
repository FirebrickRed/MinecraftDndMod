package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class ClassLoader {
    private static final Map<String, DndClass> loadedClasses = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ClassLoader");

    public static void loadAllClasses(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No class files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                DndClass dndClass = parseClass(data);
                loadedClasses.put(normalize(dndClass.getName()), dndClass);
                LOGGER.info("Loaded class: " + dndClass.getName());
            } catch (Exception e) {
                System.err.println("Failed to load class from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static DndClass parseClass(Map<String, Object> data) {
        DndClass.Builder builder = DndClass.builder()
                .name((String) data.getOrDefault("name", "Unknown"))
                .hitDie((int) data.getOrDefault("hit_die", 6))
                .savingThrows(LoaderUtils.parseAbilityList(data.get("saving_throws")))

                .armorProficiencies(LoaderUtils.normalizeStringList(data.get("armor_proficiencies")))
                .weaponProficiencies(LoaderUtils.normalizeStringList(data.get("weapon_proficiencies")))
                .toolProficiencies(LoaderUtils.normalizeStringList(data.get("tool_proficiencies")))

                .skills(LoaderUtils.normalizeStringList(data.get("skills")))
                .startingEquipment(LoaderUtils.normalizeStringList(data.get("starting_equipment")))

                .asiLevels(LoaderUtils.castList(data.get("asi_levels"), Integer.class))
                .spellcastingAbility(Ability.fromString((String) data.get("spellcasting_ability")))
                .featuresByLevel(LoaderUtils.parseLevelStringListMap(data.get("features_by_level")))
                .subclasses(LoaderUtils.castList(data.getOrDefault("subclasses", List.of()), String.class))
                .multiclassRequirements(LoaderUtils.castMap(data.get("multiclass_requirements"), String.class, Integer.class))
//                .classResources()

                .allowFeats((boolean) data.getOrDefault("allow_feats", true))
                .icon((String) data.get("icon"));

        DndClass dndClass = builder.build();
        var pcs = LoaderUtils.parsePlayerChoicesForClass(data.get("player_choices"));
        dndClass.setPlayerChoices(pcs);

        return dndClass;
    }

    public static DndClass getClass(String name) {
        return loadedClasses.get(name);
    }

    public static Collection<DndClass> getAllClasses() {
        return Collections.unmodifiableCollection(loadedClasses.values());
    }
}
