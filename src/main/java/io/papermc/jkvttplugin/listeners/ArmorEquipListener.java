package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ArmorEquipListener implements Listener {
    private final Plugin plugin;

    public ArmorEquipListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlot() != 38) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Schedule task 1 tick later to check final armor state
        Bukkit.getScheduler().runTask(plugin, () -> {
            CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
            if (sheet == null) return;

            ItemStack chestplate = player.getInventory().getChestplate();

           if (chestplate == null || chestplate.getType().isAir()) {
               sheet.unequipArmor();
               return;
           }

           if (!chestplate.hasItemMeta()) return;

           String armorId = chestplate.getItemMeta().getPersistentDataContainer()
                   .get(new NamespacedKey("jkvtt", "armor_id"), PersistentDataType.STRING);

           if (armorId != null) {
               DndArmor armor = ArmorLoader.getArmor(armorId);
               if (armor != null) {
                   sheet.equipArmor(armor);
               }
           }
        });
    }
}
