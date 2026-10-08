package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #269: "the cast went through" is one step, shared by a fight, the out-of-combat path and a ritual:
 * the cost, the readied spell, concentration, and a mark. Who is told, what a turn pays, and the DM's
 * approval stay with the callers and need a server (TEST_PLAN).
 */
class CastCompletionTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndSpell spell(String id) {
        DndSpell s = SpellLoader.getSpell(id);
        assertNotNull(s, id);
        return s;
    }

    private static CharacterSheet warlock() {
        CharacterSheet s = character("human", null, "warlock", "sage", scores(Ability.CHARISMA, 16));
        s.setSavable(false);
        assertEquals(1, s.getSpellSlotsRemaining(1), "a level-1 warlock has one slot");
        return s;
    }

    // ---------- the cost: once ----------

    @Test
    void aSlotSpellSpendsItsSlotExactlyOnce() {
        CharacterSheet w = warlock();
        DndSpell hex = spell("hex");
        SpellCost cost = SpellCost.of(w, hex);
        assertEquals(SpellCost.Kind.SLOT, cost.kind());

        CastCompletion.Done done = CastCompletion.finish(UUID.randomUUID(), w, hex, cost, null);
        assertEquals(0, w.getSpellSlotsRemaining(1), "one slot, once");
        assertEquals("level 1 slot (0 left)", done.spent());
    }

    @Test
    void aCantripAndARitualSpendNothing() {
        CharacterSheet w = warlock();
        CastCompletion.Done cantrip = CastCompletion.finish(UUID.randomUUID(), w, spell("fire_bolt"), SpellCost.of(w, spell("fire_bolt")), null);
        assertEquals("", cantrip.spent());
        CastCompletion.Done ritual = CastCompletion.finish(UUID.randomUUID(), w, spell("detect_magic"), null, null);
        assertEquals("", ritual.spent(), "a ritual has no cost to spend");
        assertEquals(1, w.getSpellSlotsRemaining(1), "neither touched a slot");
    }

    /** Nothing is completed by merely working out the cost: that's what a prompt, a refusal and a denied cast do. */
    @Test
    void workingOutTheCostCompletesNothing() {
        CharacterSheet w = warlock();
        UUID player = UUID.randomUUID();
        SpellTargeting.readyQuietly(player, spell("hex"));
        w.setConcentratingOn(spell("bless"));

        SpellCost cost = SpellCost.of(w, spell("hex")); // every path does this before it can refuse
        assertTrue(cost.available());

        assertEquals(1, w.getSpellSlotsRemaining(1), "no slot spent");
        assertTrue(SpellTargeting.isReadied(player), "still readied");
        assertSame(spell("bless"), w.getConcentratingOn(), "still concentrating on the old spell");
        assertNull(w.getMarkTargetId(), "nobody marked");
        SpellTargeting.clear(player);
    }

    // ---------- the readied spell ----------

    @Test
    void aCompletedCastClearsTheReadiedSpell() {
        CharacterSheet w = warlock();
        UUID player = UUID.randomUUID(), someoneElse = UUID.randomUUID();
        SpellTargeting.readyQuietly(player, spell("fire_bolt"));
        SpellTargeting.readyQuietly(someoneElse, spell("fire_bolt"));

        CastCompletion.finish(player, w, spell("fire_bolt"), SpellCost.of(w, spell("fire_bolt")), null);
        assertFalse(SpellTargeting.isReadied(player), "out of a fight it used to stay readied");
        assertTrue(SpellTargeting.isReadied(someoneElse), "only the caster's");
        SpellTargeting.clear(someoneElse);
    }

    // ---------- concentration ----------

    @Test
    void aNewConcentrationSpellReplacesTheOldAndSaysWhich() {
        CharacterSheet w = warlock();
        w.setConcentratingOn(spell("bless"));
        CastCompletion.Done done = CastCompletion.finish(null, w, spell("detect_magic"), SpellCost.of(w, spell("detect_magic")), null);
        assertSame(spell("bless"), done.concentrationEnded(), "the caller is told what ended, to announce it");
        assertTrue(done.concentrating());
        assertSame(spell("detect_magic"), w.getConcentratingOn());
    }

    @Test
    void theSameSpellAgainOrAFirstOneEndsNothing() {
        CharacterSheet w = warlock();
        CastCompletion.Done first = CastCompletion.finish(null, w, spell("bless"), null, null);
        assertNull(first.concentrationEnded(), "nothing to replace");
        assertTrue(first.concentrating());
        CastCompletion.Done again = CastCompletion.finish(null, w, spell("bless"), null, null);
        assertNull(again.concentrationEnded(), "the same spell cast again isn't 'ending' it");
    }

    @Test
    void aSpellWithoutConcentrationLeavesTheirConcentrationAlone() {
        CharacterSheet w = warlock();
        w.setConcentratingOn(spell("bless"));
        CastCompletion.Done done = CastCompletion.finish(null, w, spell("fire_bolt"), SpellCost.of(w, spell("fire_bolt")), null);
        assertNull(done.concentrationEnded());
        assertFalse(done.concentrating());
        assertSame(spell("bless"), w.getConcentratingOn());
    }

    /** A ritual is a cast like any other for concentration: it used to replace it without anyone being told what ended. */
    @Test
    void aRitualReplacesConcentrationAndReportsIt() {
        CharacterSheet w = warlock();
        w.setConcentratingOn(spell("bless"));
        CastCompletion.Done done = CastCompletion.finish(UUID.randomUUID(), w, spell("detect_magic"), null, null);
        assertSame(spell("bless"), done.concentrationEnded());
        assertSame(spell("detect_magic"), w.getConcentratingOn());
        assertEquals(1, w.getSpellSlotsRemaining(1), "and still no slot");
    }

    // ---------- marks ----------

    @Test
    void aMarkSpellMarksItsTargetWithItsDamageAndAbility() {
        CharacterSheet w = warlock();
        UUID goblin = UUID.randomUUID();
        DndSpell hex = spell("hex");
        CastCompletion.Mark mark = CastCompletion.Mark.of(hex, goblin, Ability.STRENGTH);
        assertEquals(new CastCompletion.Mark(goblin, "1d6", "necrotic", "strength"), mark);

        CastCompletion.finish(UUID.randomUUID(), w, hex, SpellCost.of(w, hex), mark);
        assertEquals("1d6", w.markRiderAgainst(goblin), "the +1d6 on later hits");
        assertNull(w.markRiderAgainst(UUID.randomUUID()), "only against the marked creature");
        assertEquals("strength", w.getMarkCheckDisadvantageAbility());
        assertSame(hex, w.getConcentratingOn());
        assertEquals(0, w.getSpellSlotsRemaining(1));
    }

    /** The order matters: replacing concentration clears a mark (#238), so the new mark has to be set after it. */
    @Test
    void aMarkCastOverAnotherConcentrationSpellKeepsItsMark() {
        CharacterSheet w = warlock();
        UUID goblin = UUID.randomUUID();
        w.setConcentratingOn(spell("bless"));
        CastCompletion.Done done = CastCompletion.finish(null, w, spell("hex"), SpellCost.of(w, spell("hex")),
                CastCompletion.Mark.of(spell("hex"), goblin, Ability.WISDOM));
        assertSame(spell("bless"), done.concentrationEnded());
        assertEquals("1d6", w.markRiderAgainst(goblin), "the mark survived the swap it was part of");
    }

    @Test
    void theMarkMovesWithARecastAndEndsWithTheConcentration() {
        CharacterSheet w = warlock();
        UUID goblin = UUID.randomUUID(), orc = UUID.randomUUID();
        DndSpell hex = spell("hex");
        CastCompletion.finish(null, w, hex, SpellCost.of(w, hex), CastCompletion.Mark.of(hex, goblin, Ability.STRENGTH));
        assertEquals(0, w.getSpellSlotsRemaining(1));
        w.setSpellSlotsRemaining(1, 1); // a short rest later
        CastCompletion.finish(null, w, hex, SpellCost.of(w, hex), CastCompletion.Mark.of(hex, orc, Ability.DEXTERITY));
        assertNull(w.markRiderAgainst(goblin));
        assertEquals("1d6", w.markRiderAgainst(orc));
        assertEquals(0, w.getSpellSlotsRemaining(1), "two casts, two slots");

        CastCompletion.finish(null, w, spell("bless"), null, null); // another concentration spell
        assertNull(w.markRiderAgainst(orc), "the Hex is over (#238)");
    }

    @Test
    void huntersMarkHasNoAbilityToChoose() {
        DndSpell hm = spell("hunters_mark");
        CastCompletion.Mark mark = CastCompletion.Mark.of(hm, UUID.randomUUID(), null);
        assertEquals("1d6", mark.damage());
        assertNull(mark.disadvantageAbility());
    }

    // ---------- a mark's typed words, out of a fight ----------

    @Test
    void theAbilityIsTheLastWordWhateverTheNameLooksLike() {
        DndSpell hex = spell("hex"), hm = spell("hunters_mark");
        assertEquals(new OutOfCombatAttack.MarkWords("Wolf", Ability.STRENGTH), OutOfCombatAttack.markWords(hex, "Wolf strength"));
        assertEquals(new OutOfCombatAttack.MarkWords("Dire Wolf", Ability.WISDOM), OutOfCombatAttack.markWords(hex, "\"Dire Wolf\" wis"));
        assertEquals(new OutOfCombatAttack.MarkWords("Dire Wolf", Ability.WISDOM), OutOfCombatAttack.markWords(hex, "Dire Wolf wisdom"));
        assertEquals(new OutOfCombatAttack.MarkWords("Wolf", null), OutOfCombatAttack.markWords(hex, "Wolf"), "no ability yet: it's asked for");
        assertEquals(new OutOfCombatAttack.MarkWords(null, Ability.STRENGTH), OutOfCombatAttack.markWords(hex, "strength"), "aimed by looking");
        assertEquals(new OutOfCombatAttack.MarkWords(null, null), OutOfCombatAttack.markWords(hex, null));
        assertEquals(new OutOfCombatAttack.MarkWords("Wolf strength", null), OutOfCombatAttack.markWords(hm, "Wolf strength"),
                "Hunter's Mark takes no ability, so that's all name");
    }

    // ---------- every path completes through it, and only when the cast has happened ----------

    @Test
    void theCostIsSpentInOnePlaceOnly() throws Exception {
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = f.getFileName().toString();
                if (name.equals("CastCompletion.java") || name.equals("SpellCost.java")) continue;
                String src = Files.readString(f);
                assertFalse(src.contains("cost.spend(") || src.contains(".spend(sheet, spell)") || src.contains(".spend(casterSheet, spell)"),
                        name + " spends a spell's cost itself: go through CastCompletion.finish");
            }
        }
    }

    @Test
    void bothPathsAndTheRitualCompleteThroughTheSharedStep() throws Exception {
        String base = "src/main/java/io/papermc/jkvttplugin/";
        String fight = Files.readString(Path.of(base + "combat/CombatCommand.java")).replace("\r\n", "\n");
        String outside = Files.readString(Path.of(base + "combat/OutOfCombatAttack.java")).replace("\r\n", "\n");
        String command = Files.readString(Path.of(base + "commands/CharacterCommand.java")).replace("\r\n", "\n");
        String marks = Files.readString(Path.of(base + "combat/SpellCastHandler.java")).replace("\r\n", "\n");

        assertTrue(fight.contains("CastCompletion.finish(player.getUniqueId(), casterSheet, spell, cost, mark, castId)"),
                "a fight's afterCast, completing the cast it named before resolving anyone");
        assertTrue(fight.contains("final long castId = SpellEffects.newCast();"), "taken before anything resolves");
        assertTrue(outside.contains("CastCompletion.finish(player.getUniqueId(), sheet, spell, cost, mark)"), "the out-of-combat commit");
        // The fight keeps what's its own, after the shared step.
        int shared = fight.indexOf("CastCompletion.finish(");
        assertTrue(fight.indexOf("caster.grantTempAc(", shared) > shared && fight.indexOf("state.useAction()", shared) > shared);

        // Nothing sets concentration or a mark around the shared step any more.
        assertFalse(command.contains("setConcentratingOn("), "the ritual branch goes through commit");
        assertTrue(command.contains("OutOfCombatAttack.commit(player, sheet, spell, null);"), "with no cost");
        assertFalse(command.contains("SpellTargeting.clear("), "and no path clears the readied spell by hand");
        assertFalse(marks.contains("setSpellMark(") || marks.contains("setConcentratingOn("), "a fight's castMark only says whom to mark");
        assertFalse(outside.contains("setSpellMark(") || outside.contains("breakConcentration("));

        // Out of a fight a mark spell takes the hostile road: reach, the DM's approval, and only then the cast.
        assertTrue(command.contains("if (spell.isMarkSpell()) {"), "marks are routed before the 'announce it' fall-through");
        int castMark = outside.indexOf("public static boolean castMark(Player player, CharacterSheet sheet, DndSpell spell, Integer castLevel,");
        int end = outside.indexOf("\n    }\n", castMark);
        String body = outside.substring(castMark, end);
        int approval = body.indexOf("if (!permitted(player, spell, target.getId())) {");
        int commit = body.indexOf("commit(player, sheet, spell, cost, CastCompletion.Mark.of(");
        assertTrue(approval > 0 && commit > approval, "nothing is spent or marked until the DM has let it happen");
        assertTrue(body.indexOf("askDm(") > approval && body.indexOf("askDm(") < commit);
        assertEquals(1, body.split("commit\\(player", -1).length - 1, "and it completes once");
        assertTrue(body.indexOf("inRange(") < approval, "reach is checked before the DM is bothered");
    }
}
