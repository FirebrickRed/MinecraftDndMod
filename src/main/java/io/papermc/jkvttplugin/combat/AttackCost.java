package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndWeapon;

/**
 * What a player's attack costs, so {@code /combat attack} never has to be told. "Attack" means the
 * roll; the game works out which part of the turn pays for it:
 * <ol>
 *   <li>another attack the Attack action still allows (Extra Attack, #153): free</li>
 *   <li>otherwise the Action, if it's unused: this starts the Attack action</li>
 *   <li>otherwise the bonus action, if a bonus attack fits the weapon ({@link BonusAttack})</li>
 * </ol>
 * The word {@code bonus} asks for the bonus action outright, for using it while the Action is still
 * free (off-hand first, BG3-style, when {@code combat.bonus_attack_timing} allows that).
 */
public final class AttackCost {

    private AttackCost() {}

    public enum Kind { ACTION, EXTRA_ATTACK, BONUS }

    /** What pays for the attack, or why it can't be made ({@code refusal}). */
    public record Decision(Kind kind, BonusAttack.Kind bonusKind, String source, String refusal) {
        static Decision no(String why) { return new Decision(null, null, null, why); }
        public boolean allowed() { return refusal == null; }
        /** A two-weapon-fighting attack: no positive ability modifier on its damage. */
        public boolean offHand() { return kind == Kind.BONUS && bonusKind == BonusAttack.Kind.OFF_HAND; }
    }

    /**
     * @param weapon     the weapon attacked with, or null for an unarmed strike
     * @param wantsBonus the player said {@code bonus}
     */
    public static Decision decide(CharacterSheet sheet, DndWeapon weapon, DndWeapon mainHand, DndWeapon offHand,
                                  TurnState state, boolean wantsBonus, boolean bonusNeedsAttackAction) {
        if (state.isDamagePending()) return Decision.no("Roll the damage for your last hit first (/combat damage).");

        if (!wantsBonus) {
            if (state.getAttacksLeftInAction() > 0) return new Decision(Kind.EXTRA_ATTACK, null, null, null);
            if (!state.isActionUsed()) return new Decision(Kind.ACTION, null, null, null);
        }

        BonusAttack.Verdict v = BonusAttack.check(sheet, weapon, mainHand, offHand, state.getAttackActionWith(),
                state.isBonusActionUsed(), false, bonusNeedsAttackAction);
        if (v.allowed()) return new Decision(Kind.BONUS, v.kind(), v.source(), null);
        return Decision.no(wantsBonus ? v.refusal() : "You've already used your Action this turn. " + v.refusal());
    }

    /** Attacks the Attack action gives this character: 1 until Extra Attack arrives with level-up (#153). */
    public static int attacksPerAction(CharacterSheet sheet) {
        return 1;
    }
}
