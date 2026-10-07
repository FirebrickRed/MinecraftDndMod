package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.Advantage;
import io.papermc.jkvttplugin.combat.SaveOutcome;
import io.papermc.jkvttplugin.combat.SpellSave;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.dm.CheckManager;
import io.papermc.jkvttplugin.dm.TrapCommand;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #266: what a save is against (magic, a damage type, a condition) travels from the spell or trap that
 * calls for it to the prompt and the roll, out of a fight as in one, and a conditional advantage applies
 * only when its tag and its abilities match.
 */
class SaveContextTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static CharacterSheet of(String race, String subrace) {
        CharacterSheet s = character(race, subrace, "fighter", "soldier", scores());
        s.setSavable(false);
        return s;
    }

    /** A save called on {@code sheet}'s player that says what it's against, as CheckCommand registers it. */
    private static void called(CharacterSheet sheet, Set<String> tags) {
        CheckManager.register(sheet.getPlayerId(), UUID.randomUUID(), 13, "save", Advantage.NONE, tags);
    }

    private static RollMode prompt(CharacterSheet sheet, Ability save) {
        return RollOptionsMenuHandler.withPenalties(sheet, "SAVE", save.name(), RollMode.NORMAL);
    }

    // ---------- what a save is against ----------

    @Test
    void aSpellsTagsAreMagicItsDamageTypeAndItsCondition() {
        assertEquals(Set.of("magic", "poison"), SpellSave.tagsFor(SpellLoader.getSpell("poison_spray")));
        assertEquals(Set.of("magic", "frightened"), SpellSave.tagsFor(SpellLoader.getSpell("cause_fear")));
        assertEquals(Set.of("magic", "charmed"), SpellSave.tagsFor(SpellLoader.getSpell("charm_person")));
        assertEquals(Set.of("magic"), SpellSave.tagsFor(SpellLoader.getSpell("bane")), "no damage, no condition: just magic");
    }

    @Test
    void aTrapIsOnlyMagicalWhenItSaysSo() {
        List<String> problems = new ArrayList<>();
        assertEquals(Set.of("poison"), TrapCommand.parse("constitution dc 13 1d12 type poison".split(" "), problems).saveTags());
        assertEquals(Set.of(), TrapCommand.parse("dexterity dc 13 2d10".split(" "), problems).saveTags(), "a pit is against nothing in particular");
        assertEquals(Set.of("fire", "magic"), TrapCommand.parse("dexterity dc 13 2d10 type fire magic".split(" "), problems).saveTags(), "a glyph");
        assertEquals(Set.of("magic"), TrapCommand.parse("wisdom dc 13 2d6 magical name Rune".split(" "), problems).saveTags());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    // ---------- a conditional advantage needs its tag AND its ability ----------

    @Test
    void theTagHasToMatch() {
        CharacterSheet stout = of("halfling", "stout");
        assertEquals("against poison", stout.saveAdvantageSourceVs(Ability.CONSTITUTION, Set.of("magic", "poison")));
        assertEquals("against poison", stout.saveAdvantageSourceVs(Ability.CONSTITUTION, Set.of("poisoned")), "the condition counts");
        assertEquals("against frightened", stout.saveAdvantageSourceVs(Ability.WISDOM, Set.of("magic", "frightened")), "Brave, from the race");
        assertNull(stout.saveAdvantageSourceVs(Ability.DEXTERITY, Set.of("magic", "fire")));
        assertNull(stout.saveAdvantageSourceVs(Ability.CONSTITUTION, Set.of()), "a save that says nothing gets nothing");
        assertNull(of("halfling", "lightfoot").saveAdvantageSourceVs(Ability.CONSTITUTION, Set.of("poison")));
    }

    /** Gnome Cunning: Intelligence, Wisdom and Charisma saves against magic, and no others. */
    @Test
    void theAbilityHasToMatchWhenOneIsListed() {
        CharacterSheet gnome = of("gnome", null);
        for (Ability a : List.of(Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA)) {
            assertTrue(gnome.hasSaveAdvantageVs(a, Set.of("magic")), a.name());
        }
        for (Ability a : List.of(Ability.STRENGTH, Ability.DEXTERITY, Ability.CONSTITUTION)) {
            assertFalse(gnome.hasSaveAdvantageVs(a, Set.of("magic", "fire")), a.name() + ": it used to count for every save");
        }
        assertFalse(gnome.hasSaveAdvantageVs(Ability.WISDOM, Set.of("poison")), "the right ability, but not magic");
        assertTrue(of("dwarf", "hill_dwarf").hasSaveAdvantageVs(Ability.STRENGTH, Set.of("poison")), "no abilities listed: any save");
    }

    // ---------- the called save, out of a fight ----------

    @Test
    void theCalledSaveGetsTheAdvantageItsTagsEarn() {
        CharacterSheet stout = of("halfling", "stout");
        called(stout, Set.of("magic", "poison"));
        assertEquals(RollMode.ADVANTAGE, prompt(stout, Ability.CONSTITUTION), "Poison Spray, out of a fight");
        CheckManager.takePending(stout.getPlayerId());
        assertEquals(RollMode.NORMAL, prompt(stout, Ability.CONSTITUTION), "nothing called: an ordinary save from the sheet");

        called(stout, Set.of("magic", "fire"));
        assertEquals(RollMode.NORMAL, prompt(stout, Ability.DEXTERITY), "Burning Hands isn't poison");
        CheckManager.takePending(stout.getPlayerId());

        called(stout, Set.of());
        assertEquals(RollMode.NORMAL, prompt(stout, Ability.CONSTITUTION), "a plain /dm check save knows nothing");
        CheckManager.takePending(stout.getPlayerId());
    }

    @Test
    void aGnomeAgainstAMagicalTrapButNotAPit() {
        CharacterSheet gnome = of("gnome", null);
        List<String> problems = new ArrayList<>();
        called(gnome, TrapCommand.parse("wisdom dc 13 2d6 type psychic magic".split(" "), problems).saveTags());
        assertEquals(RollMode.ADVANTAGE, prompt(gnome, Ability.WISDOM));
        CheckManager.takePending(gnome.getPlayerId());

        called(gnome, TrapCommand.parse("wisdom dc 13 2d6 type psychic".split(" "), problems).saveTags());
        assertEquals(RollMode.NORMAL, prompt(gnome, Ability.WISDOM), "not every trap is magic");
        CheckManager.takePending(gnome.getPlayerId());

        called(gnome, TrapCommand.parse("dexterity dc 13 2d10 type fire magic".split(" "), problems).saveTags());
        assertEquals(RollMode.NORMAL, prompt(gnome, Ability.DEXTERITY), "magic, but a Dexterity save");
        CheckManager.takePending(gnome.getPlayerId());
    }

    @Test
    void advantageAndDisadvantageCancel() {
        CharacterSheet stout = of("halfling", "stout");
        stout.addCondition("restrained"); // disadvantage on Dexterity saves
        called(stout, Set.of("poison"));
        assertEquals(RollMode.NORMAL, prompt(stout, Ability.DEXTERITY), "Restrained and Stout Resilience: a straight roll");
        assertEquals(RollMode.ADVANTAGE, prompt(stout, Ability.CONSTITUTION), "Restrained doesn't touch a CON save");
        // The DM's own word on top doesn't tip it back: any of each still cancels (PHB p.173).
        assertEquals(RollMode.NORMAL, RollOptionsMenuHandler.withPenalties(stout, "SAVE", "DEXTERITY", RollMode.ADVANTAGE));
        CheckManager.takePending(stout.getPlayerId());

        called(stout, Set.of("magic", "fire"));
        assertEquals(RollMode.DISADVANTAGE, prompt(stout, Ability.DEXTERITY), "no matching tag: only the Restrained");
        CheckManager.takePending(stout.getPlayerId());
    }

    // ---------- carried from the spell or trap to the check ----------

    @Test
    void theWaitingSaveHandsItsTagsToTheCheckThatIsCalledForIt() {
        UUID saver = UUID.randomUUID();
        SaveOutcome.await(saver, 13, Set.of("magic", "poison"), saved -> {});
        assertEquals(Set.of("magic", "poison"), SaveOutcome.tagsFor(saver, 13));
        assertEquals(Set.of(), SaveOutcome.tagsFor(saver, 15), "some other save at another DC isn't this one");
        assertEquals(Set.of(), SaveOutcome.tagsFor(saver, null), "an ungraded check has no DC to match");
        assertEquals(Set.of(), SaveOutcome.tagsFor(UUID.randomUUID(), 13));
        SaveOutcome.graded(saver, 13, true);
        assertEquals(Set.of(), SaveOutcome.tagsFor(saver, 13), "answered: nothing left to carry");
    }

    /** The prompt and the roll ask the same question of the same place, and the spell paths pass their tags. */
    @Test
    void thePromptAndTheRollUseTheSameRule() throws Exception {
        String base = "src/main/java/io/papermc/jkvttplugin/";
        String roller = Files.readString(Path.of(base + "ui/handler/RollOptionsMenuHandler.java"));
        int resolve = roller.indexOf("public static boolean resolvePhysical(");
        assertTrue(roller.indexOf("boonReason(character, type, value)", resolve) > resolve, "the roll asks boonReason");
        int prompt = roller.indexOf("static RollMode withPenalties(");
        assertTrue(roller.indexOf("boonReason(character, type, value)", prompt) > prompt, "so does the prompt");
        assertTrue(roller.contains("character.saveAdvantageSourceVs(abilityOf(type, value), pending.saveTags())"),
                "and boonReason asks the sheet's own rule, the one a fight uses");

        String check = Files.readString(Path.of(base + "commands/CheckCommand.java"));
        assertTrue(check.contains("SaveOutcome.tagsFor(target.getUniqueId(), dc)"), "the called save picks up the waiting spell's or trap's tags");
        String outside = Files.readString(Path.of(base + "combat/OutOfCombatAttack.java"));
        assertTrue(outside.contains("SpellSave.tagsFor(spell)") && outside.contains("SaveOutcome.await(saver, dc, facts.saveTags(), outcome)"));
        String fight = Files.readString(Path.of(base + "combat/SpellCastHandler.java"));
        assertTrue(fight.contains("SpellSave.tagsFor(spell)") && !fight.contains("saveTagsFor("), "one definition of a spell's tags, for both paths");
        String combatant = Files.readString(Path.of(base + "combat/Combatant.java"));
        assertTrue(combatant.contains("s.hasSaveAdvantageVs(ability, tags)"), "a fight checks the ability too");
    }
}
