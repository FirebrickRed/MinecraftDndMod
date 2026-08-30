package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
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
import java.util.UUID;

/**
 * Consolidated player-facing character command (Issue #122).
 *
 * <p>One command replaces four: {@code /character <create|view|list|close|give>}
 * (alias {@code /char}). The subcommands delegate to the existing executors so
 * behaviour stays identical; only the entry point is unified.
 *
 * <ul>
 *   <li>{@code /character create}                — start your own character creation</li>
 *   <li>{@code /character create <player>}       — (DM) open creation for another player</li>
 *   <li>{@code /character view [name|player <p>]}— view a sheet (delegates to viewsheet)</li>
 *   <li>{@code /character list [player]}         — list your characters (DM: another player's)</li>
 *   <li>{@code /character close}                 — save & close the active sheet</li>
 *   <li>{@code /character give <player> <name>}  — (DM) give a player their sheet item</li>
 * </ul>
 */
public class CharacterCommand implements CommandExecutor, TabCompleter {

    private final CreateCharacterCommand createExec = new CreateCharacterCommand();
    private final ViewSheetCommand viewExec = new ViewSheetCommand();
    private final CloseSheetCommand closeExec = new CloseSheetCommand();
    private final GiveSheetCommand giveExec = new GiveSheetCommand();
    private final ShortRestCommand shortRestExec = new ShortRestCommand();
    private final LongRestCommand longRestExec = new LongRestCommand();

