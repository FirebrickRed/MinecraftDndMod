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
     * Saves shop data for an entity instance.
     * Saves stock values, DM price adjustments (Issue #76), and currency reserves.
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

            // Issue #76 - Save DM price adjustments
            if (shopConfig.getGlobalDiscount() > 0.0) {
                data.put("global_discount", shopConfig.getGlobalDiscount());
            }
            if (shopConfig.getGlobalMarkup() > 0.0) {
                data.put("global_markup", shopConfig.getGlobalMarkup());
            }
            if (shopConfig.getItemPriceOverrides() != null && !shopConfig.getItemPriceOverrides().isEmpty()) {
                data.put("item_price_overrides", shopConfig.getItemPriceOverrides());
            }

            // Save currency reserves
            if (shopConfig.getCurrency() != null && !shopConfig.getCurrency().isEmpty()) {
                data.put("currency", shopConfig.getCurrency());
            }

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
     * Loads shop data for an entity instance.
     * Applies saved stock values, DM price adjustments (Issue #76), and currency reserves.
     *
     * @param entityUuid The entity instance UUID
     * @param shopConfig The shop configuration to update
     * @return true if data was loaded successfully
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
                    Integer stock = ((Number) entry.getValue()).intValue();

                    // Find the corresponding shop item and update stock
                    ShopItem shopItem = shopConfig.findItem(itemId);
                    if (shopItem != null) {
                        shopItem.setStock(stock);
                    }
                }
            }

            // Issue #76 - Load DM price adjustments
            Object discountObj = data.get("global_discount");
            if (discountObj instanceof Number discount) {
                shopConfig.setGlobalDiscount(discount.doubleValue());
            }

            Object markupObj = data.get("global_markup");
            if (markupObj instanceof Number markup) {
                shopConfig.setGlobalMarkup(markup.doubleValue());
            }

            Object overridesObj = data.get("item_price_overrides");
            if (overridesObj instanceof Map<?, ?> overridesMap) {
                Map<String, Integer> overrides = new HashMap<>();
                for (Map.Entry<?, ?> entry : overridesMap.entrySet()) {
                    String itemId = (String) entry.getKey();
                    Integer price = ((Number) entry.getValue()).intValue();
                    overrides.put(itemId, price);
                }
                shopConfig.setItemPriceOverrides(overrides);
            }

            // Load currency reserves
            Object currencyObj = data.get("currency");
            if (currencyObj instanceof Map<?, ?> currencyMap) {
                Map<String, Integer> currency = new HashMap<>();
                for (Map.Entry<?, ?> entry : currencyMap.entrySet()) {
                    String currencyType = (String) entry.getKey();
                    Integer amount = ((Number) entry.getValue()).intValue();
                    currency.put(currencyType, amount);
                }
                shopConfig.setCurrency(currency);
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