package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one roll prompt (#216): the same three buttons, in the same order, pointing at the same roll
 * words, wherever a roll is asked for. And nothing outside {@link RollPrompt} builds its own.
 */
class RollPromptTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Every click event in the component tree, in reading order. */
    private static List<ClickEvent> clicks(Component c) {
        List<ClickEvent> out = new ArrayList<>();
        if (c.clickEvent() != null) out.add(c.clickEvent());
        for (Component child : c.children()) out.addAll(clicks(child));
        return out;
    }

    @Test
    void threeButtonsWhenTheGameAddsABonus() {
        Component c = RollPrompt.buttons("/combat save ", "d20", "+3 (DEX save)");
        assertEquals("[I rolled…] [Roll it] [My total…]", plain(c));
        List<ClickEvent> clicks = clicks(c);
        assertEquals(3, clicks.size());
        assertEquals(ClickEvent.suggestCommand("/combat save manualRoll "), clicks.get(0));
        assertEquals(ClickEvent.runCommand("/combat save autoRoll"), clicks.get(1));
        assertEquals(ClickEvent.suggestCommand("/combat save total "), clicks.get(2));
    }

    @Test
    void noTotalButtonWhenNothingIsAdded() {
        // With no bonus, "my total" is the same number as "I rolled", so it isn't offered.
        assertEquals("[I rolled…] [Roll it]", plain(RollPrompt.buttons("/character damage ", "2d6", null)));
    }

    @Test
    void theThreeResultsSayWhereTheNumberCameFrom() {
        assertEquals("🎲 2d6 [4, 3] +3[CHA] = 10", RollPrompt.gameRolled("2d6", "[4, 3]", "+3[CHA]", 10));
        assertEquals("🎲 you rolled 7 +3[CHA] = 10", RollPrompt.youRolled(7, "+3[CHA]", 10));
        assertEquals("🎲 your total: 10", RollPrompt.yourTotal(10));
    }

    @Test
    void aGameRolledAdvantageShowsBothDice() {
        RollService.RollResult r = RollService.resolve(null, null, 3, "+3[DEX]", false, Advantage.ADVANTAGE, true);
        assertTrue(r.breakdown().matches("🎲 d20 \\[\\d+, \\d+] advantage \\+3\\[DEX] = \\d+.*"), r.breakdown());
    }

    /** "Where did the +5 come from": a spell's and a save's bonus are named, not lumped. */
    @Test
    void bonusesAreLabelledBySource() {
        io.papermc.jkvttplugin.TestContent.load();
        var wizard = io.papermc.jkvttplugin.TestContent.character("human", null, "wizard", "sage",
                io.papermc.jkvttplugin.TestContent.scores(io.papermc.jkvttplugin.data.model.enums.Ability.INTELLIGENCE, 16));
        var fireBolt = io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell("fire_bolt");
        assertEquals("+3[INT] +2[Prof]", wizard.getSpellAttackBreakdown(fireBolt));
        assertEquals("+3[INT]", wizard.getSpellModBreakdown(fireBolt));
        // Wizards are proficient in INT saves, not DEX saves.
        assertTrue(wizard.getSaveBreakdown(io.papermc.jkvttplugin.data.model.enums.Ability.INTELLIGENCE).endsWith("+2[Prof]"));
        assertFalse(wizard.getSaveBreakdown(io.papermc.jkvttplugin.data.model.enums.Ability.DEXTERITY).contains("Prof"));
    }

    /** Game-rolled dice show once, with one total: not "[5] = 5 +3[WIS] = 8". */
    @Test
    void gameRolledDiceHaveOneTotal() {
        io.papermc.jkvttplugin.util.DiceRoller.Rolled r = io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat("2d6");
        String line = RollPrompt.gameRolled("2d6", r.shown(), "+3[WIS]", r.total() + 3);
        assertEquals(1, line.chars().filter(ch -> ch == '=').count(), line);
        assertTrue(r.display().startsWith("🎲 2d6 ["), r.display());
    }

    /**
     * A flat damage bonus is only "added by the game" when it has a named source. Magic Missile's
     * +1 (like a potion's +2) is part of the formula you roll, so nothing unlabelled is ever added.
     */
    @Test
    void anUnlabelledFlatIsPartOfTheFormula() {
        AttackHandler.DamageSplit spell = AttackHandler.splitDamage("1d4+1", "");
        assertEquals("1d4+1", spell.dice());
        assertEquals(0, spell.bonus());

        AttackHandler.DamageSplit sword = AttackHandler.splitDamage("1d8+3", "+3[STR]");
        assertEquals("1d8", sword.dice());
        assertEquals(3, sword.bonus());
        assertEquals("+3[STR]", sword.label());
    }

    /** The playtest found about ten wordings. A new prompt must use RollPrompt, not its own labels. */
    @Test
    void noOtherFileBuildsItsOwnRollButtons() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.endsWith("RollPrompt.java")) continue;
                String src = Files.readString(p);
                for (String old : List.of("[let the game roll]", "[or let the game roll]", "[Let the game roll]",
                        "[click, then type your d20]", "[click, then type the d20]", "[click, then type your roll]",
                        "[click, then type your damage roll]", "[type a final total]", "\"[I rolled…]\"", "\"[Roll it]\"")) {
                    if (src.contains(old)) offenders.add(p.getFileName() + ": " + old);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Build roll prompts with RollPrompt: " + offenders);
    }
}
