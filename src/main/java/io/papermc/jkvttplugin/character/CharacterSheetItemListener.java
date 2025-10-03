package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CharacterSheetItemListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!CharacterSheetManager.isCharacterSheetItem(item)) return;

        event.setCancelled(true);

        if (CharacterSheetManager.isBlankCharacterSheet(item)) {
            handleCharacterCreation(player);
        } else {
            ActiveCharacterTracker.setActiveCharacter(player, CharacterSheetManager.getCharacterIdFromItem(item));
            handleCharacterSheetView(player, item);
        }
    }

    private void handleCharacterCreation(Player player) {
        if (CharacterCreationService.hasSession(player.getUniqueId())) {
            player.sendMessage("You already have a character creation session in progress.");
            CharacterCreationSheetMenu.open(player, CharacterCreationService.getSession(player.getUniqueId()).getSessionId());
            return;
        }

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());
        player.sendMessage("Starting character creation...");
        CharacterCreationSheetMenu.open(player, session.getSessionId());
    }

    private void handleCharacterSheetView(Player player, ItemStack item) {
        UUID characterId = CharacterSheetManager.getCharacterIdFromItem(item);
        if (characterId == null) {
            player.sendMessage("Invalid character sheet item.");
            return;
        }

        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
//        if (character == null) {
//            CharacterSheetManager.loadCharacterSheet(player);
//            character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
//        }

        if (character == null) {
            player.sendMessage("Character not found. The character sheet may be corrupted.");
            return;
        }

        player.sendMessage("Opening character sheet for: " + character.getCharacterName());
        ViewCharacterSheetMenu.open(player, characterId);
    }
}
