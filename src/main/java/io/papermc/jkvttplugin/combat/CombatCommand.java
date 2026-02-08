package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /combat commands for the combat system.
 *
 * Commands:
 * - /combat start - Start combat setup mode
 * - /combat add <target> - Add combatant
 * - /combat add --radius <blocks> - Add nearby combatants
 * - /combat remove <target> - Remove combatant
 * - /combat surprise <target> - Mark as surprised
 * - /combat initiative <target> set <value> - Manually set initiative
 * - /combat nextturn - Advance to next turn
 * - /combat endturn <target> - Force end someone's turn
 * - /combat turn <target> - Jump to specific combatant
 * - /combat status - View combat status
 * - /combat end - End combat session
 *
 * Issue #97 - Combat Session Foundation
 */
public class CombatCommand implements CommandExecutor, TabCompleter {

    // DM session tracking (player UUID -> their active combat session)
    private static final Map<UUID, CombatSession> DM_SESSIONS = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        // Check DM permission for most commands
        if (!isDM(player) && !player.hasPermission("jkvtt.dm")) {
            sender.sendMessage(Component.text("Only the DM can use combat commands.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "start" -> handleStart(player);
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            case "surprise" -> handleSurprise(player, args);
            case "initiative" -> handleInitiative(player, args);
            case "rollforinitiative" -> handleRollForInitiative(player);
            case "nextturn" -> handleNextTurn(player);
            case "endturn" -> handleEndTurn(player, args);
            case "turn" -> handleJumpToTurn(player, args);
            case "status" -> handleStatus(player);
            case "end" -> handleEnd(player);
            case "reveal" -> handleReveal(player, args);
            case "hide" -> handleHide(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    // ==================== SUBCOMMAND HANDLERS ====================

    private void handleStart(Player dm) {
        // Check if DM already has an active combat
        CombatSession existing = DM_SESSIONS.get(dm.getUniqueId());
        if (existing != null && existing.isActive()) {
            dm.sendMessage(Component.text("You already have an active combat session.", NamedTextColor.RED));
            dm.sendMessage(Component.text("Use /combat end to end it first.", NamedTextColor.GRAY));
            return;
        }

        // Create new combat session
        CombatSession session = new CombatSession(dm);
        DM_SESSIONS.put(dm.getUniqueId(), session);

        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        dm.sendMessage(Component.text("Combat session started!", NamedTextColor.GREEN, TextDecoration.BOLD));
        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("Add combatants with:", NamedTextColor.YELLOW));
        dm.sendMessage(Component.text("  /combat add <player|entity>", NamedTextColor.WHITE));
        dm.sendMessage(Component.text("  /combat add --radius <blocks>", NamedTextColor.WHITE));
        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("Mark surprised combatants:", NamedTextColor.YELLOW));
        dm.sendMessage(Component.text("  /combat surprise <target>", NamedTextColor.WHITE));
        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("When ready:", NamedTextColor.YELLOW));
        dm.sendMessage(Component.text("  /combat rollforinitiative", NamedTextColor.GREEN));
        dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private void handleAdd(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat add <player|entity> [--hidden]", NamedTextColor.RED));
            dm.sendMessage(Component.text("       /combat add --radius <blocks> [--hidden]", NamedTextColor.GRAY));
            return;
        }

        // Check for --hidden flag anywhere in args
        boolean hidden = hasFlag(args, "--hidden");

        // Check for radius mode
        if (args[1].equalsIgnoreCase("--radius")) {
            handleAddByRadius(dm, session, args, hidden);
            return;
        }

        // Join remaining args (excluding flags) to support names with spaces
        String targetName = joinArgsExcludingFlags(args, 1);

        // Try to find as player first
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            addPlayerToCombat(dm, session, targetPlayer, hidden);
            return;
        }

        // Try to find as entity (supports partial matching for names with spaces)
        // Pass session so we skip entities already in combat
        DndEntityInstance entity = findEntityByName(targetName, session);
        if (entity != null) {
            addEntityToCombat(dm, session, entity, hidden);
            return;
        }

        dm.sendMessage(Component.text("Could not find player or entity: " + targetName, NamedTextColor.RED));
    }

