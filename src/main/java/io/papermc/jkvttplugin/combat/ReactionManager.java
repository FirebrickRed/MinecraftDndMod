package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.DndAttack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reactions (Issue #147) — for now, opportunity attacks.
 *
 * <p>When a combatant moves out of an enemy's melee reach (without Disengaging), we <b>warn</b> that an
 * opportunity attack is available — but we do not fire anything mid-move (you might step out and back).
 * The decision surfaces at the mover's <b>end of turn</b>: the reactor's controller (DM for a creature,
 * the player for a PC) gets a clickable prompt, framed by whether the mover actually ended out of reach,
 * and always the DM's call. The attack costs the reactor its <b>reaction</b> (not its action) and only
 * once {@code /combat reaction} actually resolves. Reactions refresh at the start of the reactor's turn.
 *
 * <p>Side note (#155): "enemy" is currently players-vs-entities. Factions will refine who provokes whom.
 */
public final class ReactionManager {

    private static final Pattern NUM = Pattern.compile("(\\d+)");

    /** A provoked-but-unresolved opportunity attack: {@code reactor} may hit {@code mover} this turn. */
    private record PendingOA(UUID reactorId, UUID moverId) {}

    // Keyed by reactor id — a combatant has at most one pending opportunity attack at a time.
    private static final Map<UUID, PendingOA> pending = new ConcurrentHashMap<>();

    private ReactionManager() {}

    // ==================== DETECTION ====================

    /**
     * After {@code mover} steps from {@code from} to {@code to}, offer an opportunity attack to every
     * enemy whose melee reach it just left. Cheap enough to call on each block of movement.
     */
    public static void checkOpportunityAttacks(CombatSession session, Combatant mover, Location from, Location to) {
        if (session == null || mover == null || from == null || to == null) return;
        if (session.isSetupPhase()) return;
        if (mover.isDead()) return;
        // Disengage: your movement doesn't provoke this turn.
        if (mover.hasCondition("disengaging")) return;

        for (Combatant enemy : session.getCombatants()) {
            if (enemy == mover) continue;
            if (!isEnemy(mover, enemy)) continue;
            if (enemy.isDead() || enemy.isUnconscious()) continue;
            if (enemy.cannotAct()) continue;             // Incapacitated/Stunned/… can't react
            if (!enemy.isReactionAvailable()) continue;
            if (pending.containsKey(enemy.getId())) continue; // already provoked this turn

            Location eLoc = enemy.getLocation();
            if (eLoc == null || eLoc.getWorld() == null || !eLoc.getWorld().equals(to.getWorld())) continue;

            DndAttack oaAttack = meleeAttackFor(enemy);
            if (enemy.isEntity() && oaAttack == null) continue; // purely ranged creature — no OA

            double reachBlocks = meleeReachBlocks(enemy, oaAttack);
            double fromDist = eLoc.distance(from);
            double toDist = eLoc.distance(to);
            // Left reach: was inside, now outside. Record it and warn — decided at end of turn.
            if (fromDist <= reachBlocks && toDist > reachBlocks) {
                warn(session, enemy, mover);
            }
        }
    }

    /** Record a provoked-but-undecided OA and warn both sides. No prompt, no reaction spent yet. */
    private static void warn(CombatSession session, Combatant reactor, Combatant mover) {
        Player controller = reactor.isEntity() ? Bukkit.getPlayer(session.getDmId()) : reactor.getPlayer();
        if (controller == null) return; // no one to react (offline / no DM)
        pending.put(reactor.getId(), new PendingOA(reactor.getId(), mover.getId()));

        Player moverPlayer = mover.isPlayer() ? mover.getPlayer() : null;
        if (moverPlayer != null) {
            moverPlayer.sendActionBar(Component.text("⚠ Leaving " + reactor.getDisplayName()
                    + "'s reach — opportunity attack decided at end of turn.", NamedTextColor.GOLD));
        }
        controller.sendMessage(Component.text("⚠ " + mover.getDisplayName() + " is leaving " + reactor.getDisplayName()
                + "'s reach — you'll be offered an opportunity attack when their turn ends.", NamedTextColor.GRAY));
    }

    /**
     * Called when {@code mover}'s turn ends: surface a decision for each opportunity attack it provoked.
     * Framed by whether the mover actually ended out of reach, but always the reactor/DM's call to fire.
     */
    public static void resolveAtTurnEnd(CombatSession session, Combatant mover) {
        if (session == null || mover == null || pending.isEmpty()) return;
        for (Combatant reactor : session.getCombatants()) {
            PendingOA p = pending.get(reactor.getId());
            if (p == null || !p.moverId().equals(mover.getId())) continue;
            if (reactor.isDead() || reactor.isUnconscious() || reactor.cannotAct() || !reactor.isReactionAvailable()) {
                pending.remove(reactor.getId());
                continue;
            }
            Player controller = reactor.isEntity() ? Bukkit.getPlayer(session.getDmId()) : reactor.getPlayer();
            if (controller == null) { pending.remove(reactor.getId()); continue; }

            boolean outOfReach = !withinReach(reactor, mover);
            Component header = outOfReach
                    ? Component.text("⚡ Opportunity Attack — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text(reactor.getDisplayName() + " can strike " + mover.getDisplayName()
                                + ", who left its reach. Fire it?", NamedTextColor.YELLOW))
                    : Component.text("⚡ " + reactor.getDisplayName() + " — ", NamedTextColor.GRAY)
                        .append(Component.text(mover.getDisplayName() + " ended back within reach, so normally no OA. "
                                + "Force it only if you rule it provoked:", NamedTextColor.GRAY));
            controller.sendMessage(header);
            controller.sendMessage(reactionButtons(reactor));
        }
    }

    /** The attack/pass buttons for a reactor's pending OA (used at end of turn and in the reactions menu). */
    static Component reactionButtons(Combatant reactor) {
        Component buttons = Component.empty();
        if (reactor.isEntity()) {
            for (DndAttack atk : meleeAttacks(reactor)) buttons = buttons.append(attackButton(reactor, atk.getName()));
        } else {
            buttons = buttons.append(Component.text("[take it — add your weapon] ", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand("/combat reactions " + reactor.getDisplayName() + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Fill in your weapon (or 'unarmed') and your d20 roll."))));
        }
        return buttons.append(passButton(reactor));
    }

    private static Component attackButton(Combatant reactor, String attackName) {
        String base = "/combat reactions " + reactor.getDisplayName() + " " + attackName.toLowerCase().replace(" ", "_");
        return Component.text("[" + attackName + "] ", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(base + " --roll "))
                .hoverEvent(HoverEvent.showText(Component.text("Attack with " + attackName + " — fills the command, then type your d20 roll.")));
    }

    private static Component passButton(Combatant reactor) {
        return Component.text("[pass]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/combat reactions " + reactor.getDisplayName() + " pass"))
                .hoverEvent(HoverEvent.showText(Component.text("Hold your reaction.")));
    }

    // ==================== RESOLUTION (/combat reaction) ====================

    /** Whether a pending opportunity attack exists for this reactor. */
    public static boolean hasPending(Combatant reactor) {
        return reactor != null && pending.containsKey(reactor.getId());
    }

    /** The mover a reactor's pending OA targets, or null. */
    public static Combatant pendingMover(CombatSession session, Combatant reactor) {
        PendingOA p = pending.get(reactor.getId());
        if (p == null) return null;
        for (Combatant c : session.getCombatants()) if (c.getId().equals(p.moverId())) return c;
        return null;
    }

    public static void clearPending(Combatant reactor) {
        pending.remove(reactor.getId());
    }

    /** Drop every pending opportunity attack (e.g. when combat ends). */
    public static void clearAll() {
        pending.clear();
    }

    /** Drop opportunity attacks provoked by {@code moverId} — called when that mover acts again. */
    public static void clearForMover(UUID moverId) {
        pending.values().removeIf(p -> p.moverId().equals(moverId));
    }

    /** Mark a reactor's reaction spent and clear its pending OA. */
    public static void spendReaction(Combatant reactor) {
        reactor.setReactionAvailable(false);
        pending.remove(reactor.getId());
    }

    // ==================== ENEMY / REACH HELPERS ====================

    /** MVP faction line (#155 will refine): players and entities are on opposite sides. */
    private static boolean isEnemy(Combatant a, Combatant b) {
        return a.isPlayer() != b.isPlayer();
    }

    /** A melee attack the creature can use for an opportunity attack (smallest non-ranged reach), or null. */
    static DndAttack meleeAttackFor(Combatant c) {
        List<DndAttack> melee = meleeAttacks(c);
        return melee.isEmpty() ? null : melee.get(0);
    }

    /** All of an entity's melee attacks (reach without a "/" range split); empty for players. */
    static List<DndAttack> meleeAttacks(Combatant c) {
        java.util.List<DndAttack> out = new java.util.ArrayList<>();
        if (!c.isEntity() || c.getEntityInstance() == null) return out;
        List<DndAttack> attacks = c.getEntityInstance().getTemplate().getAttacks();
        if (attacks == null) return out;
        for (DndAttack a : attacks) {
            String reach = a.getReach();
            if (reach == null || !reach.contains("/")) out.add(a); // no "/" → melee/reach, not ranged
        }
        return out;
    }

    /** True if the mover is currently within the reactor's melee reach. */
    private static boolean withinReach(Combatant reactor, Combatant mover) {
        Location r = reactor.getLocation();
        Location m = mover.getLocation();
        if (r == null || m == null || r.getWorld() == null || !r.getWorld().equals(m.getWorld())) return false;
        return r.distance(m) <= meleeReachBlocks(reactor, meleeAttackFor(reactor));
    }

    /** The reach (in blocks) inside which the reactor threatens an opportunity attack. */
    private static double meleeReachBlocks(Combatant enemy, DndAttack entityAttack) {
        double feet = 5.0; // default melee reach
        if (enemy.isEntity() && entityAttack != null && entityAttack.getReach() != null) {
            Matcher m = NUM.matcher(entityAttack.getReach());
            if (m.find()) feet = Integer.parseInt(m.group(1));
        }
        // 5 ft = 1 block; a small buffer so diagonal adjacency still counts as "in reach".
        return feet / 5.0 + 0.6;
    }
}
