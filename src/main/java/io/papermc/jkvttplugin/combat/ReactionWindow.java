package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A blocking reaction window (#195).
 *
 * <p>The problem it solves: the game used to roll, hit, and offer damage in one breath, so a player
 * holding Shield found out too late and the table had to unwind it — and an opportunity attack was
 * a chat line that scrolled past, so it was simply missed. A window pauses the fight at the moment
 * the reaction matters, tells <b>everyone</b>, and <b>holds the next step</b> until each reactor has
 * said yes or no. No timers and no async: every pause lands on a seam that's already two separate
 * commands, so a window is just a flag that makes the second one wait.
 *
 * <p>Two triggers today, and what each one holds:
 * <ul>
 *   <li>{@link Trigger#HIT_BY_ATTACK} — the creature that was hit may react (Shield, Absorb
 *       Elements). Holds the <b>attacker's damage</b>. When it closes, a reaction that raised the
 *       target's AC gets the hit re-checked, so Shield turns a hit into a miss. A crit still lands.</li>
 *   <li>{@link Trigger#LEFT_REACH} — someone walked out of melee reach, so the creatures they left
 *       may take an opportunity attack. Holds the <b>mover's turn</b>: they can't attack, cast, use
 *       a feature or end their turn until it's settled, because the strike might drop them first.</li>
 * </ul>
 *
 * <p>Who gets asked is deliberately narrow, so the common case costs nothing: for a hit, a player
 * character with their reaction in hand who knows a spell cast as a reaction (hitting a kobold opens
 * no window at all); for a move, every enemy whose reach was left and who can still swing.
 *
 * <p>Answering is {@code /combat cast <spell>} or {@code /combat reactions <who> <attack>} (taking
 * it), {@code /combat reactions pass} (declining), or the DM's {@code /combat reactions skip
 * <who|all>} for anyone who's gone quiet. A window is only ever closed by being answered, so a
 * provoked attack can't sit around unresolved into a later round the way the old pending map did.
 */
public final class ReactionWindow {

    /** What set the window off, and therefore what it holds. */
    public enum Trigger { HIT_BY_ATTACK, LEFT_REACH }

    /**
     * Open windows, keyed by whoever is being held up: the attacker whose damage is waiting, or the
     * mover whose turn is waiting. One at a time per combatant.
     */
    private static final Map<UUID, ReactionWindow> open = new ConcurrentHashMap<>();

    /** The attack that's waiting: everything {@link AttackHandler} needs to finish it later. */
    record HeldAttack(Combatant attacker, Combatant target, String damageStr, String damageType,
                      boolean crit, String bonusLabel, int attackTotal, int acWhenRolled) {}

    private final CombatSession session;
    private final Trigger trigger;
    /** Whose next step is on hold — the attacker (HIT_BY_ATTACK) or the mover (LEFT_REACH). */
    private final UUID blockedId;
    /** Non-null for HIT_BY_ATTACK. */
    private final HeldAttack held;
    /** Reactors who haven't answered yet, in offer order. */
    private final Set<UUID> waiting = new LinkedHashSet<>();
    /** What each reactor did, for the closing summary. */
    private final Map<UUID, String> answers = new LinkedHashMap<>();

    private ReactionWindow(CombatSession session, Trigger trigger, UUID blockedId, HeldAttack held) {
        this.session = session;
        this.trigger = trigger;
        this.blockedId = blockedId;
        this.held = held;
    }

    // ==================== OPENING: A HIT ====================

    /**
     * Open a window for a hit, if the creature that was hit can actually react to it.
     *
     * @return true if the damage is now held (the caller must NOT prompt for damage); false if
     *         nobody could react and the attack should resolve immediately, as it always has.
     */
    static boolean openForHit(CombatSession session, Combatant attacker, Combatant target,
                              String damageStr, String damageType, boolean crit, String bonusLabel,
                              int attackTotal) {
        if (session == null || attacker == null || target == null) return false;
        // A window keyed by the attacker already exists (shouldn't happen — one attack at a time).
        if (open.containsKey(attacker.getId())) return true;

        List<DndSpell> options = reactionSpellsFor(target);
        if (options.isEmpty()) return false;

        ReactionWindow window = new ReactionWindow(session, Trigger.HIT_BY_ATTACK, attacker.getId(),
                new HeldAttack(attacker, target, damageStr, damageType, crit, bonusLabel,
                        attackTotal, target.getArmorClass()));
        window.waiting.add(target.getId());
        open.put(attacker.getId(), window);

        session.broadcast(Component.text("⚡ Reaction window — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(target.getDisplayName(true) + " was hit (" + attackTotal + " vs AC "
                        + target.getArmorClass() + ") and may react. Damage is held until they answer.",
                        NamedTextColor.YELLOW)));
        window.offerSpells(target, options);
        window.tellDmHowToSkip();
        return true;
    }

    // ==================== OPENING: AN OPPORTUNITY ATTACK ====================

    /**
     * Open a window for a move that left someone's reach (#147/#195). Called when the mover settles
     * rather than mid-step, so stepping out and back doesn't provoke.
     *
     * <p>The mover's turn is held: their attack, cast, feature and end-turn all wait. That's the
     * point — an opportunity attack that lands after the mover has already acted isn't an
     * interruption, it's bookkeeping, and it may well drop them before they get to act at all.
     *
     * @param reactors creatures whose reach the mover left and who can still swing
     * @return true if a window opened
     */
    static boolean openForOpportunity(CombatSession session, Combatant mover, List<Combatant> reactors) {
        if (session == null || mover == null || reactors == null || reactors.isEmpty()) return false;
        if (open.containsKey(mover.getId())) return false; // already holding this mover

        ReactionWindow window = new ReactionWindow(session, Trigger.LEFT_REACH, mover.getId(), null);
        for (Combatant r : reactors) window.waiting.add(r.getId());
        open.put(mover.getId(), window);

        List<String> names = new ArrayList<>();
        for (Combatant r : reactors) names.add(r.getDisplayName(true));
        session.broadcast(Component.text("⚡ Opportunity attack — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(mover.getDisplayName(true) + " left " + String.join(" and ", names)
                        + (names.size() > 1 ? "'" : "'s") + " reach. Their turn waits until it's answered.",
                        NamedTextColor.YELLOW)));

        for (Combatant r : reactors) window.offerOpportunity(r, mover);
        window.tellDmHowToSkip();
        return true;
    }

    /** The attack/pass buttons for a provoked reactor. */
    private void offerOpportunity(Combatant reactor, Combatant mover) {
        Player controller = reactor.isEntity() ? Bukkit.getPlayer(session.getDmId()) : reactor.getPlayer();
        if (controller == null) return;
        controller.sendMessage(Component.text("⚡ " + reactor.getDisplayName(true) + " may strike "
                + mover.getDisplayName(true) + " as they leave.", NamedTextColor.GOLD));
        controller.sendMessage(ReactionManager.reactionButtons(reactor));
    }

    // ==================== ELIGIBILITY ====================

    /**
     * The reaction-castable spells a combatant could use right now: a player character, with its
     * reaction in hand, who knows a spell whose casting time is a reaction. Entities don't hold
     * spells (their attacks are stat-block attacks), so they never open a hit window.
     */
    private static List<DndSpell> reactionSpellsFor(Combatant c) {
        List<DndSpell> out = new ArrayList<>();
        if (c == null || !c.isPlayer()) return out;
        if (c.isDead() || c.isUnconscious() || c.cannotAct()) return out;
        if (!c.isReactionAvailable()) return out;
        CharacterSheet sheet = c.getCharacterSheet();
        if (sheet == null) return out;
        Set<DndSpell> known = new LinkedHashSet<>();
        known.addAll(sheet.getKnownCantrips());
        known.addAll(sheet.getKnownSpells());
        for (DndSpell s : known) {
            String time = s.getCastingTime();
            if (time != null && time.toLowerCase().contains("reaction")) out.add(s);
        }
        return out;
    }

    /** Send the reactor their spell buttons, plus pass. */
    private void offerSpells(Combatant reactor, List<DndSpell> options) {
        Player p = reactor.getPlayer();
        if (p == null) return;
        p.sendMessage(Component.text("⚡ You were hit — react now, or the attack lands.", NamedTextColor.GOLD, TextDecoration.BOLD));
        Component row = Component.empty();
        for (DndSpell spell : options) {
            // Every reaction spell worth casting here is Self-range (Shield, Absorb Elements), and
            // /combat cast wants a target token either way — "me" resolves to the caster.
            String cmd = "/combat cast " + spell.getId() + (spell.isAoe() ? "" : " me");
            row = row.append(Component.text("[" + spell.getName() + "] ", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand(cmd))
                    .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd
                            + (spell.grantsAcBonus() ? "\n+" + spell.getAcBonus() + " AC — this attack is re-checked against it." : "")))));
        }
        row = row.append(Component.text("[pass]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand("/combat reactions pass"))
                .hoverEvent(HoverEvent.showText(Component.text("Take the hit — keep your reaction."))));
        p.sendMessage(row);
    }

    /** The DM always gets a way out, in case a reactor is AFK or has wandered off. */
    private void tellDmHowToSkip() {
        Component skips = Component.text("DM: ", NamedTextColor.GRAY);
        for (UUID id : waiting) {
            Combatant c = find(id);
            if (c == null) continue;
            String name = c.getDisplayName();
            String quoted = name.contains(" ") ? "\"" + name + "\"" : name;
            skips = skips.append(Component.text("[skip " + name + "] ", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand("/combat reactions skip " + quoted))
                    .hoverEvent(HoverEvent.showText(Component.text("Decline for them and let play continue."))));
        }
        skips = skips.append(Component.text("[skip all]", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand("/combat reactions skip all"))
                .hoverEvent(HoverEvent.showText(Component.text("Close the window and carry on."))));
        session.sendToDM(skips);
    }

    // ==================== THE BLOCK ====================

    /** True while this attacker's damage is being held for a reaction. */
    public static boolean isBlocking(UUID attackerId) {
        ReactionWindow w = attackerId == null ? null : open.get(attackerId);
        return w != null && w.trigger == Trigger.HIT_BY_ATTACK;
    }

    /** True while this combatant's turn is on hold for an opportunity attack they provoked. */
    public static boolean holdsTurnOf(Combatant mover) {
        if (mover == null) return false;
        ReactionWindow w = open.get(mover.getId());
        return w != null && w.trigger == Trigger.LEFT_REACH;
    }

    /** True while anything is holding this combatant up, for either reason. */
    public static boolean isHolding(UUID id) {
        return id != null && open.containsKey(id);
    }

    /** Tell whoever tried to carry on why it won't go through yet. */
    public static void explainBlock(Player to, UUID blockedId) {
        ReactionWindow w = open.get(blockedId);
        if (w == null || to == null) return;
        List<String> names = new ArrayList<>();
        for (UUID id : w.waiting) {
            Combatant c = w.find(id);
            if (c != null) names.add(c.getDisplayName());
        }
        String what = w.trigger == Trigger.HIT_BY_ATTACK
                ? "Damage applies as soon as they answer."
                : "Your turn continues as soon as it's resolved.";
        to.sendMessage(Component.text("⚡ Hold — waiting on " + String.join(", ", names)
                + " to use or pass a reaction.", NamedTextColor.GOLD));
        to.sendMessage(Component.text("   " + what + " (DM: /combat reactions skip all)", NamedTextColor.GRAY));
    }

    // ==================== ANSWERING ====================

    /** The window a combatant owes an answer to, or null. */
    static ReactionWindow awaiting(Combatant reactor) {
        if (reactor == null) return null;
        for (ReactionWindow w : open.values()) {
            if (w.waiting.contains(reactor.getId())) return w;
        }
        return null;
    }

    /** True if this combatant is currently being asked for a reaction. */
    public static boolean isAwaiting(Combatant reactor) {
        return awaiting(reactor) != null;
    }

    /** The mover a reactor is being offered an opportunity attack against, or null. */
    public static Combatant opportunityTarget(Combatant reactor) {
        ReactionWindow w = awaiting(reactor);
        if (w == null || w.trigger != Trigger.LEFT_REACH) return null;
        return w.find(w.blockedId);
    }

    /**
     * Record an answer and, if that was the last one outstanding, close the window.
     * {@code how} is what the table is told they did ("casts Shield", "passes", "skipped by the DM").
     */
    static void answer(Combatant reactor, String how) {
        ReactionWindow w = awaiting(reactor);
        if (w == null) return;
        w.waiting.remove(reactor.getId());
        w.answers.put(reactor.getId(), how);
        if (w.waiting.isEmpty()) w.close();
    }

    /** DM override: answer for one named reactor, or everyone still outstanding. */
    static boolean skip(CombatSession session, Combatant reactor, boolean all) {
        if (all) {
            List<ReactionWindow> windows = new ArrayList<>(open.values());
            boolean any = false;
            for (ReactionWindow w : windows) {
                if (w.session != session) continue;
                any = true;
                for (UUID id : new ArrayList<>(w.waiting)) {
                    w.waiting.remove(id);
                    w.answers.put(id, "skipped by the DM");
                }
                w.close();
            }
            return any;
        }
        ReactionWindow w = awaiting(reactor);
        if (w == null) return false;
        answer(reactor, "skipped by the DM");
        return true;
    }

    /**
     * Everyone has answered: say what happened, then let play continue.
     *
     * <p>For a held attack that means re-checking the hit — a reaction that raised the target's AC
     * (Shield's +5) can turn it into a miss, which is the whole reason the damage had to wait.
     * A critical hit lands regardless (RAW: a nat 20 always hits).
     */
    private void close() {
        open.values().remove(this);

        StringBuilder summary = new StringBuilder();
        for (Map.Entry<UUID, String> e : answers.entrySet()) {
            Combatant c = find(e.getKey());
            if (c == null) continue;
            if (summary.length() > 0) summary.append(", ");
            summary.append(c.getDisplayName(true)).append(" ").append(e.getValue());
        }
        if (summary.length() > 0) {
            session.broadcast(Component.text("⚡ " + summary + ".", NamedTextColor.GOLD));
        }

        if (trigger == Trigger.LEFT_REACH) {
            // Nothing left to resolve — the attacks (if any) already went through /combat reactions.
            // Drop any provocation that wasn't taken, so it can't be fired a round later (#195).
            for (UUID id : answers.keySet()) {
                Combatant c = find(id);
                if (c != null) ReactionManager.clearPending(c);
            }
            Combatant mover = find(blockedId);
            if (mover != null && mover.isPlayer() && mover.getPlayer() != null && !mover.isDead()) {
                mover.getPlayer().sendActionBar(Component.text("Your turn continues.", NamedTextColor.GREEN));
            }
            return;
        }

        Combatant target = held.target();
        int acNow = target.getArmorClass();
        boolean stillHits = held.crit() || held.attackTotal() >= acNow;
        if (!stillHits) {
            session.broadcast(Component.text("→ " + held.attackTotal() + " vs AC " + acNow
                    + (target.hasTempAc() ? " (" + target.getTempAcSource() + ")" : "")
                    + " — the attack MISSES after all. No damage.", NamedTextColor.GREEN, TextDecoration.BOLD));
            // The hit never happened: drop the attacker's pending-damage window so /combat damage
            // can't be used to apply it anyway.
            if (held.attacker().getTurnState() != null) held.attacker().getTurnState().clearDamagePending();
            return;
        }
        if (acNow != held.acWhenRolled()) {
            session.broadcast(Component.text("→ " + held.attackTotal() + " vs AC " + acNow
                    + " — still a hit.", NamedTextColor.YELLOW));
        }
        AttackHandler.sendDamagePrompt(session, held.attacker(), target, held.damageStr(),
                held.damageType(), held.crit(), held.bonusLabel());
    }

    // ==================== HOUSEKEEPING ====================

    private Combatant find(UUID id) {
        for (Combatant c : session.getCombatants()) if (c.getId().equals(id)) return c;
        return null;
    }

    /** Drop every open window (combat ended, or the session was torn down). */
    public static void clearAll() {
        open.clear();
    }

    /** Drop any window held by or against this combatant — they're dead, gone, or their turn ended. */
    public static void clearFor(UUID combatantId) {
        if (combatantId == null) return;
        open.entrySet().removeIf(e -> e.getKey().equals(combatantId)
                || e.getValue().waiting.contains(combatantId));
    }
}
