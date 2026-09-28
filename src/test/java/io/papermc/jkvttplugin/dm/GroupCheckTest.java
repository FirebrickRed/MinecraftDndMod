package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.combat.Advantage;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Group checks (#186) and the Hidden condition (#176). */
class GroupCheckTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static Map<UUID, String> party(UUID... ids) {
        Map<UUID, String> m = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) m.put(ids[i], "P" + i);
        return m;
    }

    /** PHB p.175: the group succeeds if at least half succeed. */
    @Test
    void atLeastHalfSucceed() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var g = CheckManager.registerGroup(null, 12, "Stealth", Advantage.NONE, party(a, b, c));
        assertTrue(CheckManager.hasPending(a), "everyone is asked");
        CheckManager.recordGroupRoll(g.id, a, 14);
        CheckManager.recordGroupRoll(g.id, b, 9);
        assertFalse(g.allIn());
        CheckManager.recordGroupRoll(g.id, c, 12); // a tie meets the DC
        assertTrue(g.allIn());
        assertEquals(2, g.successes());
        assertTrue(g.groupSucceeds());
        CheckManager.closeGroup(g.id);
    }

    @Test
    void closingEarlyDropsWhoeverHasntRolled() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var g = CheckManager.registerGroup(null, 15, "Athletics", Advantage.NONE, party(a, b));
        CheckManager.recordGroupRoll(g.id, a, 10);
        var closed = CheckManager.closeGroup(g.id);
        assertNotNull(closed);
        assertFalse(CheckManager.hasPending(b), "the one who didn't roll isn't still being asked");
        assertFalse(closed.groupSucceeds(), "0 of 1 who rolled");
        assertNull(CheckManager.closeGroup(g.id), "closing twice does nothing");
    }

    @Test
    void ungradedGroupJustCollects() {
        UUID a = UUID.randomUUID();
        var g = CheckManager.registerGroup(null, null, "Perception", Advantage.NONE, party(a));
        CheckManager.recordGroupRoll(g.id, a, 17);
        assertFalse(g.groupSucceeds(), "no DC, no verdict");
        assertEquals(17, g.totals.get(a));
        CheckManager.closeGroup(g.id);
    }

    /** Hidden gives advantage on your attacks and disadvantage on attacks against you. */
    @Test
    void hiddenIsACondition() {
        var hidden = ConditionLoader.get("hidden");
        assertNotNull(hidden);
        assertEquals("advantage", hidden.getSelfAttack());
        assertEquals("disadvantage", hidden.getIncomingAttack());
    }
}
