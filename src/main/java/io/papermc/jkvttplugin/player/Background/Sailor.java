package io.papermc.jkvttplugin.player.Background;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class Sailor extends DndBackground {
    public Sailor() {
        super("Sailor");
    }

    @Override
    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getBackgroundName()), null, "sailor_icon", 0);
    }

    @Override
    public List<ItemStack> getStartingEquipment() {
        return Arrays.asList(
                DndWeapon.getWeapon("belaying_pin").toItemStack(),
                Util.createItem(Component.text("50 ft Rope"), null, "lead", 0),
                Util.createItem(Component.text("Common Clothes"), null, "leather_chestplate", 0),
                Util.createItem(Component.text("Voyage Trinket"), null, "compass", 0),
                Util.createItem(Component.text("Gold Pouch (10gp)"), null, "gold_ingot", 10)
        );
    }
}
