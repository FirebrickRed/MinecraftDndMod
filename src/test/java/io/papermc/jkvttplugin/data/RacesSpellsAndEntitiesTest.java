package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions in races, spells and stat blocks that shipped once and must not come back. */
class RacesSpellsAndEntitiesTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static InnateSpell innate(java.util.List<InnateSpell> list, String id) {
        return list.stream().filter(i -> i.getSpellId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no innate " + id));
    }

    /** `type: cantrip` wasn't a read key, so these were 0-use leveled spells that could never be cast. */
    @Test
    void racialCantripsAreCastable() {
        InnateSpell thaumaturgy = innate(RaceLoader.getRace("tiefling").getInnateSpells(), "thaumaturgy");
        assertTrue(thaumaturgy.isCantrip());
        assertTrue(thaumaturgy.canCast());

        InnateSpell illusion = innate(RaceLoader.getRace("gnome").getSubraces().get("forest_gnome").getInnateSpells(), "minor_illusion");
        assertTrue(illusion.isCantrip());
        assertTrue(illusion.canCast());
    }

    /** Races read tool_proficiencies now; the rock gnome's Tinker grants tinker's tools. */
    @Test
    void rockGnomeHasTinkersTools() {
        assertTrue(RaceLoader.getRace("gnome").getSubraces().get("rock_gnome").getToolProficiencies().contains("tinkers_tools"));
    }

    /** A fire genasi monk (the first player to ask for one) gets the element's traits, not just the name. */
    @Test
    void fireGenasiMonk() {
        CharacterSheet c = character("genasi", "fire", "monk", "acolyte", scores(Ability.DEXTERITY, 16, Ability.WISDOM, 14));
        assertEquals(30, c.getSpeed());
        assertTrue(c.getDamageResistances().contains("fire"));
        InnateSpell flame = innate(c.getInnateSpells(), "produce_flame");
        assertTrue(flame.isCantrip() && flame.canCast(), "Produce Flame from level 1");
        assertEquals(3, innate(c.getInnateSpells(), "burning_hands").getLevelRequirement());
        assertEquals(60, RaceLoader.getRace("genasi").getDarkvision());
    }

    /** Each element overrides the right movement: air walks faster, water swims. */
    @Test
    void genasiElements() {
        var subraces = RaceLoader.getRace("genasi").getSubraces();
        assertEquals(java.util.Set.of("air", "earth", "fire", "water"), subraces.keySet());
        assertEquals(35, character("genasi", "air", "monk", "acolyte", scores()).getSpeed());
        assertEquals(30, subraces.get("water").getSwimmingSpeed());
        assertTrue(character("genasi", "water", "monk", "acolyte", scores()).getDamageResistances().contains("acid"));
    }

    /** Frostbite was defined twice and resolved as an attack; it's a CON save. */
    @Test
    void frostbiteIsASaveNotAnAttack() {
        DndSpell frostbite = SpellLoader.getSpell("frostbite");
        assertTrue(frostbite.requiresSave(), "save");
        assertFalse(frostbite.hasAttack(), "not an attack");
        assertEquals("constitution", frostbite.getSaveType().toLowerCase());
    }

    /** Upcasting spends the slot the spellbook showed (#152). */
    @Test
    void spellCostUsesTheUpcastSlot() {
        CharacterSheet wizard = character("human", null, "wizard", "sage", scores(Ability.INTELLIGENCE, 16));
        DndSpell magicMissile = SpellLoader.getSpell("magic_missile");
        assertNotNull(magicMissile);
        SpellCost normal = SpellCost.of(wizard, magicMissile);
        assertEquals(SpellCost.Kind.SLOT, normal.kind());
        assertEquals(1, normal.level());
        assertEquals(SpellCost.Kind.NONE_LEFT_SLOT, SpellCost.of(wizard, magicMissile, 2).kind(), "no 2nd-level slots at level 1");

        int before = wizard.getSpellSlotsRemaining(1);
        normal.spend(wizard, magicMissile);
        assertEquals(before - 1, wizard.getSpellSlotsRemaining(1));
        assertEquals(SpellCost.Kind.FREE, SpellCost.of(wizard, SpellLoader.getSpell("fire_bolt")).kind(), "cantrips are free");
    }

    // ---------- entity stat blocks ----------

    /** Stat-block skills are the whole printed bonus; an unlisted skill is the ability modifier. */
    @Test
    void entitySkillBonuses() {
        DndEntity guard = EntityLoader.getEntity("town_guard");
        assertEquals(2, guard.getSkillBonus(Skill.PERCEPTION));
        assertTrue(guard.listsSkill(Skill.PERCEPTION));
        assertEquals(1, guard.getSkillBonus(Skill.STEALTH), "DEX 12 → +1");

        DndEntity balin = EntityLoader.getEntity("balin_blacksmith");
        assertEquals(1, balin.getSkillBonus(Skill.DECEPTION), "CHA 13, no listed skill — the one-shot's Act I relies on +1");
        assertFalse(balin.listsSkill(Skill.DECEPTION));
    }

    /** Alira used to quietly have 10 HP; her stat block rolls hit dice. */
    @Test
    void aliraRollsHitDice() {
        DndEntity alira = EntityLoader.getEntity("alira");
        assertNotNull(alira, "alira");
        assertNotNull(alira.getHitDice(), "hit_dice, not a 10 HP default");
    }

    /**
     * Out of combat (#152), a harmful spell asks the DM first (start a fight, let it happen, or roll
     * at a thing); healing and utility spells don't need anyone's say.
     */
    @Test
    void harmfulSpellsAreTheOnesThatAskTheDm() {
        TestContent.load();
        for (String id : java.util.List.of("fire_bolt", "sacred_flame", "magic_missile", "charm_person")) {
            assertTrue(io.papermc.jkvttplugin.combat.OutOfCombatAttack.isHarmful(SpellLoader.getSpell(id)), id);
        }
        for (String id : java.util.List.of("cure_wounds", "light")) {
            assertFalse(io.papermc.jkvttplugin.combat.OutOfCombatAttack.isHarmful(SpellLoader.getSpell(id)), id);
        }
    }
}
