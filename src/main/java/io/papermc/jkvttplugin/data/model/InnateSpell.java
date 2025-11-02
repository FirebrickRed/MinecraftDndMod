package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;

/**
 * Represents a racial innate spell (e.g., Tiefling's Hellish Rebuke, Drow's Faerie Fire).
 * Innate spells can be cantrips (unlimited use) or leveled spells (limited uses per rest).
 */
public class InnateSpell {
    private String spellId;           // References spell in spell registry
    private int levelRequirement;     // Character level when spell becomes available (1, 3, 5, etc.)
    private boolean isCantrip;        // True for cantrips, false for leveled spells
    private int spellLevel;           // Spell level (1-9) for leveled spells, 0 for cantrips
    private int uses;                 // Uses per rest (0 for cantrips = unlimited, -1 if scales with proficiency)
    private int usesRemaining;        // Current remaining uses (runtime tracking, not from YAML)
    private boolean scalesWithProficiency; // True if uses = proficiency bonus
    private String recovery;          // "long_rest", "short_rest", "dawn", etc.
    private Ability castingAbility;   // Intelligence, Wisdom, or Charisma
    private String description;       // Optional description text

    public InnateSpell() {
        // Initialize usesRemaining to match uses (full charges)
        this.usesRemaining = this.uses;
    }

    public String getSpellId() {
        return spellId;
    }

    public void setSpellId(String spellId) {
        this.spellId = spellId;
    }

    public int getLevelRequirement() {
        return levelRequirement;
    }

    public void setLevelRequirement(int levelRequirement) {
        this.levelRequirement = levelRequirement;
    }

    public boolean isCantrip() {
        return isCantrip;
    }

    public void setCantrip(boolean cantrip) {
        isCantrip = cantrip;
    }

    public int getSpellLevel() {
        return spellLevel;
    }

    public void setSpellLevel(int spellLevel) {
        this.spellLevel = spellLevel;
    }

    public int getUses() {
        return uses;
    }

    public void setUses(int uses) {
        this.uses = uses;
        // When setting max uses, also initialize remaining uses if not yet set
        if (this.usesRemaining == 0 && uses > 0) {
            this.usesRemaining = uses;
        }
    }

    public int getUsesRemaining() {
        return usesRemaining;
    }

    public void setUsesRemaining(int usesRemaining) {
        this.usesRemaining = usesRemaining;
    }

    public boolean isScalesWithProficiency() {
        return scalesWithProficiency;
    }
    public void setScalesWithProficiency(boolean scalesWithProficiency) {
        this.scalesWithProficiency = scalesWithProficiency;
    }

    public String getRecovery() {
        return recovery;
    }

    public void setRecovery(String recovery) {
        this.recovery = recovery;
    }

    public Ability getCastingAbility() {
        return castingAbility;
    }

    public void setCastingAbility(Ability castingAbility) {
        this.castingAbility = castingAbility;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Check if this innate spell is available at the given character level.
     */
    public boolean isAvailableAtLevel(int characterLevel) {
        return characterLevel >= levelRequirement;
    }

    /**
     * Checks if this spell can currently be cast.
     * Cantrips can always be cast (unlimited uses). Limited-use abilities require remaining uses.
     *
     * @return true if the spell has uses remaining or is a cantrip, false otherwise
     */
    public boolean canCast() {
        return isCantrip || usesRemaining > 0;
    }

    /**
     * Uses this innate spell, consuming one charge.
     * Cantrips are not affected (unlimited uses). Limited-use spells decrement remaining uses.
     *
     * @return true if successfully used, false if no uses remaining
     */
    public boolean use() {
        if (isCantrip) return true; // Cantrips have unlimited uses
        if (usesRemaining > 0) {
            usesRemaining--;
            return true;
        }
        return false;
    }

    /**
     * Resets uses to maximum, typically called after a rest.
     * For proficiency-scaling abilities (e.g., Astral Elf's Starlight Step),
     * recalculates maximum uses based on current proficiency bonus.
     * For fixed-use abilities, simply restores to predefined maximum.
     *
     * @param proficiencyBonus The character's current proficiency bonus (+2 at level 1-4, +3 at 5-8, etc.)
     */
    public void resetUses(int proficiencyBonus) {
        if (scalesWithProficiency) {
            this.uses = proficiencyBonus;
            this.usesRemaining = proficiencyBonus;
        } else {
            this.usesRemaining = this.uses;
        }
    }

    /**
     * Initializes the uses for this innate spell, called when character is created or loaded.
     * For proficiency-scaling abilities, calculates and sets both maximum and remaining uses
     * based on the character's proficiency bonus. For fixed-use spells, ensures remaining
     * uses are initialized to maximum if not already set.
     *
     * <p>This method should be called:
     * <ul>
     *   <li>During character creation (after racial traits are applied)</li>
     *   <li>When loading a character from save file</li>
     *   <li>After leveling up (when proficiency bonus changes)</li>
     * </ul>
     *
     * @param proficiencyBonus The character's proficiency bonus (+2 at level 1-4, +3 at 5-8, etc.)
     */
    public void initializeUses(int proficiencyBonus) {
        if (scalesWithProficiency) {
            this.uses = proficiencyBonus;
            this.usesRemaining = proficiencyBonus;
        } else if (this.usesRemaining == 0 && this.uses > 0) {
            this.usesRemaining = this.uses;
        }
    }

    /**
     * Gets a formatted string displaying current usage for UI tooltips.
     *
     * @return "At Will" for cantrips and unlimited-use abilities, or "X/Y" format for limited uses (e.g., "2/2", "0/1")
     */
    public String getUsageDisplay() {
        if (isCantrip || uses == 0) {
            return "At Will";
        }
        return usesRemaining + "/" + uses;
    }

    @Override
    public String toString() {
        return "InnateSpell{" +
                "spellId='" + spellId + '\'' +
                ", levelRequirement=" + levelRequirement +
                ", isCantrip=" + isCantrip +
                ", uses=" + uses +
                ", recovery='" + recovery + '\'' +
                ", castingAbility=" + castingAbility +
                '}';
    }
}