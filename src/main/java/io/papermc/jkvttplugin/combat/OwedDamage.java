package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Damage a graded save leaves owed in a fight (#274): each target's is its own entry with its own id, bound to
 * its target, its source, the dice, the damage type and whether it's full or half. The id rides in the
 * {@code /combat damage … owed <id>} buttons, so a button only ever answers the damage it was sent for.
 *
 * <p>It used to be written onto the caster's turn as the one "pending hit", so a second failed save
 * overwrote the first (only the last target's damage could be rolled), a save for half set the half flag on
 * whichever hit was pending, and a save answered after the caster's turn had nowhere to put its damage.
 * An attack's hit still lives on the attacker's turn (one at a time: {@code AttackCost} refuses a second
 * attack while a hit's damage is owed); it has an id of its own too ({@link TurnState#getPendingDamageId}).
 *
 * <p><b>One roll per cast (PHB p.196):</b> when something damages several creatures at the same time, its
 * damage is rolled once for all of them. Entries of the same cast share a {@code group} (the cast's id);
 * the first answer's roll lands on every target of that cast still waiting, each taking all or half by its
 * own save, and a target whose save comes in later takes that same roll straight away.
 */
public final class OwedDamage {

    private OwedDamage() {}

    /**
     * One target's damage from one save.
     *
     * @param dice  what's rolled ("3d6"); blank when the damage is a flat amount, given in {@code flat}
     * @param bonus what the game adds to the roll, labelled in {@code label}
     * @param half  the target saved, and the spell deals half on a save
     * @param group the cast it came from: everyone that cast caught shares one roll; 0 for none (its own roll)
     */
    public record Owed(String id, UUID sessionId, UUID sourceId, UUID targetId, String what,
                       String dice, String flat, int bonus, String label, String type, boolean half, long group) {}

    /** Damage that lands on a target: the whole roll, and what this target takes of it. */
    public record Landing(Owed owed, int full, int amount) {}

    /** What became of a new entry: it waits ({@code owed}, to prompt for), or its cast already rolled ({@code landing}). */
    public record Arrival(Owed owed, Landing landing) {}

    private static final Map<String, Owed> owed = new LinkedHashMap<>();
    /** Per fight, per cast: the one roll its damage came to. */
    private static final Map<UUID, Map<Long, Integer>> rolled = new HashMap<>();
    /** Answered, and waiting on the DM's [Apply] (combat.damage_approval: always). */
    private static final Set<String> held = new HashSet<>();

    /**
     * A target owes damage from a save. When its cast's damage was already rolled (another target's came in
     * first), it lands now at that roll; otherwise it waits for an answer.
     */
    public static Arrival arrive(UUID sessionId, UUID sourceId, UUID targetId, String what, String dice, String flat,
                                 int bonus, String label, String type, boolean half, long group) {
        // Starts with a letter, so no command parser can take it for a number or an amount.
        String id = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Owed o = new Owed(id, sessionId, sourceId, targetId, what, dice == null ? "" : dice, flat == null ? "" : flat,
                bonus, label == null ? "" : label, type == null ? "" : type, half, group);
        Integer full = group == 0 ? null : rolled.getOrDefault(sessionId, Map.of()).get(group);
        if (full != null) return new Arrival(null, new Landing(o, full, amount(full, half)));
        owed.put(id, o);
        return new Arrival(o, null);
    }

    /** The entry with this id while its damage is still owed; null once it's dealt, or its fight is over. */
    public static Owed find(String id) { return id == null ? null : owed.get(id); }

    /**
     * What's owed in fight {@code sessionId}, oldest first: by {@code sourceId} (null: anyone's) to
     * {@code targetId} (null: anyone).
     */
    public static List<Owed> owedIn(UUID sessionId, UUID sourceId, UUID targetId) {
        List<Owed> out = new ArrayList<>();
        for (Owed o : owed.values()) {
            if (!o.sessionId().equals(sessionId)) continue;
            if (sourceId != null && !sourceId.equals(o.sourceId())) continue;
            if (targetId != null && !targetId.equals(o.targetId())) continue;
            out.add(o);
        }
        return out;
    }

    /**
     * Why {@code answerer} can't answer {@code id} in fight {@code sessionId}, or null when they can. The DM may
     * answer any of the fight's; a player only the damage their own spell is owed, on their turn or not.
     */
    public static String refusal(UUID sessionId, String id, UUID answerer, boolean isDm) {
        Owed o = find(id);
        if (o == null) return "That damage has already been dealt (or its fight is over).";
        if (!o.sessionId().equals(sessionId)) return "That damage belongs to another fight.";
        if (!isDm && !answerer.equals(o.sourceId())) return "That isn't your damage to roll.";
        if (held.contains(id)) return isDm ? "That damage is waiting on your [Apply] / [Deny]." : "That damage is with the DM: wait for them to apply it.";
        return null;
    }

    /**
     * The roll for {@code id} is in: {@code full} is what it came to before any halving. It lands on that
     * target and on every other target of the same cast still waiting (one roll for all of them), each by its
     * own save; later saves from that cast take it too. Each entry is settled once: the returned landings are
     * no longer owed. Empty when {@code id} is already settled.
     */
    public static List<Landing> answer(String id, int full) {
        Owed first = owed.remove(id);
        held.remove(id);
        List<Landing> out = new ArrayList<>();
        if (first == null) return out;
        out.add(new Landing(first, full, amount(full, first.half())));
        if (first.group() == 0) return out;
        rolled.computeIfAbsent(first.sessionId(), k -> new HashMap<>()).put(first.group(), full);
        for (Iterator<Owed> it = owed.values().iterator(); it.hasNext(); ) {
            Owed o = it.next();
            if (o.group() != first.group() || !o.sessionId().equals(first.sessionId())) continue;
            it.remove();
            held.remove(o.id());
            out.add(new Landing(o, full, amount(full, o.half())));
        }
        return out;
    }

    /** A player's answer went to the DM (#175): nothing else answers it until they rule. */
    public static void hold(String id) { if (owed.containsKey(id)) held.add(id); }
    /** The DM turned it down: it's owed again, for another roll. */
    public static void release(String id) { held.remove(id); }

    /** Fight {@code sessionId} ended: its owed damage and its casts' rolls go with it. Other fights' stay. */
    public static void clearInFight(UUID sessionId) {
        if (sessionId == null) return;
        owed.values().removeIf(o -> {
            boolean mine = sessionId.equals(o.sessionId());
            if (mine) held.remove(o.id());
            return mine;
        });
        rolled.remove(sessionId);
    }

    static int amount(int full, boolean half) { return half ? Math.max(0, full / 2) : full; }

    static void clear() { owed.clear(); rolled.clear(); held.clear(); }

    // ==================== IN THE FIGHT ====================

    /**
     * Send the roll buttons for {@code o} to whoever rolls it: the caster, when they're a character in the
     * fight, and the DM. Its id is in the command, so it answers this damage and no other.
     */
    public static void prompt(CombatSession session, Owed o) {
        Combatant target = session.getCombatantById(o.targetId());
        String name = target != null ? target.getDisplayName() : "the target";
        String quoted = name.contains(" ") ? "\"" + name + "\"" : name;
        String base = "/combat damage " + quoted + " owed " + o.id() + " ";
        String saved = o.half() ? " (saved: half)" : "";
        Component line;
        if (!o.dice().isBlank()) {
            line = RollPrompt.line("💥 Roll " + o.dice() + " " + o.what() + " damage against " + name + saved + ":",
                    NamedTextColor.YELLOW, base, o.dice(), o.bonus() == 0 ? null : o.label());
        } else {
            String amt = o.flat().isBlank() ? "1" : o.flat();
            String cmd = base + amt;
            line = Component.text("→ Apply " + o.what() + " damage (" + amt + ") to " + name + saved + ": ", NamedTextColor.YELLOW)
                    .append(Component.text("[click to apply]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand(cmd))
                            .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd))));
        }
        Combatant source = session.getCombatantById(o.sourceId());
        Player caster = source != null && source.isPlayer() ? source.getPlayer() : null;
        if (caster != null) caster.sendMessage(line);
        if (caster == null || !caster.getUniqueId().equals(session.getDmId())) session.sendToDM(line);
    }

    /**
     * Deal the landings, in order. {@code typeOverride}: a {@code type <t>} the roller typed, else each entry's
     * own. The first is the one that was answered; the rest are the same cast's other targets, told as such.
     */
    public static void land(CombatSession session, List<Landing> landings, String typeOverride) {
        for (int i = 0; i < landings.size(); i++) {
            Landing l = landings.get(i);
            Combatant target = session.getCombatantById(l.owed().targetId());
            if (target == null) {
                session.sendToDM(Component.text(l.owed().what() + "'s damage had nobody to land on (they've left the fight).", NamedTextColor.GRAY));
                continue;
            }
            String how = l.owed().half() ? "saved, half: " + l.full() + " → " + l.amount() : String.valueOf(l.amount());
            if (i > 0) {
                session.broadcast(Component.text("   " + target.getDisplayName(true) + " takes the same " + l.owed().what()
                        + " roll (" + how + ").", NamedTextColor.GRAY));
            } else if (l.owed().half()) {
                session.broadcast(Component.text("   " + target.getDisplayName(true) + " saved: half damage, " + l.full() + " → " + l.amount() + ".", NamedTextColor.GRAY));
            }
            String type = typeOverride != null && !typeOverride.isBlank() ? typeOverride : l.owed().type();
            DamageHandler.applyDamage(session, target, l.amount(), type.isBlank() ? null : type, false);
            session.refreshHpDisplays(target);
        }
    }
}
