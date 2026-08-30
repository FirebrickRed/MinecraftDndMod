package io.papermc.jkvttplugin.loot;

import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.DmModeManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Loot interactions (Issue #136): right-click a dead entity to search it; whatever's left in the
 * loot chest when it closes drops to the ground (never lost).
 */
public class LootListener implements Listener {

    @EventHandler
    public void onRightClickCorpse(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        // DMs working their toolbar (view/possess/move) shouldn't trigger looting.
        if (DmModeManager.isInDmMode(event.getPlayer())) return;

        DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
        if (inst == null || !inst.isDead()) return; // only dead D&D entities are lootable

        event.setCancelled(true);
        LootManager.requestLoot(event.getPlayer(), inst);
    }

    @EventHandler
    public void onCloseLoot(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootInventoryHolder holder)) return;
        if (holder.getDropLocation() == null || holder.getDropLocation().getWorld() == null) return;
        // Anything the player left behind drops at the body, so it's never lost.
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType().isItem()) {
                holder.getDropLocation().getWorld().dropItemNaturally(holder.getDropLocation(), item);
            }
        }
        event.getInventory().clear();
    }
}
