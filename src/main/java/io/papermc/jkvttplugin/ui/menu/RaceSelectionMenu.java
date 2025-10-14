package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

public class RaceSelectionMenu {

    private RaceSelectionMenu() {}

    public static void open(Player player, Collection<DndRace> races, UUID sessionId) {
        player.openInventory(build(races, sessionId));
    }

    public static Inventory build(Collection<DndRace> races, UUID sessionId) {
        return BaseSelectionMenu.build(races, sessionId, "Select your Race", MenuType.RACE_SELECTION, MenuAction.CHOOSE_RACE, DndRace::getId, DndRace::getName, DndRace::getIconMaterial, DndRace::getSelectionMenuLore);
    }
}
