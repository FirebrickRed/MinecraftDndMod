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

    /**
     * @param providedRoll  the player's physically-rolled d20, or null
     * @param providedTotal a final total the player computed themselves, or null
     * @param modifier      the bonus to add to a rolled die
     * @param modLabel      how the modifier reads in the breakdown, e.g. "+5[ToHit]" or "+3[STR]"
     * @return the result, or {@code null} when the config is PHYSICAL and no roll/total was given —
     *         the caller should then prompt the player to roll rather than resolving.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel) {
        if (providedTotal != null) {
            return new RollResult(-1, providedTotal, true, false, false, providedTotal + " (provided total)");
        }
        Integer d20 = providedRoll;
        if (d20 == null) {
            if (!PluginConfig.isAutoRoll()) return null; // physical mode: caller prompts for a die
            d20 = DiceRoller.rollDice(1, 20);
        }
        int total = d20 + modifier;
        return new RollResult(d20, total, false, d20 == 20, d20 == 1, "d20(" + d20 + ") " + modLabel + " = " + total);
    }
}
