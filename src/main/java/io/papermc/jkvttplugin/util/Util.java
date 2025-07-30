package io.papermc.jkvttplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class Util {
    public static ItemStack createItem(Component displayName, List<Component> lore, String itemModelName, int quantity) {
        ItemStack item = quantity <= 0 ? new ItemStack(Material.PAPER) : new ItemStack(Material.PAPER, quantity);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        meta.setItemModel(new NamespacedKey("jkvttresourcepack", itemModelName));

        item.setItemMeta(meta);
        return item;
    }

    public static int getInventorySize(int itemCount) {
        return ((itemCount - 1) / 9 + 1) * 9;
    }

    public static boolean hasDisplayName(ItemStack item, String name) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String display = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        return display.equalsIgnoreCase(name);
    }

    public static String normalize(String name) {
        return name.trim().toLowerCase().replace(" ", "_");
    }
}