    private static final List<String> SUBCOMMANDS = List.of("create", "view", "list", "close", "rest", "give", "delete", "loot", "check", "cast");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "create" -> {
                // DM form: /character create <player> — open creation for someone else.
                if (rest.length >= 1) {
                    if (!DMManager.isDM(sender)) {
                        sender.sendMessage(Component.text("Only a DM can start creation for another player.", NamedTextColor.RED));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(rest[0]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not online: " + rest[0], NamedTextColor.RED));
                        return true;
                    }
                    CharacterCreationSession session = CharacterCreationService.start(target.getUniqueId());
                    CharacterCreationMenu.open(target, session.getSessionId());
                    sender.sendMessage(Component.text("Opened character creation for " + target.getName() + ".", NamedTextColor.GREEN));
                    return true;
                }
                return createExec.onCommand(sender, cmd, label, rest);
            }
            case "view" -> {
                return viewExec.onCommand(sender, cmd, label, rest);
            }
            case "list" -> {
                return handleList(sender, rest);
            }
            case "close" -> {
                return closeExec.onCommand(sender, cmd, label, rest);
            }
            case "rest" -> {
                if (rest.length < 1) {
                    sender.sendMessage(Component.text("Usage: /character rest <short|long>", NamedTextColor.RED));
                    return true;
                }
                String kind = rest[0].toLowerCase();
                String[] restArgs = Arrays.copyOfRange(rest, 1, rest.length);
                if (kind.equals("short")) return shortRestExec.onCommand(sender, cmd, label, restArgs);
                if (kind.equals("long")) return longRestExec.onCommand(sender, cmd, label, restArgs);
                sender.sendMessage(Component.text("Rest type must be 'short' or 'long'.", NamedTextColor.RED));
                return true;
            }
            case "give" -> {
                return giveExec.onCommand(sender, cmd, label, rest);
            }
            case "delete" -> {
                return handleDelete(sender, rest);
            }
            case "loot" -> {
                return handleLoot(sender, rest);
            }
            case "check" -> {
                return handleCheck(sender, rest);
            }
            case "cast" -> {
                return handleCast(sender, rest);
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
                sendUsage(sender);
                return true;
            }
        }
    }

    private boolean handleList(CommandSender sender, String[] rest) {
        // DM: /character list all -> every saved character across all players.
        if (rest.length >= 1 && rest[0].equalsIgnoreCase("all")) {
            if (!DMManager.isDM(sender)) {
                sender.sendMessage(Component.text("Only a DM can list all characters.", NamedTextColor.RED));
                return true;
            }
            List<CharacterSheet> all = CharacterSheetManager.getAllCharacters();
            if (all.isEmpty()) {
                sender.sendMessage(Component.text("No saved characters.", NamedTextColor.GRAY));
                return true;
            }
            sender.sendMessage(Component.text("All characters (" + all.size() + "):", NamedTextColor.GOLD));
            for (CharacterSheet sheet : all) {
                String owner = Bukkit.getOfflinePlayer(sheet.getPlayerId()).getName();
                Component line = Component.text("  • " + sheet.getCharacterName(), NamedTextColor.WHITE);
                if (owner != null) line = line.append(Component.text("  (" + owner + ")", NamedTextColor.GRAY));
                sender.sendMessage(line);
            }
            return true;
        }

        UUID targetId;
        String who;
        if (rest.length >= 1) {
            if (!DMManager.isDM(sender)) {
                sender.sendMessage(Component.text("Only a DM can list another player's characters.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(rest[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not online: " + rest[0], NamedTextColor.RED));
                return true;
            }
            targetId = target.getUniqueId();
            who = target.getName() + "'s";
        } else if (sender instanceof Player player) {
            targetId = player.getUniqueId();
            who = "Your";
        } else {
            sender.sendMessage(Component.text("Console must specify a player: /character list <player>", NamedTextColor.RED));
            return true;
        }

        List<CharacterSheet> chars = CharacterSheetManager.getPlayerCharacters(targetId);
        if (chars == null || chars.isEmpty()) {
            sender.sendMessage(Component.text(who + " characters: none yet.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text(who + " characters (" + chars.size() + "):", NamedTextColor.GOLD));
        for (CharacterSheet sheet : chars) {
            sender.sendMessage(Component.text("  • " + sheet.getCharacterName(), NamedTextColor.WHITE));
        }
        return true;
    }

    /** {@code /character loot <check> <d20>} — resolve a physical loot check on the body you clicked. */
    private boolean handleLoot(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can loot.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 1) {
            player.sendMessage(Component.text("Right-click a body, then use the prompt (or /character loot <check> --roll <d20>).", NamedTextColor.RED));
            return true;
        }
        // Same --roll/--total grammar as attacks: --roll <n|dice>, or --total <n>.
        Integer providedRoll = null, providedTotal = null;
        for (int i = 1; i < rest.length - 1; i++) {
            if (rest[i].equalsIgnoreCase("--roll")) {
                providedRoll = io.papermc.jkvttplugin.combat.RollService.parseRollArg(rest[i + 1]);
            } else if (rest[i].equalsIgnoreCase("--total")) {
                try { providedTotal = Integer.parseInt(rest[i + 1].trim()); } catch (NumberFormatException ignored) {}
            }
        }
        io.papermc.jkvttplugin.loot.LootManager.roll(player, rest[0], providedRoll, providedTotal);
        return true;
    }

    /** {@code /character check <TYPE> <VALUE> [--roll n | --total n]} — resolve a physical skill/ability/save roll (#145). */
    private boolean handleCheck(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can roll checks.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 2) {
            player.sendMessage(Component.text("Click a skill on your sheet, or /character check <type> <value> --roll <n>.", NamedTextColor.RED));
            return true;
        }
        String type = rest[0].toUpperCase();
        String value = rest[1].toUpperCase();
        Integer roll = null, total = null;
        for (int i = 2; i < rest.length - 1; i++) {
            if (rest[i].equalsIgnoreCase("--roll")) {
                roll = io.papermc.jkvttplugin.combat.RollService.parseRollArg(rest[i + 1]);
            } else if (rest[i].equalsIgnoreCase("--total")) {
                try { total = Integer.parseInt(rest[i + 1].trim()); } catch (NumberFormatException ignored) {}
            }
        }
        CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }
        if (!io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.resolvePhysical(sheet, type, value, roll, total)) {
            player.sendMessage(Component.text("Provide your roll: --roll <your d20>.", NamedTextColor.YELLOW));
        }
        return true;
    }

    /**
     * {@code /character cast <spell> [target] [message…]} — cast a social/roleplay spell (#151).
     * Combat spells (attack/save) are cast with {@code /combat cast}; this is for chat spells like
     * Message and Speak with Animals. Works in or out of combat — cast on your turn it spends your action.
     */
    private boolean handleCast(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can cast spells.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 1) {
            player.sendMessage(Component.text("Usage: /character cast <spell> [target] [message…]", NamedTextColor.RED));
            return true;
        }
        io.papermc.jkvttplugin.data.model.DndSpell spell =
                io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(io.papermc.jkvttplugin.util.Util.normalize(rest[0]));
        if (spell == null) {
            player.sendMessage(Component.text("Unknown spell: " + rest[0], NamedTextColor.RED));
            return true;
        }
        if (!spell.isSocial()) {
            player.sendMessage(Component.text(spell.getName() + " isn't a chat spell — cast combat spells with /combat cast.", NamedTextColor.RED));
            return true;
        }
        boolean needsTarget = !spell.getSocialType().equalsIgnoreCase("speak_with_animals");
        String target = null;
        String words;
        if (needsTarget) {
            target = rest.length >= 2 ? rest[1] : null;
            words = rest.length >= 3 ? String.join(" ", Arrays.copyOfRange(rest, 2, rest.length)) : null;
        } else {
            words = rest.length >= 2 ? String.join(" ", Arrays.copyOfRange(rest, 1, rest.length)) : null;
        }
        io.papermc.jkvttplugin.social.SocialSpellHandler.begin(player, spell, target, words);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage(Component.text("Usage: /character delete <name>", NamedTextColor.RED));
            return true;
        }
        String name = String.join(" ", rest);
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) name = name.substring(1, name.length() - 1);
        CharacterSheet sheet = CharacterSheetManager.findCharacterByName(name);
        if (sheet == null) {
            sender.sendMessage(Component.text("No character named: " + name, NamedTextColor.RED));
            return true;
        }
        boolean isOwn = sender instanceof Player p && sheet.getPlayerId().equals(p.getUniqueId());
        if (!isOwn && !DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("You can only delete your own characters.", NamedTextColor.RED));
            return true;
        }
        String deleted = sheet.getCharacterName();
        CharacterSheetManager.deleteCharacter(sheet.getPlayerId(), sheet.getCharacterId());
        sender.sendMessage(Component.text("Deleted character: " + deleted, NamedTextColor.GREEN));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Character commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /character create           ", NamedTextColor.YELLOW)
                .append(Component.text("start creating a character", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character view [name]      ", NamedTextColor.YELLOW)
                .append(Component.text("view a character sheet", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character list             ", NamedTextColor.YELLOW)
                .append(Component.text("list your characters", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character close            ", NamedTextColor.YELLOW)
                .append(Component.text("save & close the active sheet", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character rest <short|long>", NamedTextColor.YELLOW)
                .append(Component.text("  take a rest", NamedTextColor.GRAY)));
        if (DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("  /character create <player>  ", NamedTextColor.AQUA)
                    .append(Component.text("(DM) open creation for a player", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("  /character give <player> <name>  ", NamedTextColor.AQUA)
                    .append(Component.text("(DM) give a player their sheet", NamedTextColor.GRAY)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (s.equals("give") && !DMManager.isDM(sender)) continue;
                if (s.startsWith(args[0].toLowerCase())) subs.add(s);
            }
            return subs;
        }

        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "view" -> {
                return viewExec.onTabComplete(sender, command, alias, rest);
            }
            case "give" -> {
                return giveExec.onTabComplete(sender, command, alias, rest);
            }
            case "delete" -> {
                if (rest.length == 1) {
                    List<CharacterSheet> chars = DMManager.isDM(sender)
                            ? CharacterSheetManager.getAllCharacters()
                            : (sender instanceof Player p ? CharacterSheetManager.getPlayerCharacters(p.getUniqueId()) : List.of());
                    List<String> names = new ArrayList<>();
                    for (CharacterSheet s : chars) {
                        if (s.getCharacterName().toLowerCase().startsWith(rest[0].toLowerCase())) names.add(s.getCharacterName());
                    }
                    return names;
                }
                return List.of();
            }
            case "rest" -> {
                if (rest.length == 1) {
                    List<String> kinds = new ArrayList<>();
                    for (String k : List.of("short", "long")) {
                        if (k.startsWith(rest[0].toLowerCase())) kinds.add(k);
                    }
                    return kinds;
                }
                return List.of();
            }
            case "cast" -> {
                if (rest.length == 1) {
                    List<String> spells = new ArrayList<>();
                    for (io.papermc.jkvttplugin.data.model.DndSpell s : io.papermc.jkvttplugin.data.loader.SpellLoader.getAllSpells()) {
                        if (s.isSocial() && s.getId().startsWith(rest[0].toLowerCase())) spells.add(s.getId());
                    }
                    return spells;
                }
                if (rest.length == 2) {
                    io.papermc.jkvttplugin.data.model.DndSpell s =
                            io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(io.papermc.jkvttplugin.util.Util.normalize(rest[0]));
                    if (s != null && s.isSocial() && !s.getSocialType().equalsIgnoreCase("speak_with_animals")) {
                        List<String> names = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(rest[1].toLowerCase())) names.add(p.getName());
                        }
                        return names;
                    }
                }
                return List.of();
            }
            case "create", "list" -> {
                // DM forms take an online player name (and, for list, "all") as the first extra arg.
                if (rest.length == 1 && DMManager.isDM(sender)) {
                    List<String> opts = new ArrayList<>();
                    if (sub.equals("list") && "all".startsWith(rest[0].toLowerCase())) opts.add("all");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(rest[0].toLowerCase())) opts.add(p.getName());
                    }
                    return opts;
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }
}
