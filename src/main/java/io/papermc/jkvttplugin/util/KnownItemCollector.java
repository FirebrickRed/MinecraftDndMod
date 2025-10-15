package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubRace;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility for collecting already-known items (languages, skills, tools) from a character's
 * race, subrace, class, and background. Used to filter out redundant options in choice menus.
 */
public class KnownItemCollector {

    /**
     * Collects all languages the character already knows from their race, subrace, and background.
     * Does not include languages from player choices - only fixed grants.
     *
     * @param session The character creation session
     * @return Set of language IDs the character knows
     */
    public static Set<String> collectKnownLanguages(CharacterCreationSession session) {
        Set<String> known = new LinkedHashSet<>();

        DndRace race = RaceLoader.getRace(session.getSelectedRace());
        if (race != null && race.getLanguages() != null) {
            known.addAll(race.getLanguages());
        }

        DndSubRace subrace = getSubrace(session);
        if (subrace != null && subrace.getLanguages() != null) {
            known.addAll(subrace.getLanguages());
        }

        DndBackground bg = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (bg != null && bg.getLanguages() != null) {
            known.addAll(bg.getLanguages());
        }

        return known;
    }

    /**
     * Collects all skill proficiencies the character already has from their class and background.
     * Does not include skills from player choices - only fixed grants.
     *
     * @param session The character creation session
     * @return Set of skill IDs the character knows
     */
    public static Set<String> collectKnownSkills(CharacterCreationSession session) {
        Set<String> known = new LinkedHashSet<>();

        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass != null && dndClass.getSkills() != null) {
            known.addAll(dndClass.getSkills());
        }

        DndBackground bg = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (bg != null && bg.getSkills() != null) {
            known.addAll(bg.getSkills());
        }

        return known;
    }

    /**
     * Collects all tool proficiencies the character already has from their class and background.
     * Does not include tools from player choices - only fixed grants.
     *
     * @param session The character creation session
     * @return Set of tool IDs the character knows
     */
    public static Set<String> collectKnownTools(CharacterCreationSession session) {
        Set<String> known = new LinkedHashSet<>();

        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass != null && dndClass.getToolProficiencies() != null) {
            known.addAll(dndClass.getToolProficiencies());
        }

        DndBackground bg = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (bg != null && bg.getTools() != null) {
            known.addAll(bg.getTools());
        }

        return known;
    }

    /**
     * Helper method to get the character's selected subrace.
     *
     * @param session The character creation session
     * @return The subrace, or null if no subrace selected or race not found
     */
    private static DndSubRace getSubrace(CharacterCreationSession session) {
        String subraceId = session.getSelectedSubRace();
        if (subraceId == null) return null;

        DndRace race = RaceLoader.getRace(session.getSelectedRace());
        if (race == null || race.getSubraces() == null) return null;

        return race.getSubraces().get(subraceId);
    }
}