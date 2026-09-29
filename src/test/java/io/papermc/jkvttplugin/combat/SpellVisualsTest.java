package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.DamageTypeLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import org.bukkit.Particle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #230: every spell gets a look from DMContent/DamageTypes.yml, and a shape from what it does. */
class SpellVisualsTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void shapeComesFromWhatTheSpellDoes() {
        assertEquals("bolt", SpellVisuals.shapeFor(SpellLoader.getSpell("fire_bolt")), "an attack roll flies");
        assertEquals("bolt", SpellVisuals.shapeFor(SpellLoader.getSpell("magic_missile")), "so does an auto-hit");
        assertEquals("burst", SpellVisuals.shapeFor(SpellLoader.getSpell("sacred_flame")), "a save bursts on the target");
        assertEquals("glow", SpellVisuals.shapeFor(SpellLoader.getSpell("cure_wounds")));
        assertEquals("glow", SpellVisuals.shapeFor(SpellLoader.getSpell("bless")));
        assertEquals("burst", SpellVisuals.shapeFor(SpellLoader.getSpell("bane")), "a save spell with an effect still bursts");
        assertNull(SpellVisuals.shapeFor(SpellLoader.getSpell("burning_hands")), "an area spell's aim preview already shows it");
    }

    @Test
    void lookComesFromTheDamageTypeThenHealingOrBuff() {
        assertEquals(Particle.FLAME, SpellVisuals.lookFor(SpellLoader.getSpell("fire_bolt")).particle());
        assertEquals(Particle.END_ROD, SpellVisuals.lookFor(SpellLoader.getSpell("sacred_flame")).particle());
        assertEquals(Particle.HEART, SpellVisuals.lookFor(SpellLoader.getSpell("cure_wounds")).particle());
        assertEquals(DamageTypeLoader.lookFor("buff"), SpellVisuals.lookFor(SpellLoader.getSpell("bless")));
        var bane = SpellVisuals.lookFor(SpellLoader.getSpell("bane"));
        assertTrue(bane.isDust());
        assertEquals(0x6b1e1e, bane.color().asRGB(), "its own visual: wins");
    }

    @Test
    void aBadParticleIsReportedNotThrown() {
        try {
            assertNull(DamageTypeLoader.parseLook("NOT_A_PARTICLE", null, "test"));
            assertNull(DamageTypeLoader.parseLook("BLOCK", null, "test"), "needs block data we don't give");
            assertTrue(DamageTypeLoader.problems().stream().anyMatch(p -> p.contains("NOT_A_PARTICLE")));
            assertEquals(Particle.DUST, DamageTypeLoader.parseLook("dust", "#ff0000", "test").particle());
        } finally {
            // Those made-up problems would fail the clean-load check in a later test: reload clears them.
            DamageTypeLoader.loadAll(new java.io.File("DMContent/DamageTypes.yml"));
        }
    }
}
