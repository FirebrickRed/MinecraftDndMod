package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Keeps a {@link CharacterSheet}'s equipped armor and shield in step with what the player is
 * actually wearing, so AC follows the gear (#31).
 *
 * <p>Body armor lives in the chestplate slot, a shield in the off-hand — both are real vanilla
 * items ({@code LEATHER_CHESTPLATE}/{@code CHAINMAIL_CHESTPLATE}/{@code IRON_CHESTPLATE} by armor
 * category, and {@code SHIELD}), so players equip them the way they'd expect to.
 *
 * <p>Minecraft offers a lot of ways to move an item into those slots — click, shift-click, drag,
 * the off-hand swap key, right-click-to-equip, death and respawn. Rather than special-case each
 * one, every relevant event just schedules a full re-read of both slots a tick later, once the
 * move has actually applied.
 */
public class ArmorEquipListener implements Listener {
    private final Plugin plugin;

    public ArmorEquipListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) resyncSoon(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) resyncSoon(player);
    }

    /** The off-hand swap key (F by default). */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        resyncSoon(event.getPlayer());
    }

    /** Right-click-to-equip puts armor straight on without ever touching an inventory screen. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isRightClick()) resyncSoon(event.getPlayer());
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        resyncSoon(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        resyncSoon(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        resyncSoon(event.getPlayer());
    }

    /** Re-read a tick later, so we see the slots as they end up rather than as they were. */
    private void resyncSoon(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> resync(player));
    }

    /**
     * Re-read the chestplate and off-hand slots, updating the sheet only where they've changed
     * (each equip/unequip recalculates AC and writes the sheet to disk).
     */
    private void resync(Player player) {
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) return;

        // Body armor: the chestplate slot, ignoring a shield that somehow landed there.
        DndArmor body = armorFor(player.getInventory().getChestplate());
        if (body != null && body.isShield()) body = null;
        if (!sameArmor(body, sheet.getEquippedArmor())) {
            if (body == null) sheet.unequipArmor(); else sheet.equipArmor(body);
        }

        // Shield: the off-hand, and only if it really is a shield.
        DndArmor shield = armorFor(player.getInventory().getItemInOffHand());
        if (shield != null && !shield.isShield()) shield = null;
        if (!sameArmor(shield, sheet.getEquippedShield())) {
            if (shield == null) sheet.unequipShield(); else sheet.equipShield(shield);
        }
    }

    /** The D&D armor a stack stands for, or null if it isn't one of ours. */
    private DndArmor armorFor(ItemStack item) {
        String id = ItemUtil.getItemId(item);
        return id != null ? ArmorLoader.getArmor(id) : null;
    }

    private boolean sameArmor(DndArmor a, DndArmor b) {
        return Objects.equals(a == null ? null : a.getId(), b == null ? null : b.getId());
    }
}
