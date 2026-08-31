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
    private static final Pattern DICE_PATTERN = Pattern.compile("(\\d*)d(\\d+)([+\\-]\\d+)?(?:\\s*\\*\\s*(\\d+))?");

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
        Matcher matcher = DICE_PATTERN.matcher(input.toLowerCase().replace(" ", ""));
        if (!matcher.matches()) return OptionalInt.empty();

        int numDice = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        int modifier = (matcher.group(3) != null) ? Integer.parseInt(matcher.group(3)) : 0;
        int multiplier = (matcher.group(4) != null) ? Integer.parseInt(matcher.group(4)) : 1;

        return OptionalInt.of((rollDice(numDice, sides) + modifier) * multiplier);
    }
}
