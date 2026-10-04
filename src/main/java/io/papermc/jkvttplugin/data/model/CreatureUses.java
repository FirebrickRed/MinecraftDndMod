package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * What one spawned creature has used up (#256, #257): its limited abilities ("Fire Breath 3/Day",
 * "Recharge 5-6") and its ammunition. The template says what the limits ARE; this is the count, which
 * belongs to the creature (two dragons each have their own breath) and is saved with it.
 *
 * <p>"X/Day" comes back by the {@link DayRule} in config.yml: 24 in-game hours after the first use, or
 * at the next dawn on the world clock. A long rest brings it back sooner either way.
 *
 * <p>Ammunition (MM p.11): a monster carries 2d10 pieces for a bow or crossbow and 2d4 thrown
 * weapons, rolled the first time it's needed, unless the attack's {@code ammunition:} says otherwise.
 */
public final class CreatureUses {

    /**
     * When "X/Day" comes back ({@code creatures.per_day} in config.yml). Either way a long rest brings it
     * back sooner.
     */
    public enum DayRule {
        /** 24 in-game hours after the first of those uses. */
        HOURS_24,
        /** At the next dawn on the world clock, whenever it was used. */
        DAWN
    }

    /** A Minecraft day is 24,000 ticks, starting at dawn; an in-game hour is 1,000. */
    private static final long TICKS_PER_DAY = 24000L, TICKS_PER_HOUR = 1000L;

    /** Spent uses per ability (by {@link #key}), and the world time (ticks) of the first of them (for X/Day). */
    private final Map<String, Integer> used = new LinkedHashMap<>();
    private final Map<String, Long> usedAt = new LinkedHashMap<>();
    /** "Recharge X-Y" abilities whose d6 is waiting to be rolled this turn. Not saved: a restart restarts the turn. */
    private final java.util.Set<String> rollDue = new java.util.LinkedHashSet<>();
    /** Ammunition left, by pool ("ammo:arrow", "thrown:spear"). Absent = not rolled yet. */
    private final Map<String, Integer> ammo = new LinkedHashMap<>();

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    // ==================== LIMITED USE ====================

    private static boolean dayHasPassed(long at, long now, DayRule rule) {
        return rule == DayRule.DAWN ? at / TICKS_PER_DAY < now / TICKS_PER_DAY : now - at >= TICKS_PER_DAY;
    }

    /** How many uses are left right now (an X/Day ability refills once its day has passed). Unlimited: Integer.MAX_VALUE. */
    public int left(DndAttack a, long now, DayRule rule) {
        if (a == null || !a.isLimited()) return Integer.MAX_VALUE;
        String k = key(a.getName());
        if (a.getRecharge() == DndAttack.Recharge.DAY && used.containsKey(k) && dayHasPassed(usedAt.getOrDefault(k, now), now, rule)) {
            used.remove(k);
            usedAt.remove(k);
        }
        return Math.max(0, a.getUses() - used.getOrDefault(k, 0));
    }

    /** Why it can't be used now ("Fire Breath is spent: it recharges on a 5-6."), or null. */
    public String refusal(DndAttack a, long now, DayRule rule) {
        if (left(a, now, rule) > 0) return null;
        String k = key(a.getName());
        return a.getName() + " is spent: " + switch (a.getRecharge()) {
            case DAY -> rule == DayRule.DAWN ? "it comes back at dawn, or after a long rest."
                    : "it comes back in about " + hoursUntilBack(usedAt.getOrDefault(k, now), now)
                            + " (24 hours after it was first used), or after a long rest.";
            case ROLL -> rollDue.contains(k) ? "roll its recharge first (a " + rollText(a) + " brings it back)."
                    : "it recharges on a " + rollText(a) + " at the start of its turn.";
            case SHORT_REST -> "it comes back after a short or long rest.";
            case LONG_REST -> "it comes back after a long rest.";
        };
    }

    private static String rollText(DndAttack a) {
        return a.getRechargeMin() >= 6 ? "6" : a.getRechargeMin() + "-6";
    }

    private static String hoursUntilBack(long at, long now) {
        long ticks = Math.max(0, TICKS_PER_DAY - (now - at));
        long hours = (ticks + TICKS_PER_HOUR - 1) / TICKS_PER_HOUR;
        return hours <= 1 ? "an hour" : hours + " hours";
    }

