package io.papermc.jkvttplugin.util;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static int parseDiceRoll(String input) {
        Matcher matcher = DICE_PATTERN.matcher(input.toLowerCase().replace(" ", ""));
        if (!matcher.matches()) return -1;

        int numDice = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        int modifier = (matcher.group(3) != null) ? Integer.parseInt(matcher.group(3)) : 0;
        int multiplier = (matcher.group(4) != null) ? Integer.parseInt(matcher.group(4)) : 1;

        return (rollDice(numDice, sides) + modifier) * multiplier;
    }
}
