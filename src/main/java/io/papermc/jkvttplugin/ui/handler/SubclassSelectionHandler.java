package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the Subclass Selection menu.
 * Allows players to choose their character's subclass (e.g., Life Domain for Cleric, Fiend for Warlock).
 */
 // ToDo: if changing class clear subclass
public class SubclassSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_SUBCLASS || payload == null || payload.isEmpty()) {
            return;
        }

        // If changing subclass, clear subclass-specific choices
        String previousSubclass = session.getSelectedSubclass();
        boolean subclassChanged = previousSubclass != null && !previousSubclass.equals(payload);

        session.setSelectedSubclass(payload);

        if (subclassChanged) {
            session.clearPendingChoices();
            player.sendMessage("Subclass changed! Your player choice selections have been reset.");
        } else {
            // Convert normalized name back to pretty format for display
            player.sendMessage("You have selected " + Util.prettify(payload) + " as your subclass!");
        }

        CharacterCreationSheetMenu.open(player, sessionId);
    }
}