    /** One use spent. Returns what to tell the DM ("Fire Breath: 2 of 3 left (3/Day)."), or null if unlimited. */
    public String spend(DndAttack a, long now, DayRule rule) {
        if (a == null || !a.isLimited()) return null;
        int left = left(a, now, rule); // also applies a refill first
        String k = key(a.getName());
        if (used.merge(k, 1, Integer::sum) == 1) usedAt.put(k, now); // the day runs from the first use
        rollDue.remove(k);
        int remaining = Math.max(0, left - 1);
        return a.getName() + ": " + (a.getRecharge() == DndAttack.Recharge.ROLL
                ? "used (" + a.limitLabel() + ")."
                : remaining + " of " + a.getUses() + " left (" + a.limitLabel() + ").");
    }

    /**
     * The start of the creature's turn: every spent "Recharge X-Y" ability is owed a d6. Returns them,
     * so the DM can be handed the roll buttons; {@link #answerRecharge} takes the result. One roll per
     * turn each: asking again before it's answered doesn't add another.
     */
    public List<DndAttack> rechargesDue(List<DndAttack> attacks) {
        List<DndAttack> due = new ArrayList<>();
        if (attacks == null) return due;
        for (DndAttack a : attacks) {
            if (!a.isLimited() || a.getRecharge() != DndAttack.Recharge.ROLL) continue;
            String k = key(a.getName());
            if (used.getOrDefault(k, 0) <= 0) continue;
            rollDue.add(k);
            due.add(a);
        }
        return due;
    }

    public boolean isRechargeDue(DndAttack a) {
        return a != null && rollDue.contains(key(a.getName()));
    }

    /**
     * The d6 for a recharge that's due. Returns the line for the DM ("Web (Recharge 5-6): rolled 5,
     * recharged."), or null when no roll is owed (not spent, or already rolled this turn).
     */
    public String answerRecharge(DndAttack a, int d6) {
        if (a == null || !rollDue.remove(key(a.getName()))) return null;
        if (d6 >= a.getRechargeMin()) {
            used.remove(key(a.getName()));
            return a.getName() + " (" + a.limitLabel() + "): rolled " + d6 + ", recharged.";
        }
        return a.getName() + " (" + a.limitLabel() + "): rolled " + d6 + ", still spent.";
    }

    /**
     * A rest. Short: "after a Short or Long Rest" abilities. Long: everything (X/Day and long-rest ones
     * too, and anything waiting on a recharge roll). Returns the names that came back.
     */
    public List<String> rest(List<DndAttack> attacks, boolean longRest) {
        List<String> back = new ArrayList<>();
        if (attacks == null) return back;
        for (DndAttack a : attacks) {
            if (!a.isLimited()) continue;
            boolean refills = longRest || a.getRecharge() == DndAttack.Recharge.SHORT_REST
                    || a.getRecharge() == DndAttack.Recharge.ROLL; // an hour is plenty of turns
            String k = key(a.getName());
            if (refills && used.remove(k) != null) { usedAt.remove(k); rollDue.remove(k); back.add(a.getName()); }
        }
        return back;
    }

    /** Joining a fight: a "Recharge X-Y" ability spent in an earlier fight has had time to come back. */
    public void recoverRolls(List<DndAttack> attacks) {
        if (attacks == null) return;
        for (DndAttack a : attacks) {
            if (a.isLimited() && a.getRecharge() == DndAttack.Recharge.ROLL) { used.remove(key(a.getName())); rollDue.remove(key(a.getName())); }
        }
    }

    // ==================== AMMUNITION ====================

    /**
     * Which pool an attack draws from, and what it starts with.
     * {@code spendsOne} is false for a melee swing with a weapon that can also be thrown: it needs one
     * in hand, but doesn't lose it.
     */
    public record Pool(String key, String dice, String label, boolean spendsOne) {}

