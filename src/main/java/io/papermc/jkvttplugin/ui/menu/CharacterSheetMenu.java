package io.papermc.jkvttplugin.ui.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class CharacterSheetMenu {

    public static Inventory build(UUID sessionId) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.CHARACTER_SHEET, sessionId),
                54,
                Component.text("Character Sheet")
        );

        // ToDo: populate slots with code

        return inventory;
    }

    public static void open(Player player, UUID sessionId) {
        Inventory inventory = build(sessionId);
        player.openInventory(inventory);
    }
}
