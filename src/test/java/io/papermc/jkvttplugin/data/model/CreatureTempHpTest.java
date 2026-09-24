package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A creature's temporary hit points (PHB p.198), the same rules as a character's: they soak damage
 * first, a new batch replaces the old only if it's higher, and they're gone on death.
 * No armor stand here (it needs a server), which persist() and the corpse visuals already allow for.
 */
class CreatureTempHpTest {

    private DndEntityInstance creature;

    private DndEntityInstance spawn(int maxHp) {
        TestContent.load();
        creature = new DndEntityInstance(EntityLoader.getEntity("balin_blacksmith"), null, "Balin", maxHp);
        return creature;
    }

    @AfterEach
    void cleanUp() {
        if (creature != null) creature.unregister();
    }

    @Test
    void tempHpSoakDamageFirst() {
        DndEntityInstance c = spawn(20);
        c.grantTempHp(5);
        c.takeDamage(8);
        assertEquals(0, c.getTempHp());
        assertEquals(17, c.getCurrentHp(), "5 soaked by temp HP, 3 through");
    }

    @Test
    void tempHpDontStack() {
        DndEntityInstance c = spawn(20);
        c.grantTempHp(5);
        c.grantTempHp(3);
        assertEquals(5, c.getTempHp(), "a smaller batch doesn't replace a bigger one");
        c.grantTempHp(8);
        assertEquals(8, c.getTempHp());
    }

    @Test
    void deathClearsTempHpAndTheDeadGainNone() {
        DndEntityInstance c = spawn(10);
        c.grantTempHp(2);
        c.takeDamage(40);
        assertTrue(c.isDead());
        assertEquals(0, c.getTempHp());
        c.grantTempHp(5);
        assertEquals(0, c.getTempHp());
    }
}
