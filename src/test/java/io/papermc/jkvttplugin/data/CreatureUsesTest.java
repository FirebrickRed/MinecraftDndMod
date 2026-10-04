package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.CreatureUses;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #256: a creature's limited abilities (X/Day, Recharge X-Y, a rest), counted per creature.
 * #257: its ammunition (2d10 arrows, 2d4 thrown, or what the stat block says).
 * The refusal in a fight, the turn-start roll's message and the loot need a server (TEST_PLAN).
 */
class CreatureUsesTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static final Function<String, DndWeapon> WEAPONS = WeaponLoader::getWeapon;

    private static DndAttack attack(String name, Object uses, Object recharge) {
        DndAttack a = new DndAttack(name, 5, "30 ft.", "6d6", "fire");
        a.setLimits(uses, recharge, null);
        return a;
    }

    // ==================== LIMITED USE ====================

    @Test
    void threePerDayIsSpentAfterThreeAndBackAtDawn() {
        DndAttack breath = attack("Fire Breath", 3, null);
        assertEquals("3/Day", breath.limitLabel());
        CreatureUses u = new CreatureUses();
        long today = 40;
        for (int i = 0; i < 3; i++) { assertNull(u.refusal(breath, today)); u.spend(breath, today); }
        assertNotNull(u.refusal(breath, today), "the fourth use today");
        assertEquals(0, u.left(breath, today));
        assertEquals(3, u.left(breath, today + 1), "dawn the next day: all three back");
        assertNull(u.refusal(breath, today + 1));
    }

    @Test
    void rechargeFiveSixComesBackOnlyOnAFiveOrSix() {
        DndAttack breath = attack("Fire Breath", null, "5-6");
        assertEquals("Recharge 5-6", breath.limitLabel());
        assertEquals(1, breath.getUses(), "a recharge ability is one use");
        CreatureUses u = new CreatureUses();
        assertTrue(u.rollRecharges(List.of(breath), () -> 6).isEmpty(), "nothing spent, nothing rolled");
        u.spend(breath, 1);
        assertNotNull(u.refusal(breath, 1));
        assertEquals(1, u.rollRecharges(List.of(breath), () -> 4).size());
        assertNotNull(u.refusal(breath, 1), "a 4 doesn't bring it back");
        assertNotNull(u.refusal(breath, 2), "nor does a new day");
        assertTrue(u.rollRecharges(List.of(breath), () -> 5).get(0).contains("recharged"));
        assertNull(u.refusal(breath, 1));

        assertEquals("Recharge 6", attack("Stomp", null, 6).limitLabel());
        assertEquals("Recharge 4-6", attack("Web", null, "4–6").limitLabel(), "a typed en dash is fine");
    }

    @Test
    void aFightStartsWithRechargeAbilitiesReady() {
        DndAttack breath = attack("Fire Breath", null, "5-6");
        CreatureUses u = new CreatureUses();
        u.spend(breath, 1);
        u.recoverRolls(List.of(breath));
        assertNull(u.refusal(breath, 1));
    }

    @Test
    void restsBringBackWhatTheyShould() {
        DndAttack shortOne = attack("Second Wind", null, "short_rest");
        DndAttack longOne = attack("Wail", 2, "long_rest");
        DndAttack daily = attack("Legendary Resistance", 3, null);
        List<DndAttack> all = List.of(shortOne, longOne, daily);
        CreatureUses u = new CreatureUses();
        u.spend(shortOne, 1); u.spend(longOne, 1); u.spend(longOne, 1); u.spend(daily, 1);
        assertNotNull(u.refusal(shortOne, 1));
        assertNotNull(u.refusal(longOne, 1));

        assertEquals(List.of("Second Wind"), u.rest(all, false), "a short rest: only the short-rest ability");
        assertNull(u.refusal(shortOne, 1));
        assertNotNull(u.refusal(longOne, 1));
        assertEquals(2, u.left(daily, 1));

        u.rest(all, true);
        assertEquals(2, u.left(longOne, 1), "a long rest: everything");
        assertEquals(3, u.left(daily, 1), "X/Day too, without waiting for dawn");
    }

    /** The shipped Giant Spider: its Web is Recharge 5-6 and its Bite isn't limited. */
    @Test
    void theGiantSpidersWebRecharges() {
        List<DndAttack> spider = EntityLoader.getEntity("giant_spider").getAttacks();
        DndAttack web = spider.stream().filter(a -> a.getName().equals("Web")).findFirst().orElseThrow();
        assertEquals("Recharge 5-6", web.limitLabel());
        assertEquals(DndAttack.Recharge.ROLL, web.getRecharge());
        assertFalse(spider.get(0).isLimited());
        assertNull(CreatureUses.poolFor(web, spider, WEAPONS), "a web isn't ammunition: no item");
    }

    @Test
    void anUnlimitedAttackIsNeverRefused() {
        DndAttack bite = attack("Bite", null, null);
        assertFalse(bite.isLimited());
        CreatureUses u = new CreatureUses();
        assertNull(u.spend(bite, 1));
        assertNull(u.refusal(bite, 1));
    }

    @Test
    void badLimitsAreReportedNotGuessed() {
        assertFalse(attack("A", null, "sometimes").getLimitProblems().isEmpty());
        assertFalse(attack("B", "lots", null).getLimitProblems().isEmpty());
        DndAttack c = new DndAttack("C", 1, "5 ft.", "1d4", "piercing");
        c.setLimits(null, null, "plenty");
        assertFalse(c.getLimitProblems().isEmpty());
        assertTrue(attack("D", 3, "short_rest").getLimitProblems().isEmpty());
    }

    // ==================== AMMUNITION ====================

    private static DndAttack named(List<DndAttack> attacks, String name) {
        return attacks.stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
    }

    /** A roller that hands back fixed numbers, and counts how often it was asked. */
    private static Function<String, Integer> rolls(Deque<Integer> values, List<String> asked) {
        return dice -> { asked.add(dice); return values.pop(); };
    }

    @Test
    void theGnollsPools() {
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        assertNull(CreatureUses.poolFor(named(gnoll, "Bite"), gnoll, WEAPONS), "a bite uses nothing");

        CreatureUses.Pool bow = CreatureUses.poolFor(named(gnoll, "Longbow"), gnoll, WEAPONS);
        assertEquals("ammo:arrow", bow.key());
        assertEquals("2d10", bow.dice());
        assertTrue(bow.spendsOne());

        CreatureUses.Pool thrown = CreatureUses.poolFor(named(gnoll, "Spear (Thrown)"), gnoll, WEAPONS);
        CreatureUses.Pool stab = CreatureUses.poolFor(named(gnoll, "Spear"), gnoll, WEAPONS);
        assertEquals("thrown:spear", thrown.key());
        assertEquals("2d4", thrown.dice());
        assertTrue(thrown.spendsOne(), "a throw leaves its hand");
        assertEquals(thrown.key(), stab.key(), "stabbing needs one of the same spears");
        assertFalse(stab.spendsOne(), "but doesn't lose it");

        List<DndAttack> lord = EntityLoader.getEntity("gnoll_pack_lord").getAttacks();
        assertNull(CreatureUses.poolFor(named(lord, "Glaive"), lord, WEAPONS), "never thrown: nothing to run out of");
    }

    @Test
    void ammunitionIsRolledOnceThenCountsDown() {
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        CreatureUses.Pool bow = CreatureUses.poolFor(named(gnoll, "Longbow"), gnoll, WEAPONS);
        CreatureUses u = new CreatureUses();
        List<String> asked = new java.util.ArrayList<>();
        Function<String, Integer> roll = rolls(new ArrayDeque<>(List.of(2)), asked);

        assertEquals(2, u.ammoLeft(bow, gnoll, WEAPONS, roll));
        assertEquals(1, u.spendAmmo(bow, gnoll, WEAPONS, roll));
        assertEquals(0, u.spendAmmo(bow, gnoll, WEAPONS, roll));
        assertEquals(0, u.ammoLeft(bow, gnoll, WEAPONS, roll), "out of arrows");
        assertEquals(List.of("2d10"), asked, "rolled once, as the Monster Manual's 2d10");
    }

    @Test
    void theLastSpearThrownLeavesNoneToStabWith() {
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        CreatureUses.Pool thrown = CreatureUses.poolFor(named(gnoll, "Spear (Thrown)"), gnoll, WEAPONS);
        CreatureUses.Pool stab = CreatureUses.poolFor(named(gnoll, "Spear"), gnoll, WEAPONS);
        CreatureUses u = new CreatureUses();
        Function<String, Integer> roll = rolls(new ArrayDeque<>(List.of(2)), new java.util.ArrayList<>());

        assertEquals(2, u.spendAmmo(stab, gnoll, WEAPONS, roll), "a stab keeps the spear");
        assertEquals(1, u.spendAmmo(thrown, gnoll, WEAPONS, roll));
        assertEquals(0, u.spendAmmo(thrown, gnoll, WEAPONS, roll));
        assertEquals(0, u.ammoLeft(stab, gnoll, WEAPONS, roll), "nothing left in hand");
    }

    @Test
    void theStatBlockCanSayHowMuch() {
        DndAttack bow = new DndAttack("Longbow", 4, "150/600 ft.", "1d8+2", "piercing");
        bow.setItem("longbow");
        bow.setLimits(null, null, 12);
        List<DndAttack> all = List.of(bow);
        CreatureUses.Pool pool = CreatureUses.poolFor(bow, all, WEAPONS);
        Function<String, Integer> never = dice -> { throw new AssertionError("a fixed count isn't rolled"); };
        assertEquals(12, new CreatureUses().ammoLeft(pool, all, WEAPONS, never));

        bow.setLimits(null, null, "unlimited");
        CreatureUses u = new CreatureUses();
        assertEquals(-1, u.ammoLeft(pool, all, WEAPONS, never));
        assertEquals(-1, u.spendAmmo(pool, all, WEAPONS, never), "never runs out");
    }

    // ==================== SAVING ====================

    @Test
    void theCountSurvivesARestart() {
        DndAttack breath = attack("Fire Breath", 3, null);
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        CreatureUses.Pool bow = CreatureUses.poolFor(named(gnoll, "Longbow"), gnoll, WEAPONS);
        CreatureUses u = new CreatureUses();
        u.spend(breath, 7);
        u.spendAmmo(bow, gnoll, WEAPONS, dice -> 9);

        CreatureUses back = CreatureUses.deserialize(u.serialize());
        assertEquals(2, back.left(breath, 7));
        assertEquals(3, back.left(breath, 8), "and dawn still refills it");
        assertEquals(8, back.ammoLeft(bow, gnoll, WEAPONS, dice -> { throw new AssertionError("already rolled"); }));

        assertEquals("", new CreatureUses().serialize(), "a fresh creature saves nothing");
        assertTrue(CreatureUses.deserialize(null).isEmpty());
    }
}
