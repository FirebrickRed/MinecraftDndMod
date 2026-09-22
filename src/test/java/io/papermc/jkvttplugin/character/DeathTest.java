package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dying and death (PHB p.197, #101). Death lives on the sheet, so it can't be undone by anything
 * except a revival: not healing, not a rest, not the fight ending.
 */
class DeathTest {

    private static CharacterSheet fighter() {
        return character("human", null, "fighter", "soldier", scores(Ability.CONSTITUTION, 14));
    }

    /** Drop to exactly 0 HP with no damage left over. */
    private static CharacterSheet downed() {
        CharacterSheet c = fighter();
        c.takeDamage(c.getCurrentHealth());
        assertEquals(0, c.getCurrentHealth());
        assertFalse(c.isDead(), "0 HP is dying, not dead");
        return c;
    }

    @Test
    void threeFailedSavesKill() {
        CharacterSheet c = downed();
        c.addDeathSaveFailures(1);
        c.addDeathSaveFailures(1);
        assertFalse(c.isDead());
        c.addDeathSaveFailures(1);
        assertTrue(c.isDead());
        assertEquals(3, c.getDeathSaveFailures());
    }

    @Test
    void aNaturalOneCountsTwice() {
        CharacterSheet c = downed();
        c.addDeathSaveFailures(2);
        assertFalse(c.isDead());
        c.addDeathSaveFailures(2);
        assertTrue(c.isDead());
        assertEquals(3, c.getDeathSaveFailures(), "capped at three");
    }

    @Test
    void threeSuccessesStabilise() {
        CharacterSheet c = downed();
        c.addDeathSaveSuccess();
        c.addDeathSaveSuccess();
        assertFalse(c.isStable());
        c.addDeathSaveSuccess();
        assertTrue(c.isStable());
        assertFalse(c.isDead());
    }

    @Test
    void damageAtZeroIsAFailedSaveAndACritIsTwo() {
        CharacterSheet c = downed();
        c.takeDamage(1);
        assertEquals(1, c.getDeathSaveFailures());
        c.takeDamage(1, true);
        assertTrue(c.isDead(), "1 + 2 for the crit = 3");
    }

    @Test
    void tempHpThatSoaksTheHitIsNoFailedSave() {
        CharacterSheet c = downed();
        c.setTemporaryHp(5);
        c.takeDamage(3);
        assertEquals(0, c.getDeathSaveFailures());
    }

    @Test
    void massiveDamageFromFullKillsOutright() {
        CharacterSheet c = fighter();
        int max = c.getMaxHealth();
        c.takeDamage(c.getCurrentHealth() + max);
        assertTrue(c.isDead(), "damage past 0 equal to the HP maximum");
        assertEquals(0, c.getDeathSaveFailures(), "no saves involved");
    }

    @Test
    void oneShortOfMassiveDamageIsOnlyDying() {
        CharacterSheet c = fighter();
        c.takeDamage(c.getCurrentHealth() + c.getMaxHealth() - 1);
        assertFalse(c.isDead());
        assertEquals(0, c.getCurrentHealth());
    }

    @Test
    void massiveDamageWhileDownKillsOutright() {
        CharacterSheet c = downed();
        c.takeDamage(c.getMaxHealth());
        assertTrue(c.isDead());
    }

    @Test
    void healingAtZeroWakesAndClearsTheTally() {
        CharacterSheet c = downed();
        c.addDeathSaveFailures(2);
        c.addDeathSaveSuccess();
        c.heal(3);
        assertEquals(3, c.getCurrentHealth());
        assertEquals(0, c.getDeathSaveFailures());
        assertEquals(0, c.getDeathSaveSuccesses());

        // A fresh fall starts from zero, not from the old tally.
        c.takeDamage(3);
        c.takeDamage(1);
        assertEquals(1, c.getDeathSaveFailures());
    }

    /** The bug this fixes: the dead came back from a heal, a long rest, or simply the next fight. */
    @Test
    void theDeadStayDead() {
        CharacterSheet c = downed();
        c.addDeathSaveFailures(3);
        assertTrue(c.isDead());

        c.heal(10);
        assertEquals(0, c.getCurrentHealth(), "healing can't raise the dead");
        c.longRest();
        c.shortRest();
        assertEquals(0, c.getCurrentHealth(), "nor can a rest");
        assertTrue(c.isDead());
        c.takeDamage(5);
        assertEquals(3, c.getDeathSaveFailures(), "the dead take no more damage");
    }

    @Test
    void reviveBringsThemBackAtTheGivenHp() {
        CharacterSheet c = downed();
        c.addDeathSaveFailures(3);

        assertTrue(c.revive(1));
        assertFalse(c.isDead());
        assertEquals(1, c.getCurrentHealth());
        assertEquals(0, c.getDeathSaveFailures());

        assertFalse(c.revive(5), "reviving the living does nothing");
        assertEquals(1, c.getCurrentHealth());
    }

    @Test
    void reviveHpIsClamped() {
        CharacterSheet c = fighter();
        c.takeDamage(c.getCurrentHealth() + c.getMaxHealth());
        c.revive(999);
        assertEquals(c.getMaxHealth(), c.getCurrentHealth());

        c.takeDamage(c.getCurrentHealth() + c.getMaxHealth());
        c.revive(0);
        assertEquals(1, c.getCurrentHealth(), "at least 1 HP");
    }

    /** PHB p.186: a long rest needs at least 1 HP at the start. */
    @Test
    void noLongRestAtZeroHp() {
        CharacterSheet c = downed();
        c.addDeathSaveSuccess();
        c.addDeathSaveSuccess();
        c.addDeathSaveSuccess();
        assertTrue(c.isStable());
        c.longRest();
        assertEquals(0, c.getCurrentHealth(), "stable isn't enough");

        c.heal(1);
        c.longRest();
        assertEquals(c.getMaxHealth(), c.getCurrentHealth());
    }
}
