package io.papermc.jkvttplugin.loot;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Holder for a loot chest (Issue #136). Deliberately NOT a MenuHolder, so MenuClickListener leaves
 * its clicks alone and the player can freely take items. On close, whatever's left drops at
 * {@link #getDropLocation()}.
 */
public class LootInventoryHolder implements InventoryHolder {
    private final Location dropLocation;
    private Inventory inventory;

    public LootInventoryHolder(Location dropLocation) {
        this.dropLocation = dropLocation;
    }

    public Location getDropLocation() { return dropLocation; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
