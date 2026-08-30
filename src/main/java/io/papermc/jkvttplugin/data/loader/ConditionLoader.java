package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.DndCondition;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

/** Loads D&D conditions from DMContent/Conditions/*.yml (Issue #103). */
public class ConditionLoader {
    // LinkedHashMap so /combat condition list keeps YAML order.
    private static final Map<String, DndCondition> loaded = new LinkedHashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ConditionLoader");

    public static void loadAllConditions(File folder) {
        File[] files = folder != null ? folder.listFiles((dir, name) -> name.endsWith(".yml")) : null;
        if (files == null || files.length == 0) {
            LOGGER.info("No condition files found in " + (folder != null ? folder.getPath() : "null"));
            return;
        }
        Yaml yaml = new Yaml();
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                if (data == null) continue;
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> condData) {
                        DndCondition c = parse(entry.getKey(), condData);
                        loaded.put(normalize(entry.getKey()), c);
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load conditions from " + file.getName() + ": " + e.getMessage());
            }
        }
        LOGGER.info("Loaded " + loaded.size() + " conditions.");
    }

    private static DndCondition parse(String id, Map<?, ?> data) {
        DndCondition c = new DndCondition();
        c.setId(normalize(id));
        c.setName(data.get("name") instanceof String n ? n : id);
        List<String> rules = new ArrayList<>();
        if (data.get("rules") instanceof List<?> list) {
            for (Object o : list) if (o != null) rules.add(o.toString());
        }
        c.setRules(rules);
        if (data.get("until_next_turn") instanceof Boolean b) c.setUntilNextTurn(b);
        if (data.get("minecraft_effect") instanceof String eff) c.setMinecraftEffect(eff);
        if (data.get("minecraft_effect_amplifier") instanceof Number amp) c.setMinecraftEffectAmplifier(amp.intValue());
        if (data.get("no_movement") instanceof Boolean nm) c.setNoMovement(nm);
        if (data.get("no_actions") instanceof Boolean na) c.setNoActions(na);
        return c;
    }

    public static DndCondition get(String id) {
        return id == null ? null : loaded.get(normalize(id));
    }

    public static Collection<DndCondition> getAll() {
        return Collections.unmodifiableCollection(loaded.values());
    }

    public static void clear() {
        loaded.clear();
    }
}
