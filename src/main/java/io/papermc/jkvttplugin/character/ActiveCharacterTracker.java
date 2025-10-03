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
        if (characterId == null) return null;

        return CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
    }
}
