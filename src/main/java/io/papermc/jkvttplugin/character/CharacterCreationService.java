package io.papermc.jkvttplugin.character;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CharacterCreationService {
    private static final Map<UUID, CharacterCreationSession> sessions = new HashMap<>();

    public static CharacterCreationSession start(UUID playerId) {
        return sessions.computeIfAbsent(playerId, CharacterCreationSession::new);
    }

    public static CharacterCreationSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public static void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public static boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }
}
