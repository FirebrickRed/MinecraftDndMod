package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.commands.CheckCommand;
import io.papermc.jkvttplugin.commands.ConsumeResourceCommand;
import io.papermc.jkvttplugin.commands.DmGiveCommand;
import io.papermc.jkvttplugin.commands.ReloadYamlCommand;
import io.papermc.jkvttplugin.commands.RestCommand;
import io.papermc.jkvttplugin.commands.RestoreResourceCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Folded DM-admin tools (Issue #122). Each delegates to its original executor.
    private final DmGiveCommand giveExec = new DmGiveCommand();
    private final CheckCommand checkExec = new CheckCommand();
    private final RestCommand restExec = new RestCommand();
    private final RestoreResourceCommand restoreExec = new RestoreResourceCommand();
    private final ConsumeResourceCommand consumeExec = new ConsumeResourceCommand();
    private final ReloadYamlCommand reloadExec = new ReloadYamlCommand();

    /** DM-admin verbs folded under /dm (all require DM); role verbs (add/remove/list) handled separately. */
    private static final List<String> DM_TOOL_SUBS = List.of("give", "promptcheck", "lootprompt", "rest", "resource", "reload", "mode");

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
            case "give" -> delegateDm(sender, command, label, args, giveExec);
            case "promptcheck" -> delegateDm(sender, command, label, args, checkExec);
            case "rest" -> delegateDm(sender, command, label, args, restExec);
            case "reload" -> delegateDm(sender, command, label, args, reloadExec);
            case "resource" -> handleResource(sender, command, label, args);
            case "mode" -> handleInventory(sender);
            case "lootprompt" -> handleLootPrompt(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /** /dm lootprompt <player> <check> — call a specific loot check for a player searching a body (#144). */
    private void handleLootPrompt(CommandSender sender, String[] args) {
        if (!DMManager.isDM(sender) || !(sender instanceof org.bukkit.entity.Player dm)) {
            sender.sendMessage(Component.text("Only a DM can call a loot roll.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            dm.sendMessage(Component.text("Usage: /dm lootprompt <player> <check>", NamedTextColor.RED));
            return;
        }
        org.bukkit.entity.Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            dm.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
            return;
        }
        io.papermc.jkvttplugin.loot.LootManager.promptPlayerRoll(dm, target, args[2]);
    }

    // ==================== FOLDED DM TOOLS (Issue #122) ====================

    /** Toggle DM Inventory Mode (Issue #85): swaps the DM's hotbar for DM tools and back. */
    private void handleInventory(CommandSender sender) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can use DM mode.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("DM mode is for in-game players.", NamedTextColor.RED));
            return;
        }
        DmModeManager.toggle(player);
    }

    /** Gates on DM, then hands the remaining args to the original executor. */
    private void delegateDm(CommandSender sender, Command command, String label, String[] args, CommandExecutor exec) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can use this command.", NamedTextColor.RED));
            return;
        }
        exec.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
    }

    /** /dm resource <restore|consume> <character> ... */
    private void handleResource(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dm resource <restore|consume> <character> ...", NamedTextColor.RED));
            return;
        }
        String op = args[1].toLowerCase();
        String[] sub = Arrays.copyOfRange(args, 2, args.length);
        switch (op) {
            case "restore" -> restoreExec.onCommand(sender, command, label, sub);
            case "consume" -> consumeExec.onCommand(sender, command, label, sub);
            default -> sender.sendMessage(Component.text("Resource action must be 'restore' or 'consume'.", NamedTextColor.RED));
        }
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

        if (DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("DM tools:", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/dm give <player> <item_id> [amount]", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/dm promptcheck <player> <ability|save|skill> <name> [adv|dis]", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/dm rest <character> <short|long>", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/dm resource <restore|consume> <character> ...", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/dm reload", NamedTextColor.AQUA)
                    .append(Component.text("  - reload YAML content", NamedTextColor.GRAY)));
        }
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("list");
            if (sender.isOp()) {
                subcommands.add("add");
                subcommands.add("remove");
            }
            if (DMManager.isDM(sender)) {
                subcommands.addAll(DM_TOOL_SUBS);
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subcommand = args[0].toLowerCase();
        String[] sub = Arrays.copyOfRange(args, 1, args.length);

        if (subcommand.equals("add") || subcommand.equals("remove")) {
            if (args.length == 2 && sender.isOp()) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        // Folded DM tools: forward to the original executor's completer.
        if (DMManager.isDM(sender)) {
            switch (subcommand) {
                case "give" -> { return giveExec.onTabComplete(sender, command, label, sub); }
                case "promptcheck" -> { return checkExec.onTabComplete(sender, command, label, sub); }
                case "rest" -> { return restExec.onTabComplete(sender, command, label, sub); }
                case "resource" -> {
                    if (args.length == 2) {
                        return java.util.stream.Stream.of("restore", "consume")
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    String[] sub2 = Arrays.copyOfRange(args, 2, args.length);
                    if (args[1].equalsIgnoreCase("restore")) return restoreExec.onTabComplete(sender, command, label, sub2);
                    if (args[1].equalsIgnoreCase("consume")) return consumeExec.onTabComplete(sender, command, label, sub2);
                    return List.of();
                }
                default -> { return List.of(); }
            }
        }

        return List.of();
    }
}