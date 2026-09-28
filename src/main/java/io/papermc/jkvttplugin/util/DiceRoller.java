package io.papermc.jkvttplugin.util;

import java.util.OptionalInt;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Low-level dice primitive: roll N dY, or evaluate a dice expression like "2d6+3".
 *
 * <p>This is the generic building block used for damage and healing dice. For a <b>d20 action</b>
 * (attack, check, save, death save) use {@link io.papermc.jkvttplugin.combat.RollService} instead —
 * it layers the physical/auto roll mode, {@code --roll}/{@code --total}, modifiers, and nat-20/nat-1
 * on top of this class. (RollService calls into DiceRoller, so the two aren't duplicates.)
 */
public class DiceRoller {
    private static final Random random = new Random();

    public static int rollDice(int numDice, int sides) {
        int total = 0;
        for (int i = 0; i < numDice; i++) {
            total += random.nextInt(sides) + 1;
        }
        return total;
    }

    /**
     * Rolls a dice expression like {@code 2d6+3} or {@code 1d4-10}. Returns the result as an
     * {@link OptionalInt}: {@code empty()} means the input was malformed (didn't match the dice
     * pattern), while a present value is the rolled total — which can legitimately be zero or
     * negative (e.g. {@code 1d4-10}). Do NOT use a sentinel like -1 to signal failure: a valid
     * roll can equal -1, so callers must branch on {@code isPresent()}, not on the value's sign.
     */
    public static OptionalInt parseDiceRoll(String input) {
        return roll(input).map(r -> OptionalInt.of(r.total())).orElse(OptionalInt.empty());
    }

    /** A rolled expression with every die kept, so the table can see the work, not just the total. */
    public record Rolled(String expression, java.util.List<Integer> dice, int modifier, int multiplier, int total) {
        /** "[4, 3] +3 = 10", or "([4, 3] +3) ×2 = 20" with a multiplier. A flat amount is just itself. */
        public String breakdown() {
            return dice.isEmpty() ? String.valueOf(total) : shown() + " = " + total;
        }

        /**
         * The dice as rolled, without the total: "[4, 3] +3". This is the {@code shown} part of
         * {@code RollPrompt.gameRolled}, which adds the bonus and the total itself.
         */
        public String shown() {
            if (dice.isEmpty()) return String.valueOf(total);
            String sum = dice.toString() + (modifier > 0 ? " +" + modifier : modifier < 0 ? " " + modifier : "");
            if (multiplier != 1) sum = "(" + sum + ") ×" + multiplier;
            return sum;
        }

        /**
         * The line to show whenever <b>the game</b> rolled: "🎲 2d6+3 [4, 3] +3 = 10", the same shape
         * as {@code RollPrompt.gameRolled}.
         *
         * <p>Use this everywhere the game rolls dice on someone's behalf. A bare total ("you take 7")
         * asks the table to trust the computer; the dice are what a physical table would see on the
         * felt. (A d20 action goes through {@link io.papermc.jkvttplugin.combat.RollService} instead,
         * which builds the same kind of breakdown with the modifiers named.)
         */
        public String display() {
            return dice.isEmpty() ? "🎲 " + total : "🎲 " + expression + " " + breakdown();
        }
    }

    /**
     * Roll a dice expression, or read a flat number ("5") as itself. Null if it's neither, so a
     * caller can report bad input instead of silently rolling nothing.
     */
    public static Rolled rollOrFlat(String input) {
        if (input == null || input.isBlank()) return null;
        java.util.Optional<Rolled> rolled = roll(input);
        if (rolled.isPresent()) return rolled.get();
        try {
            int flat = Integer.parseInt(input.trim());
            return new Rolled(input.trim(), java.util.List.of(), 0, 1, flat);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Roll a dice expression, keeping each die. Empty if malformed (or a die with no sides). */
    public static java.util.Optional<Rolled> roll(String input) {
        return roll(input, 0);
    }

    /** One term of an expression: "+1d6", "-2", "2d8". */
    private static final Pattern TERM = Pattern.compile("([+\\-]?)(?:(\\d*)d(\\d+)|(\\d+))");
    /** The whole expression: terms, at least one of them dice, then an optional "*N". */
    private static final Pattern EXPRESSION = Pattern.compile("[+\\-]?(?:\\d*d\\d+|\\d+)(?:[+\\-](?:\\d*d\\d+|\\d+))*(?:\\*(\\d+))?");

    /**
     * As {@link #roll(String)}, rerolling any die that shows {@code rerollAtOrBelow} or less, once
     * (Great Weapon Fighting: 1s and 2s, keeping the new roll). 0 rerolls nothing.
     *
     * <p>More than one dice group is fine ("1d8+1d6+3", a hit with Sneak Attack): every die is kept
     * and the flat terms add up into the modifier.
     */
    public static java.util.Optional<Rolled> roll(String input, int rerollAtOrBelow) {
        String expr = input.toLowerCase().replace(" ", "");
        Matcher whole = EXPRESSION.matcher(expr);
        if (!whole.matches() || !expr.contains("d")) return java.util.Optional.empty();
        int multiplier = whole.group(1) != null ? Integer.parseInt(whole.group(1)) : 1;
        String terms = whole.group(1) != null ? expr.substring(0, expr.lastIndexOf('*')) : expr;

        java.util.List<Integer> dice = new java.util.ArrayList<>();
        int sum = 0, modifier = 0, count = 0;
        Matcher t = TERM.matcher(terms);
        while (t.find()) {
            int sign = "-".equals(t.group(1)) ? -1 : 1;
            if (t.group(4) != null) { modifier += sign * Integer.parseInt(t.group(4)); continue; }
            int numDice = t.group(2).isEmpty() ? 1 : Integer.parseInt(t.group(2));
            int sides = Integer.parseInt(t.group(3));
            count += numDice;
            if (sides < 1 || count > 1000) return java.util.Optional.empty();
            for (int i = 0; i < numDice; i++) {
                int die = random.nextInt(sides) + 1;
                if (die <= rerollAtOrBelow) die = random.nextInt(sides) + 1;
                dice.add(sign * die);
                sum += sign * die;
            }
        }
        return java.util.Optional.of(new Rolled(expr, dice, modifier, multiplier, (sum + modifier) * multiplier));
    }
}
