package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.util.DiceRoller;

/**
 * The one place a d20 action (attack, check, save, death save, loot) turns inputs into a result
 * (Issue #142). Three modes, keeping today's flag names:
 *   - a provided TOTAL  → used as-is, nothing added ({@code --total}).
 *   - a provided ROLL   → that die + the modifier ({@code --roll}).
 *   - neither           → PHYSICAL config: the caller must prompt for a die (returns {@code null});
 *                         AUTO config: the game rolls the die itself.
 */
public final class RollService {

    private RollService() {}

    /** The outcome of a resolved d20 action. {@code d20} is -1 in provided-total mode. */
    public record RollResult(int d20, int total, boolean providedTotal, boolean nat20, boolean nat1, String breakdown) {}

    /** Does this to-hit roll beat the target's AC? Nat 20 always hits, nat 1 always misses (#154). */
    public static boolean hits(RollResult r, int targetAC) {
        if (r.providedTotal()) return r.total() >= targetAC;
        return r.nat20() || (!r.nat1() && r.total() >= targetAC);
    }

    /**
     * How a d20 action gets its die, parsed from the command (#183). New keywords are the preferred
     * form; the legacy {@code --roll}/{@code --total} flags are still accepted as aliases:
     *   - {@code autoRoll}            → the game rolls it (applying any advantage). ({@code --roll 1d20})
     *   - {@code manualRoll <n>}      → you rolled n; the game adds your modifiers. ({@code --roll 14})
     *   - {@code total <n>}           → a final total; nothing is added. ({@code --total 22})
     * {@code providedRoll}/{@code providedTotal} are null unless supplied; {@code forceAuto} means the
     * player explicitly asked the game to roll (so it rolls even in physical-dice mode).
     */
    public record RollInput(Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        public boolean isEmpty() { return providedRoll == null && providedTotal == null && !forceAuto; }
    }

    /** The bare words that supply a roll, so positional parsing knows to stop at them. */
    public static boolean isRollKeyword(String token) {
        if (token == null) return false;
        String t = token.toLowerCase();
        return t.equals("autoroll") || t.equals("manualroll") || t.equals("total")
                || t.equals("--roll") || t.equals("--total");
    }

    /** Parse roll input (new keywords + legacy flags) from a command's args. */
    public static RollInput parseInput(String[] args) {
        Integer providedRoll = null, providedTotal = null;
        boolean forceAuto = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i].toLowerCase();
            String next = (i + 1 < args.length) ? args[i + 1] : null;
            switch (a) {
                case "autoroll" -> forceAuto = true; // optional trailing dice (e.g. 2d20) is ignored — advantage is auto-detected
                case "manualroll", "--roll" -> {
                    if (next != null) {
                        if (next.toLowerCase().contains("d")) forceAuto = true; // a dice expression → let the game roll
                        else { try { providedRoll = Integer.parseInt(next.trim()); } catch (NumberFormatException ignored) {} }
                    }
                }
                case "total", "--total" -> {
                    if (next != null) { try { providedTotal = Integer.parseInt(next.trim()); } catch (NumberFormatException ignored) {} }
                }
                default -> {}
            }
        }
        return new RollInput(providedRoll, providedTotal, forceAuto);
    }

    /**
     * Interpret a {@code --roll} value. A plain number ("12") is the die the player rolled; a dice
     * expression ("1d20") is one the game rolls for them. Returns the resulting die value, or null
     * when the argument is empty or unparseable (the caller distinguishes those by the raw string).
     */
    public static Integer parseRollArg(String arg) {
        if (arg == null || arg.isBlank()) return null;
        if (arg.toLowerCase().contains("d")) {
            java.util.OptionalInt rolled = DiceRoller.parseDiceRoll(arg); // game rolls the expression
            return rolled.isPresent() ? rolled.getAsInt() : null;
        }
        try {
            return Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param providedRoll  the player's physically-rolled d20, or null
     * @param providedTotal a final total the player computed themselves, or null
     * @param modifier      the bonus to add to a rolled die
     * @param modLabel      how the modifier reads in the breakdown, e.g. "+5[ToHit]" or "+3[STR]"
     * @return the result, or {@code null} when the config is PHYSICAL and no roll/total was given —
     *         the caller should then prompt the player to roll rather than resolving.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, false);
    }

    /**
     * As {@link #resolve(Integer, Integer, int, String)}, but if {@code rerollNat1} is set and the d20
     * comes up a natural 1, it's rerolled once and the new die stands (Halfling Lucky). The reroll is
     * shown in the breakdown so it's transparent. A provided total is never rerolled (no die to see).
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, rerollNat1, Advantage.NONE);
    }

    /**
     * As above, applying advantage/disadvantage. When the game auto-rolls, this rolls TWO d20 and
     * keeps the higher (advantage) or lower (disadvantage), showing both dice. A single provided roll
     * is used as-is (the player already accounted for adv/dis when they physically rolled), and a
     * provided total is likewise trusted — the caller is expected to have reminded the player.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1, Advantage advantage) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, rerollNat1, advantage, false);
    }

    /** Resolve a roll straight from parsed {@link RollInput} (carries the forceAuto intent). */
    public static RollResult resolve(RollInput input, int modifier, String modLabel, boolean rerollNat1, Advantage advantage) {
        return resolve(input.providedRoll(), input.providedTotal(), modifier, modLabel, rerollNat1, advantage, input.forceAuto());
    }

    /**
     * As above, plus {@code forceAuto}: when the player explicitly asked the game to roll
     * ({@code autoRoll}), it rolls even in physical-dice mode instead of returning null to prompt.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1, Advantage advantage, boolean forceAuto) {
        if (providedTotal != null) {
            return new RollResult(-1, providedTotal, true, false, false, providedTotal + " (provided total)");
        }
        if (advantage == null) advantage = Advantage.NONE;

        Integer d20 = providedRoll;
        String advNote = "";
        if (d20 == null) {
            if (!forceAuto && !PluginConfig.isAutoRoll()) return null; // physical mode: caller prompts for a die
            if (advantage == Advantage.NONE) {
                d20 = DiceRoller.rollDice(1, 20);
            } else {
                int a = DiceRoller.rollDice(1, 20);
                int b = DiceRoller.rollDice(1, 20);
                d20 = advantage.isAdvantage() ? Math.max(a, b) : Math.min(a, b);
                advNote = " [" + advantage.label() + ": " + a + "/" + b + "]";
            }
        }
        String luck = "";
        if (rerollNat1 && d20 == 1) {
            int first = d20;
            d20 = DiceRoller.rollDice(1, 20);
            luck = " [Lucky: reroll of " + first + "]";
        }
        int total = d20 + modifier;
        return new RollResult(d20, total, false, d20 == 20, d20 == 1,
                "d20(" + d20 + ") " + modLabel + " = " + total + advNote + luck);
    }
}
