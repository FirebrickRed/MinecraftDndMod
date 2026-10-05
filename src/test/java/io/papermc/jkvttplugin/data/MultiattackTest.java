package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.Multiattack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #253: a creature's Multiattack, several attacks on one Action. Which attacks, how many, and what's
 * left after each. The Action itself and the DM's messages need a fight (TEST_PLAN).
 */
class MultiattackTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static List<DndAttack> attacks(String... names) {
        List<DndAttack> out = new ArrayList<>();
        for (String n : names) out.add(new DndAttack(n, 4, "5 ft.", "1d6+2", "piercing"));
        return out;
    }

    @Test
    void oneBiteAndTwoClaws() {
        Multiattack m = EntityLoader.getEntity("gnoll_fang_of_yeenoghu").getMultiattack();
        assertNotNull(m);
        assertEquals(3, m.total());
        assertEquals("Bite, Claw ×2", m.describe());

        Multiattack.Progress p = m.begin();
        p.use("Claw");
        assertEquals("Bite, Claw", p.leftText());
        p.use("Claw");
        assertFalse(p.canUse("Claw"), "both claws are made");
        assertTrue(p.canUse("Bite"));
        assertFalse(p.done());
        p.use("Bite");
        assertTrue(p.done());
        assertEquals("", p.leftText());
    }

    @Test
    void twoAttacksWithGlaiveOrLongbow() {
        Multiattack m = EntityLoader.getEntity("gnoll_pack_lord").getMultiattack();
        assertEquals("2 attacks: Glaive or Longbow", m.describe());
        assertTrue(m.includes("Glaive"));
        assertFalse(m.includes("Bite"), "the bite isn't part of it: a bite is its whole Action");

        Multiattack.Progress p = m.begin();
        p.use("Glaive");
        assertEquals("1 more: Glaive or Longbow", p.leftText());
        assertFalse(p.canUse("Bite"));
        p.use("Longbow");
        assertTrue(p.done());
        assertFalse(p.canUse("Glaive"), "two were made");
    }

    @Test
    void aBareNumberIsAnyOfItsAttacks() {
        List<String> problems = new ArrayList<>();
        Multiattack m = Multiattack.parse(2, attacks("Bite", "Claw"), problems);
        assertTrue(problems.isEmpty());
        assertEquals("2 attacks", m.describe());
        Multiattack.Progress p = m.begin();
        assertTrue(p.canUse("Bite") && p.canUse("Claw"));
        p.use("Bite"); p.use("Bite");
        assertTrue(p.done());
    }

    @Test
    void namesMatchWhateverTheirCase() {
        List<String> problems = new ArrayList<>();
        Multiattack m = Multiattack.parse(List.of(Map.of("attack", "bite", "count", 2)), attacks("Bite"), problems);
        assertTrue(problems.isEmpty());
        assertEquals("Bite ×2", m.describe(), "shown as the stat block spells it");
        assertTrue(m.includes("BITE"));
    }

    @Test
    void mistakesAreReported() {
        List<String> problems = new ArrayList<>();
        assertNull(Multiattack.parse(List.of(Map.of("attack", "Tail", "count", 2)), attacks("Bite"), problems),
                "nothing usable");
        assertTrue(problems.get(0).contains("Tail"), "an attack it doesn't have");

        problems.clear();
        assertNull(Multiattack.parse(1, attacks("Bite"), problems));
        assertFalse(problems.isEmpty(), "one attack isn't a multiattack");

        problems.clear();
        Multiattack.parse(Map.of("count", 2, "any_of", List.of("Bite", "Wing")), attacks("Bite"), problems);
        assertTrue(problems.get(0).contains("Wing"));

        problems.clear();
        assertNull(Multiattack.parse("lots", attacks("Bite"), problems));
        assertFalse(problems.isEmpty());

        assertNull(Multiattack.parse(null, attacks("Bite"), problems), "absent: no multiattack, no complaint");
    }

    @Test
    void theShippedGnollsHaveTheirs() {
        assertNull(EntityLoader.getEntity("gnoll").getMultiattack(), "a plain gnoll makes one attack");
        assertEquals("2 attacks: Bite or Spiked Club", EntityLoader.getEntity("gnoll_witherling").getMultiattack().describe());
        assertEquals("Bite, Shortsword ×2", EntityLoader.getEntity("gnoll_flesh_gnawer").getMultiattack().describe());
        Multiattack hunter = EntityLoader.getEntity("gnoll_hunter").getMultiattack();
        assertEquals(2, hunter.total());
        assertTrue(hunter.includes("Spear (Thrown)") && hunter.includes("Longbow") && hunter.includes("Bite"));
    }
}
