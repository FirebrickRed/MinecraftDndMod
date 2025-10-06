package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

public class SubraceSelectionMenu {

    private SubraceSelectionMenu() {}

    public static void open(Player player, Collection<DndSubRace> subRaces, UUID playerId) {
        player.openInventory(build(subRaces, playerId));
    }

    public static Inventory build(Collection<DndSubRace> subRaces, UUID sessionId) {
        return BaseSelectionMenu.build(subRaces, sessionId, "Choose Your Subrace", MenuType.SUBRACE_SELECTION, MenuAction.CHOOSE_SUBRACE, DndSubRace::getName, DndSubRace::getIconMaterial);
    }
}
