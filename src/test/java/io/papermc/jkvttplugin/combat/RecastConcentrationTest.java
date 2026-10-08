package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #269, the same spell cast again: Bless on one group, then Bless on another. The first cast ends on
 * everyone it was on, the second stays, and the slot is spent once per cast.
 *
 * <p>The shared completion step replaced concentration through {@code setConcentratingOn}, which does
 * nothing when the spell is unchanged, so out of a fight the first group stayed blessed (the old
 * out-of-combat code broke concentration first). In a fight it had always been so: there the new
 * effects go on before the cast completes, so the completion has to tell them from the old ones.
 * It does by the cast's identity ({@code SpellEffects.newCast}), which every effect of a cast carries.
 *
 * <p>The recipients are real sheets and creatures the game can find, as a concentration ending looks
 * through everyone. The two orders below are the two the code has: out of a fight the cast completes
 * and then its effects go on; in a fight they go on first.
 */
class RecastConcentrationTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private final List<CharacterSheet> registered = new ArrayList<>();
    private final List<DndEntityInstance> spawned = new ArrayList<>();

    @AfterEach
    void unregister() {
        for (CharacterSheet s : registered) CharacterPersistenceLoader.removeCharacter(s.getPlayerId(), s.getCharacterId());
        spawned.forEach(DndEntityInstance::unregister);
    }

    /** A character the game can find, never saved to disk. */
    private CharacterSheet live(String cls) {
        CharacterSheet s = character("human", null, cls, "acolyte", scores(Ability.WISDOM, 16));
        CharacterPersistenceLoader.storeCharacterInMemory(s);
        s.setSavable(false);
        registered.add(s);
        return s;
    }

    private DndEntityInstance creature(String name) {
        DndEntityInstance c = new DndEntityInstance(EntityLoader.getEntity("balin_blacksmith"), null, name, 10);
        spawned.add(c);
        return c;
    }

    private static DndSpell bless() { return SpellLoader.getSpell("bless"); }

    /** Put cast {@code castId} of the spell, by {@code caster}, on a character, as casting it on them does. */
    private static void on(CharacterSheet target, CharacterSheet caster, DndSpell spell, long castId) {
        target.addEffect(SpellEffects.castEffect(caster.getCharacterId(), spell, castId));
    }

    /** ...and on a creature (the same replace-then-add SpellEffects.give does for one). */
    private static void on(DndEntityInstance target, CharacterSheet caster, DndSpell spell, long castId) {
        var e = SpellEffects.castEffect(caster.getCharacterId(), spell, castId);
        target.getEffects().removeIf(x -> x.getSourceId().equalsIgnoreCase(e.getSourceId()));
        target.getEffects().add(e);
    }

    /** A whole cast out of a fight: it completes (slot, concentration), and hands back its identity for its effects. */
    private static long castOutOfAFight(CharacterSheet caster, DndSpell spell) {
        return CastCompletion.finish(UUID.randomUUID(), caster, spell, SpellCost.of(caster, spell), null).castId();
    }

    private static boolean blessed(CharacterSheet s) { return s.hasEffect("spell:bless"); }
    private static boolean blessed(DndEntityInstance c) { return c.getEffects().stream().anyMatch(e -> e.getSourceId().equalsIgnoreCase("spell:bless")); }
    private static UUID blessedBy(CharacterSheet s) {
        return s.getActiveEffects().stream().filter(e -> e.getSourceId().equalsIgnoreCase("spell:bless")).findFirst().orElseThrow().getCasterId();
    }

    // ---------- out of a fight: the cast completes, then its effects go on ----------

    @Test
    void outOfAFightARecastEndsTheFirstGroupAndKeepsTheSecond() {
        CharacterSheet cleric = live("cleric"), a = live("fighter"), b = live("fighter"), c = live("fighter"), d = live("fighter");
        DndSpell bless = bless();
        int slots = cleric.getSpellSlotsRemaining(1);
        assertTrue(slots >= 2, "a level-1 cleric has two 1st-level slots");

        long first = castOutOfAFight(cleric, bless);
        on(a, cleric, bless, first);
        on(b, cleric, bless, first);
        assertTrue(blessed(a) && blessed(b));
        assertEquals(slots - 1, cleric.getSpellSlotsRemaining(1));

        CastCompletion.Done done = CastCompletion.finish(UUID.randomUUID(), cleric, bless, SpellCost.of(cleric, bless), null);
        long second = done.castId();
        on(c, cleric, bless, second);
        on(d, cleric, bless, second);

        assertNotEquals(first, second, "two casts of one spell are two casts");
        assertFalse(blessed(a), "the first cast ended on its targets");
        assertFalse(blessed(b));
        assertTrue(blessed(c) && blessed(d), "the second cast's targets keep theirs");
        assertEquals(slots - 2, cleric.getSpellSlotsRemaining(1), "the recast cost one slot, once");
        assertNull(done.concentrationEnded(), "it's the same spell: nothing 'ended' for the caster");
        assertTrue(done.concentrating());
        assertSame(bless, cleric.getConcentratingOn());
        assertEquals(second, cleric.getConcentrationCastId(), "and their concentration is the second cast's");
    }

    // ---------- in a fight: the effects go on as each target resolves, then the cast completes ----------

    @Test
    void inAFightARecastEndsTheFirstGroupAndKeepsTheEffectsJustApplied() {
        CharacterSheet cleric = live("cleric"), a = live("fighter"), b = live("fighter"), c = live("fighter");
        DndSpell bless = bless();
        int slots = cleric.getSpellSlotsRemaining(1);

        long first = SpellEffects.newCast();   // handleCast takes this before resolving anyone
        on(a, cleric, bless, first);
        on(b, cleric, bless, first);
        CastCompletion.finish(UUID.randomUUID(), cleric, bless, SpellCost.of(cleric, bless), null, first);
        assertTrue(blessed(a) && blessed(b), "the first cast's own effects survive its own completion");

        long second = SpellEffects.newCast();
        on(b, cleric, bless, second); // b is in both casts: the new one replaces the old on them
        on(c, cleric, bless, second);
        CastCompletion.finish(UUID.randomUUID(), cleric, bless, SpellCost.of(cleric, bless), null, second);

        assertFalse(blessed(a), "only in the first cast: it ended");
        assertTrue(blessed(b), "in both: they hold the new cast's");
        assertTrue(blessed(c), "only in the second: untouched by the cleanup");
        assertEquals(slots - 2, cleric.getSpellSlotsRemaining(1), "two casts, one slot each");
    }

    /** What simply clearing every effect of the spell on a recast would have done in a fight: erased the new cast too. */
    @Test
    void theCompletionDoesNotEraseTheCastItIsCompleting() {
        CharacterSheet cleric = live("cleric"), a = live("fighter");
        DndSpell bless = bless();
        cleric.setConcentratingOn(bless); // already concentrating on an earlier Bless, whose targets are long gone

        long cast = SpellEffects.newCast();
        on(a, cleric, bless, cast);
        CastCompletion.finish(null, cleric, bless, SpellCost.of(cleric, bless), null, cast);
        assertTrue(blessed(a));
    }

    @Test
    void creaturesAreCleanedUpTheSameWay() {
        CharacterSheet cleric = live("cleric");
        DndEntityInstance wolf = creature("Wolf"), bear = creature("Bear");
        DndSpell bless = bless();

        on(wolf, cleric, bless, castOutOfAFight(cleric, bless));
        on(bear, cleric, bless, castOutOfAFight(cleric, bless));
        assertFalse(blessed(wolf));
        assertTrue(blessed(bear));
    }

    // ---------- other casters ----------

    @Test
    void anotherCastersBlessIsLeftAlone() {
        CharacterSheet cleric = live("cleric"), other = live("cleric"), a = live("fighter"), theirs = live("fighter"), c = live("fighter");
        DndSpell bless = bless();

        on(theirs, other, bless, castOutOfAFight(other, bless));
        on(a, cleric, bless, castOutOfAFight(cleric, bless));
        on(c, cleric, bless, castOutOfAFight(cleric, bless)); // the recast

        assertFalse(blessed(a), "this cleric's first cast ended");
        assertTrue(blessed(theirs), "the other cleric's Bless didn't");
        assertEquals(other.getCharacterId(), blessedBy(theirs));
        assertSame(bless, other.getConcentratingOn(), "nor did their concentration");
    }

    // ---------- a different spell still replaces as before ----------

    @Test
    void aDifferentConcentrationSpellEndsTheOldOneAndKeepsItsOwnEffects() {
        CharacterSheet cleric = live("cleric"), a = live("fighter"), b = live("fighter");
        DndSpell bless = bless(), faith = SpellLoader.getSpell("shield_of_faith");

        on(a, cleric, bless, castOutOfAFight(cleric, bless));

        long cast = SpellEffects.newCast(); // the fight's order: Shield of Faith goes on b first
        on(b, cleric, faith, cast);
        CastCompletion.Done done = CastCompletion.finish(null, cleric, faith, SpellCost.of(cleric, faith), null, cast);

        assertSame(bless, done.concentrationEnded(), "the caller is told Bless ended");
        assertFalse(blessed(a));
        assertTrue(b.hasEffect("spell:shield_of_faith"), "the new spell's effect isn't caught by the old one's cleanup");
    }

    // ---------- marks: a recast moves the mark, and establishes the new one ----------

    @Test
    void recastingHexMovesTheMarkAndLeavesTheNewOneStanding() {
        CharacterSheet warlock = character("human", null, "warlock", "sage", scores(Ability.CHARISMA, 16));
        warlock.setSavable(false);
        DndSpell hex = SpellLoader.getSpell("hex");
        UUID goblin = UUID.randomUUID(), orc = UUID.randomUUID();

        CastCompletion.finish(null, warlock, hex, SpellCost.of(warlock, hex), CastCompletion.Mark.of(hex, goblin, Ability.STRENGTH));
        assertEquals("1d6", warlock.markRiderAgainst(goblin));

        warlock.setSpellSlotsRemaining(1, 1);
        CastCompletion.Done again = CastCompletion.finish(null, warlock, hex, SpellCost.of(warlock, hex),
                CastCompletion.Mark.of(hex, orc, Ability.WISDOM), SpellEffects.newCast());

        assertNull(warlock.markRiderAgainst(goblin), "the old mark is gone");
        assertEquals("1d6", warlock.markRiderAgainst(orc), "the new one is established, not wiped with the old");
        assertEquals("wisdom", warlock.getMarkCheckDisadvantageAbility());
        assertNull(again.concentrationEnded());
        assertEquals(0, warlock.getSpellSlotsRemaining(1), "one slot for the recast");
    }

    /** A caster's Hex and their Bless are different spells: recasting one doesn't reach the other caster's effects of either. */
    @Test
    void recastingAMarkDoesNotTouchAnyonesEffects() {
        CharacterSheet warlock = live("warlock"), cleric = live("cleric"), a = live("fighter");
        DndSpell hex = SpellLoader.getSpell("hex"), bless = bless();
        on(a, cleric, bless, castOutOfAFight(cleric, bless));

        CastCompletion.finish(null, warlock, hex, SpellCost.of(warlock, hex), CastCompletion.Mark.of(hex, UUID.randomUUID(), null));
        warlock.setSpellSlotsRemaining(1, 1);
        CastCompletion.finish(null, warlock, hex, SpellCost.of(warlock, hex), CastCompletion.Mark.of(hex, UUID.randomUUID(), null));
        assertTrue(blessed(a), "the cleric's Bless is nothing to do with the warlock's Hex");
    }
}
