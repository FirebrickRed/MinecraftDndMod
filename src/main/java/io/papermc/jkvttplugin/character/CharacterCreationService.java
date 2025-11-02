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

        // ToDo: add player choices to Race
        if (race != null) {
            // I guess there is not getPlayerChoices here... need to update this later
//            System.out.println("Race player choices: " + race.getPlayerChoices().size());
            race.contributeChoices(pending);
            System.out.println("After race contribution: " + pending.size());
        }
        // ToDo: add player choices to Subrace
        if (subrace != null) {
//            System.out.println("Subrace player choices: " + subrace.getPlayerChoices().size());
            subrace.contributeChoices(pending);
            System.out.println("After subrace contribution: " + pending.size());
        }
        if (dndClass != null) {
//            System.out.println("Class player choices: " + dndClass.getPlayerChoices().size());
            dndClass.contributeChoices(pending);
            System.out.println("After class contribution: " + pending.size());
        }
        if (background != null) {
//            System.out.println("Background player choices: " + background.getPlayerChoices().size());
            background.contributeChoices(pending);
            System.out.println("After background contribution: " + pending.size());
        }

        session.setPendingChoices(pending);
        return pending;
    }
}
