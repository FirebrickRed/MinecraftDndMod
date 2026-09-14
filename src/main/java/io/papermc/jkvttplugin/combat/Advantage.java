package io.papermc.jkvttplugin.combat;

/**
 * Advantage / disadvantage on a d20 roll (attack, save, or ability check). Sources (conditions,
 * effects, features) each contribute one of these; {@link #combine} folds them by the 5e rule:
 * any number of advantages and disadvantages present at once simply cancel to a normal roll.
 */
public enum Advantage {
    NONE,
    ADVANTAGE,
    DISADVANTAGE;

    public boolean isAdvantage() { return this == ADVANTAGE; }
    public boolean isDisadvantage() { return this == DISADVANTAGE; }

    /** 5e stacking: having at least one advantage AND one disadvantage cancels to normal. */
    public Advantage combine(Advantage other) {
        if (other == null || other == NONE) return this;
        if (this == NONE) return other;
        if (this == other) return this;         // adv+adv or dis+dis stays as-is (5e: still just adv/dis)
        return NONE;                            // adv + dis cancel
    }

    /** Fold a source's contribution: true adds an advantage, false a disadvantage, per flag. */
    public Advantage with(boolean advantage) {
        return combine(advantage ? ADVANTAGE : DISADVANTAGE);
    }

    public String label() {
        return switch (this) {
            case ADVANTAGE -> "advantage";
            case DISADVANTAGE -> "disadvantage";
            default -> "normal";
        };
    }
}
