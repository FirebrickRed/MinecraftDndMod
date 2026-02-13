package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.listeners.NpcListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles combat-related events: movement tracking with action bar refresh.
 *
 * Issue #98 - Turn Management & Action Economy
 */
public class CombatListener implements Listener {

    // ==================== MOVEMENT TRACKING ====================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Quick exit: only process actual block changes (not head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is in an active combat session (as themselves)
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        Combatant tracked = null;

        if (session != null && !session.isSetupPhase()) {
            Combatant current = session.getCurrentCombatant();
            if (current != null && current.isPlayer()
                && current.getId().equals(player.getUniqueId())) {
                // It's this player's turn — track their movement
                tracked = current;
            } else if (!session.getDmId().equals(player.getUniqueId())) {
                // Not this player's turn and not the DM — freeze movement
                event.setCancelled(true);
                return;
            }
        }

        // If not tracking as player, check if DM is possessing an entity in combat
        if (tracked == null) {
            ArmorStand possessed = NpcListener.getPossessedNpc(player.getUniqueId());
            if (possessed != null) {
                // Find the combat session containing this possessed entity
                CombatSession possessedSession = CombatSession.getSessionForEntity(possessed);
                if (possessedSession != null && !possessedSession.isSetupPhase()) {
                    Combatant current = possessedSession.getCurrentCombatant();
                    if (current != null && !current.isPlayer()
                        && current.getEntityInstance() != null
                        && current.getEntityInstance().getArmorStand().equals(possessed)) {
                        tracked = current;
                        session = possessedSession;
                    }
                }
            }
        }

        if (tracked == null || session == null) return;

        TurnState turnState = tracked.getTurnState();
        if (turnState == null) return;

        // Calculate movement as distance from turn start (1 block = 5 feet)
        // Walking back toward start reduces movement used (VTT-style)
        Location startLoc = turnState.getTurnStartLocation();
        if (startLoc == null) return;

        double dx = event.getTo().getBlockX() - startLoc.getBlockX();
        double dy = event.getTo().getBlockY() - startLoc.getBlockY();
        double dz = event.getTo().getBlockZ() - startLoc.getBlockZ();
        double blocksFromStart = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double feetFromStart = blocksFromStart * 5.0;
        turnState.setMovementUsed(feetFromStart);

        // Soft enforcement: warn once when first exceeding movement budget
        if (turnState.isOverMovementBudget() && !turnState.hasMovementWarned()) {
            turnState.setMovementWarned(true);
            player.sendMessage(Component.text(
                "\u26A0 You've exceeded your movement! ("
                + String.format("%.0f", turnState.getMovementUsed()) + "/"
                + turnState.getMovementBudget() + " ft)",
                NamedTextColor.RED));
        }

        // Refresh action bar on each block moved (keeps it visible while moving)
        session.sendActionBar(tracked);
    }
}
