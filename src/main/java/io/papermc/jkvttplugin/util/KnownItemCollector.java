package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.model.AutomaticGrant;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects what a character already has (languages, skills, tools) so choice menus can show those
 * options as "already known" instead of letting the player waste a pick on them.
 * <p>
 * "Already known" is read from the session's automatic grants — the same list the creation menu
 * shows, built from race, subrace, class, subclass and background in one place
 * ({@code CharacterCreationService.rebuildPendingChoices}). It used to re-query each source by
 * hand and missed some (race and subclass skills, subclass tools), so a wood elf rogue could spend
 * a class skill pick on the Perception they already had. Keys are canonical ids, matching the
 * choice option keys exactly.
 */
public class KnownItemCollector {

    /** Language ids granted automatically (not the player's own picks). */
    public static Set<String> collectKnownLanguages(CharacterCreationSession session) {
        return grantedIds(session, AutomaticGrant.GrantType.LANGUAGE);
    }

    /**
     * Skill ids granted automatically. Player-selected skills are handled separately via
     * {@link #collectSelectedFromOtherSections} so they show as "selected elsewhere"
     * (light green, movable) rather than locked gray.
     */
    public static Set<String> collectKnownSkills(CharacterCreationSession session) {
        return grantedIds(session, AutomaticGrant.GrantType.SKILL_PROFICIENCY);
    }

    /** Tool ids granted automatically (not the player's own picks). */
    public static Set<String> collectKnownTools(CharacterCreationSession session) {
        return grantedIds(session, AutomaticGrant.GrantType.TOOL_PROFICIENCY);
    }

    /**
     * Every skill and tool this character is proficient in right now: granted ones plus the picks in
     * every skill/tool section. Expertise can only go on these.
     */
    public static Set<String> collectProficientSkillsAndTools(CharacterCreationSession session) {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(collectKnownSkills(session));
        out.addAll(collectKnownTools(session));
        List<PendingChoice<?>> pendingChoices = session.getPendingChoices();
        if (pendingChoices != null) {
            for (PendingChoice<?> pc : pendingChoices) {
                if (pc.getPlayersChoice() == null) continue;
                PlayersChoice.ChoiceType t = pc.getPlayersChoice().getType();
                if (t != PlayersChoice.ChoiceType.SKILL && t != PlayersChoice.ChoiceType.TOOL) continue;
                for (Object chosen : pc.getChosen()) if (chosen instanceof String key) out.add(key.toLowerCase());
            }
        }
        return out;
    }

    /** Expertise options the character can't take yet, because they aren't proficient in them. */
    public static Set<String> collectExpertiseUnavailable(CharacterCreationSession session) {
        Set<String> proficient = collectProficientSkillsAndTools(session);
        Set<String> unavailable = new LinkedHashSet<>();
        for (io.papermc.jkvttplugin.data.model.enums.Skill s : io.papermc.jkvttplugin.data.model.enums.Skill.values()) {
            String id = s.name().toLowerCase();
            if (!proficient.contains(id)) unavailable.add(id);
        }
        for (String tool : io.papermc.jkvttplugin.data.model.enums.ToolRegistry.getAllTools()) {
            if (!proficient.contains(tool)) unavailable.add(tool);
        }
        return unavailable;
    }

    private static Set<String> grantedIds(CharacterCreationSession session, AutomaticGrant.GrantType type) {
        Set<String> known = new LinkedHashSet<>();
        for (AutomaticGrant g : session.getAutomaticGrants()) {
            if (g.type() == type) known.add(g.key());
        }
        return known;
    }

    /**
     * What's picked in OTHER sections of the same kind (skill, tool, language) — shown light green
     * so the player can move a pick instead of taking the same thing twice (a class artisan's-tool
     * pick and a duplicate-replacement pick both offering Smith's Tools, say).
     *
     * @param section the pending choices that make up the section being drawn (excluded)
     */
    public static Set<String> collectSelectedFromOtherSections(CharacterCreationSession session, List<PendingChoice<?>> section) {
        Set<String> selectedElsewhere = new LinkedHashSet<>();
        if (section.isEmpty() || section.get(0).getPlayersChoice() == null) return selectedElsewhere;
        PlayersChoice.ChoiceType type = section.get(0).getPlayersChoice().getType();
        if (type == PlayersChoice.ChoiceType.EQUIPMENT) return selectedElsewhere;

        Set<String> sectionIds = new HashSet<>();
        for (PendingChoice<?> pc : section) sectionIds.add(pc.getId());

        List<PendingChoice<?>> pendingChoices = session.getPendingChoices();
        if (pendingChoices == null) return selectedElsewhere;
        for (PendingChoice<?> pc : pendingChoices) {
            if (sectionIds.contains(pc.getId())) continue;
            if (pc.getPlayersChoice() == null || pc.getPlayersChoice().getType() != type) continue;
            for (Object chosen : pc.getChosen()) {
                if (chosen instanceof String key) selectedElsewhere.add(key);
            }
        }
        return selectedElsewhere;
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
}
