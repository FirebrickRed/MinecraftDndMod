package io.papermc.jkvttplugin.loot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /loot <check> <d20>} — resolves a physical loot/harvest check against the body the player
 * last right-clicked (Issue #136). The clickable search prompt fills in the check; the player adds
 * their d20 and the game adds the modifier.
 */
public class LootCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can loot.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /loot <check> <your d20 roll>", NamedTextColor.RED));
            return true;
        }
        int d20;
        try {
            d20 = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Your d20 roll must be a number 1–20.", NamedTextColor.RED));
            return true;
        }
        LootManager.roll(player, args[0], d20);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
