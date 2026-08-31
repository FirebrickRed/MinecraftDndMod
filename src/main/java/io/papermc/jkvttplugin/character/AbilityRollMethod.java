package io.papermc.jkvttplugin.character;

import java.util.Random;

/**
 * Ways to roll a single ability score, offered as a reference on the creation Abilities tab (#59).
 * Which methods are available is configured in config.yml ({@code abilities.roll_methods}); this is
 * purely a suggestion — the rolled numbers are shown, never auto-assigned.
 */
public enum AbilityRollMethod {
    FOUR_D6_DROP_LOWEST("4d6_drop_lowest", "4d6 drop lowest"),
    THREE_D6("3d6", "3d6 straight"),
    ONE_D20("1d20", "1d20 per ability"),
    TWO_D20_DROP_LOWEST("2d20_drop_lowest", "2d20 keep highest");

    private static final Random RNG = new Random();

    private final String key;
    private final String display;

    AbilityRollMethod(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String getKey() { return key; }
    public String getDisplay() { return display; }

    public static AbilityRollMethod fromKey(String k) {
        if (k == null) return null;
        for (AbilityRollMethod m : values()) {
            if (m.key.equalsIgnoreCase(k.trim())) return m;
        }
        return null;
    }

    /** One rolled ability score plus a human-readable dice breakdown for the tooltip. */
    public record AbilityRoll(int total, String detail) {}

    /** Roll one ability score with this method. */
    public AbilityRoll rollOne() {
        return switch (this) {
            case FOUR_D6_DROP_LOWEST -> {
                int[] d = {d6(), d6(), d6(), d6()};
                int min = Math.min(Math.min(d[0], d[1]), Math.min(d[2], d[3]));
                boolean droppedOne = false;
                int total = 0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < d.length; i++) {
                    if (i > 0) sb.append(",");
                    if (!droppedOne && d[i] == min) {   // drop the single lowest die
                        sb.append("(").append(d[i]).append(")");
                        droppedOne = true;
                    } else {
                        sb.append(d[i]);
                        total += d[i];
                    }
                }
                yield new AbilityRoll(total, sb.toString());
            }
            case THREE_D6 -> {
                int a = d6(), b = d6(), c = d6();
                yield new AbilityRoll(a + b + c, a + "," + b + "," + c);
            }
            case ONE_D20 -> {
                int v = RNG.nextInt(20) + 1;
                yield new AbilityRoll(v, "d20=" + v);
            }
            case TWO_D20_DROP_LOWEST -> {
                int a = RNG.nextInt(20) + 1, b = RNG.nextInt(20) + 1;
                int high = Math.max(a, b), low = Math.min(a, b);
                // Show both, the dropped (lower) one in parentheses.
                String detail = (low == a) ? "(" + a + ")," + b : a + ",(" + b + ")";
                yield new AbilityRoll(high, detail);
            }
        };
    }

    private static int d6() { return RNG.nextInt(6) + 1; }
}
