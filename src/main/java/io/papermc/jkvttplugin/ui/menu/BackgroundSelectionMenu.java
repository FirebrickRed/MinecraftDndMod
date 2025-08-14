package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class BackgroundSelectionMenu {

    private BackgroundSelectionMenu() {}

    public static void open(Player player, Collection<DndBackground> backgrounds, UUID sessionId) {
        player.openInventory(build(backgrounds, sessionId));
    }

    public static Inventory build(Collection<DndBackground> backgrounds, UUID sessionId) {
        int inventorySize = Util.getInventorySize(backgrounds.size());

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.BACKGROUND_SELECTION, sessionId),
                inventorySize,
                Component.text("Choose your Background")
        );

        int slot = 0;
        for (DndBackground background : backgrounds) {
            ItemStack backgroundItem = new ItemStack(Material.PAPER);
            ItemMeta meta = backgroundItem.getItemMeta();

            meta.displayName(Component.text(background.getName()));

            List<Component> lore = new ArrayList<>();

            meta.lore(lore);
            backgroundItem.setItemMeta(meta);

            String backgroundId = background.getName().toLowerCase().replace(' ', '_');
            backgroundItem = ItemUtil.tagAction(backgroundItem, MenuAction.CHOOSE_BACKGROUND, backgroundId);

            inventory.setItem(slot++, backgroundItem);
        }

        return inventory;
    }
}
