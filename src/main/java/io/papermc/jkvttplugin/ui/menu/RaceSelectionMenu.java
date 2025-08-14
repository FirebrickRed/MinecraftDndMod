package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class RaceSelectionMenu {

    public static void open(Player player, Collection<DndRace> races, UUID sessionId) {
        player.openInventory(build(races, sessionId));
    }

    public static Inventory build(Collection<DndRace> races, UUID sessionId) {
        int inventorySize = Util.getInventorySize(races.size());
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.RACE_SELECTION, sessionId),
                inventorySize,
                Component.text("Select Your Race")
        );

        int slot = 0;
        for (DndRace race : races) {
            ItemStack raceItem = new ItemStack(Material.PAPER);
            ItemMeta meta = raceItem.getItemMeta();

            meta.displayName(Component.text(race.getName()));

            List<Component> lore = new ArrayList<>();

            meta.lore(lore);
            raceItem.setItemMeta(meta);

//            String raceId = race.getId() != null ? race.getId() : slug(race.getName());
            String raceId = race.getName().toLowerCase().replace(' ', '_');
            raceItem = ItemUtil.tagAction(raceItem, MenuAction.CHOOSE_RACE, raceId);

            inventory.setItem(slot++, raceItem);
        }

        return inventory;
    }
}
