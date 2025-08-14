package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CharacterSheetManager {

    private static final Map<UUID, CharacterSheet> characterSheets = new HashMap<>();

    public static boolean hasCharacterSheet(Player player) {
        return characterSheets.containsKey(player.getUniqueId());
    }

    public static CharacterSheet getCharacterSheet(Player player) {
        return characterSheets.get(player.getUniqueId());
    }

    public static void createCharacterSheet(Player player, DndRace race, DndClass dndClass, Map<Ability, Integer> abilityScores, DndBackground background) {
//        CharacterSheet sheet = new CharacterSheet(player, race, dndClass, abilityScores, background);
//        characterSheets.put(player.getUniqueId(), sheet);
    }

    public static void saveCharacterSheet(Player player) {

    }

    public static void loadCharacterSheet(Player player) {

    }

    public static void removeCharacterSheet(Player player) {
        characterSheets.remove(player.getUniqueId());
    }

    public static void resetCharacterSheet(Player player) {

    }

    public static void saveAll() {

    }

    public static void loadAll() {
        // Load character sheets from persistent storage (e.g., database, file)
        // This method should be implemented to retrieve character sheets when the plugin starts
    }
}
