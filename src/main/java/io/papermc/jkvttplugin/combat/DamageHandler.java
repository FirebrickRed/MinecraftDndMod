package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Set;

/**
 * Handles HP changes (Issue #100): damage application with resistance/vulnerability/immunity,
 * temporary HP, healing, and the death / unconscious triggers that hand off to the death-save
 * system (Issue #101).
 *
 * All amounts passed in are the raw pre-resistance numbers; this class applies
 * the damage type adjustment, then routes to the Combatant HP methods.
 *
 * <p><b>The session is optional.</b> A trap in a corridor, a potion after the fight and a DM
 * correction all change HP with no combat running, and they should behave exactly like the same
 * thing mid-fight — same resistances, same downing, same persistence. Pass {@code null} for
 * {@code session} and the combat-only parts (turn/HP displays, ritual interruption, the
 * "is the fight over?" offer) are skipped, while the messages go to the affected player and the
 * DMs instead of the table. {@link CombatTargets} builds the {@link Combatant} either way.
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
        if (finalDamage > 0) {
            target.markEffectsMaintained("took_damage"); // keeps Rage etc. going (#70)
            CombatVisuals.hurtOnDamage(target);           // flinch + hurt sound as HP actually drops (#181)
        }

        // Cosmetic on-hit effect for the damage type (fire → burning, cold → snowflakes, …).
        org.bukkit.entity.Entity body = target.isPlayer() ? target.getPlayer()
                : (target.getEntityInstance() != null ? target.getEntityInstance().getArmorStand() : null);
        io.papermc.jkvttplugin.data.loader.DamageTypeLoader.playHitEffect(damageType, body);

        int hpAfter = target.getCurrentHp();
        int tempAfter = target.getTempHp();

        say(session, target, Component.empty());
        say(session, target, Component.text("━━━ Damage ━━━", NamedTextColor.RED, TextDecoration.BOLD));
        String typeLabel = (damageType != null && !damageType.isEmpty()) ? " " + damageType : "";
        say(session, target, Component.text(target.getDisplayName() + " takes " + finalDamage + typeLabel + " damage!", NamedTextColor.WHITE));
        if (adj.note() != null) {
            say(session, target, Component.text(adj.note(), NamedTextColor.AQUA));
        }
        if (tempBefore > 0 && tempAfter < tempBefore) {
            say(session, target, Component.text("Temp HP absorbed " + (tempBefore - tempAfter)
                    + " (" + tempBefore + " → " + tempAfter + ")", NamedTextColor.GRAY));
        }
        say(session, target, Component.text("HP: " + hpBefore + " → " + hpAfter + " / " + target.getMaxHp(), NamedTextColor.GRAY));

        handleDeathTriggers(session, target, wasUnconscious, finalDamage, wasCrit);
        if (session != null) {
            RitualManager.onDamage(session, target, finalDamage); // may break a channelled ritual (#156)
        }
        say(session, target, Component.text("━━━━━━━━━━━━━━", NamedTextColor.RED));

        // If that drop decided the fight, let the DM wrap it up (one-click /combat finished).
        if (session != null) session.offerEndIfDecided();
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
        // Static resistances (race/monster) OR a temporary one from an active effect (e.g. Rage, #70).
        boolean resist = contains(target.getDamageResistances(), type) || target.resistsDamage(type);
        boolean vuln = contains(target.getDamageVulnerabilities(), type);
        if (resist && !vuln) {
            String src = target.resistanceSourceFor(type);        // name the effect (e.g. Rage), if any
            String from = (src != null) ? " (" + src + ")" : "";
            return new AdjustedDamage(damage / 2, target.getDisplayName() + " is RESISTANT to " + type + from + " — halved.");
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
                say(session, target, Component.text(target.getDisplayName() + " is defeated!",
                        NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
            return;
        }

        // Player already down: damage while unconscious = automatic death save failure(s).
        if (wasUnconscious) {
            if (damageDealt > 0) {
                int fails = wasCrit ? 2 : 1;
                target.addDeathSaveFailure(fails);
                say(session, target, Component.text(target.getDisplayName() + " takes damage while down — "
                        + fails + " death save failure" + (fails > 1 ? "s" : "") + "!", NamedTextColor.DARK_RED));
                if (target.isDead()) {
                    DeathSaveHandler.removeProne(target);
                    say(session, target, Component.text(target.getDisplayName() + " has died.",
                            NamedTextColor.DARK_RED, TextDecoration.BOLD));
                } else {
                    say(session, target, deathSaveTally(target));
                }
            }
            return;
        }

        // Player just dropped to 0 HP: fall unconscious and begin death saves (Issue #101) — unless
        // Half-Orc Relentless Endurance holds them at 1 HP instead (once per long rest, #70).
        if (target.getCurrentHp() <= 0 && !target.isUnconscious()) {
            io.papermc.jkvttplugin.character.CharacterSheet sheet = target.getCharacterSheet();
            if (sheet != null && sheet.canEndureLethalHit()) {
                sheet.markRelentlessEnduranceUsed();
                target.applyHealing(1 - target.getCurrentHp()); // brought to exactly 1 HP
                say(session, target, Component.text("✊ Relentless Endurance! " + target.getDisplayName()
                        + " refuses to fall — holding on at 1 HP!", NamedTextColor.GOLD, TextDecoration.BOLD));
                if (session != null) session.refreshHpDisplays(target);
                return;
            }
            target.setUnconscious(true);
            target.resetDeathSaves();
            DeathSaveHandler.applyProne(target);
            say(session, target, Component.text(target.getDisplayName() + " falls unconscious!",
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
            say(session, target, Component.text(session != null
                    ? "Death saving throws begin on their turn."
                    : "Out of combat: start a fight, or the DM calls death saves with /combat deathsave.",
                    NamedTextColor.GRAY));
        }
    }

    // ==================== HEALING ====================

    public static void applyHealing(CombatSession session, Combatant target, int amount) {
        if (amount < 0) amount = 0;

        // A dead entity can't be healed back (5e: needs revivify, not hit points).
        if (target.isEntity() && target.getCurrentHp() <= 0) {
            say(session, target, Component.text(target.getDisplayName() + " is dead and cannot be healed.", NamedTextColor.GRAY));
            return;
        }

        boolean wasDown = target.isPlayer() && (target.isUnconscious() || target.getCurrentHp() <= 0);
        int before = target.getCurrentHp();
        target.applyHealing(amount);
        int after = target.getCurrentHp();

        say(session, target, Component.empty());
        say(session, target, Component.text("━━━ Healing ━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        say(session, target, Component.text(target.getDisplayName() + " heals " + (after - before) + " HP.", NamedTextColor.WHITE));
        say(session, target, Component.text("HP: " + before + " → " + after + " / " + target.getMaxHp(), NamedTextColor.GRAY));

        if (wasDown && after > 0) {
            target.setUnconscious(false);
            target.resetDeathSaves();
            DeathSaveHandler.removeProne(target);
            say(session, target, Component.text(target.getDisplayName() + " regains consciousness!",
                    NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        say(session, target, Component.text("━━━━━━━━━━━━━━", NamedTextColor.GREEN));
    }

    // ==================== TEMPORARY HP ====================

    public static void applyTempHp(CombatSession session, Combatant target, int amount) {
        if (amount < 0) amount = 0;
        boolean granted = target.grantTempHp(amount);
        if (!granted) {
            say(session, target, Component.text(target.getDisplayName() + " cannot gain temporary HP.", NamedTextColor.GRAY));
            return;
        }
        say(session, target, Component.text(target.getDisplayName() + " gains " + amount
                + " temporary HP (now " + target.getTempHp() + ").", NamedTextColor.AQUA));
    }

    // ==================== SHARED DISPLAY (reused by Issue #101) ====================

    /**
     * Send a line to whoever should see this HP change: the whole table during combat, otherwise the
     * affected player plus every online DM (so a trap or a potion still leaves a trail).
     */
    private static void say(CombatSession session, Combatant target, Component message) {
        if (session != null) {
            session.broadcast(message);
            return;
        }
        org.bukkit.entity.Player affected = target.isPlayer() ? target.getPlayer() : null;
        if (affected != null && affected.isOnline()) affected.sendMessage(message);
        for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.equals(affected)) continue;
            if (io.papermc.jkvttplugin.dm.DMManager.isDM(online)) online.sendMessage(message);
        }
    }

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
