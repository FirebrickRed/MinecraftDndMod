package io.papermc.jkvttplugin.character;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActiveCharacterTracker {
    private static final Map<UUID, UUID> activeCharacters = new HashMap<>();

    public static void setActiveCharacter(Player player, UUID characterId) {
        activeCharacters.put(player.getUniqueId(), characterId);

        player.getPersistentDataContainer().set(
                new NamespacedKey("jkvtt", "active_character"),
                PersistentDataType.STRING,
                characterId.toString()
        );
        CharacterBody.apply(player); // their body takes the new character's size
    }

    public static UUID getActiveCharacterId(Player player) {
        UUID cached = activeCharacters.get(player.getUniqueId());
        if (cached != null) return cached;

        String stored = player.getPersistentDataContainer().get(new NamespacedKey("jkvtt", "active_character"), PersistentDataType.STRING);

        if (stored != null) {
            try {
                UUID id = UUID.fromString(stored);
                activeCharacters.put(player.getUniqueId(), id);
                return id;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    public static CharacterSheet getActiveCharacter(Player player) {
        UUID characterId = getActiveCharacterId(player);
        CharacterSheet sheet = choose(characterId, CharacterSheetManager.getPlayerCharacters(player.getUniqueId()));
        if (sheet != null && !sheet.getCharacterId().equals(characterId)) setActiveCharacter(player, sheet.getCharacterId());
        return sheet;
    }

    /**
     * The character a stored pointer means. The pointer can be missing or name a deleted character (a
     * delete used to leave it behind, so the player had "no active character" until they right-clicked
     * a sheet): then a player with exactly one character is playing that one. With several, they pick.
     */
    static CharacterSheet choose(UUID storedId, java.util.List<CharacterSheet> mine) {
        if (mine == null || mine.isEmpty()) return null;
        if (storedId != null) for (CharacterSheet s : mine) if (storedId.equals(s.getCharacterId())) return s;
        return mine.size() == 1 ? mine.get(0) : null;
    }

    /** Forget the pointer if it names this character (it was just deleted). */
    public static void clearIfActive(Player player, UUID characterId) {
        if (player == null || characterId == null || !characterId.equals(getActiveCharacterId(player))) return;
        activeCharacters.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(new NamespacedKey("jkvtt", "active_character"));
    }
}
