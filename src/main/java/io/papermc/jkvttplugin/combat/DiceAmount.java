package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.util.DiceRoller;

/**
 * An amount that comes from dice which aren't a d20: a healing spell, a potion, Second Wind, a Hit Die,
 * a spell's damage out of a fight. One reading of the roll words for all of them:
 *
 * <ul>
 *   <li>{@code total <n>}: that's the amount, nothing added</li>
 *   <li>{@code manualRoll <n>}: the dice as rolled; the formula's own flat part and the roller's bonus are added</li>
 *   <li>{@code autoRoll} (or no words, where the game is set to roll): the game rolls the formula and adds the bonus</li>
 *   <li>no words otherwise: {@link Status#NEEDS_ROLL}, and the caller shows its roll buttons</li>
 * </ul>
 *
 * <p>Five commands each had their own copy of this, and they had drifted (a typed roll forgot the
 * formula's flat part in two of them). What still differs is each caller's business and stays there:
 * its minimum, who hears the result, when the slot or use is spent, and what bad dice in the YAML mean.
 * Pure: it rolls dice but touches no player, sheet or config, so the differences can be tested.
 */
public final class DiceAmount {

    private DiceAmount() {}

    public enum Status {
        /** {@link Result#amount} and {@link Result#work} are set. */
        OK,
        /** Nothing was typed and the game isn't to roll: ask for the roll. */
        NEEDS_ROLL,
        /** The game was to roll, but the formula isn't dice or a number. */
        BAD_DICE
    }

    /** {@code amount} is before any minimum the caller applies; {@code work} is the line that shows the roll. */
    public record Result(Status status, int amount, String work) {
        public boolean ok() { return status == Status.OK; }
    }

    /**
     * @param in          the roll words as typed
     * @param formula     the dice, with any flat part of their own: {@code "2d4+2"}, {@code "1d8"}
     * @param source      what the formula belongs to, to label that flat part: {@code "+2[Potion of Healing]"}
     * @param bonus       what the roller adds on top (a spellcasting modifier, CON, a class level); 0 for none
     * @param bonusLabel  that bonus as shown, {@code "+3[WIS]"}; null for none
     * @param gameRollsWhenAsked whether no roll words at all means the game rolls (the server's auto-roll setting)
     */
    public static Result resolve(RollService.RollInput in, String formula, String source, int bonus, String bonusLabel,
                                 boolean gameRollsWhenAsked) {
        RollPrompt.Formula f = RollPrompt.split(formula, source);
        String label = join(f.label(), bonusLabel);
        if (in.providedTotal() != null) {
            return new Result(Status.OK, in.providedTotal(), RollPrompt.yourTotal(in.providedTotal()));
        }
        if (in.providedRoll() != null) {
            int amount = in.providedRoll() + f.flat() + bonus;
            return new Result(Status.OK, amount, RollPrompt.youRolled(in.providedRoll(), label, amount));
        }
        if (!in.forceAuto() && !gameRollsWhenAsked) return new Result(Status.NEEDS_ROLL, 0, null);
        DiceRoller.Rolled r = DiceRoller.rollOrFlat(formula);
        if (r == null) return new Result(Status.BAD_DICE, 0, null);
        int amount = r.total() + bonus; // the formula's flat part is already in the rolled total
        String shown = r.dice().isEmpty() ? String.valueOf(r.total()) : r.dice().toString();
        return new Result(Status.OK, amount, RollPrompt.gameRolled(f.dice(), shown, label, amount));
    }

    /** What a roll prompt asks for: the dice to roll, and everything the game then adds, labelled. */
    public record Ask(String dice, String bonusLabel) {}

    /**
     * The prompt's half of {@link #resolve}: "1d10+2" from Battle Medic with "+1[Fighter level]" asks for
     * {@code 1d10} and says the game adds {@code +2[Battle Medic] +1[Fighter level]}, which is exactly what a
     * typed roll then gets. Build a caller's roll buttons from this, so the prompt and the result can't disagree.
     */
    public static Ask ask(String formula, String source, String bonusLabel) {
        RollPrompt.Formula f = RollPrompt.split(formula, source);
        return new Ask(f.dice(), join(f.label(), bonusLabel));
    }

    /** The formula's own label and the roller's, as one bonus string; null when there's neither. */
    public static String join(String formulaLabel, String bonusLabel) {
        boolean a = formulaLabel != null && !formulaLabel.isBlank(), b = bonusLabel != null && !bonusLabel.isBlank();
        return a && b ? formulaLabel.trim() + " " + bonusLabel.trim() : a ? formulaLabel.trim() : b ? bonusLabel.trim() : null;
    }
}
