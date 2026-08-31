package io.papermc.jkvttplugin.character;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * One place to turn a command's character-name argument into a {@link CharacterSheet} (Issue #53,
 * a step toward the shared name resolver of #140). Accepts either a plain {@code Name} or an
 * {@code Owner/Name} form to disambiguate when two players share a character name.
 *
 * <p>Kept command-friendly: {@link #resolveOrError} messages the sender on not-found or ambiguity
 * and returns {@code null}, so callers stay a two-liner.
 */
public final class CharacterResolver {

    private CharacterResolver() {}

    /**
     * Resolve a character by {@code Name} or {@code Owner/Name}. Returns the match, or messages the
     * sender and returns {@code null} when the name is missing, unknown, or ambiguous (multiple
     * owners) — the message tells the DM how to disambiguate.
     */
    public static CharacterSheet resolveOrError(CommandSender sender, String arg) {
        if (arg == null || arg.isBlank()) {
            sender.sendMessage(Component.text("No character name given.", NamedTextColor.RED));
            return null;
        }
        String raw = stripQuotes(arg.trim());

        // Owner/Name form points straight at one character.
        int slash = raw.indexOf('/');
        if (slash > 0 && slash < raw.length() - 1) {
            String owner = raw.substring(0, slash).trim();
            String charName = raw.substring(slash + 1).trim();
            for (CharacterSheet s : CharacterSheetManager.findAllCharactersByName(charName)) {
                if (owner.equalsIgnoreCase(ownerName(s))) return s;
            }
            sender.sendMessage(Component.text("No character '" + charName + "' owned by " + owner + ".", NamedTextColor.RED));
            return null;
        }

        List<CharacterSheet> matches = CharacterSheetManager.findAllCharactersByName(raw);
        if (matches.isEmpty()) {
            sender.sendMessage(Component.text("Character '" + raw + "' not found.", NamedTextColor.RED));
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        // Ambiguous — list the owners and how to pick one.
        sender.sendMessage(Component.text("Multiple characters named '" + raw + "':", NamedTextColor.RED));
        for (CharacterSheet s : matches) {
            sender.sendMessage(Component.text("  • " + s.getCharacterName() + " (owned by " + ownerName(s) + ")", NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Use \"" + firstOwner(matches) + "/" + raw + "\" to pick one.", NamedTextColor.YELLOW));
        return null;
    }

    /** The player name that owns a character, or a short id fragment if the name is unknown. */
    public static String ownerName(CharacterSheet sheet) {
        String name = Bukkit.getOfflinePlayer(sheet.getPlayerId()).getName();
        return name != null ? name : sheet.getPlayerId().toString().substring(0, 8);
    }

    private static String firstOwner(List<CharacterSheet> matches) {
        return ownerName(matches.get(0));
    }

    private static String stripQuotes(String s) {
        return io.papermc.jkvttplugin.util.NameUtil.stripQuotes(s);
    }
}
