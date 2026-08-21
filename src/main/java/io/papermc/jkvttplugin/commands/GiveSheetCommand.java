package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
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
import java.util.HashMap;
import java.util.List;

/**
 * Give a player their character sheet paper item (Issue #47).
 * Useful when a player loses/drops theirs.
 * Usage: /givesheet &lt;player&gt; &lt;characterName&gt;
 */
public class GiveSheetCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /givesheet <player> <characterName>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not online: " + args[0], NamedTextColor.RED));
            return true;
        }

        String name = stripQuotes(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        CharacterSheet sheet = CharacterSheetManager.findCharacterByName(name);
        if (sheet == null) {
            sender.sendMessage(Component.text("No character named: " + name, NamedTextColor.RED));
            return true;
        }

        ItemStack item = CharacterSheetManager.createCharacterSheetItem(sheet);
        HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
            sender.sendMessage(Component.text(target.getName() + "'s inventory was full — the sheet was dropped at their feet.", NamedTextColor.YELLOW));
        }

        sender.sendMessage(Component.text("Gave " + sheet.getCharacterName() + "'s sheet to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You received " + sheet.getCharacterName() + "'s character sheet.", NamedTextColor.GREEN));
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
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return filter(out, args[0]);
        }
        if (args.length >= 2) {
            out.addAll(CharacterSheetManager.getAllCharacterNames());
            return filter(out, args[args.length - 1]);
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
