package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.JkVttPlugin;

import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.AutomaticGrant;
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
        String subclassId = session.getSelectedSubclass();
        String backgroundId = session.getSelectedBackground();

        var race = RaceLoader.getRace(raceId);
        var subrace = (race != null && subraceId != null) ? race.getSubraces().get(subraceId) : null;
        var dndClass = ClassLoader.getClass(classId);
        var subclass = (dndClass != null && subclassId != null) ? dndClass.getSubclasses().get(subclassId) : null;
        var background = BackgroundLoader.getBackground(backgroundId);

        List<PendingChoice<?>> pending = new ArrayList<>();
        List<AutomaticGrant> grants = new ArrayList<>();

        // ToDo: add player choices to Race
        if (race != null) {
            // I guess there is not getPlayerChoices here... need to update this later
            race.contributeChoices(pending);
            race.contributeAutomaticGrants(grants);
            JkVttPlugin.logger().fine("After race contribution: " + pending.size());
        }
        // ToDo: add player choices to Subrace
        if (subrace != null) {
            subrace.contributeChoices(pending);
            subrace.contributeAutomaticGrants(grants);
            JkVttPlugin.logger().fine("After subrace contribution: " + pending.size());
        }
        if (dndClass != null) {
            dndClass.contributeChoices(pending);
            dndClass.contributeAutomaticGrants(grants);
            JkVttPlugin.logger().fine("After class contribution: " + pending.size());
        }
        if (subclass != null) {
            subclass.contributeChoices(pending);
            subclass.contributeAutomaticGrants(grants);
            JkVttPlugin.logger().fine("After subclass contribution: " + pending.size());
        }
        if (background != null) {
            background.contributeChoices(pending);
            background.contributeAutomaticGrants(grants);
            JkVttPlugin.logger().fine("After background contribution: " + pending.size());
        }

        session.setPendingChoices(pending);
        session.setAutomaticGrants(grants);
        return pending;
    }
}
