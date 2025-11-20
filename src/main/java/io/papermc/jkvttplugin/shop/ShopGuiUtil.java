package io.papermc.jkvttplugin.shop;

import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class for creating native Minecraft merchant GUIs from shop configurations.
 * Issue #75 - Native Villager Trade GUI Shop System
 */
public class ShopGuiUtil {
    private static final Logger LOGGER = Logger.getLogger("ShopGuiUtil");

    // Track which merchant belongs to which entity instance (for stock tracking)
    private static final Map<Merchant, DndEntityInstance> activeMerchants = new HashMap<>();

    /**
     * Creates a Bukkit Merchant from a ShopConfig and entity instance.
     *
     * @param shopConfig The shop configuration to convert
     * @param merchantName The display name for the merchant
     * @param entityInstance The entity instance that owns this shop (for stock tracking)
     * @return A configured Merchant ready to open
     */
    public static Merchant createMerchant(ShopConfig shopConfig, String merchantName, DndEntityInstance entityInstance) {
        if (shopConfig == null || !shopConfig.isEnabled()) {
            LOGGER.warning("Cannot create merchant - shop is null or disabled");
            return null;
        }

        Merchant merchant = Bukkit.createMerchant(Component.text(merchantName));
        List<MerchantRecipe> recipes = new ArrayList<>();

        // Convert each ShopItem to a MerchantRecipe (merchant selling TO player)
        for (ShopItem shopItem : shopConfig.getItems()) {
            MerchantRecipe recipe = createRecipe(shopItem);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        // Add reverse recipes for items merchant accepts (player selling TO merchant)
        if (shopConfig.getAccepts() != null && !shopConfig.getAccepts().isEmpty()) {
            for (String acceptedItemId : shopConfig.getAccepts()) {
                MerchantRecipe sellRecipe = createSellRecipe(acceptedItemId, shopConfig);
                if (sellRecipe != null) {
                    recipes.add(sellRecipe);
                }
            }
        }

        merchant.setRecipes(recipes);

        // Track this merchant for stock updates
        if (entityInstance != null) {
            activeMerchants.put(merchant, entityInstance);
        }

        return merchant;
    }

    /**
     * Creates a MerchantRecipe from a ShopItem.
     *
     * @param shopItem The shop item to convert
     * @return A MerchantRecipe, or null if the item couldn't be resolved
     */
    private static MerchantRecipe createRecipe(ShopItem shopItem) {
        if (shopItem == null || !shopItem.isInStock()) {
            return null;
        }

        // Resolve the item being sold
        ItemStack itemForSale = resolveItem(shopItem.getItemId());
        if (itemForSale == null) {
            LOGGER.warning("Could not resolve item: " + shopItem.getItemId());
            return null;
        }

        // Create the currency item(s) required as payment
        ItemStack currency = createCurrencyItem(shopItem.getPrice());
        if (currency == null) {
            LOGGER.warning("Could not create currency for item: " + shopItem.getItemId());
            return null;
        }

        // Create the recipe
        MerchantRecipe recipe = new MerchantRecipe(itemForSale, 0); // 0 = no XP reward
        recipe.addIngredient(currency);

        // Set max uses based on stock (-1 = unlimited = Integer.MAX_VALUE)
        if (shopItem.hasUnlimitedStock()) {
            recipe.setMaxUses(Integer.MAX_VALUE);
        } else {
            recipe.setMaxUses(shopItem.getStock());
        }

        return recipe;
    }

    /**
     * Resolves an item ID to an ItemStack by checking weapons, armor, and items.
     *
     * @param itemId The item ID to look up
     * @return The ItemStack, or null if not found
     */
    private static ItemStack resolveItem(String itemId) {
        if (itemId == null) {
            return null;
        }

        // Check weapons
        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null) {
            return weapon.createItemStack();
        }

        // Check armor
        DndArmor armor = ArmorLoader.getArmor(itemId);
        if (armor != null) {
            return armor.createItemStack();
        }

        // Check general items
        DndItem item = ItemLoader.getItem(itemId);
        if (item != null) {
            return item.createItemStack();
        }

        // Item not found - create placeholder
        LOGGER.warning("Item not found: " + itemId + " - creating placeholder");
        return createPlaceholderItem(itemId);
    }

    /**
     * Creates a placeholder item for missing items.
     *
     * @param itemId The missing item ID
     * @return A barrier item with error lore
     */
    private static ItemStack createPlaceholderItem(String itemId) {
        ItemStack placeholder = new ItemStack(Material.BARRIER);
        placeholder.editMeta(meta -> {
            meta.displayName(Component.text(itemId, NamedTextColor.RED));
            meta.lore(List.of(
                Component.text("Item not found!", NamedTextColor.RED),
                Component.text("Item ID: " + itemId, NamedTextColor.GRAY)
            ));
        });
        return placeholder;
    }

