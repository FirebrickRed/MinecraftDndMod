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
 * Gear changes mid-turn (#190).
 *
 * <p>Weapons: scrolling the hotbar costs nothing and posts nothing to chat, only a quiet action-bar
 * line. What a switch costs is settled when the weapon is actually used, by {@link WeaponSwitch}
 * (a playtest: scroll-wheel players were warned, and charged their free interaction, for scrolling
 * past a weapon they never meant to use).
 *
 * <p>Shields: strapping one on costs a full Action, and that stays a reminder only; nothing is
 * blocked or auto-consumed.
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
     * A quiet hint when the hand now holds a weapon they'd have to switch to. Nothing is charged here.
     * Only D&D weapons count: scrolling past a torch or an empty slot says nothing.
     */
    private void checkWeaponSwap(Player player, String nowWeaponId) {
        if (nowWeaponId == null) return;
        TurnState state = activeTurnState(player);
        if (state == null) return;
        WeaponSwitch.Need need = WeaponSwitch.need(state, nowWeaponId);
        if (need != WeaponSwitch.Need.FREE && need != WeaponSwitch.Need.ACTION) return;
        player.sendActionBar(Component.text("Holding " + weaponName(nowWeaponId) + ": attacking with it is a weapon switch"
                + (need == WeaponSwitch.Need.ACTION ? " (and your free one is used)" : ""), NamedTextColor.GRAY));
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
