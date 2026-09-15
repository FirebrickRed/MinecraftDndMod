package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Reminds a player what changing gear mid-turn costs in the action economy (#190).
 *
 * <p>Drawing or stowing a weapon is your one free object interaction per turn; strapping on a
 * shield costs a full Action. Both are enforced loosely at most real tables, so this <b>only ever
 * warns</b> — nothing is blocked and no action is auto-consumed. The DM decides; we inform.
 *
 * <p>The weapon reminder measures against what the player was holding when the turn <i>began</i>
 * (snapshotted in {@link Combatant#startNewTurn}) rather than against the previous swap, so the
 * message can name the weapon they actually put away.
 */
public class GearChangeNotifier implements Listener {

    /** The D&D weapon id a stack represents, or null if it isn't a weapon of ours. */
    public static String heldWeaponId(ItemStack item) {
        String id = ItemUtil.getItemId(item);
        return (id != null && WeaponLoader.getWeapon(id) != null) ? id : null;
    }

    private static String weaponName(String weaponId) {
        DndWeapon weapon = weaponId != null ? WeaponLoader.getWeapon(weaponId) : null;
        return weapon != null ? weapon.getName() : weaponId;
    }

    /** The player's live turn state, or null if it isn't their turn in an active combat. */
    private static TurnState activeTurnState(Player player) {
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return null;
        Combatant current = session.getCurrentCombatant();
        if (current == null || !current.isPlayer() || !current.getId().equals(player.getUniqueId())) {
            return null; // not their turn — swapping gear costs them nothing right now
        }
        return current.getTurnState();
    }

    // ==================== WEAPON SWAPS ====================

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack now = player.getInventory().getItem(event.getNewSlot());
        checkWeaponSwap(player, heldWeaponId(now));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        checkWeaponSwap(event.getPlayer(), heldWeaponId(event.getMainHandItem()));
    }

    /**
     * Warn if the player is now holding a different weapon than they started the turn with.
     *
     * <p>Only changes between actual D&D weapons count. Scrolling past a torch or an empty slot
     * is technically a stow in RAW, but warning on it would nag constantly for no tactical gain.
     */
    private void checkWeaponSwap(Player player, String nowWeaponId) {
        if (nowWeaponId == null) return;

        TurnState state = activeTurnState(player);
        if (state == null) return;

        String startedWith = state.getTurnStartWeaponId();
        if (startedWith == null || startedWith.equals(nowWeaponId)) return; // back to where we began

        if (!state.isObjectInteractionUsed()) {
            state.markObjectInteractionUsed();
            player.sendMessage(Component.text("⚠ You started your turn holding ", NamedTextColor.YELLOW)
                    .append(Component.text(weaponName(startedWith), NamedTextColor.WHITE))
                    .append(Component.text(". Stowing it to draw ", NamedTextColor.YELLOW))
                    .append(Component.text(weaponName(nowWeaponId), NamedTextColor.WHITE))
                    .append(Component.text(" uses your free object interaction this turn.", NamedTextColor.YELLOW)));
            return;
        }

        if (!state.isSecondSwapWarned()) {
            state.markSecondSwapWarned();
            player.sendMessage(Component.text("⚠ You've already used your object interaction this turn — "
                    + "drawing another weapon costs an Action.", NamedTextColor.RED));
        }
    }

    // ==================== SHIELDS ====================

    /**
     * Called by the equip listener when a shield goes on or comes off, so the player hears the
     * Action cost. AC has already updated by this point — this is purely the reminder.
     */
    public static void notifyShieldChange(Player player, boolean donned) {
        TurnState state = activeTurnState(player);
        if (state == null || state.isShieldChangeWarned()) return;

        state.markShieldChangeWarned();
        player.sendMessage(Component.text("⚠ " + (donned ? "Strapping on" : "Unstrapping")
                + " a shield uses your Action for this turn.", NamedTextColor.RED));
    }
}
