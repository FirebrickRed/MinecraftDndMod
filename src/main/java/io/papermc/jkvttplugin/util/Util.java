package io.papermc.jkvttplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

// Claude TODO: CONFUSING CLASS NAMES - Util vs ItemUtil
// You have two "utility" classes with overlapping purposes:
// - Util: creates items, checks display names, prettifies/normalizes strings, calculates inventory size
// - ItemUtil: tags items with NBT data for menu actions
//
// These should be better organized:
// Option 1: Merge ItemUtil into Util since it's all item-related
// Option 2: Rename to be more specific: StringUtil, InventoryUtil, MenuItemUtil
// Option 3: Split Util into ItemFactory, StringNormalizer, and InventoryHelper
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

    // ToDo: see if there is a better way than this
    public static String prettify(String s) {
        if (s == null || s.isBlank()) return "";
        s = s.replace('_', ' ').replace('-', ' ').trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(cap ? Character.toTitleCase(c) : c);
            cap = (c == ' ');
        }
        return out.toString();
    }

    /**
     * Normalizes a display name to a consistent snake_case identifier.
     * Handles spaces, hyphens, and trims whitespace.
     * This is the SINGLE source of truth for name normalization in the plugin.
     *
     * Examples:
     * - "Half-Elf" -> "half_elf"
     * - "Mountain Dwarf" -> "mountain_dwarf"
     * - "  Fire Bolt  " -> "fire_bolt"
     *
     * @param name The display name to normalize
     * @return The normalized snake_case identifier
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim()
                .toLowerCase()
                .replace(' ', '_')
                .replace('-', '_');
    }
}
