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
 * /dm give <player> <item_id> [amount]
 * (The player is required and always first — use your own name to give yourself something.)
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

        // /dm give <player> <item_id> [amount]. The player always comes first (yourself included), so
        // tab completion is players, then items, then amounts, never a mixed list.
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }
        Player targetPlayer = Bukkit.getPlayerExact(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not online: " + args[0] + " (the player comes first: /dm give <player> <item_id> [amount])",
                    NamedTextColor.RED));
            return true;
        }
        String itemId = args[1];
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
                return true;
            }
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

        // Give item to player; a full inventory drops the rest at their feet rather than losing it.
        for (ItemStack left : targetPlayer.getInventory().addItem(itemStack).values()) {
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), left);
        }

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
        return io.papermc.jkvttplugin.util.ItemUtil.itemFromId(itemId, amount); // shared resolver (#185)
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DM Give Command ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dm give <player> <item_id> [amount]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Give D&D item to a player", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  - To yourself: use your own name", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Examples:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /dm give charlie longsword", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /dm give charlie gold_piece 64", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return getAllItemIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
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