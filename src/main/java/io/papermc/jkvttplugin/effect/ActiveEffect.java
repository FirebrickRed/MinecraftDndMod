package io.papermc.jkvttplugin.effect;

import java.util.HashSet;
import java.util.Set;

/**
 * A live buff/debuff on a creature (the Effect Engine, #70). Holds the parsed primitives plus the
 * runtime duration state. A {@link Feature}'s "apply" block is stored as a template ActiveEffect;
 * {@link #copy()} makes the instance that actually gets attached to a target.
 *
 * <p>Everything here is data-driven — the fields are the starter primitive vocabulary. New content
 * composes these in YAML; only a genuinely new primitive needs a new field + a read site.
 */
public class ActiveEffect {

    private final String sourceId;    // originating feature id, e.g. "rage"
    private final String sourceName;  // display name, e.g. "Rage"

    // ---- primitives (the vocabulary) ----
    private final Set<String> resistances;     // damage types halved
    private final Set<String> advantageOn;     // roll tags granted advantage
    private final Set<String> disadvantageOn;  // roll tags given disadvantage
    private final int bonusDamage;             // flat bonus damage
    private final String bonusDamageWhen;      // roll tag it applies to, e.g. "melee_str"
    private final String minecraftEffect;      // optional PotionEffectType name (visual)
    private final int minecraftAmplifier;
    // Boolean passive primitives, by name — e.g. "reroll_natural_1" (Lucky), "extra_crit_die"
    // (Savage Attacks), "endure_below_1" (Relentless Endurance). Extensible without touching this
    // constructor: a new always-on flag is just a new key both here and in FeatureParser.
    private final Set<String> flags;
    private final boolean stacks;              // false = a second copy refreshes instead of adding

    // ---- duration ----
    private int roundsRemaining;               // -1 = no round timer (DM/rest-ended)
    private final Set<String> maintainedBy;    // e.g. {attacked, took_damage}; empty = always kept
    private final String untilRest;            // "short" | "long" | null
    private final boolean untilUsed;           // consumed the first time it fires
    private boolean maintainedThisRound;       // reset each round; set by triggers

