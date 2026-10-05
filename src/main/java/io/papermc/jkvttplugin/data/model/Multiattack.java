package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A creature's Multiattack (#253): several attacks on one Action, as its stat block spells them out.
 * Two shapes cover the Monster Manual's wordings:
 * <pre>
 * multiattack:                          "one bite and two claw attacks"
 *   - { attack: Bite, count: 1 }
 *   - { attack: Claw, count: 2 }
 *
 * multiattack: { count: 2, any_of: [Glaive, Longbow] }     "two attacks with its glaive or longbow"
 * multiattack: 2                                           "makes two attacks" (any of its attacks)
 * </pre>
 * The definition belongs to the template; {@link Progress} is one turn's count, on the creature's
 * {@code TurnState}, so it's gone when the turn is.
 */
public final class Multiattack {

    /** Fixed shape: attack key → how many. Empty for the pool shape. */
    private final Map<String, Integer> fixed = new LinkedHashMap<>();
    /** Pool shape: how many attacks in all, drawn from {@link #poolOf} (empty = any of its attacks). */
    private int poolCount;
    private final Set<String> poolOf = new LinkedHashSet<>();
    /** Display names by key, as the stat block spells them. */
    private final Map<String, String> names = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    private Multiattack() {}

    private static String key(String name) { return CreatureUses.key(name); }

    /**
     * Read {@code multiattack:}. Returns null when it's absent or nothing usable was written (the
     * problems then say why). Attack names are matched to the creature's attacks, whatever their case.
     */
    public static Multiattack parse(Object raw, List<DndAttack> attacks, List<String> problemsOut) {
        if (raw == null) return null;
        Multiattack m = new Multiattack();
        Map<String, String> known = new LinkedHashMap<>();
        if (attacks != null) for (DndAttack a : attacks) if (a.getName() != null) known.put(key(a.getName()), a.getName());

        if (raw instanceof Number n) {
            m.poolCount = n.intValue();
        } else if (raw instanceof Map<?, ?> map) {
            if (map.get("count") instanceof Number n) m.poolCount = n.intValue();
            else m.problems.add("multiattack needs count: (how many attacks), e.g. { count: 2, any_of: [Glaive, Longbow] }");
            if (map.get("any_of") instanceof List<?> list) {
                for (Object o : list) m.addPoolAttack(String.valueOf(o), known);
            } else if (map.get("any_of") != null) {
                m.problems.add("multiattack any_of: should be a list of attack names");
            }
        } else if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> entry) || entry.get("attack") == null) {
                    m.problems.add("multiattack entry '" + o + "' should be { attack: <name>, count: <n> }");
                    continue;
                }
                String name = String.valueOf(entry.get("attack"));
                int count = entry.get("count") instanceof Number n ? n.intValue() : 1;
                String k = key(name);
                if (!known.containsKey(k)) { m.problems.add("multiattack names '" + name + "', which isn't one of its attacks"); continue; }
                if (count < 1) { m.problems.add("multiattack '" + name + "' count should be 1 or more"); continue; }
                m.fixed.merge(k, count, Integer::sum);
                m.names.put(k, known.get(k));
            }
        } else {
            m.problems.add("multiattack should be a number, a list of { attack, count }, or { count, any_of }");
        }

        if (m.total() < 2 && m.problems.isEmpty()) {
            m.problems.add("multiattack of " + m.total() + " attack isn't a multiattack: it needs 2 or more");
        }
        if (m.fixed.isEmpty() && m.poolCount >= 2 && m.poolOf.isEmpty()) m.names.putAll(known); // any of its attacks
        if (problemsOut != null) problemsOut.addAll(m.problems);
        return m.total() >= 2 ? m : null;
    }

    private void addPoolAttack(String name, Map<String, String> known) {
        String k = key(name);
        if (!known.containsKey(k)) { problems.add("multiattack names '" + name + "', which isn't one of its attacks"); return; }
        poolOf.add(k);
        names.put(k, known.get(k));
    }

    /** How many attacks the whole Multiattack is. */
    public int total() {
        return fixed.isEmpty() ? poolCount : fixed.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** True when this attack is part of it (so making it starts the Multiattack). */
    public boolean includes(String attackName) {
        String k = key(attackName);
        return fixed.isEmpty() ? (poolOf.isEmpty() || poolOf.contains(k)) : fixed.containsKey(k);
    }

    /** "Bite, Claw ×2" or "2 attacks: Glaive or Longbow" (or "2 attacks" when any will do). */
    public String describe() {
        if (!fixed.isEmpty()) return listOf(fixed);
        if (poolOf.isEmpty()) return poolCount + " attacks";
        List<String> opts = new ArrayList<>();
        for (String k : poolOf) opts.add(names.getOrDefault(k, k));
        return poolCount + " attacks: " + String.join(" or ", opts);
    }

    private String listOf(Map<String, Integer> counts) {
        List<String> parts = new ArrayList<>();
        counts.forEach((k, n) -> { if (n > 0) parts.add(names.getOrDefault(k, k) + (n > 1 ? " ×" + n : "")); });
        return String.join(", ", parts);
    }

    /** A fresh count for one turn. */
    public Progress begin() { return new Progress(); }

    /** One turn's Multiattack: what's been made and what's left. */
    public final class Progress {
        private final Map<String, Integer> left = new LinkedHashMap<>(fixed);
        private int poolLeft = poolCount;

        /** True when one more of this attack is still owed to the Multiattack. */
        public boolean canUse(String attackName) {
            String k = key(attackName);
            if (fixed.isEmpty()) return poolLeft > 0 && (poolOf.isEmpty() || poolOf.contains(k));
            return left.getOrDefault(k, 0) > 0;
        }

        public void use(String attackName) {
            if (!canUse(attackName)) return;
            if (fixed.isEmpty()) poolLeft--;
            else left.merge(key(attackName), -1, Integer::sum);
        }

        public boolean done() {
            return fixed.isEmpty() ? poolLeft <= 0 : left.values().stream().allMatch(n -> n <= 0);
        }

        /** "Claw ×2", "1 more: Glaive or Longbow", or "" when it's finished. */
        public String leftText() {
            if (done()) return "";
            if (!fixed.isEmpty()) return listOf(left);
            if (poolOf.isEmpty()) return poolLeft + " more";
            List<String> opts = new ArrayList<>();
            for (String k : poolOf) opts.add(names.getOrDefault(k, k));
            return poolLeft + " more: " + String.join(" or ", opts);
        }
    }
}
