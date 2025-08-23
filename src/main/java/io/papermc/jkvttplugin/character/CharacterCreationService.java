package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.PendingChoice;

import java.util.*;

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

    public static List<PendingChoice<?>> rebuildPendingChoices(UUID playerId) {
        CharacterCreationSession session = getSession(playerId);
        if (session == null) throw new IllegalStateException("No active session");

        String raceId = session.getSelectedRace();
        String subraceId = session.getSelectedSubRace();
        String classId = session.getSelectedClass();
        String backgroundId = session.getSelectedBackground();

        var race = RaceLoader.getRace(raceId);
        var subrace = (race != null && subraceId != null) ? race.getSubraces().get(subraceId) : null;
        var dndClass = ClassLoader.getClass(classId);
        var background = BackgroundLoader.getBackground(backgroundId);

        List<PendingChoice<?>> pending = new ArrayList<>();

        if (race != null) race.contributeChoices(pending);
        if (subrace != null) subrace.contributeChoices(pending);
        if (dndClass != null) dndClass.contributeChoices(pending);
        if (background != null) background.contributeChoices(pending);

        session.setPendingChoices(pending);
        return pending;
    }
}
