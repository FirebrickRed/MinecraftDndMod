package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.commands.CharacterCommand;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hit Dice (#52) and the rest fixes that came with them. */
class HitDiceTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void oneHitDiePerLevelOfTheClassDie() {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores());
        assertEquals(1, f.getHitDiceMax());
        assertEquals(1, f.getHitDiceRemaining(), "a new character has them all");
        assertEquals("1d10", f.hitDieDice());
        assertEquals("1d6", character("human", null, "wizard", "sage", scores()).hitDieDice());
    }

    @Test
    void spentAtTheEndOfAShortRestOnly() {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores(Ability.CONSTITUTION, 14));
        f.takeDamage(5);
        assertNotNull(CharacterCommand.hitDieRefusal(f), "no rest yet");
        f.shortRest();
        assertNull(CharacterCommand.hitDieRefusal(f));
        assertTrue(f.spendHitDie());
        assertEquals(0, f.getHitDiceRemaining());
        assertFalse(f.spendHitDie(), "none left");
        assertTrue(CharacterCommand.hitDieRefusal(f).startsWith("No Hit Dice left"));
    }

    @Test
    void aLongRestGivesBackHalfAtLeastOne() {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores());
        f.shortRest();
        f.spendHitDie();
        f.longRest();
        assertEquals(1, f.getHitDiceRemaining(), "half of 1, rounded down, but at least 1");
        assertEquals(1, f.hitDiceRegainedOnLongRest());
        assertFalse(f.isShortRestOpen(), "a long rest isn't a short rest to spend them in");
    }

    /** "When you finish a short or long rest": a long rest used to leave Second Wind spent. */
    @Test
    void aLongRestRestoresShortRestResources() {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores());
        var sw = f.getResource("Second Wind");
        sw.consume(1);
        assertEquals(0, sw.getCurrent());
        f.longRest();
        assertEquals(1, sw.getCurrent());
    }

    /** Pact Magic comes back on a short rest; a wizard's slots don't. */
    @Test
    void warlockSlotsComeBackOnAShortRest() {
        CharacterSheet w = character("tiefling", null, "warlock", "acolyte", scores(Ability.CHARISMA, 16));
        w.consumeSpellSlot(1);
        assertEquals(0, w.getSpellSlotsRemaining(1));
        w.shortRest();
        assertEquals(1, w.getSpellSlotsRemaining(1));

        CharacterSheet wiz = character("human", null, "wizard", "sage", scores(Ability.INTELLIGENCE, 16));
        wiz.consumeSpellSlot(1);
        wiz.shortRest();
        assertEquals(1, wiz.getSpellSlotsRemaining(1), "2 slots, 1 spent, not back on a short rest");
    }
}
