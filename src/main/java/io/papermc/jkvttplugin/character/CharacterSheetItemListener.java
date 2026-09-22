package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
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
        // Right-click only. Dropping an item (Q) swings the arm, which arrives as a LEFT_CLICK_AIR:
        // without this, trying to throw the paper away opened character creation instead.
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!CharacterSheetManager.isCharacterSheetItem(item)) return;

        // Players carry the sheet as their main interface, so they're holding it when they walk up
        // to a chest or a door. Right-clicking an interactable block should use the BLOCK, not pop
        // the sheet — otherwise you can never open a container while the sheet is in hand, which a
        // playtest hit as "chests won't open". Let vanilla have those clicks; the sheet still opens
        // on an air-click or a plain block.
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()) return;

        event.setCancelled(true);

        if (CharacterSheetManager.isBlankCharacterSheet(item)) {
            handleCharacterCreation(player);
        } else {
            handleCharacterSheetView(player, item);
        }
    }

    private void handleCharacterCreation(Player player) {
        if (CharacterCreationService.hasSession(player.getUniqueId())) {
            player.sendMessage("You already have a character creation session in progress.");
            CharacterCreationMenu.open(player, CharacterCreationService.getSession(player.getUniqueId()).getSessionId());
            return;
        }

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());
        CharacterSheetManager.giveCreationPaperIfAbsent(player);
        player.sendMessage("Starting character creation...");
        CharacterCreationMenu.open(player, session.getSessionId());
    }

    private void handleCharacterSheetView(Player player, ItemStack item) {
        UUID characterId = CharacterSheetManager.getCharacterIdFromItem(item);
        if (characterId == null) {
            player.sendMessage("Invalid character sheet item.");
            return;
        }

        // Looked up under the holder's own characters, so someone else's sheet (dropped, stolen) is
        // neither opened nor made the holder's active character.
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) {
            player.sendMessage(CharacterSheetManager.getCharacterById(characterId) != null
                    ? "This isn't your character sheet."
                    : "Character not found. The character sheet may be corrupted.");
            return;
        }
        ActiveCharacterTracker.setActiveCharacter(player, characterId);

        player.sendMessage("Opening character sheet for: " + character.getCharacterName());
        ViewCharacterSheetMenu.open(player, characterId);
    }
}
