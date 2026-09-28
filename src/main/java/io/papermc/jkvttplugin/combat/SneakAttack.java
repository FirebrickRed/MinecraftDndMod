package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sneak Attack (PHB p.96, #229). Once per turn, a hit with a finesse or ranged weapon deals extra
 * damage if you have advantage, or if an ally of yours is within 5 feet of the target (and isn't
 * incapacitated) and you don't have disadvantage.
 *
 * <p><b>Advantage applies on its own</b>; nobody disputes that part. <b>Everything else is the DM's
 * call</b>, because tables read "an ally next to it" differently (next to it, flanking, "it isn't
 * paying attention to you"). So a qualifying hit without advantage offers the player [Ask the DM],
 * telling the DM what the game noticed (an ally 5 ft away, or nothing), and the DM allows or denies it.
 *
 * <p>The dice join the hit's own ("1d8+1d6+3"), so a crit doubles them. It counts as used for the
 * turn only when the damage actually lands: a hit a Shield turns into a miss doesn't spend it.
 * "Once per turn" is any turn, so an opportunity attack on someone else's turn has its own chance.
 */
public final class SneakAttack {

    private SneakAttack() {}

    /**
     * It could apply: these dice, by the feature's name, why, the hit's damage type, and whether it
     * needs the DM's say (anything but advantage).
     */
    public record Use(String dice, String source, String reason, String damageType, boolean needsDm) {}

    /** Rogue id → the turn they last used it on ("round:whose turn"). */
    private static final Map<UUID, String> usedOn = new HashMap<>();

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    /** Whether this attack could get Sneak Attack, or null. Nothing is spent here. */
    public static Use check(CombatSession session, Combatant attacker, Combatant target, CharacterSheet sheet, DndWeapon weapon) {
        if (session == null || sheet == null) return null;
        Map.Entry<String, String> sa = sheet.sneakAttack();
        if (sa == null) return null;
        if (weapon == null || !(weapon.isFinesse() || weapon.isRanged())) return null;
        if (usedThisTurn(session, attacker)) return null;

        Advantage adv = attacker.attackAdvantageAgainst(target);
        if (adv.isDisadvantage()) return null;
        String type = weapon.getDamageType();
        if (adv.isAdvantage()) return new Use(sa.getValue(), sa.getKey(), "advantage", type, false);
        Combatant ally = allyNextTo(session, attacker, target);
        return new Use(sa.getValue(), sa.getKey(), ally != null
                ? "the game sees " + ally.getDisplayName() + " within 5 ft of them"
                : "the game sees no advantage and no ally next to them", type, true);
    }

    static boolean usedThisTurn(CombatSession session, Combatant attacker) {
        return turnKey(session).equals(usedOn.get(attacker.getId()));
    }

    /**
     * Someone on the attacker's side within 5 ft of the target who can act. "Side" is players vs
     * creatures until combat has factions (#155). It's what the DM is told, not a ruling.
     */
    static Combatant allyNextTo(CombatSession session, Combatant attacker, Combatant target) {
        for (Combatant c : session.getCombatants()) {
            if (c == attacker || c == target || c.isDead() || c.cannotAct()) continue;
            if (c.isPlayer() != attacker.isPlayer()) continue;
            double feet = Reach.feet(c.getLocation(), target.getLocation());
            if (feet >= 0 && feet <= 5 + Reach.SLACK_FEET) return c;
        }
        return null;
    }

    // ==================== ON A HIT ====================

    /** An automatic one (advantage) is in this hit's damage: say so, and remember it until the damage lands. */
    static void includedInHit(CombatSession session, Combatant attacker, Use use) {
        if (attacker.getTurnState() != null) attacker.getTurnState().setPendingSneak(use);
        session.broadcast(Component.text("🗡 " + use.source() + ": +" + use.dice() + " in that damage (" + use.reason() + ").",
                NamedTextColor.DARK_PURPLE));
    }

    /** A hit that could have it, if the DM agrees: offer the player [Ask the DM] (a DM gets [Add it]). */
    static void offer(CombatSession session, Combatant attacker, Combatant target, Use use, Player player) {
        if (player == null) return;
        boolean dm = DMManager.isDM(player);
        Component button = dm
                ? Component.text("[Add it]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("You're the DM: it goes in")))
                        .clickEvent(ClickEvent.callback(a -> allow(session, attacker, target, use, player), ONCE))
                : Component.text("[Ask the DM]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("Tell the DM why you should get it; they allow it or not")))
                        .clickEvent(ClickEvent.callback(a -> ask(session, attacker, target, use, player), ONCE));
        player.sendMessage(Component.text("🗡 " + use.source() + " (+" + use.dice() + ")? No advantage, so it's the DM's call: "
                + use.reason() + ". ", NamedTextColor.DARK_PURPLE).append(button));
    }

    private static void ask(CombatSession session, Combatant attacker, Combatant target, Use use, Player player) {
        if (!DmRequests.anyDmOnline(player)) return;
        UUID id = player.getUniqueId();
        Component toDms = Component.text("🗡 " + attacker.getDisplayName() + " asks for " + use.source() + " (+" + use.dice()
                        + ") on " + target.getDisplayName() + ": " + use.reason() + ". ", NamedTextColor.DARK_PURPLE)
                .append(DmRequests.button(id, "[Allow]", NamedTextColor.GREEN, "It goes into the damage (doubled on a crit)",
                        dm -> allow(session, attacker, target, use, dm)))
                .append(Component.text(" "))
                .append(DmRequests.button(id, "[Deny]", NamedTextColor.RED, "No Sneak Attack this time",
                        dm -> player.sendMessage(Component.text("The DM says no " + use.source() + " this time.", NamedTextColor.YELLOW))));
        DmRequests.send(player, use.source() + " on " + target.getDisplayName(), "Asked the DM for " + use.source() + ".", toDms);
    }

    /**
     * The DM allowed it. Still waiting on the damage roll: the dice join it and the prompt is sent
     * again. The damage already applied: the Sneak Attack dice are rolled and dealt on their own.
     */
    static void allow(CombatSession session, Combatant attacker, Combatant target, Use use, Player dm) {
        if (!session.isActive()) { dm.sendMessage(Component.text("The fight is over.", NamedTextColor.GRAY)); return; }
        if (usedThisTurn(session, attacker)) {
            dm.sendMessage(Component.text(attacker.getDisplayName() + " has already used " + use.source() + " this turn.", NamedTextColor.GRAY));
            return;
        }
        TurnState ts = attacker.getTurnState();
        boolean pending = ts != null && ts.isDamagePending() && target.getId().equals(ts.getPendingDamageTargetId())
                && ts.getPendingSneak() == null;
        if (pending) {
            String dice = ts.isPendingDamageCrit() ? AttackHandler.doubleDice(use.dice()) : use.dice();
            String combined = ts.getPendingDamageDice().isBlank() ? dice : ts.getPendingDamageDice() + "+" + dice;
            ts.setPendingDamageDice(combined);
            ts.setPendingSneak(use);
            session.broadcast(Component.text("🗡 The DM allows " + use.source() + ": +" + dice + " in that damage.", NamedTextColor.DARK_PURPLE));
            int bonus = ts.getPendingDamageBonus();
            String damageStr = combined + (bonus > 0 ? "+" + bonus : bonus < 0 ? String.valueOf(bonus) : "");
            AttackHandler.sendDamagePrompt(session, attacker, target, damageStr, ts.getPendingDamageType(),
                    ts.isPendingDamageCrit(), ts.getPendingDamageLabel());
            return;
        }
        // Too late to join the hit (it was applied, or the turn moved on): its own roll.
        DiceRoller.Rolled r = DiceRoller.rollOrFlat(use.dice());
        if (r == null || target.isDead()) return;
        markUsed(session, attacker);
        session.broadcast(Component.text("🗡 " + use.source() + " (allowed by the DM after the hit): " + r.display()
                + (use.damageType() != null ? " " + use.damageType() : ""), NamedTextColor.DARK_PURPLE));
        DamageHandler.applyDamage(session, target, r.total(), use.damageType(), false);
        session.refreshHpDisplays(target);
    }

    /** The damage with it in has landed: that's this turn's. */
    public static void markUsed(CombatSession session, Combatant attacker) {
        usedOn.put(attacker.getId(), turnKey(session));
    }

    /** "1d8+3" + "1d6" → "1d8+1d6+3": the flat part stays last, where the damage parsing expects it. */
    public static String addDice(String damage, String dice) {
        if (damage == null || damage.isBlank()) return dice;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?d\\d+)([+-]\\d+)?$").matcher(damage.replace(" ", ""));
        if (m.matches()) return m.group(1) + "+" + dice + (m.group(2) != null ? m.group(2) : "");
        return dice + "+" + damage; // a flat amount (no weapon dice): the dice first, then the flat
    }

    private static String turnKey(CombatSession session) {
        Combatant current = session.getCurrentCombatant();
        return session.getRoundNumber() + ":" + (current != null ? current.getId() : "");
    }
}
