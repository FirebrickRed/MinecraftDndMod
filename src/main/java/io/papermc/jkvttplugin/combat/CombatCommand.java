package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    // Subcommands that players can use on their own turn (no DM permission needed)
    private static final Set<String> PLAYER_ALLOWED = Set.of("action", "bonus", "endturn", "attack", "deathsave", "damage");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        // Check permissions: DM can use any command, players can only use PLAYER_ALLOWED on their turn
        if (!isDM(player) && !player.hasPermission("jkvtt.dm")) {
            if (!PLAYER_ALLOWED.contains(subcommand)) {
                sender.sendMessage(Component.text("Only the DM can use this command.", NamedTextColor.RED));
                return true;
            }
            // Verify player is in combat
            CombatSession playerSession = CombatSession.getSessionForPlayer(player.getUniqueId());
            if (playerSession == null) {
                sender.sendMessage(Component.text("You are not in combat.", NamedTextColor.RED));
                return true;
            }
        }

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
            case "finished" -> handleEnd(player, args);
            case "reveal" -> handleReveal(player, args);
            case "hide" -> handleHide(player, args);
            case "action" -> handleAction(player, args);
            case "bonus" -> handleBonusAction(player, args);
            case "movement" -> handleMovement(player, args);
            case "attack" -> handleAttack(player, args);
            case "damage" -> handleDamage(player, args);
            case "override" -> handleDamage(player, args); // DM-only (not in PLAYER_ALLOWED): apply corrective damage anytime
            case "heal" -> handleHeal(player, args);
            case "temphp" -> handleTempHp(player, args);
            case "deathsave" -> handleDeathSave(player, args);
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

    private void handleEndTurn(Player player, String[] args) {
        // Use resolveSession so both DM and players can end turns
        CombatSession session = resolveSession(player);
        if (session == null) return;

        if (session.isSetupPhase()) {
            player.sendMessage(Component.text("Combat hasn't started yet. Use /combat rollforinitiative first.", NamedTextColor.RED));
            return;
        }

        boolean isDM = isDM(player) || player.hasPermission("jkvtt.dm");
        Combatant combatant;
        if (args.length < 2) {
            // Default to current combatant
            combatant = session.getCurrentCombatant();
            if (combatant == null) {
                player.sendMessage(Component.text("No active turn to end.", NamedTextColor.RED));
                return;
            }

            // Non-DM players can only end their OWN turn (never an entity's).
            if (!isDM && (!combatant.isPlayer() || !combatant.getId().equals(player.getUniqueId()))) {
                player.sendMessage(Component.text("It's not your turn!", NamedTextColor.RED));
                return;
            }
        } else {
            // Only DM can specify a target
            if (!isDM) {
                player.sendMessage(Component.text("Only the DM can end other combatants' turns.", NamedTextColor.RED));
                return;
            }

            // Find by name (supports names with spaces by joining remaining args)
            String targetName = joinArgs(args, 1);
            combatant = findCombatantByName(session, targetName);

            if (combatant == null) {
                player.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
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
            dm.sendMessage(Component.text("Current Turn: " + current.getDisplayName(true), NamedTextColor.GREEN));
        }

        dm.sendMessage(Component.text("Phase: " + (session.isSetupPhase() ? "Setup" : "Active"), NamedTextColor.WHITE));

        // Full HP list — DM-only (players never see enemy HP; /combat status isn't player-allowed).
        for (Combatant c : session.getCombatants()) {
            String hp = c.getCurrentHp() + "/" + c.getMaxHp() + (c.getTempHp() > 0 ? " +" + c.getTempHp() : "");
            String tag = c.isDead() ? " [DEAD]" : c.isUnconscious() ? " [DOWN]" : "";
            dm.sendMessage(Component.text("  " + c.getDisplayName(true) + "  " + hp + tag,
                    c.isPlayer() ? NamedTextColor.AQUA : NamedTextColor.RED));
        }
        dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private void handleEnd(Player dm, String[] args) {
        CombatSession session = getActiveSession(dm);
        if (session == null) return;

        // "/combat finished" is deliberately distinct from "/combat endturn", so no confirm needed.
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

    // ==================== ACTION ECONOMY (Issue #98) ====================

    private void handleAction(Player player, String[] args) {
        // Resolve session: DM uses DM_SESSIONS, player uses PLAYER_SESSIONS
        CombatSession session = resolveSession(player);
        if (session == null) return;

        Combatant target = resolveActionTarget(player, session, args);
        if (target == null) return;

        TurnState state = target.getTurnState();
        if (state == null) {
            player.sendMessage(Component.text("It's not " + target.getDisplayName() + "'s turn.", NamedTextColor.RED));
            return;
        }

        if (state.isActionUsed()) {
            player.sendMessage(Component.text(target.getDisplayName() + " has already used their Action.", NamedTextColor.YELLOW));
            return;
        }

        state.useAction();
        session.broadcast(Component.text(target.getDisplayName(true) + " uses their Action.", NamedTextColor.YELLOW));
        session.sendActionBar(target);
    }

    private void handleBonusAction(Player player, String[] args) {
        CombatSession session = resolveSession(player);
        if (session == null) return;

        Combatant target = resolveActionTarget(player, session, args);
        if (target == null) return;

        TurnState state = target.getTurnState();
        if (state == null) {
            player.sendMessage(Component.text("It's not " + target.getDisplayName() + "'s turn.", NamedTextColor.RED));
            return;
        }

        if (state.isBonusActionUsed()) {
            player.sendMessage(Component.text(target.getDisplayName() + " has already used their Bonus Action.", NamedTextColor.YELLOW));
            return;
        }

        state.useBonusAction();
        session.broadcast(Component.text(target.getDisplayName(true) + " uses their Bonus Action.", NamedTextColor.YELLOW));
        session.sendActionBar(target);
    }

    private void handleMovement(Player player, String[] args) {
        CombatSession session = resolveSession(player);
        if (session == null) return;

        if (args.length >= 2 && args[1].equalsIgnoreCase("undo")) {
            // Movement undo - teleport back to turn start position
            Combatant current = session.getCurrentCombatant();
            if (current == null || current.getTurnState() == null) {
                player.sendMessage(Component.text("No active turn to undo movement for.", NamedTextColor.RED));
                return;
            }

            TurnState state = current.getTurnState();
            org.bukkit.Location startLoc = state.getTurnStartLocation();

            if (startLoc == null) {
                player.sendMessage(Component.text("No start location recorded.", NamedTextColor.RED));
                return;
            }

            // Teleport combatant back
            if (current.isPlayer()) {
                Player targetPlayer = current.getPlayer();
                if (targetPlayer != null) {
                    targetPlayer.teleport(startLoc);
                }
            } else {
                DndEntityInstance entity = current.getEntityInstance();
                if (entity != null && entity.getArmorStand() != null) {
                    entity.getArmorStand().teleport(startLoc);
                }
            }

            state.undoMovement();
            session.broadcast(Component.text(
                current.getDisplayName(true) + "'s movement has been undone.",
                NamedTextColor.YELLOW));
            session.sendActionBar(current);
        } else {
            // Show movement status for current combatant
            Combatant current = session.getCurrentCombatant();
            if (current == null || current.getTurnState() == null) {
                player.sendMessage(Component.text("No active turn.", NamedTextColor.RED));
                return;
            }

            TurnState state = current.getTurnState();
            player.sendMessage(Component.text(
                current.getDisplayName() + ": " +
                String.format("%.0f", state.getMovementUsed()) + "/" +
                state.getMovementBudget() + " ft used (" +
                String.format("%.0f", state.getMovementRemaining()) + " ft remaining)",
                NamedTextColor.YELLOW));
        }
    }

    // ==================== ATTACK COMMAND (Issue #99) ====================

    private void handleAttack(Player player, String[] args) {
        CombatSession session = resolveSession(player);
        if (session == null) return;

        if (session.isSetupPhase()) {
            player.sendMessage(Component.text("Cannot attack during setup phase. Roll initiative first!", NamedTextColor.RED));
            return;
        }

        boolean isDM = isDM(player) || player.hasPermission("jkvtt.dm");

        // Get the current combatant as the attacker
        Combatant attacker = session.getCurrentCombatant();
        if (attacker == null) {
            player.sendMessage(Component.text("No active turn.", NamedTextColor.RED));
            return;
        }

        // Non-DM may only act as their OWN character on their own turn.
        // (Entities are always DM-controlled — never let a player drive a monster.)
        if (!isDM && (!attacker.isPlayer() || !attacker.getId().equals(player.getUniqueId()))) {
            player.sendMessage(Component.text("It's not your turn!", NamedTextColor.RED));
            return;
        }

        // Parse flags
        boolean showMods = hasFlag(args, "--showmods");
        Integer providedRoll = getFlagValueInt(args, "--roll");
        Integer providedTotal = getFlagValueInt(args, "--total");

        // A physical d20 face must be 1-20; reject out-of-range values.
        if (providedRoll != null && (providedRoll < 1 || providedRoll > 20)) {
            player.sendMessage(Component.text("A d20 roll must be between 1 and 20.", NamedTextColor.RED));
            return;
        }

        // Action economy check (skip for --showmods)
        if (!showMods) {
            TurnState state = attacker.getTurnState();
            if (state == null) {
                player.sendMessage(Component.text("No active turn state.", NamedTextColor.RED));
                return;
            }
            if (state.isActionUsed()) {
                player.sendMessage(Component.text(attacker.getDisplayName() + " has already used their Action this turn.", NamedTextColor.YELLOW));
                return;
            }
        }

        // Collect positional args (after "attack", excluding flags and their values)
        List<String> positionalArgs = collectPositionalArgs(args, 1);
        if (positionalArgs.isEmpty()) {
            player.sendMessage(Component.text("Usage: /combat attack <target> <weapon>  (use 'unarmed' for an unarmed strike)", NamedTextColor.RED));
            return;
        }

        Combatant target;
        String weaponOrAttackName;

        if (attacker.isPlayer()) {
            // Players MUST name a weapon: the last token is the weapon (or 'fist'), the rest is the target.
            if (positionalArgs.size() < 2) {
                player.sendMessage(Component.text("Name your weapon: /combat attack <target> <weapon>", NamedTextColor.RED));
                player.sendMessage(Component.text("Tab-complete to see your weapons, or type 'unarmed' for an unarmed strike.", NamedTextColor.GRAY));
                return;
            }
            weaponOrAttackName = positionalArgs.get(positionalArgs.size() - 1);
            String targetName = String.join(" ", positionalArgs.subList(0, positionalArgs.size() - 1));
            target = findCombatantByName(session, stripQuotes(targetName));
            if (target == null) {
                player.sendMessage(Component.text("Target not found: " + targetName, NamedTextColor.RED));
                return;
            }
            // Validate the weapon: 'unarmed' or an item actually in the player's inventory.
            if (weaponOrAttackName.equalsIgnoreCase("unarmed") || weaponOrAttackName.equalsIgnoreCase("fist")) {
                weaponOrAttackName = "unarmed";
            } else if (!AttackHandler.getWeaponIdsInInventory(player).contains(weaponOrAttackName.toLowerCase())) {
                player.sendMessage(Component.text("You don't have a weapon called '" + weaponOrAttackName + "'.", NamedTextColor.RED));
                player.sendMessage(Component.text("Tab-complete to see your weapons, or use 'unarmed'.", NamedTextColor.GRAY));
                return;
            } else {
                weaponOrAttackName = weaponOrAttackName.toLowerCase();
            }
        } else {
            // Entities (DM-controlled) keep the flexible match; the attack name is optional.
            String allPositional = String.join(" ", positionalArgs);
            target = findCombatantByName(session, stripQuotes(allPositional));
            weaponOrAttackName = null;
            if (target == null && positionalArgs.size() >= 2) {
                weaponOrAttackName = positionalArgs.get(positionalArgs.size() - 1);
                String targetName = String.join(" ", positionalArgs.subList(0, positionalArgs.size() - 1));
                target = findCombatantByName(session, stripQuotes(targetName));
            }
            if (target == null) {
                player.sendMessage(Component.text("Target not found: " + allPositional, NamedTextColor.RED));
                return;
            }
        }

        // Block self-attack
        if (attacker.getId().equals(target.getId())) {
            player.sendMessage(Component.text("You cannot attack yourself!", NamedTextColor.RED));
            return;
        }

        // Can't attack something that's already dead (#119).
        if (target.isDead()) {
            player.sendMessage(Component.text(target.getDisplayName() + " is already dead.", NamedTextColor.YELLOW));
            return;
        }

        // Range check for player attacks: are you close enough to swing / within weapon range?
        // (--showmods just previews modifiers, so skip the range gate for it.)
        if (!showMods && attacker.isPlayer()) {
            DndWeapon rangeWeapon = AttackHandler.resolvePlayerWeapon(player, weaponOrAttackName);
            String rangeError = attackRangeError(attacker, target, rangeWeapon);
            if (rangeError != null) {
                player.sendMessage(Component.text(rangeError, NamedTextColor.RED));
                return;
            }
        }

        // Delegate to AttackHandler based on combatant type
        if (attacker.isPlayer()) {
            AttackHandler.executePlayerAttack(attacker, target, session, player,
                    weaponOrAttackName, providedRoll, providedTotal, showMods);
        } else {
            AttackHandler.executeEntityAttack(attacker, target, session, player,
                    weaponOrAttackName, providedRoll, providedTotal, showMods);
        }

        // Consume action + refresh action bar (skip for --showmods)
        if (!showMods) {
            TurnState state = attacker.getTurnState();
            if (state != null) {
                state.useAction();
                session.sendActionBar(attacker);
            }
        }
    }

    /**
     * Returns an error message if the attacker is out of range of the target for this weapon,
     * or null if the attack is in range (or positions can't be determined). 1 block ≈ 5 ft,
     * with a half-square tolerance so diagonally-adjacent still counts as "in reach".
     */
    private String attackRangeError(Combatant attacker, Combatant target, DndWeapon weapon) {
        Location a = attacker.getLocation();
        Location t = target.getLocation();
        if (a == null || t == null || a.getWorld() == null || !a.getWorld().equals(t.getWorld())) {
            return null; // can't determine positions — don't block the attack
        }
        double feet = a.distance(t) * 5.0;
        double reach = (weapon != null && weapon.hasProperty("reach")) ? 10.0 : 5.0;
        double tolerance = 2.5;

        boolean canThrowOrShoot = weapon != null && (weapon.isRanged() || weapon.hasProperty("thrown"));
        if (canThrowOrShoot) {
            if (weapon.isMelee() && feet <= reach + tolerance) return null; // used in melee
            int max = weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange();
            if (max > 0 && feet > max + tolerance) {
                return target.getDisplayName() + " is out of range — " + fmtFeet(feet) + " away (max " + max + " ft).";
            }
            return null; // within range
        }

        // Melee / unarmed: must be within reach.
        if (feet > reach + tolerance) {
            return target.getDisplayName() + " is too far — " + fmtFeet(feet) + " away, but your reach is "
                    + (int) reach + " ft. Move closer or use a ranged attack.";
        }
        return null;
    }

    private static String fmtFeet(double feet) { return String.format("%.0f ft", feet); }

    /**
     * Get the value after a flag (e.g., --roll 14 → "14").
     * Returns null if flag not found or no value follows.
     */
    private String getFlagValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * Get the integer value after a flag (e.g., --roll 14 → 14).
     * Also handles attached values like --roll14 → 14.
     * Returns null if flag not found, no value, or not a valid integer.
     */
    private Integer getFlagValueInt(String[] args, String flag) {
        // First try separated format: --roll 14
        String value = getFlagValue(args, flag);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Then try attached format: --roll14
        String flagLower = flag.toLowerCase();
        for (String arg : args) {
            String argLower = arg.toLowerCase();
            if (argLower.startsWith(flagLower) && argLower.length() > flagLower.length()) {
                String attached = arg.substring(flag.length());
                try {
                    return Integer.parseInt(attached);
                } catch (NumberFormatException e) {
                    // Not a valid number attached
                }
            }
        }
        return null;
    }

    /**
     * Collect positional (non-flag) args starting from startIndex.
     * Skips flags (--something) and their values.
     * Handles both "--flag value" and "--flagvalue" formats.
     */
    private List<String> collectPositionalArgs(String[] args, int startIndex) {
        List<String> positional = new ArrayList<>();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                // Check if this is a flag with an attached value (e.g., --roll20)
                // or a standalone flag like --showmods
                String flagPart = args[i].toLowerCase();
                boolean hasAttachedValue = flagPart.matches("--\\w+\\d+");

                if (!hasAttachedValue) {
                    // Separated format: skip flag AND next arg (its value)
                    // But only if the flag expects a value (valueless flags: --showmods, --crit)
                    boolean valueless = flagPart.equals("--showmods") || flagPart.equals("--crit");
                    if (!valueless && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        i++; // skip the value after the flag
                    }
                }
                // Either way, skip the flag itself
                continue;
            }
            positional.add(args[i]);
        }
        return positional;
    }

    // ==================== DAMAGE / HEAL / TEMP HP (Issue #100) ====================

    private void handleDamage(Player dm, String[] args) {
        CombatSession session = resolveSession(dm);
        if (session == null) return;

        // Players may apply damage only on their own turn; the DM (and /combat override) may anytime.
        boolean isDM = isDM(dm) || dm.hasPermission("jkvtt.dm");
        if (!isDM) {
            Combatant current = session.getCurrentCombatant();
            if (current == null || !current.isPlayer() || !current.getId().equals(dm.getUniqueId())) {
                dm.sendMessage(Component.text("You can only apply damage on your own turn.", NamedTextColor.RED));
                return;
            }
        }

        // /combat damage applies the damage from ONE attack hit. /combat override is the DM's
        // escape hatch to correct HP anytime and skips this gate.
        boolean isOverride = args.length > 0 && args[0].equalsIgnoreCase("override");
        Combatant attacker = session.getCurrentCombatant();
        if (!isOverride) {
            TurnState ts = attacker != null ? attacker.getTurnState() : null;
            if (ts == null || !ts.isDamagePending()) {
                dm.sendMessage(Component.text("No attack hit to apply damage for — use /combat attack first.", NamedTextColor.YELLOW));
                dm.sendMessage(Component.text("(DM: use /combat override to correct HP directly.)", NamedTextColor.GRAY));
                return;
            }
        }

        String rollStr = getFlagValue(args, "--roll");
        Integer total = getFlagValueInt(args, "--total");
        String type = getFlagValue(args, "--type");
        boolean crit = hasFlag(args, "--crit");

        List<String> positional = collectPositionalArgs(args, 1);
        if (positional.isEmpty()) {
            dm.sendMessage(Component.text("Usage: /combat damage <target> [amount] [--roll <dice>] [--total <n>] [--type <type>] [--crit]", NamedTextColor.RED));
            return;
        }

        // A trailing integer positional is a flat damage amount; the rest is the target name.
        Integer flat = null;
        if (positional.size() >= 2) {
            String last = positional.get(positional.size() - 1);
            try {
                flat = Integer.parseInt(last);
                positional = positional.subList(0, positional.size() - 1);
            } catch (NumberFormatException ignored) { /* last token is part of the name */ }
        }

        Combatant target = findCombatantByName(session, stripQuotes(String.join(" ", positional)));
        if (target == null) {
            dm.sendMessage(Component.text("Target not found: " + String.join(" ", positional), NamedTextColor.RED));
            return;
        }

        int damage;
        if (rollStr != null) {
            damage = DiceRoller.parseDiceRoll(rollStr);
            if (damage < 0) {
                // Not a dice formula — accept a plain flat number passed via --roll.
                try {
                    damage = Integer.parseInt(rollStr.trim());
                } catch (NumberFormatException e) {
                    dm.sendMessage(Component.text("Invalid dice/amount: " + rollStr, NamedTextColor.RED));
                    return;
                }
            } else {
                dm.sendMessage(Component.text("Rolled " + rollStr + " → " + damage, NamedTextColor.GRAY));
            }
        } else if (total != null) {
            damage = total;
        } else if (flat != null) {
            damage = flat;
        } else {
            dm.sendMessage(Component.text("Provide an amount, --roll <dice>, or --total <n>.", NamedTextColor.RED));
            return;
        }

        DamageHandler.applyDamage(session, target, damage, type, crit);
        session.refreshHpDisplays(target);

        // Consume the hit's damage window so it can't be applied again this turn.
        if (!isOverride && attacker != null && attacker.getTurnState() != null) {
            attacker.getTurnState().clearDamagePending();
        }
    }

    private void handleHeal(Player dm, String[] args) {
        CombatSession session = resolveSession(dm);
        if (session == null) return;

        String rollStr = getFlagValue(args, "--roll");
        Integer total = getFlagValueInt(args, "--total");

        List<String> positional = collectPositionalArgs(args, 1);
        if (positional.isEmpty()) {
            dm.sendMessage(Component.text("Usage: /combat heal <target> [amount] [--roll <dice>] [--total <n>]", NamedTextColor.RED));
            return;
        }

        Integer flat = null;
        if (positional.size() >= 2) {
            String last = positional.get(positional.size() - 1);
            try {
                flat = Integer.parseInt(last);
                positional = positional.subList(0, positional.size() - 1);
            } catch (NumberFormatException ignored) { /* part of the name */ }
        }

        Combatant target = findCombatantByName(session, stripQuotes(String.join(" ", positional)));
        if (target == null) {
            dm.sendMessage(Component.text("Target not found: " + String.join(" ", positional), NamedTextColor.RED));
            return;
        }

        int heal;
        if (rollStr != null) {
            heal = DiceRoller.parseDiceRoll(rollStr);
            if (heal < 0) {
                try {
                    heal = Integer.parseInt(rollStr.trim());
                } catch (NumberFormatException e) {
                    dm.sendMessage(Component.text("Invalid dice/amount: " + rollStr, NamedTextColor.RED));
                    return;
                }
            } else {
                dm.sendMessage(Component.text("Rolled " + rollStr + " → " + heal, NamedTextColor.GRAY));
            }
        } else if (total != null) {
            heal = total;
        } else if (flat != null) {
            heal = flat;
        } else {
            dm.sendMessage(Component.text("Provide an amount, --roll <dice>, or --total <n>.", NamedTextColor.RED));
            return;
        }

        DamageHandler.applyHealing(session, target, heal);
        session.refreshHpDisplays(target);
    }

    private void handleTempHp(Player dm, String[] args) {
        CombatSession session = resolveSession(dm);
        if (session == null) return;

        List<String> positional = collectPositionalArgs(args, 1);
        if (positional.size() < 2) {
            dm.sendMessage(Component.text("Usage: /combat temphp <target> <amount>", NamedTextColor.RED));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(positional.get(positional.size() - 1));
        } catch (NumberFormatException e) {
            dm.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
            return;
        }

        String targetName = stripQuotes(String.join(" ", positional.subList(0, positional.size() - 1)));
        Combatant target = findCombatantByName(session, targetName);
        if (target == null) {
            dm.sendMessage(Component.text("Target not found: " + targetName, NamedTextColor.RED));
            return;
        }

        DamageHandler.applyTempHp(session, target, amount);
        session.refreshHpDisplays(target);
    }

    // ==================== DEATH SAVES (Issue #101) ====================

    private void handleDeathSave(Player player, String[] args) {
        CombatSession session = resolveSession(player);
        if (session == null) return;

        boolean isDM = isDM(player) || player.hasPermission("jkvtt.dm");
        // Only the DM may supply a manual d20 — otherwise a downed player could
        // hand themselves a natural 20 and self-revive.
        Integer providedRoll = isDM ? getFlagValueInt(args, "--roll") : null;
        List<String> positional = collectPositionalArgs(args, 1);

        // DM may roll for a named downed player; otherwise you roll for yourself.
        Combatant target;
        if (isDM && !positional.isEmpty()) {
            target = findCombatantByName(session, stripQuotes(String.join(" ", positional)));
            if (target == null) {
                player.sendMessage(Component.text("Target not found: " + String.join(" ", positional), NamedTextColor.RED));
                return;
            }
        } else {
            target = findOwnCombatant(session, player.getUniqueId());
            if (target == null) {
                player.sendMessage(Component.text("You are not a combatant in this session.", NamedTextColor.RED));
                return;
            }
        }

        if (!target.isPlayer()) {
            player.sendMessage(Component.text("Only players make death saving throws.", NamedTextColor.RED));
            return;
        }
        if (target.isDead()) {
            player.sendMessage(Component.text(target.getDisplayName() + " is already dead.", NamedTextColor.RED));
            return;
        }
        if (!target.isUnconscious()) {
            player.sendMessage(Component.text(target.getDisplayName() + " is not making death saves.", NamedTextColor.RED));
            return;
        }
        if (target.isStabilized()) {
            player.sendMessage(Component.text(target.getDisplayName() + " is stable and does not need to roll.", NamedTextColor.YELLOW));
            return;
        }
        // A death save happens once, on the dying creature's own turn.
        if (!target.equals(session.getCurrentCombatant())) {
            player.sendMessage(Component.text("Death saves are made on " + target.getDisplayName() + "'s turn.", NamedTextColor.RED));
            return;
        }
        if (target.hasRolledDeathSaveThisTurn()) {
            player.sendMessage(Component.text(target.getDisplayName() + " has already rolled a death save this turn.", NamedTextColor.YELLOW));
            return;
        }

        DeathSaveHandler.rollDeathSave(session, target, providedRoll);
        target.setRolledDeathSaveThisTurn(true);
    }

    /** Find the combatant belonging to a specific player in a session. */
    private Combatant findOwnCombatant(CombatSession session, UUID playerId) {
        for (Combatant c : session.getCombatants()) {
            if (c.isPlayer() && c.getId().equals(playerId)) {
                return c;
            }
        }
        return null;
    }

    // ==================== SESSION/TARGET RESOLUTION ====================

    /**
     * Resolve which session this player belongs to (DM or player combatant).
     */
    private CombatSession resolveSession(Player player) {
        // First check if they're the DM
        CombatSession dmSession = DM_SESSIONS.get(player.getUniqueId());
        if (dmSession != null && dmSession.isActive()) {
            return dmSession;
        }

        // Otherwise check if they're a player combatant
        CombatSession playerSession = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (playerSession != null && playerSession.isActive()) {
            return playerSession;
        }

        player.sendMessage(Component.text("You are not in an active combat session.", NamedTextColor.RED));
        return null;
    }

    /**
     * Resolve the target combatant for action/bonus commands.
     * DM can specify a target name; players default to themselves (must be their turn).
     */
    private Combatant resolveActionTarget(Player player, CombatSession session, String[] args) {
        boolean isDM = isDM(player) || player.hasPermission("jkvtt.dm");

        if (isDM && args.length >= 2) {
            // DM specified a target
            String targetName = joinArgs(args, 1);
            Combatant target = findCombatantByName(session, targetName);
            if (target == null) {
                player.sendMessage(Component.text("Combatant not found: " + targetName, NamedTextColor.RED));
            }
            return target;
        }

        // Default to current combatant
        Combatant current = session.getCurrentCombatant();
        if (current == null) {
            player.sendMessage(Component.text("No active turn.", NamedTextColor.RED));
            return null;
        }

        // Non-DM players can only act as their OWN character on their own turn
        // (never as a DM-controlled entity).
        if (!isDM && (!current.isPlayer() || !current.getId().equals(player.getUniqueId()))) {
            player.sendMessage(Component.text("It's not your turn!", NamedTextColor.RED));
            return null;
        }

        return current;
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

        // Send action bar to the active player at turn start
        session.sendActionBar(combatant);
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
        String searchLower = name.toLowerCase().trim();

        // First try exact display name match
        for (Combatant c : session.getCombatants()) {
            if (c.getDisplayName().equalsIgnoreCase(name)) {
                return c;
            }
        }

        // Try matching with flexible # formatting (e.g., "Wolf 2", "Wolf#2", "Wolf #2")
        // Normalize both sides by removing spaces around #
        String normalizedSearch = searchLower.replaceAll("\\s*#\\s*", "#");
        for (Combatant c : session.getCombatants()) {
            String normalizedDisplay = c.getDisplayName().toLowerCase().replaceAll("\\s*#\\s*", "#");
            if (normalizedDisplay.equals(normalizedSearch)) {
                return c;
            }
        }

        // Try base name match (e.g., "Wolf" matches first "Wolf" combatant)
        for (Combatant c : session.getCombatants()) {
            if (c.getBaseName().equalsIgnoreCase(name)) {
                return c;
            }
        }

        // Then try starts-with match on display name
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
        player.sendMessage(Component.text("/combat action [target]", NamedTextColor.YELLOW)
            .append(Component.text(" - Mark action used", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat bonus [target]", NamedTextColor.YELLOW)
            .append(Component.text(" - Mark bonus action used", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat attack <target> [weapon]", NamedTextColor.GREEN)
            .append(Component.text(" - Attack a target", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat attack <target> --showmods", NamedTextColor.YELLOW)
            .append(Component.text(" - Show modifier breakdown", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat attack <target> --roll <d20>", NamedTextColor.YELLOW)
            .append(Component.text(" - Provide physical d20", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat attack <target> --total <N>", NamedTextColor.YELLOW)
            .append(Component.text(" - Provide final total", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat damage <target> [amt|--roll <dice>] [--type <t>]", NamedTextColor.RED)
            .append(Component.text(" - Apply damage (your turn, or DM)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat override <target> [amt|--roll <dice>]", NamedTextColor.RED)
            .append(Component.text(" - DM: apply corrective damage anytime", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat heal <target> [amt|--roll <dice>]", NamedTextColor.GREEN)
            .append(Component.text(" - Restore HP (DM)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat temphp <target> <amt>", NamedTextColor.AQUA)
            .append(Component.text(" - Grant temporary HP (DM)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat deathsave [--roll <d20>]", NamedTextColor.DARK_RED)
            .append(Component.text(" - Roll a death saving throw when down", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/combat movement [undo]", NamedTextColor.YELLOW)
            .append(Component.text(" - Check/undo movement", NamedTextColor.GRAY)));
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
                "rollforinitiative", "nextturn", "endturn", "turn", "status", "finished",
                "reveal", "hide", "action", "bonus", "movement", "attack",
                "damage", "override", "heal", "temphp", "deathsave"));
            return filterCompletions(completions, args[0]);
        }

        // Try DM session first, then player session for tab completion
        CombatSession session = DM_SESSIONS.get(player.getUniqueId());
        if (session == null) {
            session = CombatSession.getSessionForPlayer(player.getUniqueId());
        }

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
                case "remove", "surprise", "endturn", "turn", "action", "bonus", "attack", "damage", "override", "heal", "temphp", "deathsave" -> {
                    // Suggest combatants in session
                    if (session != null) {
                        for (Combatant c : session.getCombatants()) {
                            completions.add(c.getDisplayName());
                        }
                    }
                }
                case "movement" -> {
                    completions.add("undo");
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

        // Attack tab completion for weapon/attack names and flags
        if (args[0].equalsIgnoreCase("attack") && args.length >= 3) {
            String lastArg = args[args.length - 1];
            String prevArg = args[args.length - 2];

            // After --roll or --total, don't suggest anything
            if (prevArg.equalsIgnoreCase("--roll") || prevArg.equalsIgnoreCase("--total")) {
                return completions;
            }

            // If typing a flag
            if (lastArg.startsWith("--")) {
                completions.addAll(List.of("--showmods", "--roll", "--total"));
                return filterCompletions(completions, lastArg);
            }

            // Build the set of valid weapons/attacks for the current attacker.
            Combatant current = session != null ? session.getCurrentCombatant() : null;
            java.util.List<String> choices = new java.util.ArrayList<>();
            if (current != null) {
                if (current.isPlayer() && current.getPlayer() != null) {
                    choices.addAll(AttackHandler.getWeaponIdsInInventory(current.getPlayer()));
                    choices.add("unarmed");
                } else if (!current.isPlayer()) {
                    choices.addAll(AttackHandler.getEntityAttackNames(current));
                }
            }

            // Has a weapon already been named among the earlier tokens?
            boolean weaponChosen = false;
            java.util.Set<String> known = new java.util.HashSet<>();
            for (String ch : choices) known.add(ch.toLowerCase());
            for (int i = 2; i < args.length - 1; i++) {
                if (known.contains(args[i].toLowerCase())) { weaponChosen = true; break; }
            }

            if (!weaponChosen) {
                // Still choosing the weapon — offer weapons/attacks only, NOT flags (no --roll yet).
                completions.addAll(choices);
            } else {
                // Weapon chosen — now the roll flags make sense.
                completions.addAll(List.of("--showmods", "--roll", "--total"));
            }
            return filterCompletions(completions, lastArg);
        }

        // Damage/heal/temphp flag + damage-type completion
        if (args.length >= 3 && (args[0].equalsIgnoreCase("damage") || args[0].equalsIgnoreCase("override")
                || args[0].equalsIgnoreCase("heal") || args[0].equalsIgnoreCase("temphp"))) {
            String lastArg = args[args.length - 1];
            String prevArg = args[args.length - 2];

            if (prevArg.equalsIgnoreCase("--roll") || prevArg.equalsIgnoreCase("--total")) {
                return completions;
            }
            if (prevArg.equalsIgnoreCase("--type")) {
                completions.addAll(List.of("slashing", "piercing", "bludgeoning", "fire", "cold",
                    "lightning", "acid", "poison", "necrotic", "radiant", "psychic", "thunder", "force"));
                return filterCompletions(completions, lastArg);
            }
            if (lastArg.startsWith("--")) {
                if (args[0].equalsIgnoreCase("damage") || args[0].equalsIgnoreCase("override")) {
                    completions.addAll(List.of("--roll", "--total", "--type", "--crit"));
                } else if (args[0].equalsIgnoreCase("heal")) {
                    completions.addAll(List.of("--roll", "--total"));
                }
                return filterCompletions(completions, lastArg);
            }
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