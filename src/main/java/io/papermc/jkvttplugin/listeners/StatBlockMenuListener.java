package io.papermc.jkvttplugin.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

/**
 * Handles clicks in entity stat block inventories.
 * Prevents item theft and handles close button.
 */
public class StatBlockMenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String title = view.title().toString();

        // Check if this is a stat block menu (title contains "Stat Block")
        if (!title.contains("Stat Block")) {
            return;
        }

        // Cancel ALL clicks in stat block menus to prevent item theft
        event.setCancelled(true);

        // Handle close button (slot 49, Barrier item)
        if (event.getSlot() == 49 && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.BARRIER) {
            event.getWhoClicked().closeInventory();
        }
    }
}