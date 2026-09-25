package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one reach rule (Reach): spells and weapons, in and out of a fight, 5 ft of slack. It used to
 * be three copies with 2.5 ft in a fight and 5 ft out of one.
 */
class ReachTest {

    private static DndSpell spell(String id) {
        TestContent.load();
        DndSpell s = SpellLoader.getSpell(id);
        assertNotNull(s, id);
        return s;
    }

    @Test
    void touchReachesOneBlockPastFiveFeet() {
        DndSpell cure = spell("cure_wounds");
        assertNull(Reach.spell(5, "Zek", false, cure));
        assertNull(Reach.spell(10, "Zek", false, cure), "5 ft of slack past Touch");
        assertNotNull(Reach.spell(18, "Zek", false, cure), "18 ft is out of reach (the playtest case)");
    }

    @Test
    void aFeetRangeGetsTheSameSlack() {
        DndSpell fireBolt = spell("fire_bolt"); // 120 feet
        assertNull(Reach.spell(125, "Goblin", false, fireBolt));
        String why = Reach.spell(130, "Goblin", false, fireBolt);
        assertNotNull(why);
        assertTrue(why.contains("Goblin is about 130 ft away"), why);
    }

    @Test
    void aTargetedSelfSpellOnlyReachesTheCaster() {
        TestContent.load();
        DndSpell self = SpellLoader.getAllSpells().stream()
                .filter(s -> s.getRangeFeet() == 0 && !s.isAoe()).findFirst().orElseThrow();
        assertNull(Reach.spell(0, "me", true, self));
        assertNotNull(Reach.spell(5, "Goblin", false, self), self.getName() + " at someone else");
    }

    @Test
    void anAreaFromYouIsNotAReachCheck() {
        DndSpell burningHands = spell("burning_hands"); // Self (15-foot cone): the cone aims itself
        assertNull(Reach.spell(10, "Goblin", false, burningHands));
    }

    @Test
    void unknownDistanceNeverRefuses() {
        assertNull(Reach.spell(-1, "Goblin", false, spell("cure_wounds")), "other world / no position: don't block");
    }

    @Test
    void weaponsUseReachInMeleeAndLongRangeWhenShot() {
        TestContent.load();
        var longsword = WeaponLoader.getWeapon("longsword");
        assertNull(Reach.weapon(10, "Goblin", longsword), "5 ft reach + 5 ft slack");
        assertNotNull(Reach.weapon(15, "Goblin", longsword));
        var longbow = WeaponLoader.getWeapon("longbow"); // 150/600
        assertNull(Reach.weapon(600, "Goblin", longbow));
        assertNotNull(Reach.weapon(700, "Goblin", longbow));
        assertNull(Reach.weapon(10, "Goblin", null), "unarmed: 5 ft");
    }

    @Test
    void aCreatureAttackReadsItsReach() {
        var bite = new io.papermc.jkvttplugin.data.model.DndAttack();
        bite.setName("Bite");
        bite.setReach("5 ft");
        assertNull(Reach.creatureAttack(10, "Zek", bite));
        String why = Reach.creatureAttack(20, "Zek", bite);
        assertNotNull(why);
        assertTrue(why.contains("Bite reaches 5 ft"), why);
    }
}
