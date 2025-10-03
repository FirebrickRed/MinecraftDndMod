package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.DndItem;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class ItemLoader {
    private static final Map<String, DndItem> loadedItems = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ItemLoader");

    public static void loadAllItems(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No class files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);

                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String itemId = entry.getKey();

                    if (entry.getValue() instanceof Map<?,?> itemData) {
                        DndItem item = parseItem(itemId, itemData);
                        loadedItems.put(normalize(itemId), item);
                        LOGGER.info("Loaded item: " + item.getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load items from " + file.getName());
            }
        }
    }

    private static DndItem parseItem(String id, Map<?, ?> data) {
        DndItem item = new DndItem();
        item.setId(id);
        item.setName((String) data.get("name"));
        item.setType((String) data.get("type"));
        item.setFocusType((String) data.get("focus_type"));
        item.setDescription((String) data.get("description"));
        item.setIcon((String) data.get("icon"));
        item.setCost((String) data.get("cost"));
        return item;
    }

    public static DndItem getItem(String id) {
        return loadedItems.get(normalize(id));
    }
}
