package io.papermc.jkvttplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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

//        meta.setItemModel(new NamespacedKey("jkvttresourcepack", itemModelName));

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createItem(Component displayName, List<Component> lore, String itemModelName, int quantity, Material material) {
        ItemStack item = quantity <= 0 ? new ItemStack(material) : new ItemStack(material, quantity);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

//        meta.setItemModel(new NamespacedKey("jkvttresourcepack", itemModelName));

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

    /**
     * Sorts a collection alphabetically by name using a provided name extractor function.
     * Returns a new sorted list without modifying the original collection.
     *
     * @param <T> the type of elements in the collection
     * @param items the collection to sort
     * @param nameExtractor function to extract the name string from each item
     * @return a new list containing all items sorted alphabetically by name
     */
    public static <T> List<T> sortByName(Collection<T> items, Function<T, String> nameExtractor) {
        return items.stream().sorted(Comparator.comparing(nameExtractor)).toList();
    }

    /**
     * Converts a number to its ordinal form (1st, 2nd, 3rd, etc.)
     *
     * @param num the number to convert
     * @return the ordinal string representation
     */
    public static String getOrdinal(int num) {
        if (num <= 0) return String.valueOf(num);

        int lastDigit = num % 10;
        int lastTwoDigits = num % 100;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
            return num + "th";
        }

        return switch (lastDigit) {
            case 1 -> num + "st";
            case 2 -> num + "nd";
            case 3 -> num + "rd";
            default -> num + "th";
        };
    }

    /**
     * Wraps text to fit within a specified line length, breaking on word boundaries.
     * Useful for wrapping long descriptions in item lore tooltips.
     *
     * @param text the text to wrap
     * @param maxLength maximum characters per line
     * @return list of wrapped lines
     */
    public static List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }

            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