    /**
     * The pool for this attack, or null when it uses none (a bite, a spell, a plain sword).
     * A bow or crossbow draws its weapon's ammunition; a weapon thrown by ANY of the creature's attacks
     * is counted, so the last spear thrown leaves none to stab with.
     */
    public static Pool poolFor(DndAttack a, List<DndAttack> all, Function<String, DndWeapon> weapons) {
        if (a == null || a.getItem() == null || a.getItem().isBlank()) return null;
        DndWeapon w = weapons.apply(a.getItem());
        if (w == null) return null;
        if (w.usesAmmunition() && w.getAmmunition() != null && !w.getAmmunition().isBlank()) {
            return new Pool("ammo:" + w.getAmmunition().toLowerCase(), "2d10", w.getAmmunition().replace('_', ' ') + "s", true);
        }
        if (!w.hasProperty("thrown")) return null;
        boolean thrownSomewhere = false;
        if (all != null) for (DndAttack o : all) if (a.getItem().equalsIgnoreCase(o.getItem()) && isThrow(o)) thrownSomewhere = true;
        if (!thrownSomewhere) return null; // it only ever stabs with it: nothing to run out of
        return new Pool("thrown:" + a.getItem().toLowerCase(), "2d4", w.getName().toLowerCase() + "s", isThrow(a));
    }

    /** A thrown attack is written with a range split: {@code reach: "20/60 ft."}. */
    static boolean isThrow(DndAttack a) {
        return a.getReach() != null && a.getReach().contains("/");
    }

    /** The YAML's own count for a pool: the first {@code ammunition:} among the attacks that share it; null = default. */
    public static Integer overrideFor(Pool pool, List<DndAttack> all, Function<String, DndWeapon> weapons) {
        if (all == null) return null;
        for (DndAttack o : all) {
            Pool p = poolFor(o, all, weapons);
            if (p != null && p.key().equals(pool.key()) && o.getAmmunitionOverride() != null) return o.getAmmunitionOverride();
        }
        return null;
    }

    /**
     * Pieces left in the pool, rolling the starting amount the first time. -1 = unlimited
     * ({@code ammunition: unlimited}).
     */
    public int ammoLeft(Pool pool, List<DndAttack> all, Function<String, DndWeapon> weapons, Function<String, Integer> roll) {
        Integer have = ammo.get(pool.key());
        if (have != null) return have;
        Integer override = overrideFor(pool, all, weapons);
        int start = override != null ? override : Math.max(1, roll.apply(pool.dice()));
        ammo.put(pool.key(), start);
        return start;
    }

    /** One piece spent (no-op when unlimited or the attack doesn't use one up). Returns what's left, -1 = unlimited. */
    public int spendAmmo(Pool pool, List<DndAttack> all, Function<String, DndWeapon> weapons, Function<String, Integer> roll) {
        int left = ammoLeft(pool, all, weapons, roll);
        if (left < 0 || !pool.spendsOne()) return left;
        int now = Math.max(0, left - 1);
        ammo.put(pool.key(), now);
        return now;
    }

    /** Set a pool outright: the DM's own roll at spawn, or a correction. -1 = unlimited. */
    public void setAmmo(String poolKey, int count) {
        ammo.put(poolKey, Math.max(-1, count));
    }

    /** What's left in every pool that's been rolled: "ammo:arrow" → 7. For loot and for saving. */
    public Map<String, Integer> ammoPools() { return java.util.Collections.unmodifiableMap(ammo); }

    // ==================== SAVING ====================

    public boolean isEmpty() { return used.isEmpty() && ammo.isEmpty(); }

    /** "fire_breath=1@412;…" + "|" + "ammo:arrow=7;…". Empty string when there's nothing to save. */
    public String serialize() {
        if (isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        used.forEach((k, v) -> sb.append(k).append('=').append(v).append('@').append(usedAt.getOrDefault(k, 0L)).append(';'));
        sb.append('|');
        ammo.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    public static CreatureUses deserialize(String s) {
        CreatureUses out = new CreatureUses();
        if (s == null || s.isBlank() || !s.contains("|")) return out;
        String[] halves = s.split("\\|", -1);
        for (String e : halves[0].split(";")) {
            int eq = e.indexOf('='), at = e.indexOf('@');
            if (eq <= 0 || at <= eq) continue;
            try {
                out.used.put(e.substring(0, eq), Integer.parseInt(e.substring(eq + 1, at)));
                out.usedAt.put(e.substring(0, eq), Long.parseLong(e.substring(at + 1)));
            } catch (NumberFormatException ignored) {}
        }
        for (String e : halves[1].split(";")) {
            int eq = e.lastIndexOf('=');
            if (eq <= 0) continue;
            try { out.ammo.put(e.substring(0, eq), Integer.parseInt(e.substring(eq + 1))); } catch (NumberFormatException ignored) {}
        }
        return out;
    }
}
