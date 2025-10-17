package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Interface for handling menu click events.
 * Each menu type (Race Selection, Class Selection, etc.) has its own handler implementation.
 *
 * This follows the Strategy pattern to eliminate the god class anti-pattern in MenuClickListener.
 * Instead of a 641-line class with a giant switch statement, each menu type gets a focused handler.
 *
 * Design notes:
 * - The session is pre-fetched by MenuClickListener to avoid duplicate service calls
 * - Handlers are stateless and testable in isolation
 * - Null session checks are handled centrally before routing to handlers
 */
public interface MenuClickHandler {

    /**
     * Handles a click event for a specific menu type.
     *
     * @param player The player who clicked
     * @param session The player's character creation session (guaranteed non-null)
     * @param sessionId The session UUID from the MenuHolder
     * @param action The menu action extracted from the clicked item's NBT
     * @param payload The action payload (parameters) from the clicked item's NBT
     */
    void handleClick(
        Player player,
        CharacterCreationSession session,
        UUID sessionId,
        MenuAction action,
        String payload
    );
}