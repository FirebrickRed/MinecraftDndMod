package io.papermc.jkvttplugin.data.model;

import org.bukkit.Material;

import java.util.List;

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
        String description  // Optional additional info (e.g., "60 ft" for darkvision)
) {

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

    /**
     * Builder for creating lists of automatic grants.
     */
    public static class Builder {
        private final List<AutomaticGrant> grants = new java.util.ArrayList<>();

        public Builder addDarkvision(int range, String source) {
            grants.add(new AutomaticGrant(GrantType.DARKVISION, "Darkvision", source, range + " ft"));
            return this;
        }

        public Builder addSpeed(String type, int speed, String source) {
            grants.add(new AutomaticGrant(GrantType.SPEED, type + " Speed", source, speed + " ft"));
            return this;
        }

        public Builder addLanguage(String language, String source) {
            grants.add(new AutomaticGrant(GrantType.LANGUAGE, language, source));
            return this;
        }

        public Builder addSkillProficiency(String skill, String source) {
            grants.add(new AutomaticGrant(GrantType.SKILL_PROFICIENCY, skill, source));
            return this;
        }

        public Builder addWeaponProficiency(String weapon, String source) {
            grants.add(new AutomaticGrant(GrantType.WEAPON_PROFICIENCY, weapon, source));
            return this;
        }

        public Builder addArmorProficiency(String armor, String source) {
            grants.add(new AutomaticGrant(GrantType.ARMOR_PROFICIENCY, armor, source));
            return this;
        }

        public Builder addToolProficiency(String tool, String source) {
            grants.add(new AutomaticGrant(GrantType.TOOL_PROFICIENCY, tool, source));
            return this;
        }

        public Builder addDamageResistance(String damageType, String source) {
            grants.add(new AutomaticGrant(GrantType.DAMAGE_RESISTANCE, damageType, source));
            return this;
        }

        public Builder addAbilityScore(String ability, int bonus, String source) {
            grants.add(new AutomaticGrant(GrantType.ABILITY_SCORE, ability, source, "+" + bonus));
            return this;
        }

        public Builder addInnateSpell(String spell, String source, String details) {
            grants.add(new AutomaticGrant(GrantType.INNATE_SPELL, spell, source, details));
            return this;
        }

        public Builder addTrait(String trait, String source, String description) {
            grants.add(new AutomaticGrant(GrantType.TRAIT, trait, source, description));
            return this;
        }

        public List<AutomaticGrant> build() {
            return List.copyOf(grants);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}