package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.ui.action.MenuAction;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class ItemUtil {
    private static Plugin plugin;
    private static NamespacedKey ACTION_KEY;
    private static NamespacedKey PAYLOAD_KEY;

    private ItemUtil() {}

    public static void initialize(Plugin plugin) {
        ItemUtil.plugin = plugin;
        ACTION_KEY = new NamespacedKey(plugin, "menu_action");
        PAYLOAD_KEY = new NamespacedKey(plugin, "menu_payload");
    }

    /**
     * Creates a clickable menu item with an action and optional payload.
     * This is a convenience method that combines item creation, meta editing, and action tagging.
     *
     * @param material The material for the item
     * @param displayName The display name component
     * @param lore The lore (can be null)
     * @param action The menu action to trigger when clicked
     * @param payload Optional payload data (can be null)
     * @return The created and tagged ItemStack
     */
    public static ItemStack createActionItem(Material material, Component displayName, List<Component> lore, MenuAction action, String payload) {
        ItemStack item = new ItemStack(material);
        item.editMeta(m -> {
            m.displayName(displayName);
            if (lore != null) {
                m.lore(lore);
            }
        });
        return tagAction(item, action, payload);
    }

    /**
     * Tags an existing ItemStack with a menu action and optional payload.
     * This is an alias for tagAction() with a clearer name.
     *
     * @param item The item to tag
     * @param action The menu action
     * @param payload Optional payload data (can be null)
     * @return The tagged item (same instance, for chaining)
     */
    public static ItemStack setAction(ItemStack item, MenuAction action, String payload) {
        return tagAction(item, action, payload);
    }

    /**
     * Tags an existing ItemStack with a menu action and optional payload.
     * Modifies the item's persistent data container to store the action.
     *
     * @param item The item to tag
     * @param action The menu action
     * @param payload Optional payload data (can be null)
     * @return The tagged item (same instance, for chaining)
     */
    public static ItemStack tagAction(ItemStack item, MenuAction action, String payload) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action.name());

        if (payload != null) {
            pdc.set(PAYLOAD_KEY, PersistentDataType.STRING, payload);
        } else {
            pdc.remove(PAYLOAD_KEY);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static MenuAction getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (raw == null) return null;

        try {
            return MenuAction.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null; // Invalid action
        }
    }

    public static String getPayload(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(PAYLOAD_KEY, PersistentDataType.STRING);
    }
}
