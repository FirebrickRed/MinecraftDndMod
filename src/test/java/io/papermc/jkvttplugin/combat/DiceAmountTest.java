package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The one reading of roll words for an amount of dice (healing spells, potions, Second Wind, Hit Dice,
 * out-of-combat damage), and the places where the five callers are meant to differ.
 */
class DiceAmountTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static RollService.RollInput total(int n) { return new RollService.RollInput(null, n, false); }
    private static RollService.RollInput rolled(int n) { return new RollService.RollInput(n, null, false); }
    private static final RollService.RollInput AUTO = new RollService.RollInput(null, null, true);
    private static final RollService.RollInput NOTHING = new RollService.RollInput(null, null, false);

    // ---------- the shared reading ----------

    @Test
    void aTotalIsTheAmountAndNothingIsAdded() {
        DiceAmount.Result r = DiceAmount.resolve(total(9), "2d4+2", "Potion of Healing", 3, "+3[WIS]", false);
        assertTrue(r.ok());
        assertEquals(9, r.amount());
        assertEquals("🎲 your total: 9", r.work());
    }

    @Test
    void aTypedRollGetsTheFormulasFlatPartAndTheBonus() {
        DiceAmount.Result r = DiceAmount.resolve(rolled(5), "2d4+2", "Potion of Healing", 3, "+3[WIS]", false);
        assertEquals(5 + 2 + 3, r.amount());
        assertEquals("🎲 you rolled 5 +2[Potion of Healing] +3[WIS] = 10", r.work());
    }

    @Test
    void theGameRollsTheFormulaAndAddsTheBonus() {
        for (int i = 0; i < 40; i++) {
            DiceAmount.Result r = DiceAmount.resolve(AUTO, "2d4+2", "Potion of Healing", 3, "+3[WIS]", false);
            assertTrue(r.amount() >= 2 + 2 + 3 && r.amount() <= 8 + 2 + 3, "2d4 + 2 + 3, got " + r.amount());
            assertTrue(r.work().startsWith("🎲 2d4 ["), r.work());
            assertTrue(r.work().endsWith("+2[Potion of Healing] +3[WIS] = " + r.amount()), r.work());
        }
    }

    @Test
    void noWordsAsksForTheRollUnlessTheGameIsSetToRoll() {
        assertEquals(DiceAmount.Status.NEEDS_ROLL, DiceAmount.resolve(NOTHING, "1d10", "Second Wind", 1, "+1[Fighter level]", false).status());
        assertTrue(DiceAmount.resolve(NOTHING, "1d10", "Second Wind", 1, "+1[Fighter level]", true).ok(), "auto-roll servers just roll");
    }

    @Test
    void unreadableDiceAreReportedNotGuessed() {
        assertEquals(DiceAmount.Status.BAD_DICE, DiceAmount.resolve(AUTO, "lots", "Mystery Potion", 0, null, false).status());
        assertTrue(DiceAmount.resolve(rolled(4), "lots", "Mystery Potion", 0, null, false).ok(), "a typed roll doesn't need the formula");
    }

    @Test
    void aFlatAmountIsNotDice() {
        DiceAmount.Result r = DiceAmount.resolve(AUTO, "5", "Goodberry", 0, null, false);
        assertEquals(5, r.amount());
        assertEquals("🎲 5 5 = 5", r.work());
    }

    @Test
    void labelsJoinAndVanish() {
        assertEquals("+2[Potion] +3[WIS]", DiceAmount.join("+2[Potion]", "+3[WIS]"));
        assertEquals("+3[WIS]", DiceAmount.join(null, "+3[WIS]"));
        assertEquals("+2[Potion]", DiceAmount.join("+2[Potion]", " "));
        assertNull(DiceAmount.join(null, null));
        assertEquals("🎲 you rolled 6 = 6", DiceAmount.resolve(rolled(6), "1d8", "Hit Die", 0, null, false).work(), "no bonus, no label");
    }

    /**
     * The drift this replaced: Second Wind and Hit Dice added nothing of the formula's own to a typed roll,
     * so "1d10+2" healed 2 less when you rolled it yourself than when the game did. No shipped feature has
     * such dice; a homebrew one now gets the same total either way.
     */
    @Test
    void aTypedRollAndAGameRollAgreeOnTheFlatPart() {
        DiceAmount.Result typed = DiceAmount.resolve(rolled(7), "1d10+2", "Battle Medic", 1, "+1[Fighter level]", false);
        assertEquals(7 + 2 + 1, typed.amount());
        for (int i = 0; i < 40; i++) {
            int auto = DiceAmount.resolve(AUTO, "1d10+2", "Battle Medic", 1, "+1[Fighter level]", false).amount();
            assertTrue(auto >= 1 + 2 + 1 && auto <= 10 + 2 + 1, String.valueOf(auto));
        }
    }

    /**
     * A feature's heal with a flat part of its own (homebrew "1d10+2", plus 1 for a class level): the prompt
     * asks for the 1d10 and names both bonuses, and a typed 7 heals 10. The prompt used to ask for "1d10+2"
     * and mention only the level, so the +2 looked like part of what you rolled.
     */
    @Test
    void aFeatureHealPromptAsksForTheDiceAndNamesBothBonuses() throws Exception {
        String level = "+1[Fighter level]";
        DiceAmount.Ask ask = DiceAmount.ask("1d10+2", "Battle Medic", level);
        assertEquals("1d10", ask.dice());
        assertEquals("+2[Battle Medic] +1[Fighter level]", ask.bonusLabel());

        // The buttons built from it say the same thing.
        net.kyori.adventure.text.Component prompt = RollPrompt.line("💚 Roll Battle Medic:",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW, "/combat use battle_medic ", ask.dice(), ask.bonusLabel());
        String hovers = String.join("\n", hoverTexts(prompt));
        assertTrue(hovers.contains("Roll 1d10 and type what it came to"), hovers);
        assertTrue(hovers.contains("the game adds +2[Battle Medic] +1[Fighter level]"), hovers);
        assertFalse(hovers.contains("1d10+2"), "the flat part isn't something you roll: " + hovers);

        // And the answer matches the prompt: 7 on the die, +2, +1.
        DiceAmount.Result typed = DiceAmount.resolve(rolled(7), "1d10+2", "Battle Medic", 1, level, false);
        assertEquals(10, typed.amount());
        assertEquals("🎲 you rolled 7 +2[Battle Medic] +1[Fighter level] = 10", typed.work());

        // Second Wind itself (1d10, no flat part) is unchanged: the dice, and the level.
        DiceAmount.Ask secondWind = DiceAmount.ask("1d10", "Second Wind", level);
        assertEquals("1d10", secondWind.dice());
        assertEquals(level, secondWind.bonusLabel());

        String featureUse = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/FeatureUse.java"));
        assertTrue(featureUse.contains("DiceAmount.ask(h.dice(), f.getName(), label)"), "the feature heal prompt is built from DiceAmount.ask");
    }

    /** Every hover in a component tree, as plain text. */
    private static java.util.List<String> hoverTexts(net.kyori.adventure.text.Component c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        var hover = c.hoverEvent();
        if (hover != null && hover.value() instanceof net.kyori.adventure.text.Component text) {
            out.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text));
        }
        for (net.kyori.adventure.text.Component child : c.children()) out.addAll(hoverTexts(child));
        return out;
    }

    // ---------- where the callers differ, on purpose or not ----------

    /** A healing spell never heals less than 1, even on a typed total of 0. The others floor at 0 (see the scan below). */
    @Test
    void aHealingSpellHealsAtLeastOne() {
        CharacterSheet cleric = character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16));
        var cure = SpellLoader.getSpell("cure_wounds");
        assertEquals(1, SpellCastHandler.healRoll(cleric, cure, null, 0, false).amount());
        assertEquals(1, SpellCastHandler.healRoll(cleric, cure, -9, null, false).amount(), "a typed roll below zero, too");
        assertEquals(7 + 3, SpellCastHandler.healRoll(cleric, cure, 7, null, false).amount(), "1d8: 7, +3 WIS");
    }

    /**
     * Each caller keeps its own floor and its own auto-roll rule; this pins them where they're written,
     * so a later tidy-up can't flatten a difference without someone deciding to.
     */
    @Test
    void eachCallerKeepsItsOwnFloorAndRollMode() throws Exception {
        String base = "src/main/java/io/papermc/jkvttplugin/";
        String spells = Files.readString(Path.of(base + "combat/SpellCastHandler.java"));
        String features = Files.readString(Path.of(base + "combat/FeatureUse.java"));
        String ooc = Files.readString(Path.of(base + "combat/OutOfCombatAttack.java"));
        String character = Files.readString(Path.of(base + "commands/CharacterCommand.java"));
        String drink = Files.readString(Path.of(base + "commands/DrinkCommand.java"));

        for (String src : new String[]{spells, features, ooc, character, drink}) {
            assertTrue(src.contains("DiceAmount.resolve("), "one of the five has its own copy of the roll-word reading again");
        }
        assertTrue(spells.contains("Math.max(1, r.amount())"), "healing spells: at least 1");
        assertTrue(drink.contains("Math.max(0, rolled.amount())"), "potions: at least 0");
        assertTrue(features.contains("Math.max(0, total)"), "Second Wind: at least 0");
        assertTrue(character.contains("Math.max(0, healed)"), "Hit Dice: at least 0 (PHB p.186)");
        // Out-of-combat damage is the one that never rolls just because the server is in auto-roll mode.
        assertTrue(ooc.contains("DiceAmount.resolve(in, p.dice(), p.source(), 0, null, false)"));
        assertTrue(drink.contains("PluginConfig.isAutoRoll())") && features.contains("PluginConfig.isAutoRoll())"));
    }
}
