package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #252: a stat block's saving throws, and what damage and conditions do to it, come from its YAML.
 * A creature's save used to be its raw ability modifier everywhere, and it had nowhere to say it
 * resists or is immune to anything. (The halving / zeroing / doubling itself is DamageHandler's, and
 * needs a spawned creature: TEST_PLAN.)
 */
class CreatureStatBlockTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void aListedSaveIsUsedAndLabelledAsOne() {
        DndEntity fang = EntityLoader.getEntity("gnoll_fang_of_yeenoghu");
        assertEquals(4, fang.getSaveBonus(Ability.CONSTITUTION), "printed: Con +4 (the raw modifier is +2)");
        assertEquals("+4[CON save]", fang.getSaveLabel(Ability.CONSTITUTION));
        assertEquals(2, fang.getSaveBonus(Ability.WISDOM));
        assertEquals(3, fang.getSaveBonus(Ability.CHARISMA));
    }

    @Test
    void anUnlistedSaveIsTheRawModifier() {
        DndEntity fang = EntityLoader.getEntity("gnoll_fang_of_yeenoghu");
        assertEquals(3, fang.getSaveBonus(Ability.STRENGTH), "STR 17, not a proficient save");
        assertEquals("+3[STR]", fang.getSaveLabel(Ability.STRENGTH));
        DndEntity gnoll = EntityLoader.getEntity("gnoll");
        assertTrue(gnoll.getSavingThrows().isEmpty());
        assertEquals("+2[STR]", gnoll.getSaveLabel(Ability.STRENGTH));
    }

    @Test
    void theSkeletonKnowsWhatHurtsIt() {
        DndEntity skeleton = EntityLoader.getEntity("skeleton");
        assertEquals(Set.of("bludgeoning"), skeleton.getDamageVulnerabilities());
        assertEquals(Set.of("poison"), skeleton.getDamageImmunities());
        assertEquals(Set.of("poisoned"), skeleton.getConditionImmunities());
        assertTrue(skeleton.getDamageResistances().isEmpty());
    }

    @Test
    void aCreatureWithNoneOfItHasEmptySets() {
        DndEntity gnoll = EntityLoader.getEntity("gnoll");
        assertTrue(gnoll.getDamageImmunities().isEmpty());
        assertTrue(gnoll.getConditionImmunities().isEmpty());
    }
}
