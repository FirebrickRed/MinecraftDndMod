package io.papermc.jkvttplugin.util;

/**
 * Core D&D 5e rules and formulas.
 * Contains universal game mechanics that apply to all characters, NPCs, and monsters.
 */
public class DndRules {
    private DndRules() {} // Utility class - prevent instantiation

    /**
     * Calculates proficiency bonus based on character level.
     * This is a core D&D 5e rule that applies to all creatures.
     *
     * @param level Character level (1-20)
     * @return Proficiency bonus (+2 to +6)
     */
    public static int getProficiencyBonus(int level) {
        if (level <= 4) return 2;
        if (level <= 8) return 3;
        if (level <= 12) return 4;
        if (level <= 16) return 5;
        return 6;
    }
}
