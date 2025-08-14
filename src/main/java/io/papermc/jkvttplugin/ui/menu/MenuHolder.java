package io.papermc.jkvttplugin.ui.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class MenuHolder implements InventoryHolder {
    private final MenuType menuType;
    private final UUID sessionId;

    public MenuHolder(MenuType menuType, UUID sessionId) {
        this.menuType = menuType;
        this.sessionId = sessionId;
    }

    public MenuType getType() {
        return menuType;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    public Inventory getInventory() {
        // This method is required by the InventoryHolder interface, but we don't need to implement it here.
        // The inventory will be created and managed by the menu system.
        return null;
    }
}
