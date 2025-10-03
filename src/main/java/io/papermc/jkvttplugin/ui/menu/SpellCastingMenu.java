package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class SpellCastingMenu {

    public static void open(Player player, CharacterSheet sheet) {
        // ToDo: incourporate inventory selection screen for selecting active spells
        System.out.println("Known Spells:");
        System.out.println(sheet.getKnownSpells());
//        player.openInventory(build(sheet));
    }

//    public static Inventory build(CharacterSheet sheet) {
//        int inventorySize = 54;
//        Inventory inventory = Bukkit.createInventory()
//    }
}
