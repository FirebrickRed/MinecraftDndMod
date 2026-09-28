package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.combat.FeatureUse;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prepared spells (#218): a wizard's spellbook vs the day's spells, a cleric's class list, rituals, Arcane Recovery. */
class PreparedSpellsTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static List<DndSpell> firstLevel(String list, boolean ritual) {
        return SpellLoader.getSpellsForClass(list).stream()
                .filter(s -> s.getLevel() == 1 && s.isRitual() == ritual)
                .sorted(Comparator.comparing(DndSpell::getId)).toList();
    }

    private static CharacterSheet caster(String cls, String subclass, Ability ability, List<DndSpell> known) {
        Set<String> names = new HashSet<>();
        for (DndSpell s : known) names.add(Util.normalize(s.getName()));
        return CharacterSheet.loadFromData(UUID.randomUUID(), UUID.randomUUID(), "Test", "human", null, cls, subclass,
                "sage", scores(ability, 16), new HashSet<>(), names, Set.of(), 10, 10, 10);
    }

    /** A wizard with 6 in the book (one a ritual) and INT 16: 4 prepared a day. */
    private static CharacterSheet wizard() {
        List<DndSpell> book = new ArrayList<>(firstLevel("wizard", false).subList(0, 5));
        book.add(firstLevel("wizard", true).get(0));
        return caster("wizard", null, Ability.INTELLIGENCE, book);
    }

    @Test
    void aWizardPreparesFromTheSpellbook() {
        CharacterSheet w = wizard();
        assertEquals(PreparedSpells.Kind.SPELLBOOK, PreparedSpells.kind(w));
        assertEquals(4, PreparedSpells.max(w), "INT +3, level 1");
        assertEquals(6, PreparedSpells.candidates(w).size(), "the whole book");
        DndSpell plain = PreparedSpells.candidates(w).stream().filter(s -> !s.isRitual()).findFirst().orElseThrow();
        assertTrue(PreparedSpells.castRefusal(w, plain, false).contains("haven't prepared"), "a new wizard, nothing prepared");

        w.openLongRestWindow();
        List<DndSpell> plains = PreparedSpells.candidates(w).stream().filter(s -> !s.isRitual()).toList();
        for (int i = 0; i < 4; i++) assertNull(PreparedSpells.toggle(w, plains.get(i)));
        assertNotNull(PreparedSpells.toggle(w, plains.get(4)), "a fifth is one too many");
        assertNull(PreparedSpells.castRefusal(w, plains.get(0), false));
        assertTrue(PreparedSpells.castRefusal(w, plains.get(4), false).contains("not prepared"));
    }

    /** A wizard may cast a spellbook ritual without preparing it; not as a normal cast. */
    @Test
    void aWizardRitualNeedsNoPreparation() {
        CharacterSheet w = wizard();
        w.setPreparedSpellIds(new LinkedHashSet<>());
        DndSpell ritual = PreparedSpells.candidates(w).stream().filter(DndSpell::isRitual).findFirst().orElseThrow();
        assertNull(PreparedSpells.castRefusal(w, ritual, true));
        String normal = PreparedSpells.castRefusal(w, ritual, false);
        assertTrue(normal.contains("cast it as one"), normal);
    }

    @Test
    void theWindowIsTheLongRest() {
        CharacterSheet w = wizard();
        w.setPreparedSpellIds(new LinkedHashSet<>());
        assertFalse(PreparedSpells.canChangeNow(w));
        w.longRest();
        assertTrue(PreparedSpells.canChangeNow(w));
        w.shortRest();
        assertFalse(PreparedSpells.canChangeNow(w), "the next rest closes it");
        w.longRest();
        w.closeShortRest(); // joining a fight
        assertFalse(PreparedSpells.canChangeNow(w));
    }

    /** A cleric prepares from the whole list; their prepared spells are their known ones. */
    @Test
    void aClericPreparesFromTheClassList() {
        List<DndSpell> list = firstLevel("cleric", false);
        CharacterSheet c = caster("cleric", null, Ability.WISDOM, list.subList(0, 3));
        assertEquals(PreparedSpells.Kind.CLASS_LIST, PreparedSpells.kind(c));
        assertEquals(4, PreparedSpells.max(c), "WIS +3, level 1");
        DndSpell other = list.get(3);
        assertTrue(PreparedSpells.castRefusal(c, other, false).contains("isn't prepared"));
        c.openLongRestWindow();
        assertNull(PreparedSpells.toggle(c, other));
        assertNull(PreparedSpells.castRefusal(c, other, false));
        assertTrue(c.knowsSpell(other));
        assertNull(PreparedSpells.toggle(c, other));
        assertFalse(c.knowsSpell(other), "unprepared again");
    }

    /** A cleric can only ritual-cast what's prepared (PHB p.201). */
    @Test
    void aClericRitualMustBePrepared() {
        List<DndSpell> rituals = firstLevel("cleric", true);
        if (rituals.isEmpty()) return; // no 1st-level cleric ritual in the content yet
        CharacterSheet c = caster("cleric", null, Ability.WISDOM, firstLevel("cleric", false).subList(0, 1));
        assertNotNull(PreparedSpells.castRefusal(c, rituals.get(0), true));
    }

    /** Domain spells are always prepared and don't count or appear as choices. */
    @Test
    void domainSpellsAreAlwaysPrepared() {
        CharacterSheet c = caster("cleric", "life_domain", Ability.WISDOM, List.of());
        DndSpell bless = SpellLoader.getSpell("bless");
        assertTrue(PreparedSpells.isPrepared(c, bless));
        assertFalse(PreparedSpells.candidates(c).contains(bless));
        assertEquals(0, PreparedSpells.prepared(c).size());
    }

    /** A known caster (sorcerer) isn't touched by any of this. */
    @Test
    void knownCastersAreUnchanged() {
        DndSpell s = firstLevel("sorcerer", false).get(0);
        CharacterSheet sorc = caster("sorcerer", "wild_magic", Ability.CHARISMA, List.of(s));
        assertEquals(PreparedSpells.Kind.NONE, PreparedSpells.kind(sorc));
        assertNull(PreparedSpells.castRefusal(sorc, s, false));
    }

    /** Arcane Recovery: half the level rounded up, the highest spent slots that fit. */
    @Test
    void arcaneRecoveryPicksWhatFits() {
        CharacterSheet w = wizard();
        assertEquals(1, FeatureUse.recoveryBudget(w));
        assertFalse(FeatureUse.hasSpentSlots(w, 5), "nothing spent yet");
        w.consumeSpellSlot(1);
        assertTrue(FeatureUse.hasSpentSlots(w, 5));
        assertNotNull(w.getFeature("arcane_recovery"));
        assertNotNull(w.getResource("arcane_recovery"));
    }
}
