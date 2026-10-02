package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.character;
import static io.papermc.jkvttplugin.TestContent.scores;
import static org.junit.jupiter.api.Assertions.*;

/** Casting what you've already spent the action for (#235), and a mark outliving its concentration (#238). */
class CastBudgetTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void aSpentActionRefusesAnActionSpell() {
        TurnState s = new TurnState(30, null);
        var fireBolt = SpellLoader.getSpell("fire_bolt");
        assertNull(CombatCommand.castBudgetRefusal(s, fireBolt));
        s.useAction();
        assertNotNull(CombatCommand.castBudgetRefusal(s, fireBolt), "a second Fire Bolt this turn");
    }

    @Test
    void aBonusActionSpellNeedsTheBonusActionNotTheAction() {
        TurnState s = new TurnState(30, null);
        var healingWord = SpellLoader.getSpell("healing_word");
        s.useAction();
        assertNull(CombatCommand.castBudgetRefusal(s, healingWord), "the Action being spent doesn't matter");
        s.useBonusAction();
        assertNotNull(CombatCommand.castBudgetRefusal(s, healingWord));
    }

    @Test
    void switchingConcentrationEndsTheMark() {
        CharacterSheet warlock = character("human", null, "warlock", "sage", scores(Ability.CHARISMA, 16));
        var hex = SpellLoader.getSpell("hex");
        UUID goblin = UUID.randomUUID(), orc = UUID.randomUUID();
        warlock.setConcentratingOn(hex);
        warlock.setSpellMark(goblin, "1d6", "necrotic", "strength");
        assertEquals("1d6", warlock.markRiderAgainst(goblin));

        // Hex again on another target: the mark moves (castMark sets concentration, then the mark).
        warlock.setConcentratingOn(hex);
        warlock.setSpellMark(orc, "1d6", "necrotic", "strength");
        assertNull(warlock.markRiderAgainst(goblin));
        assertEquals("1d6", warlock.markRiderAgainst(orc));

        // A different concentration spell: the Hex is over, rider and all.
        warlock.setConcentratingOn(SpellLoader.getSpell("bless"));
        assertNull(warlock.markRiderAgainst(orc), "the old Hex still added its damage");
        assertNull(warlock.getMarkCheckDisadvantageAbility());
    }
}
