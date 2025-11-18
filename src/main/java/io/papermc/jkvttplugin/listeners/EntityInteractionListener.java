package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.commands.DmEntityCommand;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.ui.menu.EntityStatBlockMenu;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Handles player interactions with spawned entities.
 * Right-click with empty hand → open stat block.
 */
public class EntityInteractionListener implements Listener {

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only handle main hand interactions to avoid double-triggering
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        // Only handle right-clicks on armor stands (our entities)
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }

        // Find the entity instance for this armor stand
        DndEntityInstance instance = findEntityInstance(armorStand);
        if (instance == null) {
            return; // Not one of our spawned entities
        }

        // Only open stat block if player has empty hand
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            event.setCancelled(true); // Prevent default armor stand interaction
            EntityStatBlockMenu.open(player, instance);
        }
    }

    /**
     * Finds the DndEntityInstance for a given armor stand.
     * Searches through all spawned entities to match the armor stand.
     */
    private DndEntityInstance findEntityInstance(ArmorStand armorStand) {
        for (DndEntityInstance instance : DmEntityCommand.getAllSpawnedEntities()) {
            if (instance.getArmorStand().equals(armorStand)) {
                return instance;
            }
        }
        return null;
    }
}