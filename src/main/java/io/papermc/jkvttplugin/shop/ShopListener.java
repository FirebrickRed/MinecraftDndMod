package io.papermc.jkvttplugin.shop;

import io.papermc.jkvttplugin.data.model.Cost;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.ShopConfig;
import io.papermc.jkvttplugin.data.model.ShopItem;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Listens for merchant trade events to track stock changes.
 * Issue #75 - Native Villager Trade GUI Shop System
 */
public class ShopListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger("ShopListener");
    private final Plugin plugin;

    public ShopListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles trades when player clicks result slot in merchant GUI.
     * Handles both buying (player gets item) and selling (player gets currency).
     */
    @EventHandler
    public void onMerchantTrade(InventoryClickEvent event) {
        // Only process merchant inventories
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }

        // Only process clicks on the result slot (slot 2)
        if (event.getSlot() != 2) {
            return;
        }

        // Must be a player
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Get the merchant
        Merchant merchant = merchantInventory.getMerchant();
        if (merchant == null) {
            return;
        }

        // Find the entity instance for this merchant
        DndEntityInstance entityInstance = ShopGuiUtil.getEntityForMerchant(merchant);
        if (entityInstance == null) {
            // Merchant not tracked (shouldn't happen, but gracefully ignore)
            return;
        }

        // Get the selected recipe
        MerchantRecipe selectedRecipe = merchantInventory.getSelectedRecipe();
        if (selectedRecipe == null) {
            return;
        }

        // Determine if this is a BUY (player gets item) or SELL (player gets currency) trade
        ItemStack result = selectedRecipe.getResult();
        boolean isSellTrade = isCurrencyItem(result);

        if (isSellTrade) {
            handlePlayerSell(player, entityInstance, selectedRecipe);
        } else {
            handlePlayerBuy(player, entityInstance, selectedRecipe, event);
        }
    }

    /**
     * Handles when player BUYS from merchant (merchant selling TO player).
     * Decreases merchant's stock and adds payment to merchant's currency reserves (Issue #76).
     */
    private void handlePlayerBuy(Player player, DndEntityInstance entityInstance, MerchantRecipe selectedRecipe, InventoryClickEvent event) {
        // Check if recipe is still in stock (uses >= maxUses means out of stock)
        if (selectedRecipe.getUses() >= selectedRecipe.getMaxUses()) {
            player.sendMessage(Component.text("That item is out of stock!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Find which shop item this corresponds to
        String purchasedItemId = findItemIdForResult(selectedRecipe);
        if (purchasedItemId == null) {
            LOGGER.warning("Could not identify purchased item for merchant " + entityInstance.getDisplayName());
            return;
        }

        // Calculate how many items will be purchased (shift-click buys multiple)
        // We need to delay this check until after the trade executes
        ShopConfig shopConfig = entityInstance.getShop();
        ShopItem shopItem = shopConfig.findItem(purchasedItemId);

        if (shopItem != null && !shopItem.hasUnlimitedStock()) {
            int usesBefore = selectedRecipe.getUses();

            // Schedule stock decrease for next tick (after trade executes)
            Bukkit.getScheduler().runTask(plugin, () -> {
                int usesAfter = selectedRecipe.getUses();
                int quantityPurchased = usesAfter - usesBefore;

                if (quantityPurchased > 0) {
                    int stockBefore = shopItem.getStock();
                    boolean success = shopItem.decreaseStock(quantityPurchased);
                    int stockAfter = shopItem.getStock();

                    LOGGER.info("Stock for " + purchasedItemId + ": " + stockBefore + " -> " + stockAfter + " (purchased: " + quantityPurchased + ")");

                    if (success) {
                        // Add payment to merchant's currency reserves (Issue #76)
                        Cost payment = shopItem.getPrice();
                        int totalPayment = payment.getAmount() * quantityPurchased;
                        shopConfig.addCurrency(payment.getCurrency(), totalPayment);
                        LOGGER.info("Merchant " + entityInstance.getDisplayName() + " received " + totalPayment + " " + payment.getCurrency());

                        // Save shop after stock change and currency update
                        ShopPersistenceLoader.saveShop(entityInstance.getInstanceId(), shopConfig);

                        if (shopItem.getStock() <= 0) {
                            player.sendMessage(Component.text("✓ Purchased " + purchasedItemId + " (merchant out of stock!)", NamedTextColor.GREEN));

                            // Refresh merchant GUI to remove out-of-stock item
                            refreshMerchantGui(player, entityInstance);
                        }
                    }
                }
            });
        }
    }

    /**
     * Handles when player SELLS to merchant (player selling TO merchant).
     * Checks merchant's currency reserves and deducts payment (Issue #76).
     * Adds sold item to merchant's inventory.
     */
    private void handlePlayerSell(Player player, DndEntityInstance entityInstance, MerchantRecipe selectedRecipe) {
        // Get the item being sold (first ingredient)
        if (selectedRecipe.getIngredients().isEmpty()) {
            return;
        }

        ItemStack soldItemStack = selectedRecipe.getIngredients().get(0);
        String soldItemId = ItemUtil.getItemId(soldItemStack);

        if (soldItemId == null) {
            LOGGER.warning("Could not extract item ID from sold item");
            return;
        }

        ShopConfig shopConfig = entityInstance.getShop();

        // Verify merchant accepts this item
        if (!shopConfig.acceptsItem(soldItemId)) {
            // Shouldn't happen since recipe exists, but check anyway
            player.sendMessage(Component.text("The merchant doesn't want that item.", NamedTextColor.RED));
            return;
        }

        // Extract sell price and check merchant's funds (Issue #76)
        Cost sellPrice = extractSellPrice(selectedRecipe);

        if (!shopConfig.hasCurrency(sellPrice.getCurrency(), sellPrice.getAmount())) {
            player.sendMessage(Component.text("The merchant doesn't have enough " +
                sellPrice.getCurrency() + " to buy that!", NamedTextColor.RED));
            LOGGER.info("Merchant " + entityInstance.getDisplayName() + " has insufficient funds: needs " +
                sellPrice.getAmount() + " " + sellPrice.getCurrency() + ", has " +
                shopConfig.getCurrencyAmount(sellPrice.getCurrency()));
            return;
        }

        // Deduct payment from merchant's currency reserves (Issue #76)
        boolean paymentSuccess = shopConfig.removeCurrency(sellPrice.getCurrency(), sellPrice.getAmount());
        if (!paymentSuccess) {
            player.sendMessage(Component.text("Transaction failed - merchant couldn't pay!", NamedTextColor.RED));
            return;
        }

        // Add item to merchant's inventory
        ShopItem newShopItem = new ShopItem();
        newShopItem.setItemId(soldItemId);
        newShopItem.setPrice(sellPrice);
        newShopItem.setStock(1);

        shopConfig.addOrUpdateItem(newShopItem);

        // Save shop (includes currency and inventory updates)
        ShopPersistenceLoader.saveShop(entityInstance.getInstanceId(), shopConfig);

        // Confirm to player
        player.sendMessage(Component.text("✓ Sold " + soldItemId + " for " +
            sellPrice.getAmount() + " " + sellPrice.getCurrency(), NamedTextColor.GREEN));

        LOGGER.info("Merchant " + entityInstance.getDisplayName() + " paid " +
            sellPrice.getAmount() + " " + sellPrice.getCurrency() +
            " (remaining: " + shopConfig.getCurrencyAmount(sellPrice.getCurrency()) + ")");
    }

    /**
     * Checks if an ItemStack is a currency item (gold_piece, silver_piece, etc.).
     */
    private boolean isCurrencyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String itemId = ItemUtil.getItemId(item);
        return itemId != null && itemId.endsWith("_piece");
    }

    /**
     * Extracts the sell price from a sell recipe (result is currency).
     */
    private Cost extractSellPrice(MerchantRecipe recipe) {
        ItemStack currencyResult = recipe.getResult();
        int amount = currencyResult.getAmount();

        // Extract currency type from item ID (NBT)
        String itemId = ItemUtil.getItemId(currencyResult);
        if (itemId == null) {
            return new Cost(amount, "gold"); // Default fallback
        }

        // Convert "gold_piece" -> "gold"
        String currency = itemId.replace("_piece", "");
        return new Cost(amount, currency);
    }

    /**
     * Clean up merchant tracking when GUI closes.
     */
    @EventHandler
    public void onMerchantClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }

        Merchant merchant = merchantInventory.getMerchant();
        if (merchant != null) {
            ShopGuiUtil.untrackMerchant(merchant);
        }
    }

    /**
     * Finds the item ID for a given merchant recipe result.
     * Uses NBT data to reliably identify the item.
     *
     * @param recipe The merchant recipe
     * @return The item ID, or null if not found
     */
    private String findItemIdForResult(MerchantRecipe recipe) {
        // Get item_id directly from NBT (reliable identification)
        return ItemUtil.getItemId(recipe.getResult());
    }

    /**
     * Refreshes the merchant GUI by rebuilding it with updated stock.
     * This removes out-of-stock items from the display.
     *
     * @param player The player viewing the merchant
     * @param entityInstance The merchant entity instance
     */
    private void refreshMerchantGui(Player player, DndEntityInstance entityInstance) {
        // Rebuild merchant with updated stock (out-of-stock items excluded)
        Merchant refreshedMerchant = ShopGuiUtil.createMerchant(
            entityInstance.getShop(),
            entityInstance.getDisplayName(),
            entityInstance
        );

        if (refreshedMerchant != null) {
            // Reopen merchant GUI with updated recipes
            player.openMerchant(refreshedMerchant, true);
        }
    }
}