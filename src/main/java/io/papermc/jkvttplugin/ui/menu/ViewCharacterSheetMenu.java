package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ViewCharacterSheetMenu {
    private ViewCharacterSheetMenu() {}

    public static void open(Player player, UUID characterId) {
        player.openInventory(build(player, characterId));
    }

    public static Inventory build(Player player, UUID characterId) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.VIEW_CHARACTER_SHEET, characterId),
                54,
                Component.text(character.getCharacterName() + "'s Character Sheet")
        );

        ItemStack healthItem = new ItemStack(Material.REDSTONE_BLOCK);
        healthItem.editMeta(m -> {
            m.displayName(Component.text(character.getCurrentHealth() + "/" + character.getMaxHealth() + "HP"));
        });
        inventory.setItem(0, healthItem);

        ItemStack acItem = new ItemStack(Material.SHIELD);
        acItem.editMeta(m -> {
            m.displayName(Component.text(character.getArmorClass() + " AC"));
        });
        inventory.setItem(1, acItem);

        ItemStack proficiencyItem = new ItemStack(Material.PAPER);
        proficiencyItem.editMeta(m -> {
            // ToDo: update character sheet to keep track of proficiency bonuses
            m.displayName(Component.text("Proficiency Bonus: (need to add this to character sheet)"));
        });
        inventory.setItem(8, proficiencyItem);

        ItemStack initItem = new ItemStack(Material.PAPER);
        initItem.editMeta(m -> {
            m.displayName(Component.text("Initiative: " + character.getInitiative()));
        });
        inventory.setItem(8, initItem);

        return inventory;
    }
}
