package io.papermc.jkvttplugin.character;


import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.AutomaticGrant;
import io.papermc.jkvttplugin.data.model.ChoiceContributor;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;

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
        }
        // ToDo: add player choices to Subrace
        if (subrace != null) {
            subrace.contributeChoices(pending);
            subrace.contributeAutomaticGrants(grants);
        }
        if (dndClass != null) {
            dndClass.contributeChoices(pending);
            dndClass.contributeAutomaticGrants(grants);
        }
        if (subclass != null) {
            subclass.contributeChoices(pending);
            subclass.contributeAutomaticGrants(grants);
        }
        if (background != null) {
            background.contributeChoices(pending);
            background.contributeAutomaticGrants(grants);
        }

        pending.addAll(duplicateReplacements(grants));

        session.setPendingChoices(pending);
        session.setAutomaticGrants(grants);
        return pending;
    }

    /**
     * PHB p.125: "If a character would gain the same proficiency from two different sources, he or
     * she can choose a different proficiency of the same kind (skill or tool) instead." A wood elf
     * sailor gets Perception twice; this turns the second copy into a "pick any other skill" choice.
     * <p>
     * Only fixed grants can collide here — a player's own picks already can't select something a
     * grant gives (those options are shown as "already known"). Languages aren't proficiencies, so
     * RAW doesn't cover them; a repeated language is just known once.
     */
    private static List<PendingChoice<?>> duplicateReplacements(List<AutomaticGrant> grants) {
        // (type, id) → the distinct sources granting it, in order.
        Map<String, LinkedHashSet<String>> sourcesByKey = new LinkedHashMap<>();
        Map<String, AutomaticGrant> firstByKey = new LinkedHashMap<>();
        for (AutomaticGrant g : grants) {
            if (g.type() != AutomaticGrant.GrantType.SKILL_PROFICIENCY
                    && g.type() != AutomaticGrant.GrantType.TOOL_PROFICIENCY) continue;
            String key = g.type() + ":" + g.key();
            sourcesByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(g.source());
            firstByKey.putIfAbsent(key, g);
        }

        List<ChoiceEntry> entries = new ArrayList<>();
        for (var e : sourcesByKey.entrySet()) {
            int extra = e.getValue().size() - 1; // one replacement per source after the first
            if (extra <= 0) continue;
            AutomaticGrant g = firstByKey.get(e.getKey());
            boolean skill = g.type() == AutomaticGrant.GrantType.SKILL_PROFICIENCY;
            List<String> options = skill
                    ? Arrays.stream(Skill.values()).map(s -> s.name().toLowerCase()).toList()
                    : ToolRegistry.getAllTools();
            PlayersChoice.ChoiceType type = skill ? PlayersChoice.ChoiceType.SKILL : PlayersChoice.ChoiceType.TOOL;
            String title = "Replace duplicate " + g.displayName() + " (" + String.join(" + ", e.getValue()) + ")";
            entries.add(new ChoiceEntry("duplicate_" + (skill ? "skill_" : "tool_") + g.key(), title, type,
                    new PlayersChoice<>(extra, options, type)));
        }

        List<PendingChoice<?>> out = new ArrayList<>();
        ChoiceContributor.contribute(entries, "duplicate", out);
        return out;
    }
}
