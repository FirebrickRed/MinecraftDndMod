package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.SkillsMenu;
import io.papermc.jkvttplugin.ui.menu.SpellCastingMenu;
import io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            case OPEN_SPELLBOOK -> {
                // Get the character sheet to pass to SpellCastingMenu
                CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
                if (character != null) {
                    SpellCastingMenu.open(player, character);
                }
            }
            case CLOSE_CHARACTER_SHEET -> {
                // Save character and close inventory
                CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
                if (character != null) {
                    CharacterPersistenceLoader.saveCharacter(character);
                    player.closeInventory();
                    player.sendMessage(Component.text("Character saved!", NamedTextColor.GREEN));
                }
            }
            case BACK_TO_CHARACTER_SHEET -> {
                ViewCharacterSheetMenu.open(player, characterId);
            }
        }
    }
}