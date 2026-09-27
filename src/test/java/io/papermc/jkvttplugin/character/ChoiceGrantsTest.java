package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.ChoiceGrants;
import io.papermc.jkvttplugin.data.model.InnateSpell;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Choices that grant things (#222): an option carries what picking it gives, applied from the saved
 * pick. Replaces dragonborn's resistance mapping and the genie's conditional_bonus_spells, and gives
 * genasi a real casting-ability pick.
 */
class ChoiceGrantsTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static ChoiceEntry choice(List<ChoiceEntry> choices, String id) {
        return choices.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no choice " + id));
    }

    private static InnateSpell innate(CharacterSheet c, String id) {
        return c.getInnateSpells().stream().filter(s -> s.getSpellId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no innate " + id));
    }

    /** Picked the way a saved character comes back: the pick is set, then its grants re-derived. */
    private static CharacterSheet pick(CharacterSheet c, String choiceId, String option) {
        c.setCustomChoice(choiceId, option.toLowerCase());
        c.applyChoiceGrants();
        return c;
    }

    @Test
    void dragonbornAncestryGrantsItsResistance() {
        CharacterSheet red = pick(character("dragonborn", null, "fighter", "acolyte", scores()),
                "draconic_ancestry", "Red (Fire, 15 ft. cone, DEX save)");
        assertTrue(red.resistsDamage("fire"));
        assertFalse(red.resistsDamage("cold"));

        CharacterSheet white = pick(character("dragonborn", null, "fighter", "acolyte", scores()),
                "draconic_ancestry", "White (Cold, 15 ft. cone, CON save)");
        assertTrue(white.resistsDamage("cold"));
        assertFalse(white.resistsDamage("fire"));
    }

    /** The genasi's pick sets the ability for every genasi spell, and only for that character. */
    @Test
    void genasiPicksTheirSpellcastingAbility() {
        CharacterSheet cha = pick(character("genasi", "fire", "sorcerer", "acolyte", scores()),
                "genasi_spellcasting", "Charisma");
        assertEquals(Ability.CHARISMA, innate(cha, "produce_flame").getCastingAbility());
        assertEquals(Ability.CHARISMA, innate(cha, "burning_hands").getCastingAbility());

        CharacterSheet other = character("genasi", "fire", "monk", "acolyte", scores());
        assertEquals(Ability.WISDOM, innate(other, "produce_flame").getCastingAbility(),
                "another genasi isn't changed: each character has its own copies");
        assertEquals(Ability.WISDOM, RaceLoader.getRace("genasi").getSubraces().get("fire").getInnateSpells().get(0).getCastingAbility(),
                "nor is the race itself");
    }

    /** Before #222 every tiefling held the same Hellish Rebuke, so one's use spent everyone's. */
    @Test
    void innateSpellUsesArePerCharacter() {
        CharacterSheet a = character("tiefling", null, "rogue", "urchin", scores());
        CharacterSheet b = character("tiefling", null, "rogue", "urchin", scores());
        innate(a, "hellish_rebuke").setUsesRemaining(0);
        assertNotEquals(0, innate(b, "hellish_rebuke").getUsesRemaining());
        assertNotSame(innate(a, "hellish_rebuke"), innate(b, "hellish_rebuke"));
    }

    /** The genie's kind was `type: other`, which the parser dropped, so the player never saw it. */
    @Test
    void genieKindIsAChoice() {
        var genie = ClassLoader.getClass("warlock").getSubclasses().get("the_genie");
        ChoiceEntry kind = choice(genie.getPlayerChoices(), "genie_kind");
        assertEquals(PlayersChoice.ChoiceType.CUSTOM, kind.type());
        assertEquals(List.of("dao", "djinni", "efreeti", "marid"), kind.pc().getOptions());
    }

    // ---------- #228: a patron's spells are options to learn, not free spells ----------

    private static CharacterCreationSession warlock(String patron) {
        CharacterCreationSession s = session("human", null, "warlock", "acolyte");
        s.setSelectedSubclass(patron);
        io.papermc.jkvttplugin.character.CharacterCreationService.rebuildPendingChoices(s.getPlayerId());
        return s;
    }

    private static boolean pickable(CharacterCreationSession s, String spellId) {
        return s.pickableSpells().stream().anyMatch(sp -> sp.getId().equals(spellId));
    }

    /** A Fiend may learn Burning Hands (not a warlock spell otherwise), but doesn't know it for free. */
    @Test
    void fiendSpellsArePickableNotKnown() {
        assertFalse(pickable(session("human", null, "warlock", "acolyte"), "burning_hands"), "not on the warlock list");
        assertTrue(pickable(warlock("the_fiend"), "burning_hands"), "on the Fiend's expanded list");
        assertTrue(pickable(warlock("the_fiend"), "eldritch_blast"), "the class list is still there");

        CharacterSheet fiend = CharacterSheet.loadFromData(UUID.randomUUID(), UUID.randomUUID(), "Test", "human", null,
                "warlock", "the_fiend", "acolyte", scores(), new HashSet<>(), Set.of(), Set.of(), 10, 10, 10);
        assertFalse(fiend.getKnownSpells().contains(SpellLoader.getSpell("burning_hands")), "not known unless picked");
    }

    /** The genie's kind adds that kind's spells to the pick list, and only that kind's. */
    @Test
    void genieKindAddsItsSpellsToThePickList() {
        CharacterCreationSession s = warlock("the_genie");
        assertFalse(pickable(s, "burning_hands"), "no kind picked yet");
        assertTrue(s.toggleChoiceByKey("genie_kind", "efreeti"));
        assertTrue(pickable(s, "burning_hands"), "an efreeti's spell");
        assertFalse(pickable(s, "thunderwave"), "not a djinni's");
    }

    /** Switching patron takes the old patron's spell picks with it, instead of leaving a pick with no tile. */
    @Test
    void switchingPatronDropsItsSpellPicks() {
        CharacterCreationSession s = warlock("the_fiend");
        s.selectSpell("burning_hands", 1, 2);
        s.selectSpell("hex", 1, 2); // a warlock spell: stays
        assertTrue(s.dropUnpickableSpells().isEmpty(), "both are on a Fiend's list");

        s.setSelectedSubclass("the_archfey");
        assertEquals(List.of("Burning Hands"), s.dropUnpickableSpells());
        assertFalse(s.hasSpell("burning_hands"));
        assertTrue(s.hasSpell("hex"));
        assertEquals(1, s.getSpellCount(1));
    }

    /** A cleric's domain spells are the other rule: known for free (bonus_spells stays that). */
    @Test
    void domainSpellsAreStillFree() {
        var cleric = ClassLoader.getClass("cleric");
        var domain = cleric.getSubclasses().values().stream().filter(d -> !d.getBonusSpells().isEmpty()).findFirst().orElseThrow();
        String spellId = domain.getBonusSpells().stream().filter(id -> SpellLoader.getSpell(id) != null).findFirst().orElseThrow();
        CharacterSheet c = CharacterSheet.loadFromData(UUID.randomUUID(), UUID.randomUUID(), "Test", "human", null,
                "cleric", domain.getId(), "acolyte", scores(), new HashSet<>(), Set.of(), Set.of(), 10, 10, 10);
        var spell = SpellLoader.getSpell(spellId);
        assertTrue(c.getKnownSpells().contains(spell) || c.getKnownCantrips().contains(spell), domain.getId() + " knows " + spellId);
    }

    @Test
    void optionsParseWithOrWithoutGrants() {
        ChoiceEntry ancestry = choice(RaceLoader.getRace("dragonborn").getPlayerChoices(), "draconic_ancestry");
        assertEquals(10, ancestry.pc().getOptions().size());
        ChoiceGrants red = ancestry.pc().grantsFor("RED (Fire, 15 ft. cone, DEX save)");
        assertNotNull(red, "matched regardless of case");
        assertEquals(List.of("fire"), red.damageResistances());

        ChoiceEntry size = choice(RaceLoader.getRace("genasi").getPlayerChoices(), "race_size");
        assertNull(size.pc().grantsFor("small"), "a plain option grants nothing");
    }

    @Test
    void unknownGrantsAreReported() {
        ChoiceGrants g = ChoiceGrants.parse(Map.of("damage_resistance", List.of("fire"), "innate_casting_ability", "wis"));
        assertEquals(2, g.problems().size(), g.problems().toString());
    }
}
