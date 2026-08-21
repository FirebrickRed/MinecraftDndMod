package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu;
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
import java.util.UUID;

/**
 * View a character sheet (Issue #47).
 * - /viewsheet                     → your own active character
 * - /viewsheet &lt;characterName&gt;      → any saved character by name (DM only)
 * - /viewsheet player &lt;playerName&gt;  → a player's character (DM only)
 */
public class ViewSheetCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Self view
        if (args.length == 0) {
            UUID activeId = ActiveCharacterTracker.getActiveCharacterId(player);
            if (activeId != null) {
                ViewCharacterSheetMenu.open(player, activeId);
                return true;
            }
            List<CharacterSheet> owned = CharacterSheetManager.getPlayerCharacters(player.getUniqueId());
            if (owned != null && !owned.isEmpty()) {
                ViewCharacterSheetMenu.open(player, owned.get(0).getCharacterId());
                return true;
            }
            player.sendMessage(Component.text("You don't have a character yet. Use /createcharacter.", NamedTextColor.RED));
            return true;
        }

        // Everything below is DM-only.
        if (!DMManager.isDM(sender)) {
            player.sendMessage(Component.text("Only a DM can view other characters.", NamedTextColor.RED));
            return true;
        }

        // /viewsheet player <playerName>
        if (args[0].equalsIgnoreCase("player")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /viewsheet player <playerName>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
                return true;
            }
            List<CharacterSheet> chars = CharacterSheetManager.getPlayerCharacters(target.getUniqueId());
            if (chars == null || chars.isEmpty()) {
                player.sendMessage(Component.text(target.getName() + " has no characters.", NamedTextColor.RED));
                return true;
            }
            ViewCharacterSheetMenu.open(player, chars.get(0).getCharacterId());
            if (chars.size() > 1) {
                player.sendMessage(Component.text("(" + target.getName() + " has " + chars.size()
                        + " characters; showing the first.)", NamedTextColor.GRAY));
            }
            return true;
        }

        // /viewsheet <characterName> (supports spaces via quotes)
        String name = stripQuotes(String.join(" ", args));
        CharacterSheet sheet = CharacterSheetManager.findCharacterByName(name);
        if (sheet == null) {
            player.sendMessage(Component.text("No character named: " + name, NamedTextColor.RED));
            return true;
        }
        ViewCharacterSheetMenu.open(player, sheet.getCharacterId());
        return true;
    }

    private String stripQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!DMManager.isDM(sender)) return out;

        if (args.length == 1) {
            out.add("player");
            out.addAll(CharacterSheetManager.getAllCharacterNames());
            return filter(out, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return filter(out, args[1]);
        }
        return out;
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) result.add(o);
        }
        return result;
    }
}
