package io.papermc.jkvttplugin.data.model.enums;

/**
 * Standard D&D 5e currency types with conversion rates.
 *
 * Future-proofed for Issue #91/92 - DM Config System:
 * This enum handles standard D&D currencies, but custom currencies
 * can be defined in config YAML and will work alongside these defaults.
 *
 * Conversion rates (to copper):
 * - 1 PP = 1000 CP
 * - 1 GP = 100 CP
 * - 1 EP = 50 CP
 * - 1 SP = 10 CP
 * - 1 CP = 1 CP
 */
public enum Currency {
    COPPER("copper", "CP", 1),
    SILVER("silver", "SP", 10),
    ELECTRUM("electrum", "EP", 50),
    GOLD("gold", "GP", 100),
    PLATINUM("platinum", "PP", 1000);

    private final String id;
    private final String abbreviation;
    private final int copperValue;

    Currency(String id, String abbreviation, int copperValue) {
        this.id = id;
        this.abbreviation = abbreviation;
        this.copperValue = copperValue;
    }

    public String getId() {
        return id;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public int getCopperValue() {
        return copperValue;
    }

    // ==================== STATIC HELPERS ====================

    /**
     * Find Currency enum by string ID (case-insensitive).
     * Returns null for custom currencies not in the enum.
     *
     * @param id The currency ID (e.g., "gold", "silver", or custom like "credits")
     * @return The Currency enum, or null if custom currency
     */
    public static Currency fromString(String id) {
        if (id == null) return null;

        for (Currency currency : values()) {
            if (currency.id.equalsIgnoreCase(id)) {
                return currency;
            }
        }
        return null; // Custom currency (not in standard D&D set)
    }

    /**
     * Get conversion rate for any currency (standard or custom).
     *
     * For standard D&D currencies, returns the enum's conversion rate.
     * For custom currencies, returns 1 (base value) until config system loads custom rates.
     *
     * @param currencyId The currency ID
     * @return The copper conversion rate (defaults to 1 for unknown currencies)
     */
    public static int getConversionRate(String currencyId) {
        Currency currency = fromString(currencyId);
        if (currency != null) {
            return currency.copperValue;
        }

        // Custom currency - return base value (Issue #91/92 will load from config)
        return 1;
    }

    /**
     * Get abbreviation for any currency (standard or custom).
     *
     * @param currencyId The currency ID
     * @return The abbreviation (e.g., "GP", "SP", or uppercase ID for custom)
     */
    public static String getAbbreviation(String currencyId) {
        Currency currency = fromString(currencyId);
        if (currency != null) {
            return currency.abbreviation;
        }

        // Custom currency - uppercase the ID (Issue #91/92 will load from config)
        return currencyId != null ? currencyId.toUpperCase() : "??";
    }

    /**
     * Check if a currency ID is a standard D&D currency.
     *
     * @param currencyId The currency ID to check
     * @return true if standard D&D currency, false if custom
     */
    public static boolean isStandardCurrency(String currencyId) {
        return fromString(currencyId) != null;
    }
}