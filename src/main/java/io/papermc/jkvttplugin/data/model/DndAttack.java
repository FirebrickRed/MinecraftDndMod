package io.papermc.jkvttplugin.data.model;

/**
 * Represents a single attack action in D&D 5e.
 * Used by both entities (monsters, NPCs) and players (weapon attacks, spell attacks).
 *
 * This is a SIMPLE data structure for now - the foundation for the combat epic.
 * Future combat system will add methods like:
 * - rollAttack() - Roll d20 + toHit with advantage/disadvantage
 * - rollDamage() - Parse and roll damage dice
 * - applyResistance() - Handle resistance/vulnerability/immunity
 * - etc.
 *
 * For now, this just holds attack data from YAML and stat blocks.
 */
public class DndAttack {

    /**
     * Attack name (e.g., "Longsword", "Bite", "Fire Bolt")
     */
    private String name;

    /**
     * Attack bonus (e.g., +4, +7)
     * Used for attack rolls: 1d20 + toHit vs target AC
     */
    private int toHit;

    /**
     * Reach or range of the attack.
     * Examples:
     * - "5 ft." (melee)
     * - "10 ft." (reach weapon)
     * - "80/320 ft." (ranged, normal/long range)
     */
    private String reach;

    /**
     * Damage dice notation (e.g., "1d8+2", "2d6+4", "1d10")
     * Future combat system will parse and roll this.
     */
    private String damage;

    /**
     * Damage type (e.g., "slashing", "piercing", "bludgeoning", "fire", "cold", "poison")
     * Used for resistance/vulnerability/immunity checks.
     */
    private String damageType;

    // ==================== CONSTRUCTORS ====================

    /**
     * Default constructor for YAML deserialization.
     */
    public DndAttack() {}

    /**
     * Full constructor for manual creation.
     */
    public DndAttack(String name, int toHit, String reach, String damage, String damageType) {
        this.name = name;
        this.toHit = toHit;
        this.reach = reach;
        this.damage = damage;
        this.damageType = damageType;
    }

    // ==================== GETTERS & SETTERS ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getToHit() {
        return toHit;
    }

    public void setToHit(int toHit) {
        this.toHit = toHit;
    }

    public String getReach() {
        return reach;
    }

    public void setReach(String reach) {
        this.reach = reach;
    }

    public String getDamage() {
        return damage;
    }

    public void setDamage(String damage) {
        this.damage = damage;
    }

    public String getDamageType() {
        return damageType;
    }

    public void setDamageType(String damageType) {
        this.damageType = damageType;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Returns a human-readable description of this attack.
     * Format: "Longsword (+4 to hit, 1d8+2 slashing)"
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" (+").append(toHit).append(" to hit");
        if (reach != null && !reach.isEmpty()) {
            sb.append(", reach ").append(reach);
        }
        sb.append(", ").append(damage).append(" ").append(damageType);
        sb.append(")");
        return sb.toString();
    }
}