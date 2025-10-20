package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.SkillsMenu;
import io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the View Character Sheet and its drilldown menus (Skills, etc.).
 * Note: This handler doesn't use CharacterCreationSession since it's for viewing finalized characters.
 */
public class ViewCharacterSheetHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        // sessionId here is actually the characterId (UUID of the finalized character)
        UUID characterId = sessionId;

        switch (action) {
            case OPEN_SKILLS_MENU -> {
                SkillsMenu.open(player, characterId);
            }
            case BACK_TO_CHARACTER_SHEET -> {
                ViewCharacterSheetMenu.open(player, characterId);
            }
        }
    }
}