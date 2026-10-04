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

    /**
     * The weapon item this attack represents, if any (Issue #132). When set, the entity is holding
     * that item — so it appears in the possession hotbar and can be looted — while the attack's
     * stats (to-hit/damage/reach) still drive combat. Natural attacks (bite, claw) and spell attacks
     * leave this null. {@link #lootable} lets a weapon be usable/held but not dropped.
     */
    private String item;
    private boolean lootable = true;

    /**
     * Optional vanilla Material name (YAML `material:`) for a natural/spell attack that has no {@link #item}
     * — e.g. a wolf's Bite or a sorcerer's Fire Bolt — so a possessing DM has something to right-click.
     */
    private String material;

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

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public boolean isLootable() { return lootable; }
    public void setLootable(boolean lootable) { this.lootable = lootable; }

    /** The vanilla Material this attack renders as (YAML `material:`), for the possession hotbar. */
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    // ==================== LIMITED USE (#256) AND AMMUNITION (#257) ====================

    /** How a limited ability comes back (MM p.11, "Limited Usage"). */
    public enum Recharge {
        /** "X/Day": back at dawn, or after a long rest. */
        DAY,
        /** "Recharge 5-6": a d6 at the start of each of its turns; back on {@link #getRechargeMin()} or more. */
        ROLL,
        /** "Recharges after a Short or Long Rest". */
        SHORT_REST,
        /** Back after a long rest only. */
        LONG_REST
    }

    private int uses;                 // how many before it's spent; 0 = unlimited
    private Recharge recharge;        // null when unlimited
    private int rechargeMin;          // ROLL: the lowest d6 that brings it back
    private Integer ammunition;       // null = the Monster Manual's default; -1 = unlimited; else that many
    private final java.util.List<String> limitProblems = new java.util.ArrayList<>();

    public boolean isLimited() { return uses > 0 && recharge != null; }
    public int getUses() { return uses; }
    public Recharge getRecharge() { return recharge; }
    public int getRechargeMin() { return rechargeMin; }
    /** The YAML's {@code ammunition:}: null for the default (2d10 / 2d4), -1 for unlimited, else a count. */
    public Integer getAmmunitionOverride() { return ammunition; }
    /** What was wrong with {@code uses:} / {@code recharge:} / {@code ammunition:}, for the content check. */
    public java.util.List<String> getLimitProblems() { return limitProblems; }

    /**
     * Read {@code uses:}, {@code recharge:} and {@code ammunition:} as a stat block prints them:
     * <pre>
     * uses: 3                 3/Day
     * recharge: "5-6"         Recharge 5-6   ("6" or 6 for Recharge 6)
     * recharge: short_rest    Recharges after a Short or Long Rest   (long_rest: a long rest only)
     * uses: 2 + recharge: short_rest      twice, then a rest
     * ammunition: 12 | unlimited          instead of the default 2d10 arrows / 2d4 thrown
     * </pre>
     * {@code uses:} alone is per day; a {@code recharge:} alone is one use.
     */
    public void setLimits(Object usesRaw, Object rechargeRaw, Object ammunitionRaw) {
        limitProblems.clear();
        uses = 0; recharge = null; rechargeMin = 0; ammunition = null;

        if (usesRaw instanceof Number n && n.intValue() > 0) uses = n.intValue();
        else if (usesRaw != null) limitProblems.add("uses: '" + usesRaw + "' should be a whole number above 0 (3 for 3/Day)");

        if (rechargeRaw != null) {
            String r = String.valueOf(rechargeRaw).trim().toLowerCase().replace('–', '-').replace(' ', '_');
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([1-6])(?:-6)?").matcher(r);
            if (m.matches()) { recharge = Recharge.ROLL; rechargeMin = Integer.parseInt(m.group(1)); }
            else if (r.equals("short_rest")) recharge = Recharge.SHORT_REST;
            else if (r.equals("long_rest")) recharge = Recharge.LONG_REST;
            else if (r.equals("day")) recharge = Recharge.DAY;
            else limitProblems.add("recharge: '" + rechargeRaw + "' should be \"5-6\" (or \"6\"), short_rest, long_rest or day");
        }
        if (recharge != null && uses == 0) uses = 1;         // "Recharge 5-6": one use
        if (uses > 0 && recharge == null) recharge = Recharge.DAY; // "3/Day"

        if (ammunitionRaw instanceof Number n && n.intValue() >= 0) ammunition = n.intValue();
        else if (ammunitionRaw != null && String.valueOf(ammunitionRaw).trim().equalsIgnoreCase("unlimited")) ammunition = -1;
        else if (ammunitionRaw != null) limitProblems.add("ammunition: '" + ammunitionRaw + "' should be a number, or unlimited");
    }

    /** "3/Day", "Recharge 5-6", "1/Short Rest": the limit as a stat block prints it, or "" when unlimited. */
    public String limitLabel() {
        if (!isLimited()) return "";
        return switch (recharge) {
            case DAY -> uses + "/Day";
            case ROLL -> "Recharge " + (rechargeMin >= 6 ? "6" : rechargeMin + "-6");
            case SHORT_REST -> uses + "/Short Rest";
            case LONG_REST -> uses + "/Long Rest";
        };
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