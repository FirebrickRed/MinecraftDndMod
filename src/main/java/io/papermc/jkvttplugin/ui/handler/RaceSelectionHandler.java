package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles click events for the Race Selection menu.
 * Allows players to choose their character's race during character creation.
 */
public class RaceSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_RACE || payload == null || payload.isEmpty()) {
            return;
        }

        // If changing race, clear race-dependent selections
        String previousRace = session.getSelectedRace();
        if (previousRace != null && !previousRace.equals(payload)) {
            session.setSelectedSubrace(null);
            session.setRacialBonusDistribution(null);
            session.clearAllRacialBonuses();
            session.clearPendingChoices();
            player.sendMessage("Race changed! Your subrace, racial bonus, and player choice selections have been reset.");
        }

        session.setSelectedRace(payload);

        DndRace race = RaceLoader.getRace(payload);
        player.sendMessage("You have selected " + payload + " as your race!");

        if (race == null) {
            player.closeInventory();
            player.sendMessage("Race data not found. Please choose another.");
            return;
        }

        CharacterCreationSheetMenu.open(player, sessionId);
    }
}