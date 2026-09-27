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
        if (io.papermc.jkvttplugin.util.Util.isUsableBlock(clicked)) return;

        event.setCancelled(true);
        open(player, item);
    }

    /**
     * Right-clicking while looking at a creature (or another player) is a different Minecraft event,
     * so the sheet never opened with anyone in front of you. Anything that already handled the click
     * (looting a body, a DM tool, a corpse) cancels it first, and then this stays out of the way.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!CharacterSheetManager.isCharacterSheetItem(item)) return;
        event.setCancelled(true);
        open(event.getPlayer(), item);
    }

    private void open(Player player, ItemStack item) {
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
