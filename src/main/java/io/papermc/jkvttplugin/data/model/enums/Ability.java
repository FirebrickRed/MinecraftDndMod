package io.papermc.jkvttplugin.data.model.enums;

public enum Ability {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA;

    public static Ability fromString(String name) {
        try {
            return Ability.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null; // Optionally log this
        }
    }
}
