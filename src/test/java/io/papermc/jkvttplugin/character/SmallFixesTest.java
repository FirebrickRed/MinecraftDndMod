package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.EffectAuras;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.character;
import static io.papermc.jkvttplugin.TestContent.scores;
import static org.junit.jupiter.api.Assertions.*;

/** #247: the small playtest fixes that don't need a server. */
class SmallFixesTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void onlyACharacterWithSomethingToCastGetsASpellbook() {
        assertFalse(character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 15)).canCastSpells(),
                "a fighter has nothing to cast (the tile used to show anyway)");
        assertTrue(character("human", null, "wizard", "sage", scores(Ability.INTELLIGENCE, 15)).canCastSpells(),
                "a wizard has spell slots");
        assertTrue(character("tiefling", null, "fighter", "soldier", scores(Ability.STRENGTH, 15)).canCastSpells(),
                "a tiefling fighter has Thaumaturgy");
    }

    @Test
    void rageShowsARedAura() {
        var rage = ClassLoader.getClass("barbarian").getFeatures().stream()
                .filter(f -> f.getId().equals("rage")).findFirst().orElseThrow();
        assertEquals("red", rage.getApplyTemplate().getAura());
        assertEquals("red", rage.getApplyTemplate().copy().getAura(), "the copy put on the barbarian keeps it");
        assertNotNull(EffectAuras.color("red"));
        assertNotNull(EffectAuras.color("#ff3300"));
        assertNull(EffectAuras.color("blurple"), "the content check warns about this");
    }
}
