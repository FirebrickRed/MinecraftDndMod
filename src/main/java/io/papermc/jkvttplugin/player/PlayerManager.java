package io.papermc.jkvttplugin.player;

import io.papermc.jkvttplugin.player.Background.DndBackground;
import io.papermc.jkvttplugin.player.Classes.DndClass;
import io.papermc.jkvttplugin.player.Races.DndRace;
import io.papermc.jkvttplugin.util.Ability;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {
    // Map to store each player's CharacterSheet
    private static Map<UUID, CharacterSheet> playerCharacterSheets = new HashMap<>();

    public static CharacterSheet createCharacterSheet(Player player, DndRace dndRace, DndClass dndClass, Map<Ability, Integer> abilityScores, DndBackground dndBackground) {
        UUID playerId = player.getUniqueId();

        // Create a new CharacterSheet instance with the chosen class
        CharacterSheet newCharacterSheet = new CharacterSheet(dndRace, dndClass, abilityScores, dndBackground);

        // Store the new character sheet in the map, replacing any existing one
        playerCharacterSheets.put(playerId, newCharacterSheet);

        // Return the newly created character sheet
        return newCharacterSheet;
    }

    // Method to get a player's CharacterSheet
    public static CharacterSheet getCharacterSheet(Player player) {
        return playerCharacterSheets.get(player.getUniqueId());
    }

    public static void resetCharacterSheet(Player player) {
        playerCharacterSheets.remove(player.getUniqueId());
        // playerCharacterSheets.put(player.getUniqueId(), new CharacterSheet());
    }
}
