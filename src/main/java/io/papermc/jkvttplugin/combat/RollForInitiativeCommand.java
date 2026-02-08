package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the /rollforinitiative command.
 * Rolls initiative for all combatants and starts the combat.
 *
 * Issue #97 - Combat Session Foundation
 */
public class RollForInitiativeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        // Check DM permission
        if (!DMManager.isDM(player) && !player.hasPermission("jkvtt.dm")) {
            sender.sendMessage(Component.text("Only the DM can roll for initiative.", NamedTextColor.RED));
            return true;
        }

        // Get the DM's combat session
        CombatSession session = CombatCommand.getDMSession(player.getUniqueId());
        if (session == null || !session.isActive()) {
            player.sendMessage(Component.text("No active combat session. Use /combat start first.", NamedTextColor.RED));
            return true;
        }

        if (!session.isSetupPhase()) {
            player.sendMessage(Component.text("Combat has already started!", NamedTextColor.RED));
            return true;
        }

        if (session.getCombatants().isEmpty()) {
            player.sendMessage(Component.text("No combatants added yet! Use /combat add <target>", NamedTextColor.RED));
            return true;
        }

        // Roll initiative for everyone
        session.rollAllInitiative();

        // Announce initiative order
        session.broadcast(Component.empty());
        session.broadcast(Component.text("Rolling initiative...", NamedTextColor.YELLOW, TextDecoration.ITALIC));
        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        session.broadcast(Component.text("   INITIATIVE ORDER", NamedTextColor.GOLD, TextDecoration.BOLD));
        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        int position = 1;
        for (Combatant c : session.getCombatants()) {
            // Build the initiative line
            Component line = Component.text(position + ". ", NamedTextColor.WHITE)
                .append(Component.text(c.getDisplayName(true), NamedTextColor.GREEN))  // DM sees all names
                .append(Component.text(" (" + c.getInitiative() + ")", NamedTextColor.GRAY));

            // Add type indicator for DM
            if (c.isEntity()) {
                line = line.append(Component.text(" [Entity]", NamedTextColor.DARK_GRAY));
            }

            // Add status indicators
            if (c.isSurprised()) {
                line = line.append(Component.text(" [SURPRISED]", NamedTextColor.YELLOW));
            }
            if (c.isHidden()) {
                line = line.append(Component.text(" [hidden]", NamedTextColor.DARK_GRAY));
            }

            session.broadcast(line);
            position++;
        }

        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        session.broadcast(Component.empty());

        // Announce Round 1 and first turn
        session.broadcast(Component.text("━━━ Round 1 ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));

        Combatant first = session.getCurrentCombatant();
        if (first != null) {
            announceFirstTurn(session, first);
        }

        return true;
    }

    private void announceFirstTurn(CombatSession session, Combatant combatant) {
        // Handle surprised combatants
        if (combatant.isSurprised()) {
            session.broadcast(Component.text(combatant.getDisplayName(true) + " is surprised and loses their turn!", NamedTextColor.YELLOW));
            Combatant next = session.nextTurn();
            if (next != null) {
                announceFirstTurn(session, next);
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
            // Entity turn - different messages for DM vs players
            session.sendToDM(Component.text("DM: It's " + combatant.getDisplayName() + "'s turn", NamedTextColor.AQUA, TextDecoration.BOLD));
            session.sendToDM(Component.text("• 1 Action | 1 Bonus Action | " + speed + " ft movement", NamedTextColor.WHITE));

            // Players see the (possibly hidden) name
            String playerVisibleName = combatant.getDisplayName(false);
            for (Combatant c : session.getCombatants()) {
                if (c.isPlayer()) {
                    Player p = c.getPlayer();
                    if (p != null && !p.getUniqueId().equals(session.getDmId())) {
                        p.sendMessage(Component.text(playerVisibleName + " prepares to act...", NamedTextColor.GRAY));
                    }
                }
            }
        }

        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }
}