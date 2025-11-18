package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /dm command for managing Dungeon Master role assignments.
 *
 * Commands:
 * - /dm add <player> - Grant DM role (OP only)
 * - /dm remove <player> - Revoke DM role (OP only)
 * - /dm list - Show all current DMs (anyone can use)
 */
public class DmCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "list" -> handleList(sender);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    // ==================== LIST SUBCOMMAND ====================

    private void handleList(CommandSender sender) {
        List<String> dmList = DMManager.getFormattedDMList();

        if (dmList.isEmpty()) {
            sender.sendMessage(Component.text("No DMs currently assigned.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("OPs automatically have DM privileges.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("Current DMs (" + dmList.size() + "):", NamedTextColor.GOLD));
        for (String dm : dmList) {
            sender.sendMessage(Component.text("  • " + dm, NamedTextColor.YELLOW));
        }
    }

    // ==================== ADD SUBCOMMAND ====================

    private void handleAdd(CommandSender sender, String[] args) {
        // OP-only check
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only server operators can add DMs.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dm add <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Note: Player must be online to be added as DM.", NamedTextColor.GRAY));
            return;
        }

        // Check if already a DM
        if (DMManager.isInDMList(target.getUniqueId())) {
            sender.sendMessage(Component.text(target.getName() + " is already a DM.", NamedTextColor.YELLOW));
            return;
        }

        if (target.isOp()) {
            sender.sendMessage(Component.text(target.getName() + " is already an OP (has DM privileges).", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("You can still add them to the DM list if desired.", NamedTextColor.GRAY));
        }

        // Add DM
        DMManager.addDM(target.getUniqueId());
        DMPersistenceLoader.saveDMs();

        sender.sendMessage(Component.text("✓ " + target.getName() + " is now a DM.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You have been granted DM privileges!", NamedTextColor.GOLD));
    }

    // ==================== REMOVE SUBCOMMAND ====================

    private void handleRemove(CommandSender sender, String[] args) {
        // OP-only check
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only server operators can remove DMs.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dm remove <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Note: Player must be online to be removed.", NamedTextColor.GRAY));
            return;
        }

        // Check if in DM list
        if (!DMManager.isInDMList(target.getUniqueId())) {
            if (target.isOp()) {
                sender.sendMessage(Component.text(target.getName() + " is an OP (cannot remove DM privileges via this command).", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Use /deop to remove OP status.", NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text(target.getName() + " is not in the DM list.", NamedTextColor.YELLOW));
            }
            return;
        }

        // Remove DM
        DMManager.removeDM(target.getUniqueId());
        DMPersistenceLoader.saveDMs();

        sender.sendMessage(Component.text("✓ " + target.getName() + " is no longer a DM.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your DM privileges have been revoked.", NamedTextColor.YELLOW));
    }

    // ==================== HELP ====================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DM Management Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dm list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Show all current DMs", NamedTextColor.GRAY));

        if (sender.isOp()) {
            sender.sendMessage(Component.text("/dm add <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  - Grant DM role to a player", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/dm remove <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  - Revoke DM role from a player", NamedTextColor.GRAY));
        }
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            // Subcommands
            List<String> subcommands = new ArrayList<>();
            subcommands.add("list");

            if (sender.isOp()) {
                subcommands.add("add");
                subcommands.add("remove");
            }

            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && sender.isOp()) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("add") || subcommand.equals("remove")) {
                // Suggest online player names
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}