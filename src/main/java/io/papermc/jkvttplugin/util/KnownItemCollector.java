package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;

import java.util.LinkedHashSet;
import java.util.List;
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
            // Normalize using Util.normalize() for consistent filtering
            race.getLanguages().forEach(lang -> known.add(Util.normalize(lang)));
        }

        DndSubRace subrace = getSubrace(session);
        if (subrace != null && subrace.getLanguages() != null) {
            subrace.getLanguages().forEach(lang -> known.add(Util.normalize(lang)));
        }

        DndBackground bg = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (bg != null && bg.getLanguages() != null) {
            bg.getLanguages().forEach(lang -> known.add(Util.normalize(lang)));
        }

        return known;
    }

    /**
     * Collects all skill proficiencies the character already has from their class and background.
     * Only includes FIXED skills, not player-selected skills.
     *
     * @param session The character creation session
     * @return Set of skill IDs the character knows from fixed sources (grayed out, can't be selected)
     */
    public static Set<String> collectKnownSkills(CharacterCreationSession session) {
        Set<String> known = new LinkedHashSet<>();

        // Fixed skills from class
        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass != null && dndClass.getSkills() != null) {
            known.addAll(dndClass.getSkills());
        }

        // Fixed skills from background
        DndBackground bg = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (bg != null && bg.getSkills() != null) {
            known.addAll(bg.getSkills());
        }

        // Note: We do NOT include player-selected skills here.
        // Player-selected skills are handled separately via collectSelectedSkillsFromOtherSections()
        // to show as "light green/selected elsewhere" instead of gray "already known".

        return known;
    }

    /**
     * Collects skills that have been selected in OTHER pending choices (not the current one).
     * Used to show skills as "selected elsewhere" (light green) so they can be moved between sections.
     *
     * @param session The character creation session
     * @param currentChoiceId The ID of the current choice to exclude from the search
     * @return Set of skill IDs selected in other sections
     */
    public static Set<String> collectSelectedSkillsFromOtherSections(CharacterCreationSession session, String currentChoiceId) {
        Set<String> selectedElsewhere = new LinkedHashSet<>();

        List<PendingChoice<?>> pendingChoices = session.getPendingChoices();
        if (pendingChoices != null) {
            for (PendingChoice<?> pc : pendingChoices) {
                // Skip the current choice - we only want skills from OTHER sections
                if (pc.getId().equals(currentChoiceId)) continue;

                if (pc.getPlayersChoice() != null &&
                    pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.SKILL) {
                    // Add all chosen skills from this other pending choice
                    for (Object chosen : pc.getChosen()) {
                        if (chosen instanceof String skill) {
                            selectedElsewhere.add(skill);
                        }
                    }
                }
            }
        }

        return selectedElsewhere;
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
     * Collects all spells the character already knows from their chosen spells and cantrips.
     * Does not include fixed grants - only spells selected via player choices.
     *
     * @param session The character creation session
     * @return Set of spell names the character knows
     */
    public static Set<String> collectKnownSpells(CharacterCreationSession session) {
        Set<String> known = new LinkedHashSet<>();

        // Add already-selected cantrips
        if (session.getSelectedCantrips() != null) {
            known.addAll(session.getSelectedCantrips());
        }

        // Add already-selected leveled spells
        if (session.getSelectedSpells() != null) {
            known.addAll(session.getSelectedSpells());
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