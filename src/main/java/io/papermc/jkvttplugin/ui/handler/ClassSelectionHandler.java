package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the Class Selection menu.
 * Allows players to choose their character's class during character creation.
 */
public class ClassSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_CLASS || payload == null || payload.isEmpty()) {
            return;
        }

        // If changing class, clear class-specific selections
        String previousClass = session.getSelectedClass();
        boolean classChanged = previousClass != null && !previousClass.equals(payload);

        session.setSelectedClass(payload);

        if (classChanged) {
            // Clear spells (class-specific spell lists)
            session.clearAllSpells();
            // Clear pending choices (class-specific equipment)
            session.clearPendingChoices();
            // Clear subclass (class-specific subclass)
            session.setSelectedSubclass(null);
            player.sendMessage("Class changed! Your spell, equipment, and subclass selections have been reset.");
        } else {
            player.sendMessage("You have selected " + payload + " as your class!");
        }

        CharacterCreationSheetMenu.open(player, sessionId);
    }
}
