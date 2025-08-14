package io.papermc.jkvttplugin.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class CharacterTokenUtil {
    private static NamespacedKey TOKEN_KEY;
    private static NamespacedKey SESSION_KEY;

    private CharacterTokenUtil() {}

    public static void init(Plugin plugin) {
        TOKEN_KEY = new NamespacedKey(plugin, "token_type");
        SESSION_KEY = new NamespacedKey(plugin, "character_session");
    }

    public static ItemStack makeStarterPaper() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Character Creation Token"));
            meta.setCustomModelData(1);
            meta.getPersistentDataContainer().set(TOKEN_KEY, PersistentDataType.STRING, "character_creation");
            paper.setItemMeta(meta);
        }
        return paper;
    }


}
