package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import io.papermc.jkvttplugin.effect.FeatureParser;
import io.papermc.jkvttplugin.effect.RollTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * advantage_on / disadvantage_on are applied (#223). Before, they were parsed and read nowhere, so a
 * raging barbarian rolled Strength checks and saves normally. The roll paths (sheet checks, combat
 * saves, attacks, initiative) all ask the sheet these same questions.
 */
class EffectAdvantageTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static CharacterSheet ragingBarbarian() {
        CharacterSheet barb = character("human", null, "barbarian", "outlander", scores(Ability.STRENGTH, 16));
        barb.addEffect(barb.getFeature("rage").getApplyTemplate().copy());
        return barb;
    }

    @Test
    void rageGivesAdvantageOnStrengthChecksAndSaves() {
        CharacterSheet barb = ragingBarbarian();
        assertEquals("Rage", barb.effectAdvantageSource(RollTags.check(Ability.STRENGTH)), "Athletics is a STR check");
        assertEquals("Rage", barb.effectAdvantageSource(RollTags.save(Ability.STRENGTH)));
        assertNull(barb.effectAdvantageSource(RollTags.check(Ability.DEXTERITY)));
        assertNull(barb.effectAdvantageSource(RollTags.save(Ability.WISDOM)));
        assertNull(barb.effectAdvantageSource(RollTags.attack()), "Rage isn't advantage on attacks");
    }

    @Test
    void notRagingNoAdvantage() {
        CharacterSheet barb = character("human", null, "barbarian", "outlander", scores(Ability.STRENGTH, 16));
        assertNull(barb.effectAdvantageSource(RollTags.check(Ability.STRENGTH)));
        barb.addEffect(barb.getFeature("rage").getApplyTemplate().copy());
        barb.removeEffect("rage");
        assertNull(barb.effectAdvantageSource(RollTags.save(Ability.STRENGTH)), "gone when the rage ends");
    }

    /** The broad tags: "saves" covers every save, "dex_checks" covers initiative. */
    @Test
    void broadTagsCoverTheSpecificRoll() {
        ActiveEffect wary = FeatureParser.parseFeatures(List.of(Map.of("id", "wary", "name", "Wary",
                "apply", Map.of("effects", Map.of("advantage_on", List.of("dex_checks"), "disadvantage_on", List.of("saves"))))))
                .get(0).getApplyTemplate().copy();
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        c.addEffect(wary);
        assertEquals("Wary", c.effectAdvantageSource(RollTags.initiative()));
        assertEquals("Wary", c.effectDisadvantageSource(RollTags.save(Ability.CHARISMA)));
        assertNull(c.effectAdvantageSource(RollTags.check(Ability.STRENGTH)));
    }

    @Test
    void tagVocabulary() {
        assertTrue(RollTags.ALL.containsAll(List.of("str_checks", "cha_saves", "checks", "saves", "attacks", "initiative")));
        assertFalse(RollTags.ALL.contains("strength_checks"), "abbreviations, like the roll labels");
    }
}
