package io.papermc.jkvttplugin.data.model;

import org.bukkit.Material;

/**
 * Categories for grouping player choices in the tabbed choice menu.
 * Each category represents a type of choice that can be merged and displayed together.
 */
public enum ChoiceCategory {
    AUTOMATIC_GRANTS("Automatic Traits", Material.EMERALD), // Proficiencies, darkvision, speeds, etc. that are automatic
    LANGUAGE("Languages", Material.BOOK),
    SKILL("Skills", Material.IRON_SWORD),
    TOOL("Tools", Material.IRON_PICKAXE),
    EQUIPMENT("Equipment", Material.CHEST),
    SPELL("Spell", Material.BONE_MEAL),
    EXTRA("Other", Material.PAPER); // Size, feats, and other miscellaneous choices

    private final String displayName;
    private final Material icon;

    ChoiceCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * Maps a PlayersChoice.ChoiceType to a ChoiceCategory for display grouping.
     *
     * @param type The choice type from PlayersChoice
     * @param choiceId The ID of the choice (used to categorize CUSTOM types)
     * @return The appropriate category for display
     */
    public static ChoiceCategory fromChoiceType(PlayersChoice.ChoiceType type, String choiceId) {
        return switch (type) {
            case LANGUAGE -> LANGUAGE;
            case SKILL -> SKILL;
            case TOOL -> TOOL;
            case EQUIPMENT -> EQUIPMENT;
            case SPELL -> SPELL;
            case CUSTOM -> categorizeCustom(choiceId);
            case FEAT, ABILITY_SCORE -> EXTRA;
        };
    }

    /**
     * Categorizes custom choice types based on their ID.
     * This handles special cases like size selection.
     */
    private static ChoiceCategory categorizeCustom(String id) {
        if (id != null && id.toLowerCase().contains("size")) {
            return EXTRA;
        }
        // Add more custom categorization logic here as needed
        return EXTRA;
    }
}