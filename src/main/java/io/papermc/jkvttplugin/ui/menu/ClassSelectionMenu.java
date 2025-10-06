package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ClassSelectionMenu {

    private ClassSelectionMenu() {}

    public static void open(Player player, Collection<DndClass> classes, UUID sessionId) {
        player.openInventory(build(classes, sessionId));
    }

    public static Inventory build(Collection<DndClass> classes, UUID sessionId) {
        int inventorySize = Util.getInventorySize(classes.size());

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.CLASS_SELECTION, sessionId),
                inventorySize,
                Component.text("Choose your Class")
        );

        int slot = 0;
        for (DndClass dndClass : classes) {
//            ItemStack classItem = new ItemStack(dndClass.getIconMaterial());
            ItemStack classItem = new ItemStack(Material.PAPER);
            ItemMeta meta = classItem.getItemMeta();

            meta.displayName(Component.text(dndClass.getName()));

            List<Component> lore = new ArrayList<>();

            meta.lore(lore);
            classItem.setItemMeta(meta);

            String classId = Util.normalize(dndClass.getName());
            classItem = ItemUtil.tagAction(classItem, MenuAction.CHOOSE_CLASS, classId);

            inventory.setItem(slot++, classItem);
        }

        return inventory;
    }
}
