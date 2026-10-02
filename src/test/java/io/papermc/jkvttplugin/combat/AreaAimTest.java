package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #237: an area aim fires only on the turn it was aimed, in a fight that's still on, by a caster who
 * can still act. It used to fire whenever the right-click came, spending whatever was left.
 */
class AreaAimTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static Combatant creature(String name, List<String> conditions) {
        return Combatant.fromSavedData(UUID.randomUUID(), Combatant.CombatantType.ENTITY, name, name,
                10, 0, false, false, false, false, conditions, 0, 0, false, true);
    }

    @Test
    void theTurnItWasAimedOnCanFireIt() {
        Combatant caster = creature("Kobold", List.of());
        caster.startNewTurn(null);
        TurnState aimedOn = caster.getTurnState();
        assertNull(AreaTargeting.staleReason(true, caster, caster, aimedOn));
    }

    @Test
    void anythingElseIsStale() {
        Combatant caster = creature("Kobold", List.of());
        Combatant other = creature("Goblin", List.of());
        caster.startNewTurn(null);
        TurnState aimedOn = caster.getTurnState();

        assertNotNull(AreaTargeting.staleReason(false, caster, caster, aimedOn), "the fight ended");
        assertNotNull(AreaTargeting.staleReason(true, caster, other, aimedOn), "someone else's turn");
        assertNotNull(AreaTargeting.staleReason(true, null, other, aimedOn), "removed from the fight");

        caster.startNewTurn(null); // round comes back around
        assertNotNull(AreaTargeting.staleReason(true, caster, caster, aimedOn), "aimed on an earlier turn");
    }

    @Test
    void aCasterWhoCantActCantFire() {
        Combatant stunned = creature("Kobold", List.of("stunned"));
        stunned.startNewTurn(null);
        assertNotNull(AreaTargeting.staleReason(true, stunned, stunned, stunned.getTurnState()));
    }
}
