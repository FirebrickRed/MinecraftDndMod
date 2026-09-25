package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>The</b> reach rule, for spells and weapons, in a fight and out of one. It used to be three
 * copies that disagreed: 2.5 ft of slack in a fight and 5 ft out of one, Self checked only in a
 * fight, and a DM override only out of one.
 *
 * <ul>
 *   <li>1 block = 5 ft, and {@link #SLACK_FEET} of forgiveness: positions in Minecraft are rougher
 *       than squares on a grid.</li>
 *   <li>A spell's range is {@link DndSpell#rangeFeet}. A targeted Self spell reaches only the
 *       caster; an area that starts from you (Burning Hands) aims its own area and isn't checked.</li>
 *   <li>A weapon reaches its reach in melee and its long range when shot or thrown; a creature's
 *       attack reads its {@code reach:} ("5 ft", "80/320 ft").</li>
 *   <li>Out of reach is never final: the refusal offers <b>[Ask the DM]</b>, or <b>[Do it anyway]</b>
 *       when the one acting is a DM. An allowed action goes through once ({@link #spend}).</li>
 * </ul>
 */
public final class Reach {

    private Reach() {}

    /** One block of forgiveness past any range. */
    public static final double SLACK_FEET = 5.0;

    /** Feet between two places (1 block = 5 ft), or -1 when it can't be measured (missing, other world). */
    public static double feet(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return -1;
        return a.distance(b) * 5.0;
    }

    private static boolean within(double feet, double reach) {
        return feet < 0 || feet <= reach + SLACK_FEET;
    }

    private static String ft(double feet) { return Math.round(feet) + " ft"; }

    // ==================== THE CHECKS (null = in reach) ====================

    /**
     * A spell at one target. Null when it reaches; otherwise why not.
     * @param targetIsCaster the caster is targeting themself
     */
    public static String spell(Location from, Location to, String targetName, boolean targetIsCaster, DndSpell spell) {
        return spell(feet(from, to), targetName, targetIsCaster, spell);
    }

    /** {@link #spell(Location, Location, String, boolean, DndSpell)} at a known distance in feet (-1 = unknown). */
    public static String spell(double feet, String targetName, boolean targetIsCaster, DndSpell spell) {
        int range = spell.getRangeFeet();
        if (range < 0) return null; // Sight, Unlimited, Special
        if (range == 0) { // Self
            if (spell.isAoe() || targetIsCaster) return null;
            return spell.getName() + " only targets you (range: Self).";
        }
        if (within(feet, range)) return null;
        return targetName + " is about " + ft(feet) + " away. " + spell.getName() + " reaches " + spell.getRange() + ".";
    }

    /** A character's weapon (null = unarmed, 5 ft). Null when it reaches. */
    public static String weapon(Location from, Location to, String targetName, DndWeapon weapon) {
        return weapon(feet(from, to), targetName, weapon);
    }

    /** At a known distance in feet (-1 = unknown). */
    public static String weapon(double feet, String targetName, DndWeapon weapon) {
        double reach = weapon != null ? weapon.getReachFeet() : 5.0;
        if (weapon != null && (weapon.isRanged() || weapon.hasProperty("thrown"))) {
            if (weapon.isMelee() && within(feet, reach)) return null; // used in melee
            int max = weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange();
            if (max <= 0 || within(feet, max)) return null;
            return targetName + " is about " + ft(feet) + " away. " + weapon.getName() + " reaches " + max + " ft.";
        }
        if (within(feet, reach)) return null;
        return targetName + " is about " + ft(feet) + " away, but your reach is " + (int) reach + " ft. Move closer or use a ranged attack.";
    }

    /** A creature's attack, from its {@code reach:} ("5 ft", "10 ft.", "80/320 ft"; none = 5 ft). Null when it reaches. */
    public static String creatureAttack(Location from, Location to, String targetName, DndAttack attack) {
        return creatureAttack(feet(from, to), targetName, attack);
    }

    /** At a known distance in feet (-1 = unknown). */
    public static String creatureAttack(double feet, String targetName, DndAttack attack) {
        List<Integer> nums = numbers(attack != null ? attack.getReach() : null);
        int max = nums.isEmpty() ? 5 : nums.get(nums.size() - 1);
        if (within(feet, max)) return null;
        String name = attack != null ? attack.getName() : "Its attack";
        return targetName + " is about " + ft(feet) + " away. " + name + " reaches " + max + " ft.";
    }

    /**
     * A ranged attack past its normal range but within long range (PHB p.195): it still happens, at
     * disadvantage. The notice, or null. {@code normal}/{@code longRange} in feet.
     */
    public static String longRange(Location from, Location to, int normal, int longRange) {
        double feet = feet(from, to);
        if (feet < 0 || normal <= 0 || within(feet, normal)) return null;
        if (longRange > 0 && !within(feet, longRange)) return null; // beyond long range: that's the reach check's job
        return "⚠ Long range (" + ft(feet) + ", past " + normal + " ft): the attack is at DISADVANTAGE.";
    }

    /** Every number in a reach string: "80/320 ft" → [80, 320]. */
    public static List<Integer> numbers(String reach) {
        List<Integer> out = new ArrayList<>();
        if (reach == null) return out;
        Matcher m = Pattern.compile("(\\d+)").matcher(reach);
        while (m.find()) out.add(Integer.parseInt(m.group(1)));
        return out;
    }

    // ==================== THE OVERRIDE ====================

    /** "Close enough": this actor may do {@code what} to {@code target}, once, for a few minutes. */
    private record Allowed(String what, UUID target, long at) {}

    private static final Map<UUID, Allowed> allowed = new HashMap<>();
    private static final long GOOD_FOR_MS = Duration.ofMinutes(10).toMillis();
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    /**
     * True when the DM said this reaches. Not used up here: a roll prompt re-runs the command, so it's
     * spent only when the action goes through ({@link #spend}).
     */
    public static boolean isAllowed(UUID actor, String what, UUID target) {
        Allowed a = allowed.get(actor);
        return a != null && a.what().equalsIgnoreCase(what) && a.target().equals(target)
                && System.currentTimeMillis() - a.at() < GOOD_FOR_MS;
    }

    /** The action went through: an override covers one action, the next asks again. */
    public static void spend(UUID actor) {
        allowed.remove(actor);
    }

    private static void allow(UUID actor, String what, UUID target) {
        allowed.put(actor, new Allowed(what, target, System.currentTimeMillis()));
    }

    /**
     * Tell the actor it doesn't reach, with the way through: a player gets [Ask the DM], a DM acting
     * (their own character, or a creature they run) gets [Do it anyway]. Either way the answer hands
     * back {@code retry} to fill in chat (buttons fill, they never run).
     *
     * @param what   what is being done ("spell:cure_wounds", "weapon:longsword"), so an override
     *               covers exactly that
     * @param label  for the DM, short: "Cure Wounds on The Kindler"
     * @param retry  the command to go again with, filled into chat
     */
    public static void refuse(Player actor, String reason, String what, UUID target, String label, String retry) {
        Component msg = Component.text(reason + " ", NamedTextColor.RED);
        UUID id = actor.getUniqueId();
        if (DMManager.isDM(actor)) {
            msg = msg.append(Component.text("[Do it anyway]", NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("DM: close enough, this once")))
                    .clickEvent(ClickEvent.callback(a -> { allow(id, what, target); goAgain(id, retry); }, ONCE)));
        } else {
            msg = msg.append(Component.text("[Ask the DM]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("Ask the DM to let it reach anyway (they may be closer than the game thinks)")))
                    .clickEvent(ClickEvent.callback(a -> ask(actor, reason, what, target, label, retry), ONCE)));
        }
        actor.sendMessage(msg);
    }

    private static void ask(Player actor, String reason, String what, UUID target, String label, String retry) {
        if (!DmRequests.anyDmOnline(actor)) return;
        UUID id = actor.getUniqueId();
        Component toDm = Component.text("📏 " + actor.getName() + ": " + label + ", out of reach (" + reason + ") ", NamedTextColor.GOLD)
                .append(DmRequests.button(id, "[Allow]", NamedTextColor.GREEN, "Close enough: let it reach this once", dm -> {
                    allow(id, what, target);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendMessage(Component.text("The DM says it reaches. ", NamedTextColor.GREEN));
                    goAgain(id, retry);
                }))
                .append(Component.text(" "))
                .append(DmRequests.button(id, "[Deny]", NamedTextColor.GRAY, "It doesn't reach", dm -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendMessage(Component.text("The DM says it doesn't reach.", NamedTextColor.GRAY));
                }));
        DmRequests.send(actor, label + " out of reach", "Asked the DM whether it reaches.", toDm);
    }

    private static void goAgain(UUID actor, String retry) {
        Player p = Bukkit.getPlayer(actor);
        if (p == null) return;
        p.sendMessage(Component.text("   ", NamedTextColor.GRAY)
                .append(Component.text("[go again]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(retry))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + retry)))));
    }
}
