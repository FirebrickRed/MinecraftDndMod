package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Issue #76 - Merchant Currency Tracking & Price Adjustments
    private Map<String, Integer> currency;  // Merchant's currency reserves (gold, silver, copper, etc.)
    private double baseBuyMultiplier = 1.0;  // Price multiplier when merchant sells TO player
    private double baseSellMultiplier = 0.5; // Price multiplier when merchant buys FROM player

    // Issue #76 - DM Price Adjustments
    private double globalDiscount = 0.0;  // 0.0 to 1.0 (e.g., 0.20 = 20% off)
    private double globalMarkup = 0.0;    // 0.0+ (e.g., 0.15 = 15% increase)
    private Map<String, Integer> itemPriceOverrides;  // item_id -> fixed price (ignores multipliers)

    public ShopConfig() {
        this.enabled = true;
        this.items = new ArrayList<>();
        this.accepts = new ArrayList<>();
        this.currency = new HashMap<>();
        this.itemPriceOverrides = new HashMap<>();

        // Initialize with default starting funds (100 gold) - Issue #76
        this.currency.put("gold", 100);
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

    // ==================== CURRENCY TRACKING (Issue #76) ====================

    public Map<String, Integer> getCurrency() {
        return currency;
    }

    public void setCurrency(Map<String, Integer> currency) {
        this.currency = currency != null ? currency : new HashMap<>();
    }

    /**
     * Get merchant's currency amount for a specific type.
     * Supports both standard D&D currencies and custom currencies.
     *
     * @param currencyType The currency type (gold, silver, copper, etc.)
     * @return The amount, or 0 if not found
     */
    public int getCurrencyAmount(String currencyType) {
        return currency.getOrDefault(currencyType.toLowerCase(), 0);
    }

    /**
     * Set merchant's currency amount for a specific type.
     *
     * @param currencyType The currency type
     * @param amount The amount to set (negative values become 0)
     */
    public void setCurrencyAmount(String currencyType, int amount) {
        currency.put(currencyType.toLowerCase(), Math.max(0, amount));
    }

    /**
     * Add currency to merchant's reserves.
     *
     * @param currencyType The currency type
     * @param amount The amount to add
     */
    public void addCurrency(String currencyType, int amount) {
        int current = getCurrencyAmount(currencyType);
        setCurrencyAmount(currencyType, current + amount);
    }

    /**
     * Remove currency from merchant's reserves.
     *
     * @param currencyType The currency type
     * @param amount The amount to remove
     * @return true if successful, false if insufficient funds
     */
    public boolean removeCurrency(String currencyType, int amount) {
        int current = getCurrencyAmount(currencyType);
        if (current < amount) {
            return false; // Insufficient funds
        }
        setCurrencyAmount(currencyType, current - amount);
        return true;
    }

    /**
     * Check if merchant has enough currency.
     *
     * @param currencyType The currency type
     * @param amount The amount needed
     * @return true if merchant has enough funds
     */
    public boolean hasCurrency(String currencyType, int amount) {
        return getCurrencyAmount(currencyType) >= amount;
    }

    // ==================== PRICE MULTIPLIERS (Issue #76) ====================

    public double getBaseBuyMultiplier() {
        return baseBuyMultiplier;
    }

    public void setBaseBuyMultiplier(double baseBuyMultiplier) {
        this.baseBuyMultiplier = Math.max(0.0, baseBuyMultiplier);
    }

    public double getBaseSellMultiplier() {
        return baseSellMultiplier;
    }

    public void setBaseSellMultiplier(double baseSellMultiplier) {
        this.baseSellMultiplier = Math.max(0.0, baseSellMultiplier);
    }

    /**
     * Calculate the price merchant will pay for an item (buying FROM player).
     * Applies the baseSellMultiplier to the base price.
     *
     * @param basePrice The item's base price
     * @return The calculated sell price
     */
    public Cost calculateSellPrice(Cost basePrice) {
        int adjustedAmount = (int) Math.ceil(basePrice.getAmount() * baseSellMultiplier);
        return new Cost(Math.max(1, adjustedAmount), basePrice.getCurrency());
    }

    /**
     * Calculate the price merchant will charge for an item (selling TO player).
     * Applies the baseBuyMultiplier to the base price.
     * NOTE: This is the BASE calculation. Use getEffectivePrice() for DM adjustments.
     *
     * @param basePrice The item's base price
     * @return The calculated buy price
     */
    public Cost calculateBuyPrice(Cost basePrice) {
        int adjustedAmount = (int) Math.ceil(basePrice.getAmount() * baseBuyMultiplier);
        return new Cost(Math.max(1, adjustedAmount), basePrice.getCurrency());
    }

    // ==================== DM PRICE ADJUSTMENTS (Issue #76) ====================

    public double getGlobalDiscount() {
        return globalDiscount;
    }

    /**
     * Set global discount for this shop.
     * @param discount 0.0 to 1.0 (e.g., 0.20 = 20% off)
     */
    public void setGlobalDiscount(double discount) {
        this.globalDiscount = Math.max(0.0, Math.min(1.0, discount));
    }

    public double getGlobalMarkup() {
        return globalMarkup;
    }

    /**
     * Set global markup for this shop.
     * @param markup 0.0+ (e.g., 0.15 = 15% price increase)
     */
    public void setGlobalMarkup(double markup) {
        this.globalMarkup = Math.max(0.0, markup);
    }

    public Map<String, Integer> getItemPriceOverrides() {
        return itemPriceOverrides;
    }

    public void setItemPriceOverrides(Map<String, Integer> overrides) {
        this.itemPriceOverrides = overrides != null ? overrides : new HashMap<>();
    }

    /**
     * Set a fixed price override for a specific item.
     * This completely overrides all multipliers and discounts.
     *
     * @param itemId The item ID
     * @param price The fixed price (in base currency units)
     */
    public void setItemPriceOverride(String itemId, int price) {
        if (itemPriceOverrides == null) {
            itemPriceOverrides = new HashMap<>();
        }
        itemPriceOverrides.put(itemId.toLowerCase(), Math.max(1, price));
    }

    /**
     * Remove a price override for a specific item.
     *
     * @param itemId The item ID
     * @return true if override was removed, false if it didn't exist
     */
    public boolean removeItemPriceOverride(String itemId) {
        if (itemPriceOverrides == null) return false;
        return itemPriceOverrides.remove(itemId.toLowerCase()) != null;
    }

    /**
     * Check if an item has a price override.
     *
     * @param itemId The item ID
     * @return true if item has override
     */
    public boolean hasItemPriceOverride(String itemId) {
        return itemPriceOverrides != null && itemPriceOverrides.containsKey(itemId.toLowerCase());
    }

    /**
     * Get the price override for an item.
     *
     * @param itemId The item ID
     * @return The override price, or null if no override exists
     */
    public Integer getItemPriceOverride(String itemId) {
        if (itemPriceOverrides == null) return null;
        return itemPriceOverrides.get(itemId.toLowerCase());
    }

    /**
     * Reset all DM price adjustments (discount, markup, overrides).
     */
    public void resetAllAdjustments() {
        this.globalDiscount = 0.0;
        this.globalMarkup = 0.0;
        if (this.itemPriceOverrides != null) {
            this.itemPriceOverrides.clear();
        }
    }

    /**
     * Check if shop has any active DM adjustments.
     *
     * @return true if discount, markup, or overrides are active
     */
    public boolean hasActiveAdjustments() {
        return globalDiscount > 0.0 ||
               globalMarkup > 0.0 ||
               (itemPriceOverrides != null && !itemPriceOverrides.isEmpty());
    }

    /**
     * Get the effective price for an item, applying all adjustments.
     * Priority: 1) Item override (ignores all else)
     *           2) Base price * (1 - discount) * (1 + markup) * buyMultiplier
     *
     * @param itemId The item ID
     * @param basePrice The item's base price from ShopItem
     * @return The effective price after all adjustments
     */
    public Cost getEffectivePrice(String itemId, Cost basePrice) {
        // Check for item-specific override first (ignores all multipliers)
        Integer override = getItemPriceOverride(itemId);
        if (override != null) {
            return new Cost(override, basePrice.getCurrency());
        }

        // Apply stacking: base * (1 - discount) * (1 + markup) * buyMultiplier
        double adjustedAmount = basePrice.getAmount();
        adjustedAmount = adjustedAmount * (1.0 - globalDiscount);  // Apply discount
        adjustedAmount = adjustedAmount * (1.0 + globalMarkup);    // Apply markup
        adjustedAmount = adjustedAmount * baseBuyMultiplier;       // Apply base multiplier

        return new Cost(Math.max(1, (int) Math.ceil(adjustedAmount)), basePrice.getCurrency());
    }
}