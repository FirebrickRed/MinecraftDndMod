package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Set;

/**
 * Handles HP changes during combat (Issue #100): damage application with
 * resistance/vulnerability/immunity, temporary HP, healing, and the death /
 * unconscious triggers that hand off to the death-save system (Issue #101).
 *
 * All amounts passed in are the raw pre-resistance numbers; this class applies
 * the damage type adjustment, then routes to the Combatant HP methods.
 */
public class DamageHandler {

    /** Damage after applying the target's resistance/vulnerability/immunity, plus an optional note to broadcast. */
    private record AdjustedDamage(int amount, String note) {}

    // ==================== DAMAGE ====================

    public static void applyDamage(CombatSession session, Combatant target,
                                   int rawDamage, String damageType, boolean wasCrit) {
        if (rawDamage < 0) rawDamage = 0;

        AdjustedDamage adj = adjustForType(target, rawDamage, damageType);
        int finalDamage = adj.amount();

        boolean wasUnconscious = target.isPlayer() && target.isUnconscious();
        int hpBefore = target.getCurrentHp();
        int tempBefore = target.getTempHp();

        target.applyDamage(finalDamage);

        int hpAfter = target.getCurrentHp();
        int tempAfter = target.getTempHp();

        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━ Damage ━━━", NamedTextColor.RED, TextDecoration.BOLD));
        String typeLabel = (damageType != null && !damageType.isEmpty()) ? " " + damageType : "";
        session.broadcast(Component.text(target.getDisplayName() + " takes " + finalDamage + typeLabel + " damage!", NamedTextColor.WHITE));
        if (adj.note() != null) {
            session.broadcast(Component.text(adj.note(), NamedTextColor.AQUA));
        }
        if (tempBefore > 0 && tempAfter < tempBefore) {
            session.broadcast(Component.text("Temp HP absorbed " + (tempBefore - tempAfter)
                    + " (" + tempBefore + " → " + tempAfter + ")", NamedTextColor.GRAY));
        }
        session.broadcast(Component.text("HP: " + hpBefore + " → " + hpAfter + " / " + target.getMaxHp(), NamedTextColor.GRAY));

        handleDeathTriggers(session, target, wasUnconscious, finalDamage, wasCrit);
        session.broadcast(Component.text("━━━━━━━━━━━━━━", NamedTextColor.RED));
    }

    /**
     * Adjust raw damage for the target's resistance/vulnerability/immunity to a damage type.
     * 5e rule: resistance halves (rounded down), vulnerability doubles, immunity zeroes,
     * and resistance + vulnerability cancel each other out.
     */
    private static AdjustedDamage adjustForType(Combatant target, int damage, String type) {
        if (type == null || type.isEmpty()) return new AdjustedDamage(damage, null);

        if (contains(target.getDamageImmunities(), type)) {
            return new AdjustedDamage(0, target.getDisplayName() + " is IMMUNE to " + type + "!");
        }
        boolean resist = contains(target.getDamageResistances(), type);
        boolean vuln = contains(target.getDamageVulnerabilities(), type);
        if (resist && !vuln) {
            return new AdjustedDamage(damage / 2, target.getDisplayName() + " is RESISTANT to " + type + " (halved).");
        }
        if (vuln && !resist) {
            return new AdjustedDamage(damage * 2, target.getDisplayName() + " is VULNERABLE to " + type + " (doubled).");
        }
        return new AdjustedDamage(damage, null);
    }

    private static boolean contains(Set<String> set, String type) {
        for (String s : set) {
            if (s.equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    // ==================== DEATH / UNCONSCIOUS TRIGGERS ====================

    private static void handleDeathTriggers(CombatSession session, Combatant target,
                                            boolean wasUnconscious, int damageDealt, boolean wasCrit) {
        if (target.isEntity()) {
            if (target.getCurrentHp() <= 0 && !target.isDead()) {
                target.setDead(true);
                session.broadcast(Component.text(target.getDisplayName() + " is defeated!",
                        NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
            return;
        }

        // Player already down: damage while unconscious = automatic death save failure(s).
        if (wasUnconscious) {
            if (damageDealt > 0) {
                int fails = wasCrit ? 2 : 1;
                target.addDeathSaveFailure(fails);
                session.broadcast(Component.text(target.getDisplayName() + " takes damage while down — "
                        + fails + " death save failure" + (fails > 1 ? "s" : "") + "!", NamedTextColor.DARK_RED));
                if (target.isDead()) {
                    DeathSaveHandler.removeProne(target);
                    session.broadcast(Component.text(target.getDisplayName() + " has died.",
                            NamedTextColor.DARK_RED, TextDecoration.BOLD));
                } else {
                    session.broadcast(deathSaveTally(target));
                }
            }
            return;
        }

        // Player just dropped to 0 HP: fall unconscious and begin death saves (Issue #101).
        if (target.getCurrentHp() <= 0 && !target.isUnconscious()) {
            target.setUnconscious(true);
            target.resetDeathSaves();
            DeathSaveHandler.applyProne(target);
            session.broadcast(Component.text(target.getDisplayName() + " falls unconscious!",
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
            session.broadcast(Component.text("Death saving throws begin on their turn.", NamedTextColor.GRAY));
        }
    }

    // ==================== HEALING ====================

    public static void applyHealing(CombatSession session, Combatant target, int amount) {
        if (amount < 0) amount = 0;

        // A dead entity can't be healed back (5e: needs revivify, not hit points).
        if (target.isEntity() && target.getCurrentHp() <= 0) {
            session.broadcast(Component.text(target.getDisplayName() + " is dead and cannot be healed.", NamedTextColor.GRAY));
            return;
        }

        boolean wasDown = target.isPlayer() && (target.isUnconscious() || target.getCurrentHp() <= 0);
        int before = target.getCurrentHp();
        target.applyHealing(amount);
        int after = target.getCurrentHp();

        session.broadcast(Component.empty());
        session.broadcast(Component.text("━━━ Healing ━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        session.broadcast(Component.text(target.getDisplayName() + " heals " + (after - before) + " HP.", NamedTextColor.WHITE));
        session.broadcast(Component.text("HP: " + before + " → " + after + " / " + target.getMaxHp(), NamedTextColor.GRAY));

        if (wasDown && after > 0) {
            target.setUnconscious(false);
            target.resetDeathSaves();
            DeathSaveHandler.removeProne(target);
            session.broadcast(Component.text(target.getDisplayName() + " regains consciousness!",
                    NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        session.broadcast(Component.text("━━━━━━━━━━━━━━", NamedTextColor.GREEN));
    }

    // ==================== TEMPORARY HP ====================

    public static void applyTempHp(CombatSession session, Combatant target, int amount) {
        if (amount < 0) amount = 0;
        boolean granted = target.grantTempHp(amount);
        if (!granted) {
            session.broadcast(Component.text(target.getDisplayName() + " cannot gain temporary HP.", NamedTextColor.GRAY));
            return;
        }
        session.broadcast(Component.text(target.getDisplayName() + " gains " + amount
                + " temporary HP (now " + target.getTempHp() + ").", NamedTextColor.AQUA));
    }

    // ==================== SHARED DISPLAY (reused by Issue #101) ====================

    /** Render a combatant's death-save progress as filled/empty pips. */
    public static Component deathSaveTally(Combatant c) {
        return Component.text("Death Saves: " + dots(c.getDeathSaveSuccesses()) + " success | "
                + dots(c.getDeathSaveFailures()) + " failure", NamedTextColor.YELLOW);
    }

    private static String dots(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(i < n ? "●" : "○");
        }
        return sb.toString();
    }
}
