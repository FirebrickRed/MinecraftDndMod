package io.papermc.jkvttplugin.data.model.enums;

public enum Size {
    TINY(0.4),
    SMALL(0.6),
    MEDIUM(1.0),
    LARGE(2.0),
    HUGE(3.0),
    GARGANTUAN(4.0);

    /**
     * The Minecraft SCALE attribute for a body of this size: a player is 1.8 blocks (about 6 ft) at
     * 1.0, so a Small character at 0.6 stands about 3.5 ft, like a halfling or gnome. Used for a
     * character's own body and for a DM possessing a creature. The values are a guess to check on a
     * server (#193).
     */
    private final double scale;

    Size(double scale) {
        this.scale = scale;
    }

    public double scale() {
        return scale;
    }

    public static Size fromString(String input) {
        try {
            return Size.valueOf(input.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid size: " + input);
        }
    }

    /** As {@link #fromString}, but {@code fallback} for a missing or unknown size. */
    public static Size parseOr(String input, Size fallback) {
        if (input == null || input.isBlank()) return fallback;
        try {
            return fromString(input);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
