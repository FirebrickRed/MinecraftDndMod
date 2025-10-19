package io.papermc.jkvttplugin.data.model;

/**
 * Represents a class-specific resource that can be consumed and recovered.
 * Examples: Barbarian Rage, Monk Ki Points, Bard Bardic Inspiration, Fighter Action Surge
 */
public class ClassResource {
    private final String name;
    private int current;
    private final int max;
    private final RecoveryType recovery;
    private final String icon;  // Material ID for display (e.g., "red_dye", "glowstone_dust")

    public ClassResource(String name, int max, RecoveryType recovery) {
        this(name, max, recovery, null);
    }

    public ClassResource(String name, int max, RecoveryType recovery, String icon) {
        this.name = name;
        this.max = max;
        this.current = max;  // Start at full
        this.recovery = recovery;
        this.icon = icon;
    }

    /**
     * Copy constructor for creating instances with current/max values.
     */
    public ClassResource(String name, int current, int max, RecoveryType recovery) {
        this(name, current, max, recovery, null);
    }

    /**
     * Full constructor for creating instances with current/max values and icon.
     */
    public ClassResource(String name, int current, int max, RecoveryType recovery, String icon) {
        this.name = name;
        this.current = current;
        this.max = max;
        this.recovery = recovery;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public int getCurrent() {
        return current;
    }

    public int getMax() {
        return max;
    }

    public RecoveryType getRecovery() {
        return recovery;
    }

    /**
     * Gets the material icon ID for this resource (e.g., "red_dye", "glowstone_dust").
     * @return Material ID string, or null if not specified (defaults to nether_star)
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Checks if the resource can be used (has charges remaining).
     */
    public boolean canUse() {
        return current > 0;
    }

    /**
     * Consumes one use of this resource.
     * @return true if successfully consumed, false if no uses remain
     */
    public boolean consume() {
        return consume(1);
    }

    /**
     * Consumes the specified amount of this resource.
     * @param amount Number of uses to consume
     * @return true if successfully consumed, false if insufficient uses
     */
    public boolean consume(int amount) {
        if (current >= amount) {
            current -= amount;
            return true;
        }
        return false;
    }

    /**
     * Restores this resource to maximum.
     */
    public void restore() {
        current = max;
    }

    /**
     * Restores a specific amount of this resource (up to max).
     */
    public void restore(int amount) {
        current = Math.min(current + amount, max);
    }

    /**
     * Returns a display string for this resource (e.g., "Rage: 2/3").
     */
    public String getDisplayString() {
        return name + ": " + current + "/" + max;
    }

    /**
     * Defines when a resource recovers.
     */
    public enum RecoveryType {
        SHORT_REST,  // Recovers on short rest (Ki Points, Bardic Inspiration)
        LONG_REST,   // Recovers on long rest (Rage, most resources)
        DAWN,        // Recovers at dawn (some special abilities)
        NONE         // Never recovers automatically (one-time abilities)
    }

    @Override
    public String toString() {
        return "ClassResource{" +
                "name='" + name + '\'' +
                ", current=" + current +
                ", max=" + max +
                ", recovery=" + recovery +
                '}';
    }
}