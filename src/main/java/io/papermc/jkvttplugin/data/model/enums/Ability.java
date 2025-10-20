package io.papermc.jkvttplugin.data.model.enums;

public enum Ability {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA;

    /**
     * Gets the standard 3-letter abbreviation for this ability.
     * @return The abbreviation (e.g., "STR", "DEX", "CON")
     */
    public String getAbbreviation() {
        return switch (this) {
            case STRENGTH -> "STR";
            case DEXTERITY -> "DEX";
            case CONSTITUTION -> "CON";
            case INTELLIGENCE -> "INT";
            case WISDOM -> "WIS";
            case CHARISMA -> "CHA";
        };
    }

    public static Ability fromString(String name) {
        try {
            return Ability.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null; // Optionally log this
        }
    }

    /**
     * Calculates the ability modifier for a given ability score.
     * Formula: (abilityScore - 10) / 2, rounded down
     *
     * @param abilityScore The ability score (typically 1-30)
     * @return The modifier (e.g., score 16 -> modifier +3)
     */
    public static int getModifier(int abilityScore) {
        return Math.floorDiv(abilityScore - 10, 2);
    }
}
