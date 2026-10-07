package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Playtest: a DM playing their own character cast Fire Bolt three times in a turn, the move that
 * started a fight was scrolled past and then ignored, and the bonus-attack refusal talked about a monk
 * feature to a fighter. The chat and the clicks need a server (TEST_PLAN); the rules under them are here.
 */
class PlaytestRound4Test {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndWeapon w(String id) { return WeaponLoader.getWeapon(id); }

    /** A cantrip needs the Action like any spell: once it's spent, a second Fire Bolt is refused. */
    @Test
    void aSecondCantripInOneTurnIsRefused() {
        TurnState turn = new TurnState(30, null);
        var fireBolt = SpellLoader.getSpell("fire_bolt");
        assertNull(CombatCommand.castBudgetRefusal(turn, fireBolt));
        turn.useAction();
        assertNotNull(CombatCommand.castBudgetRefusal(turn, fireBolt));
    }

    /**
     * The refusal above used to be skipped for a DM ("going ahead anyway"), which is every solo tester.
     * Nothing in the cast path may wave a DM through on its own again: their way past is a button.
     */
    @Test
    void theCastPathDoesNotWaveADmThrough() throws Exception {
        String src = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/CombatCommand.java"));
        assertFalse(src.contains("going ahead anyway"), "the DM bypass on the cast budget is back");
        assertFalse(src.contains("aoeReaction || isDM"), "an area spell's confirm skips the budget for a DM again");
        assertTrue(src.contains("takeCastOverride(player, spell)"), "the DM's explicit [Cast it anyway] is how it's overridden");
    }

    /** The opening move is recognised whatever roll words follow it; anything else isn't it. */
    @Test
    void theOpeningMoveIsRecognised() {
        String opening = "/combat cast fire_bolt Wolf ";
        assertTrue(OutOfCombatAttack.isOpening(opening, "/combat cast fire_bolt Wolf"));
        assertTrue(OutOfCombatAttack.isOpening(opening, "/combat cast fire_bolt Wolf autoRoll"));
        assertTrue(OutOfCombatAttack.isOpening(opening, "/combat cast Fire_Bolt wolf manualRoll 14"), "case doesn't matter");
        assertTrue(OutOfCombatAttack.isOpening(opening, "combat cast fire_bolt Wolf total 19"), "with or without the slash");
        assertFalse(OutOfCombatAttack.isOpening(opening, "/combat cast fire_bolt Wolfhound autoRoll"), "another target");
        assertFalse(OutOfCombatAttack.isOpening(opening, "/combat cast burning_hands"));
        assertFalse(OutOfCombatAttack.isOpening(opening, "/combat attack Wolf dagger autoRoll"));
        assertFalse(OutOfCombatAttack.isOpening(null, "/combat cast fire_bolt Wolf"));
    }

    /** The owed move lives on the turn, so it lapses with it. */
    @Test
    void theOpeningMoveIsHeldOnTheTurn() {
        TurnState turn = new TurnState(30, null);
        assertNull(turn.getOpeningCommand());
        turn.setOpening("Fire Bolt at Wolf", "/combat cast fire_bolt Wolf ");
        assertEquals("Fire Bolt at Wolf", turn.getOpeningLabel());
        turn.clearOpening();
        assertNull(turn.getOpeningCommand());
        assertNull(new TurnState(30, null).getOpeningCommand(), "the next turn starts without one");
    }

    /** Why a bonus attack isn't possible is said about what's in their hands, and never names a class feature. */
    @Test
    void theBonusAttackRefusalIsAboutTheirHands() {
        DndWeapon longsword = w("longsword"), dagger = w("dagger"), shortsword = w("shortsword");

        String notLight = BonusAttack.noBonusAttackReason(dagger, longsword, dagger);
        assertTrue(notLight.contains("Your Longsword isn't light"), notLight);

        String wrongHand = BonusAttack.noBonusAttackReason(shortsword, shortsword, dagger);
        assertTrue(wrongHand.contains("off-hand weapon (the Dagger)"), wrongHand);

        String emptyOff = BonusAttack.noBonusAttackReason(shortsword, shortsword, null);
        assertTrue(emptyOff.contains("Your off hand is empty"), emptyOff);

        String punch = BonusAttack.noBonusAttackReason(null, longsword, null);
        assertTrue(punch.contains("unarmed strike"), punch);

        for (String s : new String[]{notLight, wrongHand, emptyOff, punch}) {
            assertFalse(s.contains("Martial Arts"), "a fighter isn't told about a monk feature: " + s);
        }
    }
}
