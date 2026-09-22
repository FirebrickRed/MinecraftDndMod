package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.character.CharacterResolver;
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
 * - /viewsheet &lt;characterName&gt;      → one of your own characters; any character for a DM
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

        // Players may open any of their OWN characters by name. Anyone else's sheet is DM-only: an
        // NPC ally or a traitor in the party mustn't be readable by the players (a DM can still
        // share what a successful check would reveal).
        if (!DMManager.isDM(sender)) {
            String wanted = stripQuotes(String.join(" ", args));
            List<CharacterSheet> owned = CharacterSheetManager.getPlayerCharacters(player.getUniqueId());
            if (owned != null) {
                for (CharacterSheet own : owned) {
                    if (own.getCharacterName().equalsIgnoreCase(wanted)) {
                        ViewCharacterSheetMenu.open(player, own.getCharacterId());
                        return true;
                    }
                }
            }
            player.sendMessage(Component.text("You can only view your own characters.", NamedTextColor.RED));
            return true;
        }

        // /viewsheet player <playerName>
        if (args[0].equalsIgnoreCase("player")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /character view player <playerName>", NamedTextColor.RED));
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
        CharacterSheet sheet = CharacterResolver.resolveOrError(player, name);
        if (sheet == null) return true;
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
        if (!DMManager.isDM(sender)) {
            // Only your own characters' names — never a list of everyone else's.
            if (args.length == 1 && sender instanceof Player p) {
                List<CharacterSheet> owned = CharacterSheetManager.getPlayerCharacters(p.getUniqueId());
                if (owned != null) for (CharacterSheet c : owned) out.add(c.getCharacterName());
                return filter(out, args[0]);
            }
            return out;
        }

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
