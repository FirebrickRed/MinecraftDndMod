package io.papermc.jkvttplugin.commands;


import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.shop.ShopGuiUtil;
import io.papermc.jkvttplugin.shop.ShopPersistenceLoader;
import io.papermc.jkvttplugin.ui.menu.EntityStatBlockMenu;
import io.papermc.jkvttplugin.util.CommandUtil;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**

 DM-only command for spawning and managing entities in the world.

 Subcommands:
 - /dmentity spawn <id> [name] [x y z]
 - /dmentity list
 - /dmentity remove <name|all|type <type>|radius <distance>>
 - /dmentity teleport <name> <x y z>
 - /dmentity spawngroup <group_id>

 */
public class DmEntityCommand implements CommandExecutor, TabCompleter {

    // Resource pack namespace (configurable)
    private static final String RESOURCE_PACK_NAMESPACE = "jkvttresourcepack";

    // Track all spawned entities globally
    private static final Map<String, DndEntityInstance> spawnedEntities = new HashMap<>();
    private static int nameCounter = 0; // For handling duplicate names

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // DM permission check
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "spawn" -> handleSpawn(sender, args);
            case "list" -> handleList(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "teleport" -> handleTeleport(sender, args);
            case "info" -> handleInfo(sender, args);
            case "trade" -> handleTrade(sender, args);
            case "shop" -> handleShop(sender, args);
            case "spawngroup" -> handleSpawnGroup(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn entities.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity spawn <id> [name] [x y z]", NamedTextColor.RED));
            return;
        }

        String entityId = args[1];
        DndEntity template = EntityLoader.getEntity(entityId);

        if (template == null) {
            sender.sendMessage(Component.text("Unknown entity ID: " + entityId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Use tab completion to see available entities.", NamedTextColor.GRAY));
            return;
        }

        // Parse optional custom name
        String customName = null;
        int coordStartIndex = 2;

        // Check for quoted string first (e.g., "Marcus the Brave")
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 2);
        if (quotedResult != null) {
            customName = quotedResult.getValue();
            coordStartIndex = quotedResult.getNextIndex();
        } else if (args.length > 2 && !isCoordinate(args[2])) {
            // Single word name without quotes
            customName = args[2];
            coordStartIndex = 3;
        }

        // Parse location (default to player location if not specified)
        Location spawnLocation = player.getLocation();
        if (args.length >= coordStartIndex + 3) {
            try {
                double x = parseCoordinate(args[coordStartIndex], player.getLocation().getX());
                double y = parseCoordinate(args[coordStartIndex + 1], player.getLocation().getY());
                double z = parseCoordinate(args[coordStartIndex + 2], player.getLocation().getZ());
                spawnLocation = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
                return;
            }
        }

        // Generate name if not provided
        String finalName = generateName(template, customName);

        // Roll HP
        int maxHp = rollHitPoints(template);

        // Spawn armor stand
        ArmorStand armorStand = spawnArmorStand(template, finalName, spawnLocation);

        // Create entity instance
        DndEntityInstance instance = new DndEntityInstance(template, armorStand, finalName, maxHp);

        // Initialize shop if entity is a merchant (Issue #75)
        if (template.hasShop()) {
            ShopConfig instanceShop = cloneShop(template.getShop());
            instance.setInstanceShop(instanceShop);

            // Try to load saved shop stock
            ShopPersistenceLoader.loadShop(instance.getInstanceId(), instanceShop);
        }

        // Track entity
        String trackingKey = generateTrackingKey(finalName);
        spawnedEntities.put(trackingKey, instance);

        // Success message
        sender.sendMessage(Component.text("✓ Spawned ", NamedTextColor.GREEN)
                .append(Component.text(finalName, NamedTextColor.GOLD))
                .append(Component.text(" (" + template.getId() + ")", NamedTextColor.GRAY))
                .append(Component.text(" at " + formatLocation(spawnLocation), NamedTextColor.GRAY)));
    }

    // ==================== LIST SUBCOMMAND ====================
    private void handleList(CommandSender sender, String[] args) {
        if (spawnedEntities.isEmpty()) {
            sender.sendMessage(Component.text("No entities currently spawned.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("Spawned Entities (" + spawnedEntities.size() + "):", NamedTextColor.GOLD));

        int index = 1;
        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            DndEntityInstance instance = entry.getValue();
            Location loc = instance.getArmorStand().getLocation();

            sender.sendMessage(Component.text(index + ". ", NamedTextColor.GRAY)
                    .append(Component.text(instance.getDisplayName(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + instance.getTemplate().getId() + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" at " + formatLocation(loc), NamedTextColor.GRAY))
                    .append(Component.text(" [" + instance.getCurrentHp() + "/" + instance.getMaxHp() + " HP]", NamedTextColor.RED))
            );
            index++;
        }
    }

    // ==================== REMOVE SUBCOMMAND ====================

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity remove <name...>|all|type <type>|radius <distance>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Tip: You can remove multiple entities: /dmentity remove wolf guard Marcus", NamedTextColor.GRAY));
            return;
        }

        String target = args[1].toLowerCase();

        switch (target) {
            case "all" -> removeAll(sender);
            case "type" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dmentity remove type <creature_type>", NamedTextColor.RED));
                    return;
                }
                removeByType(sender, args[2]);
            }
            case "radius" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use radius removal.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dmentity remove radius <distance>", NamedTextColor.RED));
                    return;
                }
                try {
                    double radius = Double.parseDouble(args[2]);
                    removeByRadius(sender, player.getLocation(), radius);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid radius.", NamedTextColor.RED));
                }
            }
            default -> {
                // Multiple names support: /dmentity remove name1 name2 name3
                List<String> names = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    names.add(args[i]);
                }
                removeByNames(sender, names);
            }
        }
    }

    // ==================== TELEPORT SUBCOMMAND ====================

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport entities.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity teleport <name> [x y z]", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        int coordStartIndex;

        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 1);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
            coordStartIndex = quotedResult.getNextIndex();
        } else {
            entityName = args[1];
            coordStartIndex = 2;
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        Location newLocation;

        if (args.length >= coordStartIndex + 3) {
            // Coordinates provided
            try {
                Location currentLoc = instance.getArmorStand().getLocation();
                double x = parseCoordinate(args[coordStartIndex], currentLoc.getX());
                double y = parseCoordinate(args[coordStartIndex + 1], currentLoc.getY());
                double z = parseCoordinate(args[coordStartIndex + 2], currentLoc.getZ());
                newLocation = new Location(currentLoc.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
                return;
            }
        } else {
            // No coordinates - teleport to DM's location
            newLocation = player.getLocation();
        }

        instance.getArmorStand().teleport(newLocation);

        sender.sendMessage(Component.text("✓ Teleported ", NamedTextColor.GREEN)
                .append(Component.text(instance.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" to " + formatLocation(newLocation), NamedTextColor.GRAY)));
    }

    // ==================== INFO SUBCOMMAND ====================

    private void handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view stat blocks.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity info <name>", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 1);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
        } else {
            entityName = args[1];
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        // Open stat block menu
        EntityStatBlockMenu.open(player, instance);
    }

    // ==================== TRADE SUBCOMMAND ====================

    private void handleTrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can trade with entities.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity trade <name>", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 1);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
        } else {
            entityName = args[1];
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        // Check if entity has a shop
        if (!instance.getTemplate().hasShop()) {
            sender.sendMessage(Component.text(instance.getDisplayName() + " is not a merchant.", NamedTextColor.RED));
            return;
        }

        // Create and open merchant GUI (uses instance shop with per-instance stock)
        Merchant merchant = ShopGuiUtil.createMerchant(
            instance.getShop(),
            instance.getDisplayName(),
            instance
        );

        if (merchant == null) {
            sender.sendMessage(Component.text("Failed to create merchant GUI.", NamedTextColor.RED));
            return;
        }

        player.openMerchant(merchant, true);
    }

    // ==================== SHOP SUBCOMMAND ====================

    private void handleShop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity shop <view|restock|add> <entity> ...", NamedTextColor.RED));
            return;
        }

        String shopAction = args[1].toLowerCase();

        switch (shopAction) {
            case "view" -> handleShopView(sender, args);
            case "restock" -> handleShopRestock(sender, args);
            case "add" -> handleShopAdd(sender, args);
            default -> sender.sendMessage(Component.text("Unknown shop action. Use: view, restock, or add", NamedTextColor.RED));
        }
    }

    /**
     * /dmentity shop view <entity>
     * Shows current stock for all items in the merchant's shop.
     */
    private void handleShopView(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dmentity shop view <entity>", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 2);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
        } else {
            entityName = args[2];
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        if (!instance.getTemplate().hasShop()) {
            sender.sendMessage(Component.text(instance.getDisplayName() + " is not a merchant.", NamedTextColor.RED));
            return;
        }

        ShopConfig shop = instance.getShop();
        sender.sendMessage(Component.text("=== " + instance.getDisplayName() + "'s Shop ===", NamedTextColor.GOLD));

        if (shop.getItems() == null || shop.getItems().isEmpty()) {
            sender.sendMessage(Component.text("Shop is empty.", NamedTextColor.GRAY));
            return;
        }

        for (ShopItem item : shop.getItems()) {
            String stockDisplay = item.hasUnlimitedStock()
                ? "∞"
                : item.getStock() + "/" + instance.getTemplate().getShop().findItem(item.getItemId()).getStock();

            String priceDisplay = item.getPrice().getAmount() + " " + item.getPrice().getCurrency();

            Component line = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(item.getItemId(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(priceDisplay, NamedTextColor.GOLD))
                    .append(Component.text(" [Stock: ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(stockDisplay, item.isInStock() ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY));

            sender.sendMessage(line);
        }
    }

    /**
     * /dmentity shop restock <entity> <item_id> [amount]
     * Restocks an item to specified amount (or template default if not specified).
     */
    private void handleShopRestock(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /dmentity shop restock <entity> <item_id> [amount]", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        int nextArgIndex;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 2);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
            nextArgIndex = quotedResult.getNextIndex();
        } else {
            entityName = args[2];
            nextArgIndex = 3;
        }

        if (args.length < nextArgIndex + 1) {
            sender.sendMessage(Component.text("Usage: /dmentity shop restock <entity> <item_id> [amount]", NamedTextColor.RED));
            return;
        }

        String itemId = args[nextArgIndex];
        Integer restockAmount = null;

        if (args.length >= nextArgIndex + 2) {
            try {
                restockAmount = Integer.parseInt(args[nextArgIndex + 1]);
                if (restockAmount < -1) {
                    sender.sendMessage(Component.text("Amount must be -1 (unlimited) or >= 0", NamedTextColor.RED));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[nextArgIndex + 1], NamedTextColor.RED));
                return;
            }
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        if (!instance.getTemplate().hasShop()) {
            sender.sendMessage(Component.text(instance.getDisplayName() + " is not a merchant.", NamedTextColor.RED));
            return;
        }

        ShopConfig shop = instance.getShop();
        ShopItem shopItem = shop.findItem(itemId);

        if (shopItem == null) {
            sender.sendMessage(Component.text("Item not found in shop: " + itemId, NamedTextColor.RED));
            return;
        }

        // Determine restock amount
        int newStock;
        if (restockAmount != null) {
            newStock = restockAmount;
        } else {
            // Restore to template default
            ShopItem templateItem = instance.getTemplate().getShop().findItem(itemId);
            newStock = templateItem != null ? templateItem.getStock() : 0;
        }

        shopItem.setStock(newStock);

        // Save shop
        ShopPersistenceLoader.saveShop(instance.getInstanceId(), shop);

        String stockDisplay = newStock == -1 ? "unlimited" : String.valueOf(newStock);
        sender.sendMessage(Component.text("✓ Restocked ", NamedTextColor.GREEN)
                .append(Component.text(itemId, NamedTextColor.GOLD))
                .append(Component.text(" to " + stockDisplay, NamedTextColor.GREEN)));
    }

    /**
     * /dmentity shop add <entity> <item_id> <price_amount> <price_currency> [stock]
     * Adds a new item to the shop.
     */
    private void handleShopAdd(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(Component.text("Usage: /dmentity shop add <entity> <item_id> <price_amount> <price_currency> [stock]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Example: /dmentity shop add balin healing_potion 50 gold 10", NamedTextColor.GRAY));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        int nextArgIndex;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 2);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
            nextArgIndex = quotedResult.getNextIndex();
        } else {
            entityName = args[2];
            nextArgIndex = 3;
        }

        if (args.length < nextArgIndex + 3) {
            sender.sendMessage(Component.text("Usage: /dmentity shop add <entity> <item_id> <price_amount> <price_currency> [stock]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Example: /dmentity shop add balin healing_potion 50 gold 10", NamedTextColor.GRAY));
            return;
        }

        String itemId = args[nextArgIndex];
        int priceAmount;
        String priceCurrency = args[nextArgIndex + 2];
        int stock = -1; // Default unlimited

        try {
            priceAmount = Integer.parseInt(args[nextArgIndex + 1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid price amount: " + args[nextArgIndex + 1], NamedTextColor.RED));
            return;
        }

        if (args.length >= nextArgIndex + 4) {
            try {
                stock = Integer.parseInt(args[nextArgIndex + 3]);
                if (stock < -1) {
                    sender.sendMessage(Component.text("Stock must be -1 (unlimited) or >= 0", NamedTextColor.RED));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid stock: " + args[nextArgIndex + 3], NamedTextColor.RED));
                return;
            }
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        if (!instance.getTemplate().hasShop()) {
            sender.sendMessage(Component.text(instance.getDisplayName() + " is not a merchant.", NamedTextColor.RED));
            return;
        }

        // Verify item exists in data
        if (resolveItemForShop(itemId) == null) {
            sender.sendMessage(Component.text("Unknown item ID: " + itemId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Item must exist in weapons, armor, or items data.", NamedTextColor.GRAY));
            return;
        }

        ShopConfig shop = instance.getShop();

        // Check if item already exists
        if (shop.findItem(itemId) != null) {
            sender.sendMessage(Component.text("Item already exists in shop. Use 'restock' to change stock.", NamedTextColor.RED));
            return;
        }

        // Create new shop item
        ShopItem newItem = new ShopItem();
        newItem.setItemId(itemId);
        newItem.setPrice(new Cost(priceAmount, priceCurrency));
        newItem.setStock(stock);

        // Add to shop
        if (shop.getItems() == null) {
            shop.setItems(new ArrayList<>());
        }
        shop.getItems().add(newItem);

        // Save shop
        ShopPersistenceLoader.saveShop(instance.getInstanceId(), shop);

        String stockDisplay = stock == -1 ? "unlimited" : String.valueOf(stock);
        sender.sendMessage(Component.text("✓ Added ", NamedTextColor.GREEN)
                .append(Component.text(itemId, NamedTextColor.GOLD))
                .append(Component.text(" to shop (", NamedTextColor.GREEN))
                .append(Component.text(priceAmount + " " + priceCurrency, NamedTextColor.YELLOW))
                .append(Component.text(", stock: " + stockDisplay + ")", NamedTextColor.GREEN)));
    }

    /**
     * Helper method to verify an item exists in the data loaders.
     * Returns true if item exists in weapons, armor, or items.
     */
    private Object resolveItemForShop(String itemId) {
        if (WeaponLoader.getWeapon(itemId) != null) return WeaponLoader.getWeapon(itemId);
        if (ArmorLoader.getArmor(itemId) != null) return ArmorLoader.getArmor(itemId);
        if (ItemLoader.getItem(itemId) != null) return ItemLoader.getItem(itemId);
        return null;
    }

    // ==================== SPAWNGROUP SUBCOMMAND ====================

    private void handleSpawnGroup(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Group spawning not yet implemented. (Issue #79)", NamedTextColor.YELLOW));
        // TODO: Implement group spawning in Issue #79
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate entity name from template or custom name.
     */
    private String generateName(DndEntity template, String customName) {
        if (customName != null) {
            return customName;
        }

        // Random name from pool
        List<String> randomNames = template.getRandomNames();
        if (randomNames != null && !randomNames.isEmpty()) {
            return randomNames.get(new Random().nextInt(randomNames.size()));
        }

        // Fallback to template name
        return template.getName();
    }

    /**
     * Generate unique tracking key for entity (handles duplicates).
     */
    private String generateTrackingKey(String name) {
        String baseKey = name.toLowerCase();
        if (!spawnedEntities.containsKey(baseKey)) {
            return baseKey;
        }

        // Name conflict - add counter
        nameCounter++;
        return baseKey + "#" + nameCounter;
    }

    /**
     * Roll hit points for entity based on hit_dice or hit_points.
     * Priority: hit_dice > hit_points > default (10)
     */
    private int rollHitPoints(DndEntity template) {
        if (template.getHitDice() != null) {
            // Roll hit dice
            return DiceRoller.parseDiceRoll(template.getHitDice());
        } else if (template.getHitPoints() != null) {
            // Use fixed HP
            return template.getHitPoints();
        } else {
            // Default
            return 10;
        }
    }

    /**
     * Spawn armor stand with resource pack model.
     */
    private ArmorStand spawnArmorStand(DndEntity template, String name, Location location) {
        ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

        // Set name
        armorStand.customName(Component.text(name));
        armorStand.setCustomNameVisible(true);

        // Configure armor stand
        armorStand.setGravity(false);
        armorStand.setInvisible(true);
        armorStand.setMarker(false);

        // Apply resource pack model if specified
        if (template.getModel() != null) {
            ItemStack modelItem = new ItemStack(Material.STICK);
            ItemMeta meta = modelItem.getItemMeta();
            meta.setItemModel(new NamespacedKey(RESOURCE_PACK_NAMESPACE, template.getModel()));
            meta.displayName(Component.text(name));
            modelItem.setItemMeta(meta);
            armorStand.getEquipment().setHelmet(modelItem);
        }

        return armorStand;
    }

    /**
     * Find entity by name (case-insensitive, handles #suffix).
     */
    private DndEntityInstance findEntity(String name) {
        String normalized = name.toLowerCase();

        // Exact match
        if (spawnedEntities.containsKey(normalized)) {
            return spawnedEntities.get(normalized);
        }

        // Partial match (ignoring #suffix)
        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            if (entry.getKey().startsWith(normalized)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Remove all entities.
     */
    private void removeAll(CommandSender sender) {
        int count = spawnedEntities.size();
        for (DndEntityInstance instance : spawnedEntities.values()) {
            // Save shop before removing (Issue #75)
            if (instance.getShop() != null) {
                ShopPersistenceLoader.saveShop(instance.getInstanceId(), instance.getShop());
            }
            instance.getArmorStand().remove();
            instance.unregister();
        }
        spawnedEntities.clear();

        sender.sendMessage(Component.text("✓ Removed " + count + " entities.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities by creature type.
     */
    private void removeByType(CommandSender sender, String creatureType) {
        List<String> toRemove = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            if (creatureType.equalsIgnoreCase(entry.getValue().getTemplate().getCreatureType())) {
                // Save shop before removing (Issue #75)
                if (entry.getValue().getShop() != null) {
                    ShopPersistenceLoader.saveShop(entry.getValue().getInstanceId(), entry.getValue().getShop());
                }
                entry.getValue().getArmorStand().remove();
                entry.getValue().unregister();
                toRemove.add(entry.getKey());
                count++;
            }
        }

        toRemove.forEach(spawnedEntities::remove);

        sender.sendMessage(Component.text("✓ Removed " + count + " entities of type '" + creatureType + "'.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities within radius.
     */
    private void removeByRadius(CommandSender sender, Location center, double radius) {
        List<String> toRemove = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            Location entityLoc = entry.getValue().getArmorStand().getLocation();
            if (entityLoc.distance(center) <= radius) {
                // Save shop before removing (Issue #75)
                if (entry.getValue().getShop() != null) {
                    ShopPersistenceLoader.saveShop(entry.getValue().getInstanceId(), entry.getValue().getShop());
                }
                entry.getValue().getArmorStand().remove();
                entry.getValue().unregister();
                toRemove.add(entry.getKey());
                count++;
            }
        }

        toRemove.forEach(spawnedEntities::remove);

        sender.sendMessage(Component.text("✓ Removed " + count + " entities within " + radius + " blocks.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities by multiple names.
     */
    private void removeByNames(CommandSender sender, List<String> names) {
        int count = 0;

        for (String name : names) {
            DndEntityInstance instance = findEntity(name);
            if (instance != null) {
                String key = spawnedEntities.entrySet().stream()
                        .filter(e -> e.getValue() == instance)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);

                if (key != null) {
                    // Save shop before removing (Issue #75)
                    if (instance.getShop() != null) {
                        ShopPersistenceLoader.saveShop(instance.getInstanceId(), instance.getShop());
                    }
                    instance.getArmorStand().remove();
                    instance.unregister();
                    spawnedEntities.remove(key);
                    count++;
                }
            }
        }

        sender.sendMessage(Component.text("✓ Removed " + count + " entities.", NamedTextColor.GREEN));
    }

    /**
     * Parse coordinate with relative support (~).
     */
    private double parseCoordinate(String arg, double current) {
        if (arg.startsWith("~")) {
            if (arg.length() == 1) {
                return current;
            }
            return current + Double.parseDouble(arg.substring(1));
        }
        return Double.parseDouble(arg);
    }

    /**
     * Check if string is a coordinate (number or ~).
     */
    private boolean isCoordinate(String arg) {
        return arg.startsWith("~") || arg.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Format location as string.
     */
    private String formatLocation(Location loc) {
        return String.format("(%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Send help message.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DM Entity Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dmentity spawn <id> [name] [x y z]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Spawn a single entity at location", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - List all spawned entities", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity remove <name...>|all|type <type>|radius <distance>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Remove one or more entities (supports multiple names)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity teleport <name> [x y z]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Teleport entity to location", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity info <name>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - View entity stat block", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity trade <name>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Open merchant trade GUI", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity shop <view|restock|add> <entity> ...", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Manage merchant shop inventory", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity spawngroup <group_id>", NamedTextColor.DARK_GRAY)
                .append(Component.text(" (Coming in Issue #79)", NamedTextColor.DARK_GRAY)));
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            // Subcommands
            return List.of("spawn", "list", "remove", "teleport", "info", "trade", "shop", "spawngroup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "spawn":
                    // Suggest entity IDs
                    return EntityLoader.getAllEntities().stream()
                            .map(DndEntity::getId)
                            .filter(id -> id.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "remove":
                    // Suggest entity names + special keywords
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("all");
                    suggestions.add("type");
                    suggestions.add("radius");
                    suggestions.addAll(spawnedEntities.values().stream()
                            .map(DndEntityInstance::getDisplayName)
                            .toList());
                    return suggestions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "teleport":
                case "info":
                case "trade":
                    // Suggest spawned entity names
                    return spawnedEntities.values().stream()
                            .map(DndEntityInstance::getDisplayName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "shop":
                    // Suggest shop actions
                    return List.of("view", "restock", "add").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("type")) {
                // Suggest creature types
                return EntityLoader.getAllEntities().stream()
                        .map(DndEntity::getCreatureType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .filter(type -> type.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("shop")) {
                // Suggest merchant names
                return spawnedEntities.values().stream()
                        .filter(e -> e.getTemplate().hasShop())
                        .map(DndEntityInstance::getDisplayName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("shop")) {
                String shopAction = args[1].toLowerCase();

                if (shopAction.equals("restock")) {
                    // Suggest item IDs from merchant's shop
                    DndEntityInstance merchant = findEntity(args[2]);
                    if (merchant != null && merchant.getShop() != null && merchant.getShop().getItems() != null) {
                        return merchant.getShop().getItems().stream()
                                .map(ShopItem::getItemId)
                                .filter(id -> id.toLowerCase().startsWith(args[3].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                } else if (shopAction.equals("add")) {
                    // Suggest all available item IDs (weapons, armor, items)
                    return getAllItemIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("shop") && args[1].equalsIgnoreCase("add")) {
            // Suggest currency types
            // ToDo: update
            return List.of("gold", "silver", "copper", "platinum", "electrum").stream()
                    .filter(c -> c.toLowerCase().startsWith(args[5].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    /**
     * Gets all available item IDs from weapons, armor, and items.
     */
    private List<String> getAllItemIds() {
        List<String> allIds = new ArrayList<>();

        // Add weapons
        allIds.addAll(WeaponLoader.getAllWeapons().stream()
                .map(DndWeapon::getId)
                .collect(Collectors.toList()));

        // Add armor
        allIds.addAll(ArmorLoader.getAllArmors().stream()
                .map(DndArmor::getId)
                .collect(Collectors.toList()));

        // Add items
        allIds.addAll(ItemLoader.getAllItems().stream()
                .map(DndItem::getId)
                .collect(Collectors.toList()));

        return allIds;
    }

    /**
     * Clone shop configuration for per-instance shops.
     * Creates a deep copy so each spawned merchant has independent stock.
     * Issue #75 - Shop System
     */
    private ShopConfig cloneShop(ShopConfig template) {
        if (template == null) {
            return null;
        }

        ShopConfig clone = new ShopConfig();
        clone.setEnabled(template.isEnabled());

        // Clone shop items (deep copy so stock is independent)
        if (template.getItems() != null) {
            List<ShopItem> clonedItems = new ArrayList<>();
            for (ShopItem item : template.getItems()) {
                ShopItem clonedItem = new ShopItem();
                clonedItem.setItemId(item.getItemId());
                clonedItem.setPrice(item.getPrice()); // Cost is immutable, no need to clone
                clonedItem.setStock(item.getStock());
                clonedItems.add(clonedItem);
            }
            clone.setItems(clonedItems);
        }

        // Clone accepts list
        if (template.getAccepts() != null) {
            clone.setAccepts(new ArrayList<>(template.getAccepts()));
        }

        return clone;
    }

    // ==================== PUBLIC API ====================

    /**
     * Get all spawned entities (for persistence system).
     */
    public static Collection<DndEntityInstance> getAllSpawnedEntities() {
        return Collections.unmodifiableCollection(spawnedEntities.values());
    }

    /**
     * Clear all spawned entities (for persistence loading).
     */
    public static void clearAllSpawnedEntities() {
        spawnedEntities.clear();
    }
}