    private void handleAddByRadius(Player dm, CombatSession session, String[] args, boolean hidden) {
        if (args.length < 3) {
            dm.sendMessage(Component.text("Usage: /combat add --radius <blocks> [--hidden]", NamedTextColor.RED));
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            dm.sendMessage(Component.text("Invalid radius: " + args[2], NamedTextColor.RED));
            return;
        }

        int addedCount = 0;
        int hiddenCount = 0;

        // Add nearby players with character sheets (players are never hidden)
        for (Entity entity : dm.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player nearbyPlayer && !nearbyPlayer.equals(dm)) {
                CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(nearbyPlayer);
                if (sheet != null) {
                    try {
                        Combatant combatant = Combatant.fromPlayer(nearbyPlayer);
                        if (session.addCombatant(combatant)) {
                            addedCount++;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        // Add nearby DndEntityInstances
        // Note: DndEntityInstance uses ArmorStands, check the registry
        for (Entity entity : dm.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.ArmorStand armorStand) {
                DndEntityInstance instance = DndEntityInstance.getByArmorStand(armorStand);
                if (instance != null && !instance.isDead()) {
                    Combatant combatant = Combatant.fromEntity(instance);
                    if (hidden) {
                        combatant.setHidden(true);
                        hiddenCount++;
                    }
                    if (session.addCombatant(combatant)) {
                        addedCount++;
                    }
                }
            }
        }

        String msg = "Added " + addedCount + " combatants within " + radius + " blocks.";
        if (hiddenCount > 0) {
            msg += " (" + hiddenCount + " entities hidden)";
        }
        dm.sendMessage(Component.text(msg, NamedTextColor.GREEN));
        session.updateScoreboard();
    }

    private void addPlayerToCombat(Player dm, CombatSession session, Player target, boolean hidden) {
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(target);
        if (sheet == null) {
            dm.sendMessage(Component.text(target.getName() + " does not have an active character.", NamedTextColor.RED));
            return;
        }

        try {
            Combatant combatant = Combatant.fromPlayer(target);
            // Note: Players are never hidden (only entities can be hidden)
            if (session.addCombatant(combatant)) {
                dm.sendMessage(Component.text("Added " + sheet.getCharacterName() + " to combat.", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You have been added to combat!", NamedTextColor.YELLOW));
                session.updateScoreboard();
            } else {
                dm.sendMessage(Component.text(sheet.getCharacterName() + " is already in combat.", NamedTextColor.YELLOW));
            }
        } catch (IllegalArgumentException e) {
            dm.sendMessage(Component.text("Error adding player: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void addEntityToCombat(Player dm, CombatSession session, DndEntityInstance entity, boolean hidden) {
        if (entity.isDead()) {
            dm.sendMessage(Component.text(entity.getDisplayName() + " is dead and cannot join combat.", NamedTextColor.RED));
            return;
        }

        Combatant combatant = Combatant.fromEntity(entity);
        if (hidden) {
            combatant.setHidden(true);
        }
        if (session.addCombatant(combatant)) {
            String msg = "Added " + entity.getDisplayName() + " to combat.";
            if (hidden) {
                msg += " (hidden as ???)";
            }
            dm.sendMessage(Component.text(msg, NamedTextColor.GREEN));
            session.updateScoreboard();
        } else {
            dm.sendMessage(Component.text(entity.getDisplayName() + " is already in combat.", NamedTextColor.YELLOW));
        }
    }

    private void handleRemove(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat remove <target>", NamedTextColor.RED));
            return;
        }

        // Support names with spaces
        String targetName = joinArgs(args, 1);
        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        session.removeCombatant(combatant);
        dm.sendMessage(Component.text("Removed " + combatant.getDisplayName() + " from combat.", NamedTextColor.GREEN));
    }

    private void handleSurprise(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (!session.isSetupPhase()) {
            dm.sendMessage(Component.text("Surprise can only be set before rolling initiative.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat surprise <target>", NamedTextColor.RED));
            return;
        }

        // Support names with spaces
        String targetName = joinArgs(args, 1);
        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        session.markSurprised(combatant);
        dm.sendMessage(Component.text(combatant.getDisplayName() + " is marked as surprised.", NamedTextColor.YELLOW));
    }

    private void handleInitiative(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        // /combat initiative <target...> set <value>
        // Find where "set" appears to support names with spaces
        int setIndex = -1;
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("set")) {
                setIndex = i;
                break;
            }
        }

        if (setIndex == -1 || setIndex + 1 >= args.length) {
            dm.sendMessage(Component.text("Usage: /combat initiative <target> set <value>", NamedTextColor.RED));
            return;
        }

        // Join args from 1 to setIndex for the target name
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 1; i < setIndex; i++) {
            if (i > 1) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String targetName = nameBuilder.toString();

        if (targetName.isEmpty()) {
            dm.sendMessage(Component.text("Usage: /combat initiative <target> set <value>", NamedTextColor.RED));
            return;
        }

        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        int newInit;
        try {
            newInit = Integer.parseInt(args[setIndex + 1]);
        } catch (NumberFormatException e) {
            dm.sendMessage(Component.text("Invalid initiative value: " + args[setIndex + 1], NamedTextColor.RED));
            return;
        }

        int oldInit = combatant.getInitiative();
        session.setInitiative(combatant, newInit);
        dm.sendMessage(Component.text(combatant.getDisplayName() + " initiative: " + oldInit + " → " + newInit, NamedTextColor.GREEN));
    }

    private void handleRollForInitiative(Player dm) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (!session.isSetupPhase()) {
            dm.sendMessage(Component.text("Combat has already started!", NamedTextColor.RED));
            return;
        }

        if (session.getCombatants().isEmpty()) {
            dm.sendMessage(Component.text("No combatants added yet! Use /combat add <target>", NamedTextColor.RED));
            return;
        }

        // Roll initiative for everyone with detailed output
        session.broadcast(Component.empty());
        session.broadcast(Component.text("Rolling initiative...", NamedTextColor.YELLOW, TextDecoration.ITALIC));
        session.broadcast(Component.empty());

        for (Combatant combatant : session.getCombatants()) {
            int roll = DiceRoller.rollDice(1, 20);
            int bonus = combatant.getInitiativeBonus();
            int total = roll + bonus;
            combatant.setInitiative(total);

            // Show detailed roll breakdown to DM
            String bonusStr = bonus >= 0 ? "+" + bonus : String.valueOf(bonus);
            Component rollMsg = Component.text("  " + combatant.getDisplayName() + ": ", NamedTextColor.WHITE)
                .append(Component.text("[" + roll + "]", NamedTextColor.AQUA))
                .append(Component.text(" " + bonusStr + " (DEX)", NamedTextColor.GRAY))
                .append(Component.text(" = ", NamedTextColor.WHITE))
                .append(Component.text(String.valueOf(total), NamedTextColor.GREEN, TextDecoration.BOLD));
            dm.sendMessage(rollMsg);
        }

        // Sort by initiative
        session.sortByInitiative();

        // End setup phase, start combat
        session.startCombat();

        // Announce initiative order (DM sees real names, players see ??? for hidden)
        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        session.broadcast(Component.text("   INITIATIVE ORDER", NamedTextColor.GOLD, TextDecoration.BOLD));
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        int position = 1;
        for (Combatant c : session.getCombatants()) {
            // DM line - always shows real name with DM-only tags
            Component dmLine = Component.text(position + ". ", NamedTextColor.WHITE)
                .append(Component.text(c.getDisplayName(true), NamedTextColor.GREEN))
                .append(Component.text(" (" + c.getInitiative() + ")", NamedTextColor.GRAY));

            if (c.isEntity()) {
                dmLine = dmLine.append(Component.text(" [Entity]", NamedTextColor.DARK_GRAY));
            }
            if (c.isSurprised()) {
                dmLine = dmLine.append(Component.text(" [SURPRISED]", NamedTextColor.YELLOW));
            }
            if (c.isHidden()) {
                dmLine = dmLine.append(Component.text(" [hidden]", NamedTextColor.DARK_GRAY));
            }

            session.sendToDM(dmLine);

            // Player line - respects hidden status
            Component playerLine = Component.text(position + ". ", NamedTextColor.WHITE)
                .append(Component.text(c.getDisplayName(false), NamedTextColor.GREEN))
                .append(Component.text(" (" + c.getInitiative() + ")", NamedTextColor.GRAY));

            if (c.isSurprised()) {
                playerLine = playerLine.append(Component.text(" [SURPRISED]", NamedTextColor.YELLOW));
            }

            session.sendToPlayers(playerLine);
            position++;
        }

        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        session.broadcast(Component.empty());

        // Announce Round 1 and first turn
        session.broadcast(Component.text("━━━ Round 1 ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));

        Combatant first = session.getCurrentCombatant();
        if (first != null) {
            announceTurnStart(session, first);
        }
    }

    private void handleNextTurn(Player dm) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (session.isSetupPhase()) {
            dm.sendMessage(Component.text("Combat hasn't started yet. Use /combat rollforinitiative first.", NamedTextColor.RED));
            return;
        }

        Combatant previous = session.getCurrentCombatant();
        Combatant next = session.nextTurn();

        if (previous != null) {
            session.sendToDM(Component.text(previous.getDisplayName(true) + "'s turn ends.", NamedTextColor.GRAY));
            session.sendToPlayers(Component.text(previous.getDisplayName(false) + "'s turn ends.", NamedTextColor.GRAY));
        }

        if (next != null) {
            announceTurnStart(session, next);
        }
    }

    private void handleEndTurn(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (session.isSetupPhase()) {
            dm.sendMessage(Component.text("Combat hasn't started yet. Use /combat rollforinitiative first.", NamedTextColor.RED));
            return;
        }

        Combatant combatant;
        if (args.length < 2) {
            // Default to current combatant
            combatant = session.getCurrentCombatant();
            if (combatant == null) {
                dm.sendMessage(Component.text("No active turn to end.", NamedTextColor.RED));
                return;
            }
        } else {
            // Find by name (supports names with spaces by joining remaining args)
            String targetName = joinArgs(args, 1);
            combatant = findCombatantByName(session, targetName);

            if (combatant == null) {
                dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
                return;
            }
        }

        Combatant next = session.endTurn(combatant);
        session.sendToDM(Component.text(combatant.getDisplayName(true) + "'s turn ended by DM.", NamedTextColor.YELLOW));
        session.sendToPlayers(Component.text(combatant.getDisplayName(false) + "'s turn ended.", NamedTextColor.YELLOW));

        if (next != null && next != combatant) {
            announceTurnStart(session, next);
        }
    }

    private void handleJumpToTurn(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat turn <target>", NamedTextColor.RED));
            return;
        }

        // Support names with spaces
        String targetName = joinArgs(args, 1);
        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        session.jumpToTurn(combatant);
        session.sendToDM(Component.text("Jumping to " + combatant.getDisplayName(true) + "'s turn (out of order).", NamedTextColor.YELLOW));
        session.sendToPlayers(Component.text("Jumping to " + combatant.getDisplayName(false) + "'s turn.", NamedTextColor.YELLOW));
        announceTurnStart(session, combatant);
    }

    private void handleStatus(Player dm) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("━━━ Combat Status ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        dm.sendMessage(Component.text("Round: " + session.getRoundNumber(), NamedTextColor.WHITE));
        dm.sendMessage(Component.text("Combatants: " + session.getCombatants().size(), NamedTextColor.WHITE));

        Combatant current = session.getCurrentCombatant();
        if (current != null) {
            dm.sendMessage(Component.text("Current Turn: " + current.getDisplayName(), NamedTextColor.GREEN));
        }

        dm.sendMessage(Component.text("Phase: " + (session.isSetupPhase() ? "Setup" : "Active"), NamedTextColor.WHITE));
        dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private void handleEnd(Player dm) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        int rounds = session.getRoundNumber();
        int combatantsRemaining = session.getCombatants().size();

        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━ Combat Ended ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        session.broadcast(Component.text("Final Stats:", NamedTextColor.YELLOW));
        session.broadcast(Component.text("  Rounds: " + rounds, NamedTextColor.WHITE));
        session.broadcast(Component.text("  Combatants remaining: " + combatantsRemaining, NamedTextColor.WHITE));
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        session.endCombat();
        DM_SESSIONS.remove(dm.getUniqueId());

        dm.sendMessage(Component.text("Combat session ended.", NamedTextColor.GREEN));
    }

    private void handleReveal(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat reveal <entity>", NamedTextColor.RED));
            return;
        }

        // Support names with spaces - search by actual name (DM sees real names)
        String targetName = joinArgs(args, 1);
        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        if (!combatant.isHidden()) {
            dm.sendMessage(Component.text(combatant.getDisplayName() + " is already revealed.", NamedTextColor.YELLOW));
            return;
        }

        combatant.setHidden(false);
        session.updateScoreboard();

        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        session.broadcast(Component.text("The mysterious figure is revealed to be...", NamedTextColor.YELLOW));
        session.broadcast(Component.text(combatant.getDisplayName() + "!", NamedTextColor.RED, TextDecoration.BOLD));
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private void handleHide(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        if (args.length < 2) {
            dm.sendMessage(Component.text("Usage: /combat hide <entity>", NamedTextColor.RED));
            return;
        }

        // Support names with spaces
        String targetName = joinArgs(args, 1);
        Combatant combatant = findCombatantByName(session, targetName);

        if (combatant == null) {
            dm.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            return;
        }

        if (combatant.isPlayer()) {
            dm.sendMessage(Component.text("Cannot hide player names.", NamedTextColor.RED));
            return;
        }

        combatant.setHidden(true);
        session.updateScoreboard();
        dm.sendMessage(Component.text(combatant.getDisplayName() + " is now hidden (shown as ???).", NamedTextColor.GREEN));
    }

    // ==================== HELPER METHODS ====================

    private void announceTurnStart(CombatSession session, Combatant combatant) {
        // Check if surprised (loses their turn)
        if (combatant.isSurprised()) {
            // DM sees real name, players see ??? for hidden entities
            session.sendToDM(Component.text(combatant.getDisplayName(true) + " is surprised and loses their turn!", NamedTextColor.YELLOW));
            session.sendToPlayers(Component.text(combatant.getDisplayName(false) + " is surprised and loses their turn!", NamedTextColor.YELLOW));
            session.nextTurn();
            Combatant next = session.getCurrentCombatant();
            if (next != null) {
                announceTurnStart(session, next);
            }
            return;
        }

        int speed = combatant.getSpeed();

        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        if (combatant.isPlayer()) {
            session.broadcast(Component.text("It's " + combatant.getDisplayName() + "'s turn!", NamedTextColor.GREEN, TextDecoration.BOLD));
            session.broadcast(Component.text("• 1 Action", NamedTextColor.WHITE));
            session.broadcast(Component.text("• 1 Bonus Action", NamedTextColor.WHITE));
            session.broadcast(Component.text("• " + speed + " ft movement", NamedTextColor.WHITE));
        } else {
            // Entity turn - DM notification
            session.sendToDM(Component.text("DM: It's " + combatant.getDisplayName() + "'s turn", NamedTextColor.AQUA, TextDecoration.BOLD));
            session.sendToDM(Component.text("• 1 Action | 1 Bonus Action | " + speed + " ft movement", NamedTextColor.WHITE));

            // Players see hidden name version
            String playerVisibleName = combatant.getDisplayName(false);
            for (Combatant c : session.getCombatants()) {
                if (c.isPlayer() && !c.getId().equals(session.getDmId())) {
                    Player p = c.getPlayer();
                    if (p != null) {
                        p.sendMessage(Component.text(playerVisibleName + " prepares to act...", NamedTextColor.GRAY));
                    }
                }
            }
        }

        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private CombatSession getActiveSession(Player dm) {
        CombatSession session = DM_SESSIONS.get(dm.getUniqueId());
        if (session == null || !session.isActive()) {
            dm.sendMessage(Component.text("No active combat session. Use /combat start first.", NamedTextColor.RED));
            return null;
        }
        return session;
    }

    private boolean isDM(Player player) {
        return DMManager.isDM(player);
    }

    /**
     * Find an entity instance by display name.
     * Supports names with spaces via case-insensitive matching.
     * Skips entities already in the given combat session.
     */
    private DndEntityInstance findEntityByName(String name, CombatSession session) {
        String searchLower = name.toLowerCase();

        // Collect IDs of entities already in combat to skip them
        Set<UUID> alreadyInCombat = new HashSet<>();
        if (session != null) {
            for (Combatant c : session.getCombatants()) {
                if (c.isEntity()) {
                    alreadyInCombat.add(c.getId());
                }
            }
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.ArmorStand armorStand) {
                    DndEntityInstance instance = DndEntityInstance.getByArmorStand(armorStand);
                    if (instance != null && !alreadyInCombat.contains(instance.getInstanceId())) {
                        String displayLower = instance.getDisplayName().toLowerCase();
                        if (displayLower.equals(searchLower) || displayLower.startsWith(searchLower)) {
                            return instance;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find a combatant by name in the session.
     * Supports names with spaces via case-insensitive partial matching.
     */
    private Combatant findCombatantByName(CombatSession session, String name) {
        String searchLower = name.toLowerCase();

        // First try exact match
        for (Combatant c : session.getCombatants()) {
            if (c.getDisplayName().equalsIgnoreCase(name)) {
                return c;
            }
        }

        // Then try starts-with match
        for (Combatant c : session.getCombatants()) {
            if (c.getDisplayName().toLowerCase().startsWith(searchLower)) {
                return c;
            }
        }

        return null;
    }

    // ==================== STRING HELPERS ====================

    /**
     * Check if args contain a specific flag.
     */
    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Join args from startIndex to end into a single string.
     * Strips surrounding quotes to support "name with spaces" syntax.
     */
    private String joinArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) sb.append(" ");
            sb.append(args[i]);
        }
        return stripQuotes(sb.toString());
    }

    /**
     * Join args from startIndex, excluding flags (--something).
     * Strips surrounding quotes to support "name with spaces" syntax.
     */
    private String joinArgsExcludingFlags(String[] args, int startIndex) {
        if (startIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].startsWith("--")) continue;  // Skip flags
            if (!first) sb.append(" ");
            sb.append(args[i]);
            first = false;
        }
        return stripQuotes(sb.toString());
    }

    /**
     * Strip surrounding quotes from a string.
     * Handles both "double quotes" and 'single quotes'.
     */
    private String stripQuotes(String input) {
        if (input == null || input.length() < 2) return input;

        // Check for matching surrounding quotes
        char first = input.charAt(0);
        char last = input.charAt(input.length() - 1);

        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━ Combat Commands ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/combat start", NamedTextColor.YELLOW)
            .append(Component.text(" - Start combat setup", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat add <target> [--hidden]", NamedTextColor.YELLOW)
            .append(Component.text(" - Add combatant", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat add --radius <blocks>", NamedTextColor.YELLOW)
            .append(Component.text(" - Add nearby", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat remove <target>", NamedTextColor.YELLOW)
            .append(Component.text(" - Remove combatant", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat surprise <target>", NamedTextColor.YELLOW)
            .append(Component.text(" - Mark surprised", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat rollforinitiative", NamedTextColor.GREEN)
            .append(Component.text(" - Roll initiative, start combat", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat initiative <target> set <val>", NamedTextColor.YELLOW)
            .append(Component.text(" - Override initiative", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat nextturn", NamedTextColor.YELLOW)
            .append(Component.text(" - Next turn", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat endturn [target]", NamedTextColor.YELLOW)
            .append(Component.text(" - End turn (default: current)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat turn <target>", NamedTextColor.YELLOW)
            .append(Component.text(" - Jump to turn", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat reveal/hide <entity>", NamedTextColor.YELLOW)
            .append(Component.text(" - Show/hide name", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat end", NamedTextColor.YELLOW)
            .append(Component.text(" - End combat", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    // ==================== STATIC ACCESS ====================

    /**
     * Get the DM's active combat session.
     */
    public static CombatSession getDMSession(UUID dmId) {
        return DM_SESSIONS.get(dmId);
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return null;

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            completions.addAll(List.of("start", "add", "remove", "surprise", "initiative",
                "rollforinitiative", "nextturn", "endturn", "turn", "status", "end", "reveal", "hide"));
            return filterCompletions(completions, args[0]);
        }

        CombatSession session = DM_SESSIONS.get(player.getUniqueId());

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            switch (sub) {
                case "add" -> {
                    // Suggest --radius, online players, and entity names
                    completions.add("--radius");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                    // Add entity names would need entity registry iteration
                }
                case "remove", "surprise", "endturn", "turn" -> {
                    // Suggest combatants in session
                    if (session != null) {
                        for (Combatant c : session.getCombatants()) {
                            completions.add(c.getDisplayName());
                        }
                    }
                }
                case "initiative" -> {
                    // Suggest combatants
                    if (session != null) {
                        for (Combatant c : session.getCombatants()) {
                            completions.add(c.getDisplayName());
                        }
                    }
                }
                case "reveal" -> {
                    // Suggest hidden combatants (DM sees real names)
                    if (session != null) {
                        for (Combatant c : session.getCombatants()) {
                            if (c.isHidden()) {
                                completions.add(c.getDisplayName());
                            }
                        }
                    }
                }
                case "hide" -> {
                    // Suggest revealed entities
                    if (session != null) {
                        for (Combatant c : session.getCombatants()) {
                            if (!c.isHidden() && c.isEntity()) {
                                completions.add(c.getDisplayName());
                            }
                        }
                    }
                }
            }
            return filterCompletions(completions, args[1]);
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("--radius")) {
                completions.addAll(List.of("10", "20", "30", "50"));
            } else if (args[0].equalsIgnoreCase("initiative")) {
                completions.add("set");
            }
            return filterCompletions(completions, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("initiative") && args[2].equalsIgnoreCase("set")) {
            completions.addAll(List.of("1", "5", "10", "15", "20", "25"));
            return filterCompletions(completions, args[3]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String partial) {
        String lower = partial.toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }
}