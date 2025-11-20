package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a merchant shop configuration for an entity.
 *
 * YAML format:
 * shop:
 *   enabled: true
 *   items:
 *     - item_id: longsword
 *       price:
 *         amount: 15
 *         currency: gold
 *       stock: 5
 *     - item_id: bread
 *       price:
 *         amount: 5
 *         currency: copper
 *       stock: 20
 *   accepts:
 *     - longsword
 *     - dagger
 */
public class ShopConfig {
    private boolean enabled;
    private List<ShopItem> items;
    private List<String> accepts;  // Item IDs merchant will buy from players

    // Future fields (Issue #76 - Price Adjustments):
    // private double baseBuyMultiplier = 1.0;
    // private double baseSellMultiplier = 0.5;

    public ShopConfig() {
        this.enabled = true;
        this.items = new ArrayList<>();
        this.accepts = new ArrayList<>();
    }

    // ==================== GETTERS & SETTERS ====================

    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ShopItem> getItems() {
        return items;
    }
    public void setItems(List<ShopItem> items) {
        this.items = items;
    }

    public List<String> getAccepts() {
        return accepts;
    }
    public void setAccepts(List<String> accepts) {
        this.accepts = accepts;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Find a shop item by item ID.
     * @param itemId The item ID to search for
     * @return The ShopItem or null if not found
     */
    public ShopItem findItem(String itemId) {
        if (items == null) return null;

        return items.stream()
                .filter(item -> itemId.equalsIgnoreCase(item.getItemId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if merchant accepts this item for buying from players.
     * @param itemId The item ID to check
     * @return true if merchant will buy this item
     */
    public boolean acceptsItem(String itemId) {
        return accepts != null && accepts.stream()
                .anyMatch(id -> id.equalsIgnoreCase(itemId));
    }

    /**
     * Add a new item to the shop or update existing stock.
     * @param shopItem The item to add
     */
    public void addOrUpdateItem(ShopItem shopItem) {
        ShopItem existing = findItem(shopItem.getItemId());
        if (existing != null) {
            // Update existing item
            existing.setPrice(shopItem.getPrice());
            existing.setStock(existing.getStock() + shopItem.getStock());
        } else {
            // Add new item
            items.add(shopItem);
        }
    }

    /**
     * Get count of items available for sale.
     */
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    /**
     * Check if shop has any items for sale.
     */
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }
}