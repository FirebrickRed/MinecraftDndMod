package io.papermc.jkvttplugin.CustomNPCs;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

public class Hadozee {
    private final ArmorStand npc;
    private final Inventory inventory;
    private static final Map<ArmorStand, Hadozee> HADOZEE_MAP = new HashMap<>();

    public Hadozee(Location spawnLocation) {

        ItemStack stickItem = new ItemStack(Material.STICK);
        ItemMeta meta = stickItem.getItemMeta();

        if (meta != null) {
            meta.setItemModel(new NamespacedKey("jkvttresourcepack", "hadozee"));
            meta.displayName(Component.text("Hadozee"));
            stickItem.setItemMeta(meta);
        }

        this.npc = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);

        npc.customName(Component.text("Hadozee"));
        npc.setCustomNameVisible(true);
        npc.setGravity(false);
        npc.setInvisible(true);
        npc.setMarker(false);

        npc.getEquipment().setHelmet(stickItem);

        this.inventory = Bukkit.createInventory(null, 27, Component.text("Hadozee Inventory"));

        HADOZEE_MAP.put(this.npc, this);
    }

    public ArmorStand getNpc() {
        return npc;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public static Hadozee findByArmorStand(ArmorStand stand) {
        return HADOZEE_MAP.get(stand);
    }

    public static void removeHadozee(ArmorStand stand) {
        HADOZEE_MAP.remove(stand);
    }
}
