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

    /** A low score gives a negative save, listed or not: the Witherling's WIS 5 is -3. */
    @Test
    void aSaveCanBeNegative() {
        DndEntity witherling = EntityLoader.getEntity("gnoll_witherling");
        assertEquals(-3, witherling.getSaveBonus(Ability.WISDOM));
        assertEquals("-3[WIS]", witherling.getSaveLabel(Ability.WISDOM));

        DndEntity listed = new DndEntity();
        listed.setSavingThrows(java.util.Map.of(Ability.INTELLIGENCE, -2)); // proficient, but INT 3: -4 +2
        assertEquals(-2, listed.getSaveBonus(Ability.INTELLIGENCE));
        assertEquals("-2[INT save]", listed.getSaveLabel(Ability.INTELLIGENCE));
    }

    /** Skills were already stat-block totals: the Hunter's Stealth +4 and Perception +3. */
    @Test
    void skillsAreListedTheSameWay() {
        DndEntity hunter = EntityLoader.getEntity("gnoll_hunter");
        assertEquals(4, hunter.getSkillBonus(io.papermc.jkvttplugin.data.model.enums.Skill.STEALTH));
        assertEquals(2, hunter.getSkillBonus(io.papermc.jkvttplugin.data.model.enums.Skill.SLEIGHT_OF_HAND),
                "not listed: the DEX modifier");
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
