package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #179: the command a left-click on a target fills for a readied spell. */
class SpellTargetingTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void fillsTheCastWithTheTargetAndRoomForTheRollWords() {
        var bolt = SpellLoader.getSpell("fire_bolt");
        assertEquals("/combat cast fire_bolt Goblin ", SpellTargeting.command(bolt, "Goblin", 0));
        assertEquals("/combat cast fire_bolt \"Goblin Boss\" ", SpellTargeting.command(bolt, "Goblin Boss", 0),
                "a name with a space is quoted, as /combat cast reads it");
    }

    @Test
    void anUpcastPutsTheLevelAfterTheTarget() {
        var missile = SpellLoader.getSpell("magic_missile");
        assertEquals("/combat cast magic_missile Goblin level 2 ", SpellTargeting.command(missile, "Goblin", 2));
        assertEquals("/combat cast magic_missile Goblin ", SpellTargeting.command(missile, "Goblin", 1),
                "its own level is no upcast");
    }
}
