package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the Background Selection menu.
 * Allows players to choose their character's background during character creation.
 */
public class BackgroundSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_BACKGROUND || payload == null || payload.isEmpty()) {
            return;
        }

        // If changing background, clear background-specific selections
        String previousBackground = session.getSelectedBackground();
        boolean backgroundChanged = previousBackground != null && !previousBackground.equals(payload);

        session.setSelectedBackground(payload);

        if (backgroundChanged) {
            // Clear pending choices (background-specific equipment)
            session.clearPendingChoices();
            player.sendMessage("Background changed! Your equipment selections have been reset.");
        } else {
            player.sendMessage("You have selected " + payload + " as your background!");
        }

        CharacterCreationSheetMenu.open(player, sessionId);
    }
}