    public ActiveEffect(String sourceId, String sourceName, Set<String> resistances, Set<String> advantageOn,
                        Set<String> disadvantageOn, int bonusDamage, String bonusDamageWhen,
                        String minecraftEffect, int minecraftAmplifier, Set<String> flags, boolean stacks,
                        int roundsRemaining, Set<String> maintainedBy, String untilRest, boolean untilUsed) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.resistances = lower(resistances);
        this.advantageOn = lower(advantageOn);
        this.disadvantageOn = lower(disadvantageOn);
        this.bonusDamage = bonusDamage;
        this.bonusDamageWhen = bonusDamageWhen == null ? null : bonusDamageWhen.toLowerCase();
        this.minecraftEffect = minecraftEffect;
        this.minecraftAmplifier = minecraftAmplifier;
        this.flags = lower(flags);
        this.stacks = stacks;
        this.roundsRemaining = roundsRemaining;
        this.maintainedBy = lower(maintainedBy);
        this.untilRest = untilRest == null ? null : untilRest.toLowerCase();
        this.untilUsed = untilUsed;
    }

    private static Set<String> lower(Set<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) for (String s : in) if (s != null && !s.isBlank()) out.add(s.trim().toLowerCase());
        return out;
    }

    /** A fresh instance of this (template) effect, to attach to a target. */
    public ActiveEffect copy() {
        return new ActiveEffect(sourceId, sourceName, resistances, advantageOn, disadvantageOn, bonusDamage,
                bonusDamageWhen, minecraftEffect, minecraftAmplifier, flags, stacks, roundsRemaining,
                maintainedBy, untilRest, untilUsed);
    }

    /**
     * Put back the live duration state saved with a character (#212). Everything else comes from
     * the feature's YAML template, so tuning Rage in YAML also reaches a Rage that was saved mid-fight.
     */
    public void restoreState(int roundsRemaining, boolean maintainedThisRound) {
        this.roundsRemaining = roundsRemaining;
        this.maintainedThisRound = maintainedThisRound;
    }

    public boolean isMaintainedThisRound() { return maintainedThisRound; }

    /** Whether this effect carries a named boolean primitive (e.g. "reroll_natural_1"). */
    public boolean hasFlag(String flag) { return flag != null && flags.contains(flag.toLowerCase()); }
    public boolean rerollsNat1() { return hasFlag("reroll_natural_1"); }
    public boolean hasExtraCritDie() { return hasFlag("extra_crit_die"); }
    public boolean enduresBelow1() { return hasFlag("endure_below_1"); }

    // ---- queries (read sites) ----
    public boolean resists(String damageType) {
        return damageType != null && resistances.contains(damageType.toLowerCase());
    }
    public boolean givesAdvantageOn(String rollTag) {
        return rollTag != null && advantageOn.contains(rollTag.toLowerCase());
    }
    public boolean givesDisadvantageOn(String rollTag) {
        return rollTag != null && disadvantageOn.contains(rollTag.toLowerCase());
    }
    /** Flat bonus damage this effect adds for a swing tagged {@code rollTag}, or 0. */
    public int bonusDamageFor(String rollTag) {
        return (bonusDamage != 0 && bonusDamageWhen != null && bonusDamageWhen.equalsIgnoreCase(rollTag))
                ? bonusDamage : 0;
    }

    // ---- duration lifecycle ----
    /** Note that a maintenance trigger happened this round (e.g. the holder attacked / took damage). */
    public void markMaintained(String trigger) {
        if (trigger != null && maintainedBy.contains(trigger.toLowerCase())) maintainedThisRound = true;
    }

    /**
     * Advance one round at the holder's turn start. Returns true if the effect has now EXPIRED
     * (round timer hit 0, or a maintenance requirement went unmet last round).
     */
    public boolean tickTurnStartAndCheckExpiry() {
        // Did the required maintenance happen during the round that just ended?
        boolean maintenanceFailed = !maintainedBy.isEmpty() && !maintainedThisRound;
        maintainedThisRound = false;            // reset for the new round
        if (roundsRemaining > 0) roundsRemaining--;
        return maintenanceFailed || roundsRemaining == 0;
    }

    public boolean endsOnRest(String restType) {
        return untilRest != null && untilRest.equalsIgnoreCase(restType);
    }

    // ---- display ----
    /** Short human-readable lines describing what this effect grants (for messages / the sheet). */
    public java.util.List<String> describe() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!resistances.isEmpty()) parts.add("Resistance to " + String.join(", ", resistances));
        if (bonusDamage != 0) parts.add((bonusDamage > 0 ? "+" : "") + bonusDamage + " damage"
                + (bonusDamageWhen != null ? " (" + bonusDamageWhen.replace('_', ' ') + ")" : ""));
        if (!advantageOn.isEmpty()) parts.add("Advantage on " + String.join(", ", advantageOn).replace('_', ' '));
        if (!disadvantageOn.isEmpty()) parts.add("Disadvantage on " + String.join(", ", disadvantageOn).replace('_', ' '));
        return parts;
    }

    /** A short label for how long the effect lasts. */
    public String durationLabel() {
        if (roundsRemaining > 0) return roundsRemaining + (roundsRemaining == 1 ? " round" : " rounds");
        if (untilRest != null) return "until " + untilRest + " rest";
        if (untilUsed) return "until used";
        return "until removed";
    }

    // ---- getters ----
    public String getSourceId() { return sourceId; }
    public String getSourceName() { return sourceName; }
    public boolean stacks() { return stacks; }
    public boolean isUntilUsed() { return untilUsed; }
    public int getRoundsRemaining() { return roundsRemaining; }
    public String getMinecraftEffect() { return minecraftEffect; }
    public int getMinecraftAmplifier() { return minecraftAmplifier; }
}
