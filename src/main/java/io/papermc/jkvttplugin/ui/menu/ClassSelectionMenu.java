package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

public class ClassSelectionMenu {

    private ClassSelectionMenu() {}

    public static void open(Player player, Collection<DndClass> classes, UUID sessionId) {
        player.openInventory(build(classes, sessionId));
    }

    public static Inventory build(Collection<DndClass> classes, UUID sessionId) {
        return BaseSelectionMenu.build(classes, sessionId, "Choose Your Class", MenuType.CLASS_SELECTION, MenuAction.CHOOSE_CLASS, DndClass::getId, DndClass::getName, DndClass::getIconMaterial, DndClass::getSelectionMenuLore);
    }
}
