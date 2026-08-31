package io.papermc.jkvttplugin.character;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * One place to turn a command's target argument into a {@link CharacterSheet} (Issues #53 / #108,
 * built on the shared name plumbing of #140). Forgiving by design so DMs don't have to guess which
 * identity a command wants — it accepts, in order:
 * <ol>
 *   <li>{@code Owner/Name} — an explicit owner + character name (always unambiguous)</li>
 *   <li>a character name (the D&D nameplate identity); duplicates across players are reported</li>
 *   <li>a player's username — resolves to that player's character</li>
 * </ol>
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

        // 1. A character with this name.
        List<CharacterSheet> matches = CharacterSheetManager.findAllCharactersByName(raw);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            // Ambiguous character name — list the owners and how to pick one.
            sender.sendMessage(Component.text("Multiple characters named '" + raw + "':", NamedTextColor.RED));
            for (CharacterSheet s : matches) {
                sender.sendMessage(Component.text("  • " + s.getCharacterName() + " (owned by " + ownerName(s) + ")", NamedTextColor.GRAY));
            }
            sender.sendMessage(Component.text("Use \"" + firstOwner(matches) + "/" + raw + "\" to pick one.", NamedTextColor.YELLOW));
            return null;
        }

        // 2. No character by that name — treat it as a player's username and use their character.
        //    This makes targeting forgiving: the character name OR the owning player's name both work.
        List<CharacterSheet> byOwner = charactersOfOwnerNamed(raw);
        if (byOwner.size() == 1) {
            return byOwner.get(0);
        }
        if (byOwner.size() > 1) {
            sender.sendMessage(Component.text(raw + " has multiple characters — name the one you mean:", NamedTextColor.RED));
            for (CharacterSheet s : byOwner) {
                sender.sendMessage(Component.text("  • " + s.getCharacterName(), NamedTextColor.GRAY));
            }
            return null;
        }

        sender.sendMessage(Component.text("No character or player named '" + raw + "'.", NamedTextColor.RED));
        return null;
    }

    /** Characters owned by the player whose name matches (case-insensitive). */
    private static List<CharacterSheet> charactersOfOwnerNamed(String playerName) {
        List<CharacterSheet> out = new ArrayList<>();
        for (CharacterSheet s : CharacterSheetManager.getAllCharacters()) {
            if (playerName.equalsIgnoreCase(ownerName(s))) out.add(s);
        }
        return out;
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
