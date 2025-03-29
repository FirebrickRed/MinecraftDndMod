package io.papermc.jkvttplugin.player.Background;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class Noble extends DndBackground {
    public Noble() {
        super("Noble");
    }

    @Override
    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getBackgroundName()), null, "noble_icon", 0);
    }

    @Override
    public List<ItemStack> getStartingEquipment() {
        return Arrays.asList(
                Util.createItem(Component.text("Fine Clothes"), null, "golden_chestplate", 0),
                Util.createItem(Component.text("Signet Ring"), null, "diamond", 0),
                Util.createItem(Component.text("Scroll of Lineage"), null, "paper", 0),
                Util.createItem(Component.text("Purse (25gp)"), null, "gold_ingot", 25)
        );
    }
}
