package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndSubClass;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

/**
 * Menu for selecting a subclass (e.g., Cleric Domain, Warlock Patron, Sorcerer Origin).
 * Displayed during character creation for classes with subclass_level: 1 (Cleric, Warlock, Sorcerer).
 */
public class SubclassSelectionMenu {

    private SubclassSelectionMenu() {}

    public static void open(Player player, DndClass dndClass, UUID sessionId) {
        if (dndClass == null || !dndClass.hasSubclasses()) {
            player.sendMessage("§cThis class has no subclasses available.");
            return;
        }

        Collection<DndSubClass> subclasses = dndClass.getSubclasses().values();
        String title = "Choose Your " + dndClass.getSubclassTypeName();
        player.openInventory(build(subclasses, sessionId, title));
    }

    public static Inventory build(Collection<DndSubClass> subclasses, UUID sessionId, String title) {
        return BaseSelectionMenu.build(
                subclasses,
                sessionId,
                title,
                MenuType.SUBCLASS_SELECTION,
                MenuAction.CHOOSE_SUBCLASS,
                DndSubClass::getId,
                DndSubClass::getName,
                DndSubClass::getIconMaterial,
                DndSubClass::getSelectionMenuLore
        );
    }
}