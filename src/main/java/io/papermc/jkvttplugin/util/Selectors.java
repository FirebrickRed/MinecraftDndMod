package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft target selectors where a command takes a name: {@code @p}, {@code @r},
 * {@code @p[distance=..3]}. This is what lets a command block be a trap: a pressure plate wired to
 * {@code /dm check @p save dexterity dc 13} or {@code /dm adjust @p hp -6 type fire} hits whoever
 * stepped on it. The server resolves the selector from where the sender is (the block, or the DM).
 */
public final class Selectors {

    private Selectors() {}

    public static boolean is(String arg) {
        return arg != null && arg.length() >= 2 && arg.charAt(0) == '@' && Character.isLetter(arg.charAt(1));
    }

    /**
     * The one player a selector picks, or null after telling the sender why not: nobody matched, several
     * did (these commands act on one target; {@code @p} or {@code limit=1} narrows it), or it's malformed.
     */
    public static Player onePlayer(CommandSender sender, String selector) {
        List<Player> players = new ArrayList<>();
        try {
            for (Entity e : Bukkit.selectEntities(sender, selector)) if (e instanceof Player p) players.add(p);
        } catch (IllegalArgumentException bad) {
            sender.sendMessage(Component.text("'" + selector + "' isn't a selector the game understands.", NamedTextColor.RED));
            return null;
        }
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("No player matches " + selector + ".", NamedTextColor.RED));
            return null;
        }
        if (players.size() > 1) {
            sender.sendMessage(Component.text(selector + " matches " + players.size() + " players; this takes one (use @p, or add limit=1).", NamedTextColor.RED));
            return null;
        }
        return players.get(0);
    }

    /** That player's active character, or null after saying they have none. */
    public static CharacterSheet oneCharacter(CommandSender sender, String selector) {
        Player p = onePlayer(sender, selector);
        if (p == null) return null;
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(p);
        if (sheet == null) sender.sendMessage(Component.text(p.getName() + " has no active character.", NamedTextColor.RED));
        return sheet;
    }
}
