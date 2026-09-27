package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.effect.Feature;

/**
 * Whether an attack can be made as a <b>bonus action</b> ({@code /combat attack <target> <weapon> bonus}).
 * Two things grant one, and both only after taking the Attack action this turn:
 * <ul>
 *   <li>two-weapon fighting: a light weapon in each hand, attacking with the off-hand one (PHB p.195).
 *       The damage then gets no positive ability modifier.</li>
 *   <li>a feature with {@code activation: bonus_action} and an {@code attack:} block, such as Martial
 *       Arts' unarmed strike (PHB p.78, #221), while its requirements (no armor, no shield) hold.</li>
 * </ul>
 */
public final class BonusAttack {

    private BonusAttack() {}

    public enum Kind { OFF_HAND, FEATURE }

    /** Either what allows it ({@code kind}, and the feature's name for a feature) or why not ({@code refusal}). */
    public record Verdict(Kind kind, String source, String refusal) {
        static Verdict no(String why) { return new Verdict(null, null, why); }
        public boolean allowed() { return refusal == null; }
    }

    /**
     * @param weapon   the weapon attacked with, or null for an unarmed strike
     * @param mainHand the weapon held in the main hand, or null
     * @param offHand  the weapon held in the off hand, or null
     */
    public static Verdict check(CharacterSheet sheet, DndWeapon weapon, DndWeapon mainHand, DndWeapon offHand,
                                boolean attackActionTaken, boolean bonusActionUsed, boolean damagePending) {
        if (bonusActionUsed) return Verdict.no("You've already used your bonus action this turn.");
        if (!attackActionTaken) {
            return Verdict.no("A bonus attack comes after taking the Attack action this turn (two-weapon fighting, Martial Arts). Attack first.");
        }
        if (damagePending) return Verdict.no("Roll the damage for your last hit first (/combat damage).");

        if (sheet != null) {
            for (Feature f : sheet.getAllFeatures()) {
                if (!f.isAttack() || f.getActivation() == null || !f.getActivation().equalsIgnoreCase("bonus_action")) continue;
                if (!sheet.meets(f.getAttack().requires())) continue;
                boolean sameWeapon = f.getAttack().isUnarmed()
                        ? weapon == null
                        : weapon != null && f.getAttack().weapon().equalsIgnoreCase(weapon.getId());
                if (sameWeapon) return new Verdict(Kind.FEATURE, f.getName(), null);
            }
        }

        if (weapon != null && offHand != null && weapon.getId().equalsIgnoreCase(offHand.getId())
                && isLight(mainHand) && isLight(offHand)) {
            return new Verdict(Kind.OFF_HAND, "two-weapon fighting", null);
        }

        String what = weapon == null ? "an unarmed strike" : "the " + weapon.getName();
        return Verdict.no("Nothing lets you make " + what + " as a bonus action. Two-weapon fighting needs a light "
                + "weapon in each hand, attacking with the off-hand one; Martial Arts needs no armor or shield.");
    }

    /** True if a weapon has the Light property (two-weapon fighting). */
    public static boolean isLight(DndWeapon weapon) {
        if (weapon == null || weapon.getProperties() == null) return false;
        for (String p : weapon.getProperties()) {
            if (p != null && p.equalsIgnoreCase("light")) return true;
        }
        return false;
    }
}
