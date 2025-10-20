package io.papermc.jkvttplugin.data.model.enums;

/**
 * Represents the 18 skills in D&D 5e, each associated with a specific ability score.
 * These skills are core to D&D 5e rules and are hard-coded (not configurable).
 */
public enum Skill {
    // Strength
    ATHLETICS(Ability.STRENGTH),

    // Dexterity
    ACROBATICS(Ability.DEXTERITY),
    SLEIGHT_OF_HAND(Ability.DEXTERITY),
    STEALTH(Ability.DEXTERITY),

    // Intelligence
    ARCANA(Ability.INTELLIGENCE),
    HISTORY(Ability.INTELLIGENCE),
    INVESTIGATION(Ability.INTELLIGENCE),
    NATURE(Ability.INTELLIGENCE),
    RELIGION(Ability.INTELLIGENCE),

    // Wisdom
    ANIMAL_HANDLING(Ability.WISDOM),
    INSIGHT(Ability.WISDOM),
    MEDICINE(Ability.WISDOM),
    PERCEPTION(Ability.WISDOM),
    SURVIVAL(Ability.WISDOM),

    // Charisma
    DECEPTION(Ability.CHARISMA),
    INTIMIDATION(Ability.CHARISMA),
    PERFORMANCE(Ability.CHARISMA),
    PERSUASION(Ability.CHARISMA);

    private final Ability ability;

    Skill(Ability ability) {
        this.ability = ability;
    }

    /**
     * Gets the ability score associated with this skill.
     * @return The ability (e.g., ATHLETICS -> STRENGTH)
     */
    public Ability getAbility() {
        return ability;
    }

    /**
     * Gets a human-readable display name for this skill.
     * @return The formatted name (e.g., "Sleight of Hand")
     */
    public String getDisplayName() {
        return switch (this) {
            case ATHLETICS -> "Athletics";
            case ACROBATICS -> "Acrobatics";
            case SLEIGHT_OF_HAND -> "Sleight of Hand";
            case STEALTH -> "Stealth";
            case ARCANA -> "Arcana";
            case HISTORY -> "History";
            case INVESTIGATION -> "Investigation";
            case NATURE -> "Nature";
            case RELIGION -> "Religion";
            case ANIMAL_HANDLING -> "Animal Handling";
            case INSIGHT -> "Insight";
            case MEDICINE -> "Medicine";
            case PERCEPTION -> "Perception";
            case SURVIVAL -> "Survival";
            case DECEPTION -> "Deception";
            case INTIMIDATION -> "Intimidation";
            case PERFORMANCE -> "Performance";
            case PERSUASION -> "Persuasion";
        };
    }

    /**
     * Parses a skill from a string (case-insensitive).
     * @param name The skill name (e.g., "athletics", "Sleight of Hand")
     * @return The Skill enum, or null if not found
     */
    public static Skill fromString(String name) {
        if (name == null) return null;

        // Try direct enum match first (handles "ATHLETICS", "athletics")
        try {
            return Skill.valueOf(name.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Fall back to display name matching
            for (Skill skill : values()) {
                if (skill.getDisplayName().equalsIgnoreCase(name)) {
                    return skill;
                }
            }
            return null;
        }
    }
}
