package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sneak Attack (PHB p.96, #229). Once per turn, a hit with a finesse or ranged weapon deals extra
 * damage if you have advantage, or if an ally of yours is within 5 feet of the target (and isn't
 * incapacitated) and you don't have disadvantage.
 *
 * <p>The dice join the hit's own damage ("1d8+1d6+3"), so a crit doubles them as the rules say, and
 * the table is told why it applied. "Once per turn" is any turn, so an opportunity attack on someone
 * else's turn can use it too.
 */
public final class SneakAttack {

    private SneakAttack() {}

    /** It applies: these dice, by the feature's name, and why ("advantage" / "Borin is next to them"). */
    public record Use(String dice, String source, String reason) {}

    /** Rogue id → the turn they last used it on ("round:whose turn"). */
    private static final Map<UUID, String> usedOn = new HashMap<>();

    /** Whether this attack gets Sneak Attack, or null. Nothing is spent until it hits. */
    public static Use check(CombatSession session, Combatant attacker, Combatant target, CharacterSheet sheet, DndWeapon weapon) {
        if (session == null || sheet == null) return null;
        Map.Entry<String, String> sa = sheet.sneakAttack();
        if (sa == null) return null;
        if (weapon == null || !(weapon.isFinesse() || weapon.isRanged())) return null;
        if (turnKey(session).equals(usedOn.get(attacker.getId()))) return null;

        Advantage adv = attacker.attackAdvantageAgainst(target);
        if (adv.isDisadvantage()) return null;
        if (adv.isAdvantage()) return new Use(sa.getValue(), sa.getKey(), "advantage");
        Combatant ally = allyNextTo(session, attacker, target);
        return ally != null ? new Use(sa.getValue(), sa.getKey(), ally.getDisplayName() + " is next to them") : null;
    }

    /**
     * Someone on the attacker's side within 5 ft of the target who can act. "Side" is players vs
     * creatures until combat has factions (#155), so a charmed or allied NPC doesn't count yet.
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

    /** The hit landed: spend it for this turn and tell the table. */
    public static void spend(CombatSession session, Combatant attacker, Combatant target, Use use) {
        usedOn.put(attacker.getId(), turnKey(session));
        session.broadcast(Component.text("🗡 " + use.source() + "! +" + use.dice() + " in that damage (" + use.reason() + ").",
                NamedTextColor.DARK_PURPLE));
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
