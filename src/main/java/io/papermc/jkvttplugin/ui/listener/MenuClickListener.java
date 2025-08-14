package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.BackgroundSelectionMenu;
import io.papermc.jkvttplugin.ui.menu.ClassSelectionMenu;
import io.papermc.jkvttplugin.ui.menu.MenuHolder;
import io.papermc.jkvttplugin.ui.menu.SubraceSelectionMenu;
import io.papermc.jkvttplugin.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class MenuClickListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        MenuAction action = ItemUtil.getAction(clickedItem);
        String payload = ItemUtil.getPayload(clickedItem);
        if (action == null) return;

        Player player = (Player) event.getWhoClicked();

        switch (holder.getType()) {
            case CHARACTER_SHEET -> handleCharacterSheetClick(event, holder);
            case RACE_SELECTION -> handleRaceSelectionClick(player, event, holder, clickedItem, action, payload);
            case SUBRACE_SELECTION -> handleSubraceSelectionClick(player, event, holder, clickedItem, action, payload);
            case CLASS_SELECTION -> handleClassSelectionClick(player, event, holder, clickedItem, action, payload);
            case ABILITY_ALLOCATION -> handleAbilityAllocationClick(event, holder);
        }
    }

    private void handleCharacterSheetClick(InventoryClickEvent event, MenuHolder holder) {

    }

    private void handleRaceSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_RACE || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());
        session.setSelectedRace(payload);

        DndRace race = RaceLoader.getRace(payload);

        player.sendMessage("You have selected " + payload + " as your race!");

        if (race == null) {
            player.closeInventory();
            player.sendMessage("Race data not found. Please choose another.");
            return;
        }

        System.out.println("Does Race have subraces: " + race.hasSubraces());
        if (race.hasSubraces()) {
            SubraceSelectionMenu.open(player, race.getSubraces(), holder.getSessionId());
        } else {
            ClassSelectionMenu.open(player, ClassLoader.getAllClasses(), holder.getSessionId());
        }
    }

    private void handleSubraceSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_SUBRACE || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        session.setSelectedSubrace(payload);

        ClassSelectionMenu.open(player, ClassLoader.getAllClasses(), holder.getSessionId());
        player.sendMessage("You have selected " + payload + " as your subrace!");
    }

    private void handleClassSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_CLASS || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        session.setSelectedClass(payload);

        BackgroundSelectionMenu.open(player, BackgroundLoader.getAllBackgrounds(), holder.getSessionId());
        player.sendMessage("You have selected " + payload + " as your class!");
    }

    private void handleBackgroundSelectionClick(InventoryClickEvent event, MenuHolder holder) {

    }

    private void handleAbilityAllocationClick(InventoryClickEvent event, MenuHolder holder) {

    }

}