    /**
     * Creates a currency ItemStack from a Cost object.
     *
     * @param cost The cost to convert
     * @return An ItemStack of the appropriate currency item
     */
    private static ItemStack createCurrencyItem(Cost cost) {
        if (cost == null) {
            return null;
        }

        // Map currency name to item ID
        // ToDo: do we want to normalize this instead of just doing .toLowercase
        String currencyItemId = cost.getCurrency().toLowerCase() + "_piece";

        // Look up the currency item
        DndItem currencyItem = ItemLoader.getItem(currencyItemId);
        if (currencyItem == null) {
            LOGGER.warning("Currency item not found: " + currencyItemId);
            return null;
        }

        // Create the currency ItemStack with the correct amount
        ItemStack currencyStack = currencyItem.createItemStack();
        currencyStack.setAmount(cost.getAmount());

        return currencyStack;
    }

    /**
     * Creates a reverse MerchantRecipe for items the merchant accepts (player sells TO merchant).
     * Sell price is calculated as 50% of the buy price if the item is in the shop,
     * or a default value if not.
     *
     * @param acceptedItemId The item ID the merchant will buy
     * @param shopConfig The shop configuration
     * @return A MerchantRecipe where player gives item and receives currency, or null if item can't be resolved
     */
    private static MerchantRecipe createSellRecipe(String acceptedItemId, ShopConfig shopConfig) {
        // Resolve the item player is selling
        ItemStack itemToSell = resolveItem(acceptedItemId);
        if (itemToSell == null) {
            LOGGER.warning("Could not resolve accepted item: " + acceptedItemId);
            return null;
        }

        // Calculate sell price
        Cost sellPrice = calculateSellPrice(acceptedItemId, shopConfig);
        if (sellPrice == null) {
            LOGGER.warning("Could not calculate sell price for: " + acceptedItemId);
            return null;
        }

        // Create currency player receives
        ItemStack currencyReward = createCurrencyItem(sellPrice);
        if (currencyReward == null) {
            LOGGER.warning("Could not create currency reward for: " + acceptedItemId);
            return null;
        }

        // Create reverse recipe (player gives item, gets currency)
        MerchantRecipe recipe = new MerchantRecipe(currencyReward, Integer.MAX_VALUE); // Unlimited purchases
        recipe.addIngredient(itemToSell);

        return recipe;
    }

    /**
     * Calculates the sell price for an item.
     * If item is in shop, sells for 50% of buy price.
     * Otherwise, uses default pricing based on item type.
     *
     * @param itemId The item ID
     * @param shopConfig The shop configuration
     * @return The sell price, or null if unable to calculate
     */
    private static Cost calculateSellPrice(String itemId, ShopConfig shopConfig) {
        // Check if item is in shop inventory
        ShopItem shopItem = shopConfig.findItem(itemId);
        if (shopItem != null) {
            // Sell for 50% of buy price (Issue #76 will make this configurable)
            int sellAmount = Math.max(1, shopItem.getPrice().getAmount() / 2);
            return new Cost(sellAmount, shopItem.getPrice().getCurrency());
        }

        // Item not in shop - use default pricing based on cost field from item data
        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null && weapon.getCost() != null) {
            int sellAmount = Math.max(1, weapon.getCost().getAmount() / 2);
            return new Cost(sellAmount, weapon.getCost().getCurrency());
        }

        DndArmor armor = ArmorLoader.getArmor(itemId);
        if (armor != null && armor.getCost() != null) {
            int sellAmount = Math.max(1, armor.getCost().getAmount() / 2);
            return new Cost(sellAmount, armor.getCost().getCurrency());
        }

        DndItem item = ItemLoader.getItem(itemId);
        if (item != null && item.getCost() != null) {
            int sellAmount = Math.max(1, item.getCost().getAmount() / 2);
            return new Cost(sellAmount, item.getCost().getCurrency());
        }

        // No cost found - default to 1 gold
        LOGGER.warning("No cost found for " + itemId + ", defaulting to 1 gold");
        return new Cost(1, "gold");
    }

    /**
     * Updates shop stock after a successful trade.
     * Called when a player completes a purchase.
     *
     * @param shopConfig The shop configuration
     * @param itemId The item that was purchased
     * @param amount The quantity purchased
     * @return true if stock was updated successfully
     */
    public static boolean decreaseStock(ShopConfig shopConfig, String itemId, int amount) {
        if (shopConfig == null) {
            return false;
        }

        ShopItem shopItem = shopConfig.findItem(itemId);
        if (shopItem == null) {
            return false;
        }

        return shopItem.decreaseStock(amount);
    }

    /**
     * Gets the entity instance associated with a merchant.
     *
     * @param merchant The merchant to look up
     * @return The entity instance, or null if not tracked
     */
    public static DndEntityInstance getEntityForMerchant(Merchant merchant) {
        return activeMerchants.get(merchant);
    }

    /**
     * Removes a merchant from tracking (called when GUI closes).
     *
     * @param merchant The merchant to stop tracking
     */
    public static void untrackMerchant(Merchant merchant) {
        activeMerchants.remove(merchant);
    }
}