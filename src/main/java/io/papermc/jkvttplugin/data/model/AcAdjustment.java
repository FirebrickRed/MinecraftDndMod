package io.papermc.jkvttplugin.data.model;

/**
 * A temporary change to AC that the DM made (#175): "+1 for the cover you found", "−1, that Shield
 * shouldn't have landed". Kept apart from spell bonuses like Shield, so undoing one never swallows
 * the other, and it can be negative. The DM picks how long it lasts when setting it, so it can't be
 * forgotten on the sheet.
 */
public record AcAdjustment(int amount, Until until) {

    /** How long a DM adjustment lasts. */
    public enum Until {
        /** Until the start of their next turn in a fight (and it ends with the fight). */
        NEXT_TURN("until their next turn"),
        SHORT_REST("until a short rest"),
        LONG_REST("until a long rest"),
        /** Until the DM takes it off. */
        REMOVED("until you remove it");

        private final String label;

        Until(String label) { this.label = label; }

        public String label() { return label; }

        /** Parse a saved or typed value ("long_rest", "LONG_REST", "long"); null if it's none of them. */
        public static Until parse(String s) {
            if (s == null) return null;
            String n = s.trim().toUpperCase().replace(' ', '_');
            for (Until u : values()) if (u.name().equals(n) || u.name().startsWith(n + "_")) return u;
            return null;
        }
    }

    /** "+1 (DM, until a long rest)" — how the adjustment reads next to an AC. */
    public String describe() {
        return (amount >= 0 ? "+" : "") + amount + " (DM, " + until.label() + ")";
    }

    /** Does a short rest end this? A short rest ends turn- and short-rest-scoped adjustments. */
    public boolean endsOnShortRest() { return until == Until.NEXT_TURN || until == Until.SHORT_REST; }

    /** Does a long rest end this? Everything but "until you remove it". */
    public boolean endsOnLongRest() { return until != Until.REMOVED; }
}
