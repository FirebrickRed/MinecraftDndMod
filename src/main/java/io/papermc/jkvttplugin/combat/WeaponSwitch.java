package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Attacking with a different weapon than the one you started your turn holding (#190 playtest).
 *
 * <p>Scrolling the hotbar costs nothing: players scroll past weapons all the time, and charging them
 * for it (or filling chat about it) was wrong. The cost is settled when the weapon is <b>used</b>,
 * before anything is rolled:
 * <ul>
 *   <li>Drawing or stowing a weapon is your one free object interaction per turn (PHB p.190), so the
 *       first switch asks <b>[Yes, I'm switching]</b> (it uses that) or <b>[Ask the DM]</b>.</li>
 *   <li>Once it's used, another switch takes your Action, which leaves nothing to attack with, so the
 *       only way through is [Ask the DM].</li>
 *   <li>Empty hands at the start of the turn: drawing a weapon is the free interaction, settled
 *       without asking, because there's nothing to have switched by accident.</li>
 * </ul>
 * Once settled, the same weapon is fine for the rest of the turn.
 */
public final class WeaponSwitch {

    private WeaponSwitch() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(5)).build();

    /** What attacking with a weapon needs first. */
    public enum Need {
        /** It was in hand at the start of the turn (or the switch is already settled). */
        NONE,
        /** Hands were empty: drawing it is the free interaction, no question. */
        DRAW,
        /** A switch the free object interaction covers: confirm it. */
        FREE,
        /** The free interaction is gone: a switch would take the Action. */
        ACTION
    }

    public static Need need(TurnState s, String weaponId) {
        if (s == null || weaponId == null || weaponId.equalsIgnoreCase("unarmed")) return Need.NONE;
        if (weaponId.equalsIgnoreCase(s.getTurnStartWeaponId()) || weaponId.equalsIgnoreCase(s.getTurnStartOffhandWeaponId())
                || weaponId.equalsIgnoreCase(s.getSwitchedToWeaponId())) return Need.NONE;
        if (s.isObjectInteractionUsed()) return Need.ACTION;
        boolean emptyHanded = s.getTurnStartWeaponId() == null && s.getTurnStartOffhandWeaponId() == null
                && s.getSwitchedToWeaponId() == null;
        return emptyHanded ? Need.DRAW : Need.FREE;
    }

    /** The switch is settled: it used the free interaction (unless the DM waved it), and this weapon is now fine. */
    static void settle(TurnState s, String weaponId, boolean usesInteraction) {
        if (usesInteraction) s.markObjectInteractionUsed();
        s.setSwitchedToWeaponId(weaponId);
    }

    /**
     * True when the attack may go ahead now. Otherwise the player has been asked, and the answer hands
     * the attack back to them as roll buttons on {@code base} (the command up to the roll words).
     */
    public static boolean allow(Player player, Combatant attacker, String weaponId, String base, String dice, String bonus) {
        TurnState s = attacker.getTurnState();
        Need need = need(s, weaponId);
        if (need == Need.NONE) return true;
        if (need == Need.DRAW) { settle(s, weaponId, true); return true; }

        String now = name(weaponId);
        String before = name(s.getSwitchedToWeaponId() != null ? s.getSwitchedToWeaponId()
                : s.getTurnStartWeaponId() != null ? s.getTurnStartWeaponId() : s.getTurnStartOffhandWeaponId());
        Runnable again = () -> player.sendMessage(RollPrompt.line("⚔ Attack with " + now + ":", NamedTextColor.GOLD, base, dice, bonus));

        Component ask = button("[Ask the DM]", NamedTextColor.AQUA, "The DM decides whether the switch is free this time", a -> {
            if (attacker.getTurnState() != s) { player.sendMessage(Component.text("That turn is over.", NamedTextColor.GRAY)); return; }
            if (!DmRequests.anyDmOnline(player)) return;
            Component toDms = Component.text("⚔ " + attacker.getDisplayName() + " wants to switch from " + before + " to " + now
                            + (need == Need.ACTION ? " (their free object interaction is used, so RAW it takes their Action) " : " "), NamedTextColor.AQUA)
                    .append(DmRequests.button(player.getUniqueId(), "[Allow]", NamedTextColor.GREEN, "The switch costs them nothing; they attack", dm -> {
                        if (attacker.getTurnState() != s) { dm.sendMessage(Component.text("Their turn is already over.", NamedTextColor.GRAY)); return; }
                        settle(s, weaponId, false);
                        dm.sendMessage(Component.text("Allowed.", NamedTextColor.GREEN));
                        player.sendMessage(Component.text("The DM lets you switch to " + now + ".", NamedTextColor.GREEN));
                        again.run();
                    }))
                    .append(Component.text(" "))
                    .append(DmRequests.button(player.getUniqueId(), "[Deny]", NamedTextColor.RED, "They keep what they had", dm -> {
                        dm.sendMessage(Component.text("Denied.", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("The DM says no: attack with " + before + " this turn.", NamedTextColor.YELLOW));
                    }));
            DmRequests.send(player, "switching to " + now, "Asked the DM about switching to " + now + ".", toDms);
        });

        if (need == Need.FREE) {
            Component yes = button("[Yes, I'm switching]", NamedTextColor.GREEN, "Uses your free object interaction this turn", a -> {
                if (attacker.getTurnState() != s) { player.sendMessage(Component.text("That turn is over.", NamedTextColor.GRAY)); return; }
                settle(s, weaponId, true);
                player.sendMessage(Component.text("You stow your " + before + " and draw your " + now + ".", NamedTextColor.GRAY));
                again.run();
            });
            player.sendMessage(Component.text("⚠ You started your turn holding " + before + ". Switching to " + now
                    + " uses your free object interaction. ", NamedTextColor.YELLOW).append(yes).append(Component.text(" ")).append(ask));
        } else {
            player.sendMessage(Component.text("⚠ You've already switched weapons this turn, and another switch takes your Action, "
                    + "which leaves nothing to attack with. Attack with " + before + ", or ", NamedTextColor.YELLOW).append(ask));
        }
        return false;
    }

    private static String name(String weaponId) {
        DndWeapon w = weaponId != null ? WeaponLoader.getWeapon(weaponId) : null;
        return w != null ? w.getName() : weaponId != null ? weaponId : "nothing";
    }

    private static Component button(String text, NamedTextColor color, String hover, ClickCallback<net.kyori.adventure.audience.Audience> action) {
        return Component.text(text, color, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.callback(action, ONCE));
    }
}
