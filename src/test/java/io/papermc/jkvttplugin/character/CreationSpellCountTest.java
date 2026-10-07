package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.handler.CharacterCreationHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Set;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Playtest: what the creation spell picker counts and offers. A prepared caster's count uses the
 * scores the sheet will have (racial bonuses in), and a spell a choice already gives isn't a pick.
 */
class CreationSpellCountTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    /** INT 15 with a gnome's +2 is 17 (+3): three prepared spells, not the two the base 15 gave. */
    @Test
    void anArtificerPreparesWithTheRacialBonusCounted() {
        CharacterCreationSession s = session("gnome", null, "artificer", "acolyte");
        s.setAbilityScores(scores(Ability.INTELLIGENCE, 15));
        assertEquals(17, s.getFinalAbilityScores().get(Ability.INTELLIGENCE));
        assertEquals(15, s.getAbilityScores().get(Ability.INTELLIGENCE), "the base scores are left alone");
        assertEquals(3, CharacterCreationHandler.spellMax(ClassLoader.getClass("artificer"), 1, s));
    }

    /** A +1 the player places themselves counts too, on top of whatever the race fixes. */
    @Test
    void aBonusThePlayerPlacesCounts() {
        CharacterCreationSession s = session("human", null, "artificer", "acolyte");
        s.setAbilityScores(scores(Ability.INTELLIGENCE, 15));
        int withoutIt = s.getFinalAbilityScores().get(Ability.INTELLIGENCE);
        s.setRacialBonus(Ability.INTELLIGENCE, 1);
        int withIt = s.getFinalAbilityScores().get(Ability.INTELLIGENCE);
        assertEquals(withoutIt + 1, withIt);
        assertEquals(Math.max(1, Ability.getModifier(withIt)),
                CharacterCreationHandler.spellMax(ClassLoader.getClass("artificer"), 1, s));
    }

    /** A cleric's count follows the same scores: WIS 15 + a hill dwarf's +1 is 16 (+3), and +1 for level 1. */
    @Test
    void aClericsCountUsesTheFinalScoreToo() {
        CharacterCreationSession s = session("dwarf", "hill_dwarf", "cleric", "acolyte");
        s.setAbilityScores(scores(Ability.WISDOM, 15));
        assertEquals(16, s.getFinalAbilityScores().get(Ability.WISDOM));
        assertEquals(4, CharacterCreationHandler.spellMax(ClassLoader.getClass("cleric"), 1, s));
    }

    /** Divine Soul, Chaos: Bane is known for free, so the picker shows it as fixed instead of a pick. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aSpellAChoiceGrantsIsAlwaysKnown() {
        CharacterCreationSession s = session("human", null, "sorcerer", "acolyte");
        s.setSelectedSubclass("divine_soul");
        CharacterCreationService.rebuildPendingChoices(s.getPlayerId());
        assertTrue(s.alwaysKnownSpellIds().isEmpty(), "nothing until the affinity is picked");

        PendingChoice affinity = s.getPendingChoices().stream()
                .filter(pc -> pc.getId().contains("divine_magic_affinity")).findFirst()
                .orElseThrow(() -> new AssertionError("no affinity choice"));
        Object chaos = affinity.getPlayersChoice().getOptions().stream()
                .filter(o -> o.toString().equalsIgnoreCase("Chaos")).findFirst().orElseThrow();
        affinity.toggleOption(chaos, Set.of());
        assertEquals(Set.of("bane"), s.alwaysKnownSpellIds());

        affinity.toggleOption(chaos, Set.of());
        Object law = affinity.getPlayersChoice().getOptions().stream()
                .filter(o -> o.toString().equalsIgnoreCase("Law")).findFirst().orElseThrow();
        affinity.toggleOption(law, Set.of());
        assertEquals(Set.of("bless"), s.alwaysKnownSpellIds(), "changing the pick changes the spell");
    }

    /** A subclass's own bonus spells are on the same list (a Life cleric's Bless and Cure Wounds). */
    @Test
    void aDomainsSpellsAreAlwaysKnown() {
        CharacterCreationSession s = session("human", null, "cleric", "acolyte");
        s.setSelectedSubclass("life_domain");
        assertTrue(s.alwaysKnownSpellIds().containsAll(Set.of("bless", "cure_wounds")), s.alwaysKnownSpellIds().toString());
    }

    /** The count tile says where its number comes from, in the roll-bonus wording. */
    @Test
    void theCountIsSpelledOut() {
        CharacterCreationSession art = session("gnome", null, "artificer", "acolyte");
        art.setAbilityScores(scores(Ability.INTELLIGENCE, 15));
        assertEquals("Prepared: +3[INT] +0[half level, rounded down] = 3",
                CharacterCreationHandler.spellMaxReason(ClassLoader.getClass("artificer"), 1, art));

        CharacterCreationSession cleric = session("dwarf", "hill_dwarf", "cleric", "acolyte");
        cleric.setAbilityScores(scores(Ability.WISDOM, 15));
        assertEquals("Prepared: +3[WIS] +1[level] = 4",
                CharacterCreationHandler.spellMaxReason(ClassLoader.getClass("cleric"), 1, cleric));

        CharacterCreationSession wizard = session("human", null, "wizard", "acolyte");
        assertTrue(CharacterCreationHandler.spellMaxReason(ClassLoader.getClass("wizard"), 1, wizard).contains("spellbook holds 6"));
        assertTrue(CharacterCreationHandler.spellMaxReason(ClassLoader.getClass("sorcerer"), 1,
                session("human", null, "sorcerer", "acolyte")).contains("knows 2 spells"));
    }

    /** A low score still prepares one spell, and says so; a 9 is -1, not 0. */
    @Test
    void theMinimumIsNamedAndNegativeModifiersRoundDown() {
        CharacterCreationSession art = session("dwarf", "hill_dwarf", "artificer", "acolyte");
        art.setAbilityScores(scores(Ability.INTELLIGENCE, 9));
        assertEquals("Prepared: -1[INT] +0[half level, rounded down] = 1 (never fewer than 1)",
                CharacterCreationHandler.spellMaxReason(ClassLoader.getClass("artificer"), 1, art));
    }

    /** Every prepared caster's number at level 1, so a formula edit can't quietly change one. */
    @Test
    void everyPreparedCastersCountAtLevelOne() {
        for (String[] row : new String[][]{{"artificer", "INTELLIGENCE", "3"}, {"cleric", "WISDOM", "4"}, {"druid", "WISDOM", "4"}}) {
            CharacterCreationSession s = session("human", null, row[0], "acolyte");
            EnumMap<Ability, Integer> sc = scores();
            sc.put(Ability.valueOf(row[1]), 16 - (s.getFinalAbilityScores().get(Ability.valueOf(row[1])) - 10));
            s.setAbilityScores(sc);
            assertEquals(16, s.getFinalAbilityScores().get(Ability.valueOf(row[1])), row[0]);
            assertEquals(Integer.parseInt(row[2]), CharacterCreationHandler.spellMax(ClassLoader.getClass(row[0]), 1, s), row[0]);
        }
        // A wizard's book holds 6; what they prepare from it is INT + level.
        var wiz = character("gnome", null, "wizard", "sage", scores(Ability.INTELLIGENCE, 16));
        assertEquals(PreparedSpells.max(wiz) + "", PreparedSpells.maxExplained(wiz).replaceAll(".*= ", ""));
        assertTrue(PreparedSpells.maxExplained(wiz).contains("[INT] +1[level]"), PreparedSpells.maxExplained(wiz));
    }

    /** Stout Resilience: advantage on a save against poison damage, and against being Poisoned. */
    @Test
    void aStoutHalflingHasAdvantageAgainstPoison() {
        var stout = character("halfling", "stout", "fighter", "soldier", scores());
        assertTrue(stout.hasSaveAdvantageVs(Set.of("magic", "poison")), "Poison Spray");
        assertTrue(stout.hasSaveAdvantageVs(Set.of("poisoned")), "a save against the condition alone");
        assertFalse(stout.hasSaveAdvantageVs(Set.of("magic", "fire")));
        assertTrue(stout.resistsDamage("poison"));
        assertFalse(character("halfling", "lightfoot", "fighter", "soldier", scores()).hasSaveAdvantageVs(Set.of("poison")));
    }
}
