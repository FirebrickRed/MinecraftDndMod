package io.papermc.jkvttplugin;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.DataManager;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.util.ChoiceMerger;

import java.io.File;
import java.util.*;

/**
 * Shared fixture: the repo's real {@code DMContent/}, loaded once per test run through the same
 * {@link DataManager} the plugin uses (same order, same content check). Tests against real content
 * are the point — a YAML typo or a loader regression fails the build instead of a playtest.
 * <p>
 * The registries are static, so everything loads once and tests only read. A test that must change
 * global state (a homebrew Languages.yml) restores it afterwards.
 */
public final class TestContent {

    private static List<String> warnings;

    private TestContent() {}

    /** Loads DMContent once; returns the content check's warnings (empty = clean). */
    public static synchronized List<String> load() {
        if (warnings == null) {
            warnings = new DataManager(new File("DMContent")).loadAllData();
        }
        return warnings;
    }

    /** All abilities 10, then the given overrides: {@code scores(Ability.DEXTERITY, 16)}. */
    public static EnumMap<Ability, Integer> scores(Object... abilityThenScore) {
        EnumMap<Ability, Integer> out = new EnumMap<>(Ability.class);
        for (Ability a : Ability.values()) out.put(a, 10);
        for (int i = 0; i + 1 < abilityThenScore.length; i += 2) {
            out.put((Ability) abilityThenScore[i], (Integer) abilityThenScore[i + 1]);
        }
        return out;
    }

    /**
     * A level-1 character built the way a saved one is loaded (race, class and background traits
     * re-applied; no items — those need a server). {@code subrace} may be null.
     */
    public static CharacterSheet character(String race, String subrace, String dndClass, String background,
                                           EnumMap<Ability, Integer> scores, Skill... skills) {
        load();
        return CharacterSheet.loadFromData(UUID.randomUUID(), UUID.randomUUID(), "Test", race, subrace,
                dndClass, null, background, scores, new HashSet<>(Arrays.asList(skills)),
                Set.of(), Set.of(), 10, 10, 10);
    }

    /** A creation session with race/class/background chosen and its choices built, as the menu does. */
    public static CharacterCreationSession session(String race, String subrace, String dndClass, String background) {
        load();
        UUID player = UUID.randomUUID();
        CharacterCreationSession s = CharacterCreationService.start(player);
        s.setSelectedRace(race);
        if (subrace != null) s.setSelectedSubrace(subrace);
        s.setSelectedClass(dndClass);
        s.setSelectedBackground(background);
        CharacterCreationService.rebuildPendingChoices(player);
        return s;
    }

    public static PendingChoice<?> choice(CharacterCreationSession s, String id) {
        PendingChoice<?> pc = s.findPendingChoice(id);
        if (pc == null) {
            throw new AssertionError("no pending choice '" + id + "'; have " + s.getPendingChoices().stream().map(PendingChoice::getId).toList());
        }
        return pc;
    }

    /** The menu's sections, merged exactly as the creation screen shows them. */
    public static List<MergedChoice> merged(CharacterCreationSession s) {
        return ChoiceMerger.mergeChoices(s.getPendingChoices(), s);
    }
}
