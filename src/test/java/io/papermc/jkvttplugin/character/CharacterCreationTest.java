package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.ui.handler.CharacterCreationHandler;
import io.papermc.jkvttplugin.util.KnownItemCollector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** The creation menu's choices: sources, duplicates, merging, expertise. */
class CharacterCreationTest {

    private static List<MergedChoice> sections(CharacterCreationSession s, ChoiceCategory cat) {
        return merged(s).stream().filter(m -> m.getCategory() == cat).toList();
    }

    // ---------- duplicate proficiencies (PHB p.125) ----------

    @Test
    void woodElfSailorGetsAReplacementForPerception() {
        CharacterCreationSession s = session("elf", "wood_elf", "rogue", "sailor");
        PendingChoice<?> dup = choice(s, "duplicate_skill_perception");
        assertTrue(dup.getTitle().contains("Elf") && dup.getTitle().contains("Sailor"), dup.getTitle());
        for (MergedChoice m : sections(s, ChoiceCategory.SKILL)) {
            assertFalse(m.getAvailableOptionKeys().contains("perception"), "Perception is already known in " + m.getChoiceId());
        }
    }

    @Test
    void rockGnomeArtificerGetsAReplacementForTinkersTools() {
        CharacterCreationSession s = session("gnome", "rock_gnome", "artificer", "sage");
        choice(s, "duplicate_tool_tinkers_tools");
    }

    /** PHB p.125: the replacement is ANY other tool — artisan's tools, instruments and gaming sets too. */
    @Test
    void duplicateToolReplacementOffersEveryOtherTool() {
        CharacterCreationSession s = session("gnome", "rock_gnome", "artificer", "sage");
        MergedChoice dup = sections(s, ChoiceCategory.TOOL).stream()
                .filter(m -> m.getChoiceId().equals("duplicate_tool_tinkers_tools")).findFirst().orElseThrow();
        for (String tool : List.of("smiths_tools", "flute", "dice_set", "navigators_tools", "vehicles_land")) {
            assertTrue(dup.getAvailableOptionKeys().contains(tool), tool + " missing from " + dup.getAvailableOptionKeys());
        }
        assertFalse(dup.getAvailableOptionKeys().contains("tinkers_tools"), "already known");
    }

    @Test
    void druidHermitGetsAReplacementForHerbalismKit() {
        CharacterCreationSession s = session("human", null, "druid", "hermit");
        choice(s, "duplicate_tool_herbalism_kit");
    }

    @Test
    void noDuplicateWithoutAnOverlap() {
        CharacterCreationSession s = session("human", null, "fighter", "sage");
        assertTrue(s.getPendingChoices().stream().noneMatch(pc -> pc.getId().startsWith("duplicate_")));
    }

    // ---------- choices from every source ----------

    /** Race/subrace choices of every type go through the same path; the high elf's cantrip was once dropped. */
    @Test
    void highElfStillGetsItsWizardCantrip() {
        CharacterCreationSession s = session("elf", "high_elf", "wizard", "noble");
        assertTrue(choice(s, "wizard_cantrip").optionKeys().size() > 10);
    }

    /**
     * The high elf's cantrip is racial magic (INT, whatever the class), so the pick becomes an innate
     * spell. A Nature cleric's druid cantrip has no casting_ability: it's a class cantrip (WIS).
     */
    @Test
    void racialCantripPickCarriesItsOwnAbility() {
        CharacterCreationSession s = session("elf", "high_elf", "rogue", "sage");
        assertEquals(io.papermc.jkvttplugin.data.model.enums.Ability.INTELLIGENCE,
                choice(s, "wizard_cantrip").getPlayersChoice().getCastingAbility());
        s.toggleChoiceByKey("wizard_cantrip", "fire_bolt");
        assertEquals(java.util.Map.of("fire_bolt", io.papermc.jkvttplugin.data.model.enums.Ability.INTELLIGENCE),
                s.chosenInnateSpells());

        var druidCantrip = io.papermc.jkvttplugin.data.loader.ClassLoader.getClass("cleric").getSubclasses().values().stream()
                .flatMap(sc -> sc.getPlayerChoices().stream())
                .filter(c -> c.id().equals("druid_cantrip")).findFirst().orElseThrow();
        assertNull(druidCantrip.pc().getCastingAbility(), "a class feature's cantrip uses the class's ability");
    }

