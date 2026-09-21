package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.Material;

/**
 * Represents an automatic trait or proficiency granted to a character during creation.
 * These are displayed in the character creation UI to show what the player is receiving
 * automatically (without needing to make a choice).
 * <p>
 * Examples:
 * - Darkvision 60 ft (from Elf race)
 * - Heavy Armor proficiency (from Life Domain subclass)
 * - Perception skill proficiency (from Elf race)
 * - Common and Elvish languages (from Elf race)
 */
public record AutomaticGrant(
        GrantType type,
        String displayName,
        String source,  // e.g., "Elf", "Life Domain", "Fighter"
        String description,  // Optional additional info (e.g., "60 ft" for darkvision)
        String id  // Canonical id for skills/tools/languages (perception, thieves_tools, deep_speech)
) {

    /**
     * A skill, tool or language grant, keyed by its canonical id. Use this rather than building one
     * from a display name: duplicate detection and "already known" filtering match on {@link #id()},
     * and a display name like "Navigator's Tools" doesn't round-trip to {@code navigators_tools}.
     */
    public static AutomaticGrant proficiency(GrantType type, String raw, String source) {
        String id = switch (type) {
            case TOOL_PROFICIENCY -> ToolRegistry.idOf(raw);
            case LANGUAGE -> LanguageRegistry.idOf(raw);
            default -> Util.normalize(raw);
        };
        String display = switch (type) {
            case TOOL_PROFICIENCY -> ToolRegistry.displayName(id);
            case LANGUAGE -> LanguageRegistry.displayName(id);
            default -> Util.prettify(id);
        };
        return new AutomaticGrant(type, display, source, null, id);
    }

    public AutomaticGrant(GrantType type, String displayName, String source, String description) {
        this(type, displayName, source, description, null);
    }

    /** The canonical id if this grant has one, else the normalized display name. */
    public String key() {
        return id != null ? id : Util.normalize(displayName);
    }

    public enum GrantType {
        DARKVISION("Darkvision", Material.ENDER_EYE),
        SPEED("Speed", Material.FEATHER),
        LANGUAGE("Language", Material.BOOK),
        SKILL_PROFICIENCY("Skill Proficiency", Material.IRON_SWORD),
        WEAPON_PROFICIENCY("Weapon Proficiency", Material.DIAMOND_SWORD),
        ARMOR_PROFICIENCY("Armor Proficiency", Material.DIAMOND_CHESTPLATE),
        TOOL_PROFICIENCY("Tool Proficiency", Material.IRON_PICKAXE),
        DAMAGE_RESISTANCE("Damage Resistance", Material.SHIELD),
        ABILITY_SCORE("Ability Score Bonus", Material.EXPERIENCE_BOTTLE),
        INNATE_SPELL("Innate Spell", Material.ENCHANTED_BOOK),
        TRAIT("Special Trait", Material.NETHER_STAR);

        private final String displayName;
        private final Material icon;

        GrantType(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }
    }

    /**
     * Creates an AutomaticGrant with no additional description.
     */
    public AutomaticGrant(GrantType type, String displayName, String source) {
        this(type, displayName, source, null);
    }

    /**
     * Returns the icon material for this grant type.
     */
    public Material getIcon() {
        return type.getIcon();
    }

    /**
     * Returns the full display text for this grant.
     */
    public String getFullDisplay() {
        if (description != null && !description.isEmpty()) {
            return displayName + " (" + description + ")";
        }
        return displayName;
    }
}