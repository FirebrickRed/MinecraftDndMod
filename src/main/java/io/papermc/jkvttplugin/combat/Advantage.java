package io.papermc.jkvttplugin.combat;

/**
 * Advantage / disadvantage on a d20 roll (attack, save, or ability check). Sources (conditions,
 * effects, features, armor) each contribute one of these; {@link #combine} folds them by the 5e rule
 * (PHB p.173): if at least one source grants advantage and at least one imposes disadvantage, the roll
 * is normal — "even if multiple circumstances impose disadvantage and only one grants advantage".
 * <p>
 * That rule is why {@link #CANCELLED} exists. Folding pairwise with only three values is wrong:
 * advantage + disadvantage → normal, then + another disadvantage → disadvantage again. Once both kinds
 * have been seen the result must stay normal, so the fold remembers it. Treat CANCELLED like NONE when
 * rolling ({@link #affectsRoll()} is false); it only differs in that it can't be tipped back.
 */
public enum Advantage {
    NONE,
    ADVANTAGE,
    DISADVANTAGE,
    /** Both advantage and disadvantage applied: roll normally, and nothing further changes that. */
    CANCELLED;

    public boolean isAdvantage() { return this == ADVANTAGE; }
    public boolean isDisadvantage() { return this == DISADVANTAGE; }

    /** True if this changes how the d20 is rolled (two dice, keep one). NONE and CANCELLED roll one. */
    public boolean affectsRoll() { return this == ADVANTAGE || this == DISADVANTAGE; }

    /** 5e stacking: any advantage and any disadvantage together cancel, permanently. */
    public Advantage combine(Advantage other) {
        if (other == null || other == NONE) return this;
        if (this == CANCELLED || other == CANCELLED) return CANCELLED;
        if (this == NONE) return other;
        if (this == other) return this;         // adv+adv or dis+dis stays as-is (5e: still just adv/dis)
        return CANCELLED;                       // adv + dis cancel
    }

    /** Fold a source's contribution: true adds an advantage, false a disadvantage, per flag. */
    public Advantage with(boolean advantage) {
        return combine(advantage ? ADVANTAGE : DISADVANTAGE);
    }

    public String label() {
        return switch (this) {
            case ADVANTAGE -> "advantage";
            case DISADVANTAGE -> "disadvantage";
            case CANCELLED -> "a normal roll (advantage and disadvantage cancel)";
            default -> "normal";
        };
    }
}
