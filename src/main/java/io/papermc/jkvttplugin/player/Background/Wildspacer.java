package io.papermc.jkvttplugin.player.Background;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class Wildspacer extends DndBackground {
    public Wildspacer() {
        super("Wildspacer");
    }

    @Override
    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getBackgroundName()), null, "wildspacer_icon", 0);
    }

    @Override
    public List<ItemStack> getStartingEquipment() {
        return Arrays.asList(
                Util.createItem(Component.text("Grappling Hook"), null, "fishing_rod", 0),
                DndWeapon.getWeapon("dagger").toItemStack(),
                Util.createItem(Component.text("Tattered Star Chart"), null, "map", 0),
                Util.createItem(Component.text("Lost Expedition Trinket"), null, "player_head", 0),
                Util.createItem(Component.text("Gold Pouch (10gp)"), null, "gold_ingot", 0)
        );
    }
}
