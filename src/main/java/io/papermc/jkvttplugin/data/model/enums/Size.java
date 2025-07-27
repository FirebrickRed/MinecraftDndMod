package io.papermc.jkvttplugin.data.model.enums;

public enum Size {
    TINY,
    SMALL,
    MEDIUM,
    LARGE,
    HUGE,
    GARGANTUAN;

    public static Size fromString(String input) {
        try {
            return Size.valueOf(input.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid size: " + input);
        }
    }
}
