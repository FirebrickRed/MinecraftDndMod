package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;

import java.util.*;

/**
 * Utility for merging multiple PendingChoice objects of the same category
 * into unified MergedChoice objects for display in the tabbed choice menu.
 */
public class ChoiceMerger {

    /**
     * Merges all pending choices from a session by category.
     * Categories with no choices are excluded from the result.
     *
     * @param allChoices All pending choices from race/subrace/class/background
     * @param session The character creation session (used to collect already-known items)
     * @return List of merged choices, one per non-empty category
     */
    public static List<MergedChoice> mergeChoices(
            List<PendingChoice<?>> allChoices,
            CharacterCreationSession session
    ) {
        if (allChoices == null || allChoices.isEmpty()) {
            return Collections.emptyList();
        }

        // Group choices by category
        Map<ChoiceCategory, List<PendingChoice<?>>> byCategory = new EnumMap<>(ChoiceCategory.class);

        for (PendingChoice<?> pc : allChoices) {
            PlayersChoice<?> playersChoice = pc.getPlayersChoice();
            if (playersChoice == null) continue;

            PlayersChoice.ChoiceType type = playersChoice.getType();
            ChoiceCategory category = ChoiceCategory.fromChoiceType(type, pc.getId());

            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(pc);
        }

        // Merge each category
        List<MergedChoice> merged = new ArrayList<>();
        for (var entry : byCategory.entrySet()) {
            ChoiceCategory category = entry.getKey();
            List<PendingChoice<?>> choices = entry.getValue();

            // Equipment choices should NOT be merged - each equipment choice is its own group
            // This preserves the context of mutually exclusive options (e.g., "Dungeoneer's Pack OR Scholar's Pack")
            if (category == ChoiceCategory.EQUIPMENT) {
                for (PendingChoice<?> pc : choices) {
                    // Create a separate MergedChoice for each equipment choice
                    MergedChoice individualChoice = mergeCategory(category, List.of(pc), session);
                    merged.add(individualChoice);
                }
            } else {
                // Other categories (Languages, Skills, Tools) can be merged
                MergedChoice mergedChoice = mergeCategory(category, choices, session);
                merged.add(mergedChoice);
            }
        }

        return merged;
    }

    /**
     * Merges all PendingChoice objects in a single category into one MergedChoice.
     *
     * @param category The category being merged
     * @param choices All pending choices in this category
     * @param session The session (used to collect already-known items)
     * @return A merged choice combining all sources
     */
    private static MergedChoice mergeCategory(
            ChoiceCategory category,
            List<PendingChoice<?>> choices,
            CharacterCreationSession session
    ) {
        Set<String> alreadyKnown = collectKnownForCategory(category, session);
        return new MergedChoice(category, choices, alreadyKnown);
    }

    /**
     * Collects already-known items for a specific category.
     * This is used to filter out redundant options (e.g., don't show Common as a language
     * choice if the character already knows it from their race).
     *
     * @param category The category to collect known items for
     * @param session The character creation session
     * @return Set of item IDs the character already has in this category
     */
    private static Set<String> collectKnownForCategory(
            ChoiceCategory category,
            CharacterCreationSession session
    ) {
        return switch (category) {
            case LANGUAGE -> KnownItemCollector.collectKnownLanguages(session);
            case SKILL -> KnownItemCollector.collectKnownSkills(session);
            case TOOL -> KnownItemCollector.collectKnownTools(session);
            case SPELL -> KnownItemCollector.collectKnownSpells(session);
            case EQUIPMENT, EXTRA -> Collections.emptySet(); // No filtering needed
        };
    }
}