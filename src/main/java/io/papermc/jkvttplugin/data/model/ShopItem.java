package io.papermc.jkvttplugin.data.model;

/**
 * Represents a single item for sale in a merchant's shop.
 *
 * YAML format:
 * - item_id: longsword
 *   price:
 *     amount: 15
 *     currency: gold  # Optional, defaults to gold
 *   stock: 5
 */
public class ShopItem {
    private String itemId;    // References weapon/armor/item ID
    private Cost price;       // Merchant's selling price (can differ from item's base cost)
    private int stock;        // Available quantity (-1 = unlimited)

    public ShopItem() {
        this.stock = 1;  // Default to 1 in stock
    }

    public ShopItem(String itemId, Cost price, int stock) {
        this.itemId = itemId;
        this.price = price;
        this.stock = stock;
    }

    // ==================== GETTERS & SETTERS ====================

    public String getItemId() {
        return itemId;
    }
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Cost getPrice() {
        return price;
    }
    public void setPrice(Cost price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }
    public void setStock(int stock) {
        this.stock = stock;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Check if item is in stock.
     */
    public boolean isInStock() {
        return stock == -1 || stock > 0;  // -1 = unlimited
    }

    /**
     * Check if item has unlimited stock.
     */
    public boolean hasUnlimitedStock() {
        return stock == -1;
    }

    /**
     * Decrease stock by specified amount.
     * @return true if successful, false if insufficient stock
     */
    public boolean decreaseStock(int amount) {
        if (hasUnlimitedStock()) {
            return true;  // Unlimited stock never depletes
        }

        if (stock < amount) {
            return false;  // Not enough stock
        }

        stock -= amount;
        return true;
    }

    /**
     * Increase stock by specified amount.
     * No-op if unlimited stock.
     */
    public void increaseStock(int amount) {
        if (!hasUnlimitedStock()) {
            stock += amount;
        }
    }

    @Override
    public String toString() {
        String stockStr = hasUnlimitedStock() ? "unlimited" : String.valueOf(stock);
        return itemId + ": " + price + " (" + stockStr + " in stock)";
    }
}