package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

public class BackgroundSelectionMenu {

    private BackgroundSelectionMenu() {}

    public static void open(Player player, Collection<DndBackground> backgrounds, UUID sessionId) {
        player.openInventory(build(backgrounds, sessionId));
    }

    public static Inventory build(Collection<DndBackground> backgrounds, UUID sessionId) {
        return BaseSelectionMenu.build(backgrounds, sessionId, "Select Your Background", MenuType.BACKGROUND_SELECTION, MenuAction.CHOOSE_BACKGROUND, DndBackground::getId, DndBackground::getName, DndBackground::getIconMaterial);
    }
}
