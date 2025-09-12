package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.UUID;

public class SpellSelectionMenu {

    public static void open(Player player, Collection<DndSpell> spells, UUID sessionId) {
        player.openInventory(build(spells, sessionId));
    }

    public static Inventory build(Collection<DndSpell> spells, UUID sessionId) {
        int inventorySize = 54;
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.SPELL_SELECTION, sessionId),
                inventorySize,
                Component.text("Choose your spells")
        );

        int slot = 0;
        for(DndSpell spell : spells) {
//            boolean isSelected = session.hasSpell(spell.getName().toLowerCase());
            ItemStack spellItem = new ItemStack(Material.PAPER);
            ItemMeta meta = spellItem.getItemMeta();

            meta.displayName(Component.text(spell.getName()));
            spellItem.setItemMeta(meta);

            String spellId = spell.getName().toLowerCase().replace(' ', '_');
            spellItem = ItemUtil.tagAction(spellItem, MenuAction.CHOOSE_SPELL, spellId);

            inventory.setItem(slot++, spellItem);
        }

        return inventory;
    }

//    public static void open(Player player, String className, CharacterCreationSession session, UUID sessionId) {
//        open(player, className, session, sessionId, 0);
//    }
//
//    public static void open(Player player, String className, CharacterCreationSession session, UUID sessionId, int selectedLevel) {
//        player.openInventory(build(player, className, session, sessionId, selectedLevel));
//    }
//
//    public static Inventory build(Player player, String className, CharacterCreationSession session, UUID sessionId, int selectedLevel) {
//        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.SPELL_SELECTION, sessionId), 54, Component.text("Spell selection - " + (selectedLevel == 0 ? "Cantrips" : "Level " + selectedLevel)));
//
//
//
//        return inventory;
//    }
}
