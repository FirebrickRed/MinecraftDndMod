package io.papermc.jkvttplugin.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What attacking with a weapon you didn't start the turn holding costs (#190): settled at the attack,
 * never by scrolling. The buttons need a server (TEST_PLAN.md).
 */
class WeaponSwitchTest {

    private static TurnState holding(String main, String off) {
        TurnState s = new TurnState(30, null);
        s.setTurnStartWeaponId(main);
        s.setTurnStartOffhandWeaponId(off);
        return s;
    }

    @Test
    void whatYouStartedWithIsFree() {
        TurnState s = holding("longsword", "dagger");
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, "longsword"));
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, "dagger"), "the off-hand weapon is in hand too");
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, "unarmed"));
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, null));
    }

    @Test
    void theFirstSwitchAsksAndUsesTheFreeInteraction() {
        TurnState s = holding("longsword", null);
        assertEquals(WeaponSwitch.Need.FREE, WeaponSwitch.need(s, "shortbow"));
        assertFalse(s.isObjectInteractionUsed(), "asking costs nothing: only the answer does");

        WeaponSwitch.settle(s, "shortbow", true);
        assertTrue(s.isObjectInteractionUsed());
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, "shortbow"), "settled: the rest of the turn is free");
        assertEquals(WeaponSwitch.Need.NONE, WeaponSwitch.need(s, "longsword"), "going back to what you started with");
        assertEquals(WeaponSwitch.Need.ACTION, WeaponSwitch.need(s, "handaxe"), "a second switch takes the Action");
    }

    @Test
    void aSwitchTheDmAllowsKeepsTheFreeInteraction() {
        TurnState s = holding("longsword", null);
        WeaponSwitch.settle(s, "shortbow", false);
        assertFalse(s.isObjectInteractionUsed());
        assertEquals(WeaponSwitch.Need.FREE, WeaponSwitch.need(s, "handaxe"));
    }

    @Test
    void emptyHandsDrawWithoutAsking() {
        TurnState s = holding(null, null);
        assertEquals(WeaponSwitch.Need.DRAW, WeaponSwitch.need(s, "longsword"));
        WeaponSwitch.settle(s, "longsword", true);
        assertEquals(WeaponSwitch.Need.ACTION, WeaponSwitch.need(s, "shortbow"), "the draw was the free interaction");
    }
}
