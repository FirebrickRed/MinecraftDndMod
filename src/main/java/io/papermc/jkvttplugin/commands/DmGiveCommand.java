package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DM-only command for giving D&D items to players.
 * Useful for testing shops, rewarding players, and debugging.
 *
 * Usage:
 * /dmgive <player> <item_id> [amount]
 * /dmgive <item_id> [amount] - Give to self
 */
public class DmGiveCommand implements CommandExecutor, TabCompleter {

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

        // Parse arguments: /dmgive <player> <item_id> [amount] OR /dmgive <item_id> [amount]
        Player targetPlayer;
        String itemId;
        int amount = 1;

        if (args.length >= 2) {
            // Check if first arg is a player name
            Player possiblePlayer = Bukkit.getPlayer(args[0]);
            if (possiblePlayer != null) {
                // Format: /dmgive <player> <item_id> [amount]
                targetPlayer = possiblePlayer;
                itemId = args[1];
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
                        return true;
                    }
                }
            } else {
                // Format: /dmgive <item_id> [amount] (give to self)
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("Console must specify a player name.", NamedTextColor.RED));
                    return true;
                }
                targetPlayer = (Player) sender;
                itemId = args[0];
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                }
            }
        } else {
            // Format: /dmgive <item_id> (give 1 to self)
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Usage: /dmgive <player> <item_id> [amount]", NamedTextColor.RED));
                return true;
            }
            targetPlayer = (Player) sender;
            itemId = args[0];
        }

        // Validate amount
        if (amount <= 0 || amount > 64) {
            sender.sendMessage(Component.text("Amount must be between 1 and 64.", NamedTextColor.RED));
            return true;
        }

        // Try to resolve item from weapons, armor, or items
        ItemStack itemStack = resolveItem(itemId, amount);

        if (itemStack == null) {
            sender.sendMessage(Component.text("Unknown item: " + itemId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Use tab completion to see available items.", NamedTextColor.GRAY));
            return true;
        }

        // Give item to player
        targetPlayer.getInventory().addItem(itemStack);

        // Success message
        Component itemName = itemStack.displayName();
        sender.sendMessage(Component.text("✓ Gave ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x ", NamedTextColor.GOLD))
                .append(itemName)
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(targetPlayer.getName(), NamedTextColor.GOLD)));

        if (targetPlayer != sender) {
            targetPlayer.sendMessage(Component.text("You received ", NamedTextColor.GREEN)
                    .append(Component.text(amount + "x ", NamedTextColor.GOLD))
                    .append(itemName));
        }

        return true;
    }

    /**
     * Resolves an item ID to an ItemStack by checking weapons, armor, and items.
     *
     * @param itemId The item ID to look up
     * @param amount The stack size
     * @return The ItemStack, or null if not found
     */
    private ItemStack resolveItem(String itemId, int amount) {
        // Check weapons
        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null) {
            ItemStack stack = weapon.createItemStack();
            stack.setAmount(amount);
            return stack;
        }

        // Check armor
        DndArmor armor = ArmorLoader.getArmor(itemId);
        if (armor != null) {
            ItemStack stack = armor.createItemStack();
            stack.setAmount(amount);
            return stack;
        }

        // Check general items
        DndItem item = ItemLoader.getItem(itemId);
        if (item != null) {
            ItemStack stack = item.createItemStack();
            stack.setAmount(amount);
            return stack;
        }

        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DM Give Command ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dmgive <player> <item_id> [amount]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Give D&D item to a player", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmgive <item_id> [amount]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Give item to yourself", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Examples:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /dmgive gold_piece 64", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /dmgive charlie longsword 1", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /dmgive silver_piece 100", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            // First arg: player names OR item IDs
            List<String> suggestions = new ArrayList<>();

            // Add online player names
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList()));

            // Add all item IDs
            suggestions.addAll(getAllItemIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList()));

            return suggestions;
        }

        if (args.length == 2) {
            // Second arg: item IDs (if first was a player) OR amount
            Player possiblePlayer = Bukkit.getPlayer(args[0]);
            if (possiblePlayer != null) {
                // First arg was a player, suggest item IDs
                return getAllItemIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                // First arg was an item, suggest common amounts
                return List.of("1", "8", "16", "32", "64");
            }
        }

        if (args.length == 3) {
            // Third arg: amount
            return List.of("1", "8", "16", "32", "64");
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
}