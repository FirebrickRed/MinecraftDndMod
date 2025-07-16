package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.CustomNPCs.NpcData;
import io.papermc.jkvttplugin.CustomNPCs.NpcManager;
import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NpcListener implements Listener {
    private final JkVttPlugin plugin;
    private final Map<UUID, ArmorStand> possessedNpcs = new HashMap<>();
    private final Map<UUID, BukkitRunnable> movementTasks = new HashMap<>();

    public NpcListener() {
        this.plugin = JkVttPlugin.getInstance();
    }

    @EventHandler
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (player.isSneaking() || player.getInventory().getItemInMainHand().getType() == Material.NAME_TAG) {
            System.out.println("in name tag if");
            return;
        }

        if (item.getType() == Material.PAPER && item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName().equals(Component.text("Open NPC Inventory"))) {
            return;
        }

        if (possessedNpcs.containsKey(player.getUniqueId())) {
            unpossessNpc(player);
        } else {
            possessNpc(player, (ArmorStand) entity);
            giveNpcInventoryItem(player);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && possessedNpcs.containsKey(player.getUniqueId())) {
            unpossessNpc(player);
        }
    }

    private void possessNpc(Player player, ArmorStand armorStand) {
        possessedNpcs.put(player.getUniqueId(), armorStand);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        player.sendMessage(Component.text("You are now possessing " + armorStand.getName(), NamedTextColor.GREEN));

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !possessedNpcs.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                armorStand.teleport(player.getLocation());
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        movementTasks.put(player.getUniqueId(), task);
    }

    private void giveNpcInventoryItem(Player player) {
        ItemStack item = Util.createItem(Component.text("Open NPC Inventory"), null, "4", 0);
        player.getInventory().addItem(item);
    }

    private void removeNpcInventoryItem(Player player) {
//        player.getInventory().remove
    }

    private void unpossessNpc(Player player) {
        ArmorStand armorStand = possessedNpcs.remove(player.getUniqueId());
        if (armorStand != null) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendMessage(Component.text("You are no longer possessing " + armorStand.getName(), NamedTextColor.RED));
        }
        BukkitRunnable task = movementTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unpossessNpc(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ItemStack item = player.getInventory().getItemInMainHand();

        // ✅ Only proceed if the item is the paper
        if (item.getType() != Material.PAPER ||
                !item.hasItemMeta() ||
                !item.getItemMeta().hasDisplayName() ||
                !item.getItemMeta().displayName().equals(Component.text("Open NPC Inventory"))) {
            return;
        }

        // ✅ Player must be possessing an NPC
        if (!possessedNpcs.containsKey(playerId)) {
            player.sendMessage(Component.text("You are not currently possessing an NPC."));
            return;
        }

        event.setCancelled(true); // Prevent other interactions
        openNpcInventory(player);
    }

    public void openNpcInventory(Player player) {
        UUID playerId = player.getUniqueId();
        if (!possessedNpcs.containsKey(playerId)) return;

        ArmorStand armorStand = possessedNpcs.get(playerId);
        NpcData npcData = NpcManager.getNpc(armorStand.getName());

        if (npcData == null) {
            player.sendMessage(Component.text("No NPC data found."));
            return;
        }

        Inventory npcInventory = Bukkit.createInventory(null, 27, Component.text(npcData.getName() + "'s Inventory"));

        npcInventory.setItem(0, createStatItem(Material.BOOK, "Name", npcData.getName()));
        npcInventory.setItem(1, createStatItem(Material.BOOK, "Type", npcData.getType()));
        npcInventory.setItem(2, createStatItem(Material.BOOK, "HP", String.valueOf(npcData.getHitPoints())));
        npcInventory.setItem(3, createStatItem(Material.BOOK, "AC", String.valueOf(npcData.getArmorClass())));
        npcInventory.setItem(4, createStatItem(Material.BOOK, "Speed", String.valueOf(npcData.getSpeed())));

        // Add abilities
        int abilitySlot = 5;
        for (Map.Entry<String, Integer> entry : npcData.getAbilities().entrySet()) {
            npcInventory.setItem(abilitySlot++, createStatItem(Material.ENCHANTED_BOOK, entry.getKey(), String.valueOf(entry.getValue())));
        }

        // Add inventory items
        if (npcData.getInventory() != null && !npcData.getInventory().isEmpty()) {
            for (String itemName : npcData.getInventory()) {
                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(itemName));
                item.setItemMeta(meta);
                npcInventory.addItem(item);
            }
        }

        player.openInventory(npcInventory);
    }
//    @EventHandler
//    public void onInventoryOpen(InventoryOpenEvent event) {
//        Player player = (Player) event.getPlayer();
//        System.out.println("In on Inventory open");
//        if (possessedNpcs.containsKey(player.getUniqueId())) {
//            event.setCancelled(true);
//            System.out.println("in if");
//
//            ArmorStand armorStand = possessedNpcs.get(player.getUniqueId());
//            NpcData npcData = NpcManager.getNpc(armorStand.getName());
//            System.out.println(npcData);
//
//            if (npcData != null) {
//                Inventory npcInventory = Bukkit.createInventory(null, 27, Component.text(npcData.getName() + "'s Inventory"));
//
//                // Add stats as items
//                npcInventory.setItem(0, createStatItem(Material.BOOK, "Name", npcData.getName()));
//                npcInventory.setItem(1, createStatItem(Material.BOOK, "Type", npcData.getType()));
//                npcInventory.setItem(2, createStatItem(Material.BOOK, "HP", String.valueOf(npcData.getHitPoints())));
//                npcInventory.setItem(3, createStatItem(Material.BOOK, "AC", String.valueOf(npcData.getArmorClass())));
//                npcInventory.setItem(4, createStatItem(Material.BOOK, "Speed", String.valueOf(npcData.getSpeed())));
//
//                // Add abilities
//                int abilitySlot = 5;
//                for (Map.Entry<String, Integer> entry : npcData.getAbilities().entrySet()) {
//                    npcInventory.setItem(abilitySlot++, createStatItem(Material.ENCHANTED_BOOK, entry.getKey(), String.valueOf(entry.getValue())));
//                }
//
//
//                if (npcData.getInventory() != null && !npcData.getInventory().isEmpty()) {
//                    for (String itemName : npcData.getInventory()) {
//                        ItemStack item = new ItemStack(Material.CHEST);
//                        ItemMeta meta = item.getItemMeta();
//                        meta.displayName(Component.text(itemName));
//                        item.setItemMeta(meta);
//                        npcInventory.addItem(item);
//                    }
//                }
//                player.openInventory(npcInventory);
//            }
//        }
//    }

    private ItemStack createStatItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(value, NamedTextColor.WHITE)));
        item.setItemMeta(meta);
        return item;
    }
}