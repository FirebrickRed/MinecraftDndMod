package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * <p>The problem it solves: an attack used to roll, hit, and offer damage in one breath, so a player
 * holding Shield found out too late — the damage was already applied and the table had to unwind it.
 * A window opens in the gap that already exists between {@code /combat attack} and
 * {@code /combat damage}, and <b>holds the damage</b> until everyone who could react has said yes or
 * no. No timers and no async: the seam is two separate commands, so the window is just a flag that
 * makes the second one wait.
 *
 * <p>Who gets asked: the creature that was hit, if it's a player character with its reaction
 * available who knows a spell with a {@code 1 reaction} casting time. Hitting a kobold opens no
 * window at all, so the common case costs nothing. The whole table sees the window open and close —
 * a reaction that only one person's chat log knows about is a reaction that gets missed.
 *
 * <p>Answering is {@code /combat cast <spell>} (casting it), {@code /combat reactions pass}
 * (declining), or the DM's {@code /combat reactions skip <who|all>} for anyone who's gone quiet.
 * When the last answer lands the held attack finishes: if the target's AC went up enough to matter
 * (Shield's +5), the hit becomes a miss and no damage prompt is offered at all.
 *
 * <p>Opportunity attacks live in {@link ReactionManager} — they're the other half of #147 and are
 * offered at the end of a move rather than blocking anything.
 */
public final class ReactionWindow {

    /** Open windows, keyed by the ATTACKER whose damage is being held. */
    private static final Map<UUID, ReactionWindow> open = new ConcurrentHashMap<>();

    /** The attack that's waiting: everything {@link AttackHandler} needs to finish it later. */
    record HeldAttack(Combatant attacker, Combatant target, String damageStr, String damageType,
                      boolean crit, String bonusLabel, int attackTotal, int acWhenRolled) {}

    private final CombatSession session;
    private final HeldAttack held;
    /** Reactors who haven't answered yet, in offer order. */
    private final Set<UUID> waiting = new LinkedHashSet<>();
    /** What each reactor did, for the closing summary. */
    private final Map<UUID, String> answers = new LinkedHashMap<>();

    private ReactionWindow(CombatSession session, HeldAttack held) {
        this.session = session;
        this.held = held;
    }

    // ==================== OPENING ====================

    /**
     * Open a window for a hit, if anyone can actually react to it.
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

        ReactionWindow window = new ReactionWindow(session,
                new HeldAttack(attacker, target, damageStr, damageType, crit, bonusLabel,
                        attackTotal, target.getArmorClass()));
        window.waiting.add(target.getId());
        open.put(attacker.getId(), window);

        session.broadcast(Component.text("⚡ Reaction window — ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(target.getDisplayName(true) + " was hit (" + attackTotal + " vs AC "
                        + target.getArmorClass() + ") and may react. Damage is held until they answer.",
                        NamedTextColor.YELLOW)));
        window.offerTo(target, options);
        window.tellDmHowToSkip();
        return true;
    }

    /**
     * The reaction-castable spells a combatant could use right now: a player character, with its
     * reaction in hand, who knows a spell whose casting time is a reaction. Entities don't hold
     * spells (their attacks are stat-block attacks), so they never open a window.
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

    /** Send the reactor their buttons: one per reaction spell, plus pass. */
    private void offerTo(Combatant reactor, List<DndSpell> options) {
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
                    .hoverEvent(HoverEvent.showText(Component.text("Decline for them and let the attack resolve."))));
        }
        skips = skips.append(Component.text("[skip all]", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand("/combat reactions skip all"))
                .hoverEvent(HoverEvent.showText(Component.text("Close the window and resolve the attack."))));
        session.sendToDM(skips);
    }

    // ==================== THE BLOCK ====================

    /** True while this attacker's damage is being held for a reaction. */
    public static boolean isBlocking(UUID attackerId) {
        return attackerId != null && open.containsKey(attackerId);
    }

    /** Tell whoever tried to apply damage why it won't go through yet. */
    public static void explainBlock(Player to, UUID attackerId) {
        ReactionWindow w = open.get(attackerId);
        if (w == null || to == null) return;
        List<String> names = new ArrayList<>();
        for (UUID id : w.waiting) {
            Combatant c = w.find(id);
            if (c != null) names.add(c.getDisplayName());
        }
        to.sendMessage(Component.text("⚡ Hold — waiting on " + String.join(", ", names)
                + " to use or pass a reaction.", NamedTextColor.GOLD));
        to.sendMessage(Component.text("   Damage applies as soon as they answer. (DM: /combat reactions skip all)",
                NamedTextColor.GRAY));
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

    /**
     * Record an answer and, if that was the last one outstanding, resolve the held attack.
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
     * Everyone has answered: say what happened, then finish the attack. A reaction that raised the
     * target's AC gets the hit re-checked against the new number — that's the whole point of Shield,
     * and the reason the damage had to wait. A critical hit lands regardless (RAW: a nat 20 always hits).
     */
    private void close() {
        open.values().remove(this);

        Combatant target = held.target();
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
