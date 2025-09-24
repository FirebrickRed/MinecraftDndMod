package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
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

public class SubraceSelectionMenu {

    private SubraceSelectionMenu() {}

    public static void open(Player player, Map<String, DndSubRace> subRaces, UUID playerId) {
        player.openInventory(build(subRaces, playerId));
    }

    public static Inventory build(Map<String, DndSubRace> subRaces, UUID sessionId) {
        int inventorySize = Util.getInventorySize(subRaces.size());

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.SUBRACE_SELECTION, sessionId),
                inventorySize,
                Component.text("Choose Your Subrace")
        );

        int slot = 0;
        for (Map.Entry<String, DndSubRace> subrace : subRaces.entrySet()) {
            String key = subrace.getKey();
            DndSubRace subraceValue = subrace.getValue();

            ItemStack subraceItem = new ItemStack(Material.PAPER);
            ItemMeta meta = subraceItem.getItemMeta();

            meta.displayName(Component.text(subraceValue.getName()));

            List<Component> lore = new ArrayList<>();

            meta.lore(lore);
            subraceItem.setItemMeta(meta);

            String subraceId = subraceValue.getName().toLowerCase().replace(' ', '_');
            subraceItem = ItemUtil.tagAction(subraceItem, MenuAction.CHOOSE_SUBRACE, subraceId);

            inventory.setItem(slot++, subraceItem);
        }

        return inventory;
    }
}
