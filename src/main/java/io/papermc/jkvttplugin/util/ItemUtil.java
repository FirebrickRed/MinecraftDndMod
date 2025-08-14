package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.ui.action.MenuAction;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

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
