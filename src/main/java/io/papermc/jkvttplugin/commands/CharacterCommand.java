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

    private static final List<String> SUBCOMMANDS = List.of("create", "view", "list", "close", "rest", "give");

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
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
                sendUsage(sender);
                return true;
            }
        }
    }

    private boolean handleList(CommandSender sender, String[] rest) {
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
            case "create", "list" -> {
                // DM forms take an online player name as the first extra arg.
                if (rest.length == 1 && DMManager.isDM(sender)) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(rest[0].toLowerCase())) names.add(p.getName());
                    }
                    return names;
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }
}