    /** "Already known" covers race skills too — a wood elf rogue can't waste a class pick on Perception. */
    @Test
    void knownSkillsIncludeRaceGrants() {
        CharacterCreationSession s = session("elf", "wood_elf", "rogue", "sage");
        assertTrue(KnownItemCollector.collectKnownSkills(s).contains("perception"));
    }

    // ---------- merging ----------

    /** Different tool lists stay separate; merging them let a player skip one pick entirely. */
    @Test
    void differentToolPicksStaySeparate() {
        CharacterCreationSession s = session("gnome", "rock_gnome", "artificer", "archaeologist");
        List<String> ids = sections(s, ChoiceCategory.TOOL).stream().map(MergedChoice::getChoiceId).toList();
        assertTrue(ids.contains("class_tool_proficiencies"), ids.toString());
        assertTrue(ids.contains("background_tool_proficiencies"), ids.toString());
    }

    /** Identical "any language" picks share one pool. */
    @Test
    void identicalLanguagePicksMerge() {
        CharacterCreationSession s = session("elf", "high_elf", "wizard", "noble");
        List<MergedChoice> langs = sections(s, ChoiceCategory.LANGUAGE);
        assertEquals(1, langs.size(), "one merged language section");
        assertEquals(2, langs.get(0).getTotalChooseCount());
        assertFalse(langs.get(0).getAvailableOptionKeys().contains("elvish"), "already known");
    }

    // ---------- expertise (rogue, PHB p.96) ----------

    @Test
    void expertiseOffersOnlyProficiencies() {
        CharacterCreationSession s = session("human", null, "rogue", "sage");
        s.toggleChoiceByKey("class_skills", "stealth");
        s.toggleChoiceByKey("class_skills", "perception");
        MergedChoice exp = sections(s, ChoiceCategory.EXPERTISE).get(0);
        assertEquals(Set.of("stealth", "perception", "arcana", "history", "thieves_tools"),
                Set.copyOf(exp.getAvailableOptionKeys()));
    }

    /** Un-picking a skill takes its expertise with it, instead of leaving a pick that does nothing. */
    @Test
    void unpickingAProficiencyDropsItsExpertise() {
        CharacterCreationSession s = session("human", null, "rogue", "sage");
        s.toggleChoiceByKey("class_skills", "athletics");
        s.toggleChoiceByKey("rogue_expertise", "athletics");
        s.toggleChoiceByKey("rogue_expertise", "arcana"); // from Sage: stays
        assertTrue(s.dropOrphanedExpertise().isEmpty());

        s.toggleChoiceByKey("class_skills", "athletics");
        assertEquals(List.of("Athletics"), s.dropOrphanedExpertise());
        assertEquals(Set.of("arcana"), Set.copyOf(choice(s, "rogue_expertise").getChosen()));
        assertTrue(CharacterCreationHandler.missingSteps(s).stream().noneMatch(m -> m.startsWith("Expertise (")));
    }

    @Test
    void expertiseWithoutTheProficiencyBlocksFinishing() {
        CharacterCreationSession s = session("human", null, "rogue", "sage");
        s.toggleChoiceByKey("class_skills", "stealth");
        s.toggleChoiceByKey("rogue_expertise", "stealth");
        assertTrue(CharacterCreationHandler.missingSteps(s).stream().noneMatch(m -> m.startsWith("Expertise")));

        s.toggleChoiceByKey("class_skills", "stealth"); // un-pick the proficiency underneath
        assertTrue(CharacterCreationHandler.missingSteps(s).contains("Expertise (not proficient in Stealth)"),
                CharacterCreationHandler.missingSteps(s).toString());
    }
}
