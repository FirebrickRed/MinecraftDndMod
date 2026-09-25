package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The one healing roll (SpellCastHandler.healRoll), used in and out of a fight. The two copies it
 * replaced disagreed: one ignored autoRoll in physical-dice mode, the other a formula's own +N.
 */
class HealRollTest {

    private static CharacterSheet cleric() {
        return character("human", null, "cleric", "acolyte", scores(Ability.WISDOM, 16)); // +3 WIS
    }

    private static DndSpell cureWounds() {
        return SpellLoader.getSpell("cure_wounds");
    }

    @Test
    void aTypedRollAddsTheCastersModifier() {
        SpellCastHandler.HealRoll heal = SpellCastHandler.healRoll(cleric(), cureWounds(), 5, null, false);
        assertEquals(8, heal.amount());
        assertEquals("🎲 you rolled 5 +3[WIS] = 8", heal.work());
    }

    @Test
    void aTotalIsTakenAsIs() {
        SpellCastHandler.HealRoll heal = SpellCastHandler.healRoll(cleric(), cureWounds(), null, 12, false);
        assertEquals(12, heal.amount());
        assertEquals("🎲 your total: 12", heal.work());
    }

    @Test
    void autoRollRollsEvenInPhysicalDiceMode() {
        // The default mode is physical dice; clicking [Roll it] (autoRoll) must still roll, not re-ask.
        for (int i = 0; i < 20; i++) {
            SpellCastHandler.HealRoll heal = SpellCastHandler.healRoll(cleric(), cureWounds(), null, null, true);
            assertNotNull(heal);
            assertTrue(heal.amount() >= 4 && heal.amount() <= 11, "1d8 +3: " + heal.amount());
            assertTrue(heal.work().startsWith("🎲 1d8 ["), heal.work());
            assertEquals(1, heal.work().chars().filter(c -> c == '=').count(), "one total: " + heal.work());
        }
    }

    @Test
    void nothingGivenInPhysicalModeAsksForTheRoll() {
        assertNull(SpellCastHandler.healRoll(cleric(), cureWounds(), null, null, false));
    }
}
