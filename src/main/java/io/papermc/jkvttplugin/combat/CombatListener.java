package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.dm.PossessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

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
            ArmorStand possessed = PossessionManager.getPossessedArmorStand(player.getUniqueId());
            if (possessed != null) {
                // Find the combat session containing this possessed entity
                CombatSession possessedSession = CombatSession.getSessionForEntity(possessed);
                if (possessedSession != null && !possessedSession.isSetupPhase()) {
                    Combatant current = possessedSession.getCurrentCombatant();
                    if (current != null && !current.isPlayer()
                        && current.getEntityInstance() != null
                        && current.getEntityInstance().isBody(possessed)) {
                        tracked = current;
                        session = possessedSession;
                    } else {
                        // Possessing an entity in combat but it's NOT its turn — freeze, like any
                        // other combatant off-turn (otherwise the DM could walk it around anytime).
                        event.setCancelled(true);
                        return;
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

        // A condition may set speed to 0 (Restrained, Paralyzed, …) — no moving away from the start (#150).
        if (tracked.isImmobilized() && feetFromStart > 0.5) {
            event.setCancelled(true);
            if (!turnState.hasMovementWarned()) {
                turnState.setMovementWarned(true);
                player.sendActionBar(Component.text("⚠ " + tracked.getDisplayName() + " can't move (a condition holds it in place).", NamedTextColor.RED));
            }
            return;
        }
        // Hard cap: you can't move outside your speed range (the green ring). Block any step that
        // would take you past your budget; moving back within range is always allowed.
        if (feetFromStart > turnState.getEffectiveMovementBudget()) {
            event.setCancelled(true);
            if (!turnState.hasMovementWarned()) {
                turnState.setMovementWarned(true);
                player.sendActionBar(Component.text("\u26A0 You've reached the edge of your movement.", NamedTextColor.RED));
            }
            return;
        }

        turnState.setMovementWarned(false); // back within range
        turnState.setMovementUsed(feetFromStart);

        // Leaving an enemy's melee reach may provoke an opportunity attack (#147).
        ReactionManager.checkOpportunityAttacks(session, tracked, event.getFrom(), event.getTo());

        // Refresh action bar on each block moved (keeps it visible while moving)
        session.sendActionBar(tracked);
    }

    /**
     * Freeze boats/mounts too: cancelling PlayerMoveEvent doesn't stop a player being carried by
     * a vehicle, so if a vehicle's passenger is a frozen combatant, pin the vehicle in place.
     */
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        // Only care about real block movement.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && isMovementFrozen(player)) {
                event.getVehicle().teleport(event.getFrom());
                return;
            }
        }
    }

    /** True when this player is in an active combat but it is NOT their turn (and they're not the DM). */
    private static boolean isMovementFrozen(Player player) {
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return false;
        Combatant current = session.getCurrentCombatant();
        if (current != null && current.isPlayer() && current.getId().equals(player.getUniqueId())) {
            return false; // it's their turn
        }
        return !session.getDmId().equals(player.getUniqueId());
    }
}
