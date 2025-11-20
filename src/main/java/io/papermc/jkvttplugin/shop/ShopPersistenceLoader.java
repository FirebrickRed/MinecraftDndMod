package io.papermc.jkvttplugin.shop;

import io.papermc.jkvttplugin.data.model.ShopConfig;
import io.papermc.jkvttplugin.data.model.ShopItem;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles persistence of shop stock for spawned entity instances.
 * Each entity instance gets its own shop inventory that persists across restarts.
 *
 * Issue #75 - Native Villager Trade GUI Shop System
 */
public class ShopPersistenceLoader {
    private static final Logger LOGGER = Logger.getLogger("ShopPersistenceLoader");
    private static File shopsFolder;

    /**
     * Initialize the shop persistence system.
     * Creates DMContent/Saved/Shops/ directory if it doesn't exist.
     *
     * @param plugin The plugin instance
     */
    public static void initialize(Plugin plugin) {
        File dmContentFolder = new File(plugin.getDataFolder().getParentFile().getParentFile(), "DMContent");
        File savedFolder = new File(dmContentFolder, "Saved");
        shopsFolder = new File(savedFolder, "Shops");

        if (!shopsFolder.exists()) {
            if (shopsFolder.mkdirs()) {
                LOGGER.info("Created shop persistence folder: " + shopsFolder.getPath());
            } else {
                LOGGER.severe("Failed to create shop persistence folder!");
            }
        }
    }

    /**
     * Saves shop stock for an entity instance.
     * Only saves stock values, not the full shop configuration.
     *
     * @param entityUuid The entity instance UUID
     * @param shopConfig The shop configuration with current stock
     */
    public static void saveShop(UUID entityUuid, ShopConfig shopConfig) {
        if (shopConfig == null || shopConfig.getItems() == null) {
            return;
        }

        File shopFile = new File(shopsFolder, entityUuid.toString() + ".yml");

        try (FileWriter writer = new FileWriter(shopFile)) {
            Map<String, Object> data = new HashMap<>();

            // Save stock for each item
            Map<String, Integer> stockMap = new HashMap<>();
            for (ShopItem shopItem : shopConfig.getItems()) {
                stockMap.put(shopItem.getItemId(), shopItem.getStock());
            }
            data.put("stock", stockMap);

            // Write to YAML
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            yaml.dump(data, writer);

            LOGGER.info("Saved shop for entity " + entityUuid);
        } catch (IOException e) {
            LOGGER.severe("Failed to save shop for entity " + entityUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads shop stock for an entity instance.
     * Applies saved stock values to the shop configuration.
     *
     * @param entityUuid The entity instance UUID
     * @param shopConfig The shop configuration to update
     * @return true if stock was loaded successfully
     */
    @SuppressWarnings("unchecked")
    public static boolean loadShop(UUID entityUuid, ShopConfig shopConfig) {
        if (shopConfig == null) {
            return false;
        }

        File shopFile = new File(shopsFolder, entityUuid.toString() + ".yml");
        if (!shopFile.exists()) {
            // No saved shop - entity is newly spawned or first time
            return false;
        }

        try (FileReader reader = new FileReader(shopFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            if (data == null) {
                return false;
            }

            // Load stock values
            Object stockObj = data.get("stock");
            if (stockObj instanceof Map<?, ?> stockMap) {
                for (Map.Entry<?, ?> entry : stockMap.entrySet()) {
                    String itemId = (String) entry.getKey();
                    Integer stock = (Integer) entry.getValue();

                    // Find the corresponding shop item and update stock
                    ShopItem shopItem = shopConfig.findItem(itemId);
                    if (shopItem != null) {
                        shopItem.setStock(stock);
                    }
                }
            }

            LOGGER.info("Loaded shop for entity " + entityUuid);
            return true;
        } catch (IOException e) {
            LOGGER.severe("Failed to load shop for entity " + entityUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes saved shop data for an entity instance.
     * Called when an entity is permanently removed.
     *
     * @param entityUuid The entity instance UUID
     */
    public static void deleteShop(UUID entityUuid) {
        File shopFile = new File(shopsFolder, entityUuid.toString() + ".yml");
        if (shopFile.exists()) {
            if (shopFile.delete()) {
                LOGGER.info("Deleted shop for entity " + entityUuid);
            } else {
                LOGGER.warning("Failed to delete shop file for entity " + entityUuid);
            }
        }
    }

    /**
     * Saves all active shops.
     * Called on server shutdown.
     */
    public static void saveAllShops() {
        // TODO: Implement when we have entity instance tracking
        // Will iterate through all spawned entities and save their shops
    }
}