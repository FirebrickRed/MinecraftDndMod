package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the Subrace Selection menu.
 * Allows players to choose their character's subrace (e.g., High Elf, Mountain Dwarf).
 */
public class SubraceSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_SUBRACE || payload == null || payload.isEmpty()) {
            return;
        }

        // If changing subrace, clear subrace-specific choices
        String previousSubrace = session.getSelectedSubRace();
        boolean subraceChanged = previousSubrace != null && !previousSubrace.equals(payload);

        session.setSelectedSubrace(payload);

        if (subraceChanged) {
            session.clearPendingChoices();
            player.sendMessage("Subrace changed! Your player choice selections have been reset.");
        } else {
            player.sendMessage("You have selected " + payload + " as your subrace!");
        }

        CharacterCreationSheetMenu.open(player, sessionId);
    }
}