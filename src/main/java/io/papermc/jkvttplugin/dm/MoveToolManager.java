package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.combat.TurnState;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The DM-mode Move tool (#107, redesign): select one or more entities by right-clicking them, then
 * right-click the ground to send them there. Works in and out of combat — in combat a selected
 * entity can only be moved on its own turn and the move is tracked against its speed budget (and
 * reversible with {@code /combat movement undo}); out of combat it's free repositioning. The
 * multi-select is the groundwork for group movement.
 */
public class MoveToolManager {

    // Entities each DM currently has selected (insertion-ordered).
    private static final Map<UUID, Set<ArmorStand>> selection = new ConcurrentHashMap<>();

    /** Add or remove an entity from the DM's selection (and glow it while selected). */
    public static void toggleSelect(Player dm, ArmorStand stand) {
        DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
        if (inst == null) {
            dm.sendActionBar(Component.text("That isn't a movable entity.", NamedTextColor.GRAY));
            return;
        }
        Set<ArmorStand> sel = selection.computeIfAbsent(dm.getUniqueId(), k -> new LinkedHashSet<>());
        if (sel.remove(stand)) {
            setSelectGlow(stand, false);
            dm.sendActionBar(Component.text("Deselected " + inst.getDisplayName()
                    + (sel.isEmpty() ? "." : " (" + sel.size() + " selected)."), NamedTextColor.GRAY));
        } else {
            // In combat you can only move the entity whose turn it is (group-select is out-of-combat only).
            if (!isCurrentTurnStand(stand) && inCombat(stand)) {
                dm.sendActionBar(Component.text("In combat you can only move the entity whose turn it is.", NamedTextColor.RED));
                return;
            }
            sel.add(stand);
            stand.setGlowing(true);
            dm.sendActionBar(Component.text("Selected " + inst.getDisplayName()
                    + " (" + sel.size() + ") — right-click the ground to move.", NamedTextColor.GREEN));
        }
    }

    /** Send every selected entity to {@code dest} (each keeps its own facing), then clear the selection. */
    public static void moveSelectionTo(Player dm, Location dest) {
        Set<ArmorStand> sel = selection.get(dm.getUniqueId());
        if (sel == null || sel.isEmpty()) {
            dm.sendActionBar(Component.text("Select an entity first (right-click it), then the ground.", NamedTextColor.GRAY));
            return;
        }
        List<ArmorStand> stands = new ArrayList<>();
        for (ArmorStand s : sel) if (s != null && s.isValid()) stands.add(s);
        int moved = 0;
        for (int i = 0; i < stands.size(); i++) {
            // Spread a group into a ring around the target so they don't stack on one block (#141).
            Location spot = stands.size() == 1 ? dest : spreadSpot(dest, i, stands.size());
            if (moveOne(dm, stands.get(i), spot)) moved++;
        }
        clearSelection(dm);
        if (moved > 0) {
            dm.sendActionBar(Component.text("Moved " + moved + " " + (moved == 1 ? "entity" : "entities") + ".", NamedTextColor.GREEN));
        }
    }

    /** Drop the DM's selection (clearing glow), e.g. when leaving DM mode. */
    public static void clearSelection(Player dm) {
        Set<ArmorStand> sel = selection.remove(dm.getUniqueId());
        if (sel != null) {
            for (ArmorStand s : sel) if (s != null && s.isValid()) setSelectGlow(s, false);
        }
    }

    // ==================== INTERNAL ====================

    /** A spot in a ring around {@code dest} for member {@code i} of {@code n} (so a group fans out). */
    private static Location spreadSpot(Location dest, int i, int n) {
        double radius = 0.9 + 0.35 * ((n - 1) / 8); // widen the ring a bit for big groups
        double angle = 2 * Math.PI * i / n;
        Location spot = dest.clone();
        spot.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
        return spot;
    }

    private static boolean moveOne(Player dm, ArmorStand stand, Location dest) {
        DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
        if (inst == null) return false;

        Location d = dest.clone();
        d.setYaw(stand.getLocation().getYaw());
        d.setPitch(stand.getLocation().getPitch());

        CombatSession session = CombatSession.getSessionForEntity(stand);
        if (session != null && !session.isSetupPhase()) {
            Combatant current = session.getCurrentCombatant();
            boolean itsTurn = current != null && current.isEntity() && current.getEntityInstance() != null
                    && current.getEntityInstance().isBody(stand);
            if (!itsTurn) {
                dm.sendActionBar(Component.text("It's not " + inst.getDisplayName() + "'s turn.", NamedTextColor.RED));
                return false;
            }
            if (current.isImmobilized()) {
                dm.sendActionBar(Component.text(inst.getDisplayName() + " can't move (a condition holds it in place).", NamedTextColor.RED));
                return false;
            }
            // Hard-cap the destination at the entity's speed — it can't move past its budget.
            Location capped = capToBudget(current, d);
            boolean wasCapped = capped != d;
            stand.teleport(capped);
            trackBudget(dm, session, current, capped);
            if (wasCapped) {
                dm.sendActionBar(Component.text(inst.getDisplayName() + " moved as far as its speed allows.", NamedTextColor.YELLOW));
            }
        } else {
            stand.teleport(d); // out of combat: free repositioning
        }
        return true;
    }

    /** Clamp {@code dest} so its straight-line distance from the turn's start stays within budget. */
    private static Location capToBudget(Combatant entity, Location dest) {
        TurnState ts = entity.getTurnState();
        if (ts == null || ts.getTurnStartLocation() == null) return dest;
        Location start = ts.getTurnStartLocation();
        double budgetBlocks = ts.getEffectiveMovementBudget() / 5.0;
        Vector fromStart = dest.toVector().subtract(start.toVector());
        double dist = fromStart.length();
        if (dist <= budgetBlocks || dist < 1.0e-6) return dest;
        Location capped = start.clone().add(fromStart.normalize().multiply(budgetBlocks));
        capped.setYaw(dest.getYaw());
        capped.setPitch(dest.getPitch());
        return capped;
    }

    /** Count the move against the entity's speed budget (straight-line from its turn start). */
    private static void trackBudget(Player dm, CombatSession session, Combatant entity, Location dest) {
        TurnState ts = entity.getTurnState();
        if (ts == null || ts.getTurnStartLocation() == null) return;
        Location start = ts.getTurnStartLocation();
        double dx = dest.getBlockX() - start.getBlockX();
        double dy = dest.getBlockY() - start.getBlockY();
        double dz = dest.getBlockZ() - start.getBlockZ();
        double feet = Math.sqrt(dx * dx + dy * dy + dz * dz) * 5.0;
        ts.setMovementUsed(feet);
        if (ts.isOverMovementBudget() && !ts.hasMovementWarned()) {
            ts.setMovementWarned(true);
            dm.sendMessage(Component.text("⚠ " + entity.getDisplayName() + " exceeded its movement ("
                    + String.format("%.0f", ts.getMovementUsed()) + "/" + ts.getEffectiveMovementBudget() + " ft).", NamedTextColor.RED));
        }
        session.sendActionBar(entity);
    }

    /** Turn selection glow on/off, but never clear the glow of the entity whose combat turn it is. */
    private static void setSelectGlow(ArmorStand stand, boolean on) {
        if (!on && isCurrentTurnStand(stand)) return;
        stand.setGlowing(on);
    }

    /** True if this entity is in an active (past-setup) combat. */
    private static boolean inCombat(ArmorStand stand) {
        CombatSession s = CombatSession.getSessionForEntity(stand);
        return s != null && !s.isSetupPhase();
    }

    private static boolean isCurrentTurnStand(ArmorStand stand) {
        CombatSession s = CombatSession.getSessionForEntity(stand);
        if (s == null || s.isSetupPhase()) return false;
        Combatant cur = s.getCurrentCombatant();
        return cur != null && cur.isEntity() && cur.getEntityInstance() != null
                && cur.getEntityInstance().isBody(stand);
    }
}
