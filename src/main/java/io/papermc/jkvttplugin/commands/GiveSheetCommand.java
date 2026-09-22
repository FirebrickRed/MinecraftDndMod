package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.character.CharacterResolver;
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
 * Give a player a character sheet paper item (Issue #47): a replacement for their own when they
 * lose it, or, for someone else's character, a DM-confirmed hand-over of the character itself.
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
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, name);
        if (sheet == null) return true;

        // Someone else's character: this is a hand-over, not a replacement paper. The paper alone
        // would be useless to them (a sheet only opens for its owner), so it transfers ownership,
        // after the DM confirms.
        if (!sheet.getPlayerId().equals(target.getUniqueId())) {
            confirmTransfer(sender, target, sheet);
            return true;
        }

        givePaper(sender, target, sheet);
        return true;
    }

    private void confirmTransfer(CommandSender sender, Player target, CharacterSheet sheet) {
        String owner = Bukkit.getOfflinePlayer(sheet.getPlayerId()).getName();
        if (io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(sheet.getPlayerId()) != null
                || io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(target.getUniqueId()) != null) {
            sender.sendMessage(Component.text("Finish the fight first — " + (owner != null ? owner : "the owner")
                    + " or " + target.getName() + " is in combat.", NamedTextColor.RED));
            return;
        }
        java.util.UUID oldOwnerId = sheet.getPlayerId();
        Runnable transfer = () -> {
            if (!sheet.getPlayerId().equals(oldOwnerId)) return; // already handed on
            CharacterSheetManager.transferCharacter(sheet, target.getUniqueId());
            givePaper(sender, target, sheet);
            sender.sendMessage(Component.text(sheet.getCharacterName() + " now belongs to " + target.getName()
                    + ". Their gear is still with " + (owner != null ? owner : "the old owner") + " — hand it over in game.",
                    NamedTextColor.GRAY));
            Player old = Bukkit.getPlayer(oldOwnerId);
            if (old != null) old.sendMessage(Component.text(sheet.getCharacterName() + " was handed to "
                    + target.getName() + " by the DM.", NamedTextColor.GRAY));
        };
        if (!(sender instanceof Player)) {
            transfer.run();
            return;
        }
        var once = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(1).lifetime(java.time.Duration.ofMinutes(5)).build();
        sender.sendMessage(Component.text(sheet.getCharacterName() + " belongs to " + (owner != null ? owner : "someone else")
                        + ". ", NamedTextColor.YELLOW)
                .append(Component.text("[Give " + sheet.getCharacterName() + " to " + target.getName() + "]",
                                NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(a -> transfer.run(), once))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(
                                "Transfers the character: " + target.getName() + " becomes its player.")))));
    }

    private void givePaper(CommandSender sender, Player target, CharacterSheet sheet) {
        ItemStack item = CharacterSheetManager.createCharacterSheetItem(sheet);
        HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
            sender.sendMessage(Component.text(target.getName() + "'s inventory was full — the sheet was dropped at their feet.", NamedTextColor.YELLOW));
        }

        sender.sendMessage(Component.text("Gave " + sheet.getCharacterName() + "'s sheet to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You received " + sheet.getCharacterName() + "'s character sheet.", NamedTextColor.GREEN));
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
