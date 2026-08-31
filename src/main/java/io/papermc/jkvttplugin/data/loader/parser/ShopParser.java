package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.model.Cost;
import io.papermc.jkvttplugin.data.model.ShopConfig;
import io.papermc.jkvttplugin.data.model.ShopItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses shop configuration, shop items, and item costs from YAML (issue #75 - Shop System).
 * Split out of {@code LoaderUtils} (issue #12).
 */
public final class ShopParser {

    private ShopParser() {}

    /**
     * Parse cost from YAML data.
     * Expected YAML structure:
     *   cost:
     *     amount: 15
     *     currency: gold  # Optional, defaults to gold if omitted
     *
     * @param costObj The cost object from YAML (Map)
     * @param itemId The item ID for error logging
     * @return Cost object or null if not specified/invalid
     */
    public static Cost parseCost(Object costObj, String itemId) {
        if (costObj == null) {
            return null;
        }

        if (costObj instanceof Map<?, ?> costMap) {
            try {
                int amount = 0;
                String currency = "gold";  // Default currency

                // Parse amount (required)
                Object amountObj = costMap.get("amount");
                if (amountObj instanceof Integer) {
                    amount = (Integer) amountObj;
                } else {
                    JkVttPlugin.logger().warning("[ShopParser] Missing or invalid 'amount' in cost for " + itemId);
                    return null;
                }

                // Parse currency (optional, defaults to gold)
                Object currencyObj = costMap.get("currency");
                if (currencyObj instanceof String) {
                    currency = (String) currencyObj;
                }

                return new Cost(amount, currency);
            } catch (Exception e) {
                JkVttPlugin.logger().warning("[ShopParser] Failed to parse cost for " + itemId + ": " + e.getMessage());
                return null;
            }
        } else {
            JkVttPlugin.logger().warning("[ShopParser] Invalid cost format for " + itemId + " (expected map with amount/currency)");
            return null;
        }
    }

    /**
     * Parse shop configuration from YAML.
     *
     * @param data The shop data from YAML
     * @param entityId The entity ID for error logging
     * @return ShopConfig object (never null)
     */
    public static ShopConfig parseShop(Map<?, ?> data, String entityId) {
        ShopConfig shop = new ShopConfig();

        // Enabled flag (default true)
        Object enabledObj = data.get("enabled");
        if (enabledObj instanceof Boolean enabled) {
            shop.setEnabled(enabled);
        } else {
            shop.setEnabled(true); // Default to enabled
        }

        // Parse shop items
        Object itemsObj = data.get("items");
        if (itemsObj instanceof List<?> itemsList) {
            List<ShopItem> shopItems = new ArrayList<>();
            for (Object itemObj : itemsList) {
                if (itemObj instanceof Map<?, ?> itemData) {
                    ShopItem shopItem = parseShopItem(itemData, entityId);
                    if (shopItem != null) {
                        shopItems.add(shopItem);
                    }
                }
            }
            shop.setItems(shopItems);
        }

        // Parse accepted items (what merchant buys)
        Object acceptsObj = data.get("accepts");
        if (acceptsObj instanceof List<?> acceptsList) {
            List<String> accepts = new ArrayList<>();
            for (Object item : acceptsList) {
                if (item instanceof String itemId) {
                    accepts.add(itemId);
                }
            }
            shop.setAccepts(accepts);
        }

        return shop;
    }

    /**
     * Parse a single shop item from YAML.
     *
     * @param data The shop item data from YAML
     * @param entityId The entity ID for error logging
     * @return ShopItem object or null if invalid
     */
    public static ShopItem parseShopItem(Map<?, ?> data, String entityId) {
        ShopItem shopItem = new ShopItem();

        // Item ID (required)
        Object itemIdObj = data.get("item_id");
        if (itemIdObj instanceof String itemId) {
            shopItem.setItemId(itemId);
        } else {
            JkVttPlugin.logger().warning("[ShopParser] Shop item missing item_id for entity " + entityId);
            return null;
        }

        // Price (uses parseCost)
        Object priceObj = data.get("price");
        Cost price = parseCost(priceObj, shopItem.getItemId());
        if (price != null) {
            shopItem.setPrice(price);
        } else {
            JkVttPlugin.logger().warning("[ShopParser] Shop item " + shopItem.getItemId() + " has invalid price for entity " + entityId);
            return null;
        }

        // Stock (default 1, -1 = unlimited)
        Object stockObj = data.get("stock");
        if (stockObj instanceof Integer stock) {
            shopItem.setStock(stock);
        } else {
            shopItem.setStock(1); // Default stock
        }

        return shopItem;
    }
}
