package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Death saving throws and the unconscious/prone state (Issue #101).
 *
 * A downed player (0 HP) makes a death save on their turn: DC 10, three successes
 * stabilize, three failures kill. Natural 20 regains 1 HP; natural 1 counts as two
 * failures. Damage-while-down (auto-fail) and heal-while-down (revive) live in
 * {@link DamageHandler}; this class owns the save roll itself and the prone effect.
 */
public class DeathSaveHandler {

    private static final int DEATH_SAVE_DC = 10;

    public static void rollDeathSave(CombatSession session, Combatant target, Integer providedRoll) {
        int d20 = (providedRoll != null) ? providedRoll : DiceRoller.rollDice(1, 20);

        session.broadcast(Component.empty());
        session.broadcast(Component.text(target.getDisplayName() + " makes a death saving throw...", NamedTextColor.GRAY));

        if (d20 == 20) {
            // Natural 20: regain 1 HP and consciousness.
            target.applyHealing(1);
            target.setUnconscious(false);
            target.resetDeathSaves();
            removeProne(target);
            session.broadcast(Component.text("★ NATURAL 20 ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            session.broadcast(Component.text(target.getDisplayName() + " regains 1 HP and consciousness!", NamedTextColor.GREEN, TextDecoration.BOLD));
            session.updateScoreboard();
            return;
        }

        if (d20 == 1) {
            // Natural 1: two failures.
            target.addDeathSaveFailure(2);
            session.broadcast(Component.text("✗ NATURAL 1 — counts as TWO failures!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else if (d20 >= DEATH_SAVE_DC) {
            target.addDeathSaveSuccess();
            session.broadcast(Component.text("Roll: " + d20 + " → SUCCESS (DC " + DEATH_SAVE_DC + ")", NamedTextColor.GREEN));
        } else {
            target.addDeathSaveFailure(1);
            session.broadcast(Component.text("Roll: " + d20 + " → FAILURE (DC " + DEATH_SAVE_DC + ")", NamedTextColor.RED));
        }

        // Resolve outcome.
        if (target.isDead()) {
            removeProne(target);
            session.broadcast(Component.text(target.getDisplayName() + " has DIED.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            session.offerEndIfDecided(); // a death here may have ended the fight (e.g. a TPK)
        } else if (target.isStabilized()) {
            session.broadcast(Component.text(target.getDisplayName() + " is STABILIZED (unconscious but no longer dying).", NamedTextColor.AQUA, TextDecoration.BOLD));
            session.broadcast(DamageHandler.deathSaveTally(target));
        } else {
            session.broadcast(DamageHandler.deathSaveTally(target));
        }

        session.updateScoreboard();
    }

    // ==================== PRONE EFFECT ====================

    /** Apply a prone-like effect (heavy slowness) to a downed player. */
    public static void applyProne(Combatant combatant) {
        if (!combatant.isPlayer()) return;
        Player player = combatant.getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
        }
    }

    /** Remove the prone effect when a player is revived or combat ends. */
    public static void removeProne(Combatant combatant) {
        if (!combatant.isPlayer()) return;
        Player player = combatant.getPlayer();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }
}
