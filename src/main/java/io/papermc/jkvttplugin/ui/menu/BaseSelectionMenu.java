package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class BaseSelectionMenu {
    private BaseSelectionMenu() {}

    public static <T>Inventory build(Collection<T> items, UUID sessionId, String menuTitle, MenuType menuType, MenuAction action, Function<T, String> idExtractor, Function<T, String> nameExtractor, Function<T, Material> iconExtractor, Function<T, List<Component>> loreExtractor) {
        int inventorySize = Util.getInventorySize(items.size());

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(menuType, sessionId),
                inventorySize,
                Component.text(menuTitle)
        );

        int slot = 0;
        for (T item : items) {
            Material iconMaterial = iconExtractor.apply(item);
            ItemStack itemStack = new ItemStack(iconMaterial);
            ItemMeta meta = itemStack.getItemMeta();

            String displayName = nameExtractor.apply(item);
            meta.displayName(Component.text(displayName));

            if (loreExtractor != null) {
                List<Component> lore = loreExtractor.apply(item);
                if (lore != null) {
                    meta.lore(lore);
                }
            }

            itemStack.setItemMeta(meta);

            String id = idExtractor.apply(item);
            itemStack = ItemUtil.tagAction(itemStack, action, id);

            inventory.setItem(slot++, itemStack);
        }

        return inventory;
    }
}
