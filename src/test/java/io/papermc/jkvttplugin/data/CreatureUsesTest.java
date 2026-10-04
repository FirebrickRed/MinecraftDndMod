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
    private static final CreatureUses.DayRule H24 = CreatureUses.DayRule.HOURS_24, DAWN = CreatureUses.DayRule.DAWN;
    /** World time in ticks: 1,000 to the in-game hour, 24,000 to the day, a day starting at dawn. */
    private static final long HOUR = 1000, DAY = 24000;

    @Test
    void threePerDayIsSpentAfterThree() {
        DndAttack breath = attack("Fire Breath", 3, null);
        assertEquals("3/Day", breath.limitLabel());
        CreatureUses u = new CreatureUses();
        long now = 5 * DAY;
        for (int i = 0; i < 3; i++) { assertNull(u.refusal(breath, now, H24)); u.spend(breath, now, H24); }
        assertNotNull(u.refusal(breath, now, H24), "the fourth use");
        assertEquals(0, u.left(breath, now, H24));
    }

    /** The default (creatures.per_day: 24_hours): 24 in-game hours after the FIRST of those uses. */
    @Test
    void twentyFourHoursAfterTheFirstUseItIsBack() {
        DndAttack breath = attack("Fire Breath", 2, null);
        CreatureUses u = new CreatureUses();
        long evening = 5 * DAY + 14 * HOUR; // 8 pm (dawn is tick 0, 6 am)
        u.spend(breath, evening, H24);
        u.spend(breath, evening + 3 * HOUR, H24);
        assertEquals(0, u.left(breath, evening + 12 * HOUR, H24), "the next morning: dawn has passed, 24 hours haven't");
        assertTrue(u.refusal(breath, evening + 12 * HOUR, H24).contains("12 hours"), "and it says how long is left");
        assertEquals(0, u.left(breath, evening + 23 * HOUR, H24));
        assertEquals(2, u.left(breath, evening + 24 * HOUR, H24), "24 hours from the first use, not the second");
    }

    /** creatures.per_day: dawn. The same evening use is back the next morning. */
    @Test
    void withTheDawnRuleItIsBackAtDawn() {
        DndAttack breath = attack("Fire Breath", 1, null);
        CreatureUses u = new CreatureUses();
        long evening = 5 * DAY + 14 * HOUR;
        u.spend(breath, evening, DAWN);
        assertEquals(0, u.left(breath, evening + 9 * HOUR, DAWN), "still the same night");
        assertEquals(1, u.left(breath, 6 * DAY, DAWN), "dawn the next day");
        assertTrue(new CreatureUsesRefusal(breath).after(evening, DAWN).contains("dawn"));
    }

    /** A spent ability's refusal under a rule, for the wording. */
    private record CreatureUsesRefusal(DndAttack a) {
        String after(long at, CreatureUses.DayRule rule) {
            CreatureUses u = new CreatureUses();
            for (int i = 0; i < a.getUses(); i++) u.spend(a, at, rule);
            return u.refusal(a, at, rule);
        }
    }

    @Test
    void rechargeFiveSixWaitsForTheDmsD6() {
        DndAttack breath = attack("Fire Breath", null, "5-6");
        assertEquals("Recharge 5-6", breath.limitLabel());
        assertEquals(1, breath.getUses(), "a recharge ability is one use");
        CreatureUses u = new CreatureUses();
        List<DndAttack> all = List.of(breath);
        assertTrue(u.rechargesDue(all).isEmpty(), "nothing spent, nothing owed");
        assertNull(u.answerRecharge(breath, 6), "and no roll is taken for it");

        u.spend(breath, 1, H24);
        assertNotNull(u.refusal(breath, 1, H24));
        assertFalse(u.isRechargeDue(breath), "not until its next turn starts");

        assertEquals(all, u.rechargesDue(all), "the start of its turn: a d6 is owed");
        assertTrue(u.refusal(breath, 1, H24).contains("roll its recharge first"));
        assertTrue(u.answerRecharge(breath, 4).contains("still spent"));
        assertNull(u.answerRecharge(breath, 6), "one roll per turn: a second isn't taken");
        assertNotNull(u.refusal(breath, 1 + 3 * DAY, H24), "time doesn't bring it back, only the roll");

        u.rechargesDue(all); // its next turn
        assertTrue(u.answerRecharge(breath, 5).contains("recharged"));
        assertNull(u.refusal(breath, 1, H24));
        assertTrue(u.rechargesDue(all).isEmpty(), "ready again: no more rolls");

        assertEquals("Recharge 6", attack("Stomp", null, 6).limitLabel());
        assertEquals("Recharge 4-6", attack("Web", null, "4–6").limitLabel(), "a typed en dash is fine");
    }

    @Test
    void aFightStartsWithRechargeAbilitiesReady() {
        DndAttack breath = attack("Fire Breath", null, "5-6");
        CreatureUses u = new CreatureUses();
        u.spend(breath, 1, H24);
        u.recoverRolls(List.of(breath));
        assertNull(u.refusal(breath, 1, H24));
    }

    @Test
    void restsBringBackWhatTheyShould() {
        DndAttack shortOne = attack("Second Wind", null, "short_rest");
        DndAttack longOne = attack("Wail", 2, "long_rest");
        DndAttack daily = attack("Legendary Resistance", 3, null);
        List<DndAttack> all = List.of(shortOne, longOne, daily);
        CreatureUses u = new CreatureUses();
        u.spend(shortOne, 1, H24); u.spend(longOne, 1, H24); u.spend(longOne, 1, H24); u.spend(daily, 1, H24);
        assertNotNull(u.refusal(shortOne, 1, H24));
        assertNotNull(u.refusal(longOne, 1, H24));

        assertEquals(List.of("Second Wind"), u.rest(all, false), "a short rest: only the short-rest ability");
        assertNull(u.refusal(shortOne, 1, H24));
        assertNotNull(u.refusal(longOne, 1, H24));
        assertEquals(2, u.left(daily, 1, H24));

        u.rest(all, true);
        assertEquals(2, u.left(longOne, 1, H24), "a long rest: everything");
        assertEquals(3, u.left(daily, 1, H24), "X/Day too, without waiting out the day");
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
        assertNull(u.spend(bite, 1, H24));
        assertNull(u.refusal(bite, 1, H24));
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

    /** The DM's own roll at spawn (or a correction): the pool is whatever they say, with no further roll. */
    @Test
    void theDmCanSetWhatItCarries() {
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        CreatureUses.Pool bow = CreatureUses.poolFor(named(gnoll, "Longbow"), gnoll, WEAPONS);
        Function<String, Integer> never = dice -> { throw new AssertionError("set by hand: not rolled"); };
        CreatureUses u = new CreatureUses();
        u.setAmmo(bow.key(), 14);
        assertEquals(14, u.ammoLeft(bow, gnoll, WEAPONS, never));
        assertEquals(13, u.spendAmmo(bow, gnoll, WEAPONS, never));
        u.setAmmo(bow.key(), -1);
        assertEquals(-1, u.spendAmmo(bow, gnoll, WEAPONS, never), "unlimited");
    }

    /** The recharge d6 is a d6. */
    @Test
    void aRechargeRollIsOneToSix() {
        assertTrue(io.papermc.jkvttplugin.combat.RechargeRoll.isD6(1));
        assertTrue(io.papermc.jkvttplugin.combat.RechargeRoll.isD6(6));
        assertFalse(io.papermc.jkvttplugin.combat.RechargeRoll.isD6(0));
        assertFalse(io.papermc.jkvttplugin.combat.RechargeRoll.isD6(7));
    }

    // ==================== SAVING ====================

    @Test
    void theCountSurvivesARestart() {
        DndAttack breath = attack("Fire Breath", 3, null);
        List<DndAttack> gnoll = EntityLoader.getEntity("gnoll").getAttacks();
        CreatureUses.Pool bow = CreatureUses.poolFor(named(gnoll, "Longbow"), gnoll, WEAPONS);
        CreatureUses u = new CreatureUses();
        u.spend(breath, 7 * DAY, H24);
        u.spendAmmo(bow, gnoll, WEAPONS, dice -> 9);

        CreatureUses back = CreatureUses.deserialize(u.serialize());
        assertEquals(2, back.left(breath, 7 * DAY + HOUR, H24));
        assertEquals(3, back.left(breath, 8 * DAY, H24), "and the day still runs from when it was used");
        assertEquals(8, back.ammoLeft(bow, gnoll, WEAPONS, dice -> { throw new AssertionError("already rolled"); }));

        assertEquals("", new CreatureUses().serialize(), "a fresh creature saves nothing");
        assertTrue(CreatureUses.deserialize(null).isEmpty());
    }
}
