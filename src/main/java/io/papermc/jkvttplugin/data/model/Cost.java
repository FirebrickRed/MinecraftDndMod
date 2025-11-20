package io.papermc.jkvttplugin.data.model;

/**
 * Represents the cost of an item in D&D currency.
 *
 * YAML format:
 * cost:
 *   amount: 15
 *   currency: gold
 */
public class Cost {
    private int amount;
    private String currency;  // "copper", "silver", "electrum", "gold", "platinum"

    public Cost() {
        this.amount = 0;
        this.currency = "gold";
    }

    public Cost(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    // Getters and Setters
    public int getAmount() {
        return amount;
    }
    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    // Utility Methods

    /**
     * Get currency abbreviation (GP, SP, CP, etc.)
     * ToDo: Update this when working on config I think issue 91 or 92
     */
    public String getCurrencyAbbreviation() {
        return switch (currency.toLowerCase()) {
            case "copper" -> "CP";
            case "silver" -> "SP";
            case "electrum" -> "EP";
            case "gold" -> "GP";
            case "platinum" -> "PP";
            default -> currency.toUpperCase();
        };
    }

    /**
     * Convert cost to base copper value (for comparison/sorting)
     * Using standard D&D conversion: 1 PP = 10 GP = 100 SP = 1000 CP
     * ToDo: update when working on config I think issue 91 or 92
     */
    public int toBaseValue() {
        return switch (currency.toLowerCase()) {
            case "copper" -> amount;
            case "silver" -> amount * 10;
            case "electrum" -> amount * 50;
            case "gold" -> amount * 100;
            case "platinum" -> amount * 1000;
            default -> amount; // Unknown currency defaults to base value
        };
    }

    /**
     * Format cost as display string (e.g., "15 gp", "5 sp")
     */
    public String toDisplayString() {
        return amount + " " + getCurrencyAbbreviation().toLowerCase();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}