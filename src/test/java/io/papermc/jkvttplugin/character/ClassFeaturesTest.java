package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.combat.AttackHandler;
import io.papermc.jkvttplugin.combat.FeatureUse;
import io.papermc.jkvttplugin.combat.SneakAttack;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Level-1 class features that do something (#229). */
class ClassFeaturesTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    /** A fighter with the style picked (the pick is saved lower case, like every custom choice). */
    private static CharacterSheet fighter(String styleLabelStart) {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores(Ability.DEXTERITY, 16, Ability.STRENGTH, 16));
        var choice = f.getMainClass().getPlayerChoices().stream().filter(c -> c.id().equals("fighting_style")).findFirst().orElseThrow();
        String option = choice.pc().getOptions().stream().map(Object::toString)
                .filter(o -> o.startsWith(styleLabelStart.toLowerCase())).findFirst().orElseThrow();
        f.setCustomChoice("fighting_style", option);
        f.applyChoiceGrants();
        return f;
    }

    @Test
    void archeryAddsTwoToRangedAttacks() {
        CharacterSheet f = fighter("archery");
        var bow = WeaponLoader.getWeapon("longbow");
        var sword = WeaponLoader.getWeapon("longsword");
        assertEquals(3 + 2 + 2, AttackHandler.calculatePlayerAttackMod(f, bow), "+3 DEX +2 prof +2 Archery");
        assertTrue(AttackHandler.buildPlayerModBreakdown(f, bow).contains("+2[Archery]"));
        assertEquals(3 + 2, AttackHandler.calculatePlayerAttackMod(f, sword), "melee: no Archery");
    }

    @Test
    void defenseIsPlusOneInArmorOnly() {
        CharacterSheet f = fighter("defense");
        int unarmored = f.getArmorClass();
        assertEquals(10 + 3, unarmored, "no armor: no Defense");
        f.equipArmor(ArmorLoader.getArmor("chain_mail"));
        assertEquals(16 + 1, f.getArmorClass());
    }

    @Test
    void duelingIsOneHandedMeleeDamage() {
        CharacterSheet f = fighter("dueling");
        assertEquals(2, f.bonusDamageFor("melee_one_handed"));
        assertEquals("+2[Dueling]", f.bonusDamageBreakdownFor("melee_one_handed"));
        assertEquals(0, fighter("archery").bonusDamageFor("melee_one_handed"));
    }

    @Test
    void theOtherStylesAreFlags() {
        assertTrue(fighter("great weapon").hasPassiveFlag("reroll_low_damage"));
        assertTrue(fighter("two-weapon").hasPassiveFlag("offhand_ability_damage"));
        assertFalse(fighter("archery").hasPassiveFlag("reroll_low_damage"));
    }

    @Test
    void secondWindHealsFromItsResource() {
        CharacterSheet f = fighter("defense");
        var sw = f.getFeature("second_wind");
        assertNotNull(sw);
        assertTrue(FeatureUse.handles(sw));
        assertEquals("bonus_action", sw.getActivation());
        assertNotNull(f.getResource(sw.getCostResource()), "second_wind finds the \"Second Wind\" resource");
    }

    @Test
    void sneakAttackScalesAndJoinsTheDamage() {
        CharacterSheet r = character("halfling", "lightfoot", "rogue", "criminal", scores(Ability.DEXTERITY, 16));
        assertEquals("1d6", r.sneakAttack().getValue());
        assertEquals("1d8+1d6+3", SneakAttack.addDice("1d8+3", "1d6"));
        assertEquals("1d4+1d6-1", SneakAttack.addDice("1d4-1", "1d6"));
        assertEquals("1d6+1d6", SneakAttack.addDice("1d6", "1d6"));
        assertNull(fighter("archery").sneakAttack(), "not a rogue");
    }

    /**
     * Sneak Attack rides on the hit until its damage lands: a Shield that turns the hit into a miss
     * clears the hit and the Sneak Attack with it, so it isn't spent; a new hit starts without one.
     */
    @Test
    void sneakAttackGoesWithTheHit() {
        var ts = new io.papermc.jkvttplugin.combat.TurnState(30, null);
        var use = new SneakAttack.Use("1d6", "Sneak Attack", "advantage", "piercing", false);
        ts.markAttackHit(java.util.UUID.randomUUID(), 3, "+3[DEX]", false);
        ts.setPendingSneak(use);
        ts.clearDamagePending(); // the hit became a miss
        assertNull(ts.getPendingSneak());
        ts.setPendingSneak(use);
        ts.markAttackHit(java.util.UUID.randomUUID(), 3, "+3[DEX]", false); // the next hit
        assertNull(ts.getPendingSneak());
    }

    @Test
    void paladinFeatures() {
        CharacterSheet p = character("human", null, "paladin", "acolyte", scores(Ability.CHARISMA, 16));
        var loh = p.getFeature("lay_on_hands");
        assertTrue(loh.getHeal().fromPool());
        assertTrue(loh.getHeal().notTypes().contains("undead"));
        assertNotNull(p.getResource(loh.getCostResource()));
        var ds = p.getFeature("divine_sense");
        assertTrue(ds.getSense().creatureTypes().containsAll(java.util.Set.of("celestial", "fiend", "undead")));
        assertEquals(4, p.getResource(ds.getCostResource()).getMax(), "1 + CHA 3");
    }

    @Test
    void divineSenseSaysWhichWay() {
        Location here = new Location(null, 0, 64, 0);
        assertEquals("north", FeatureUse.direction(here, new Location(null, 0, 64, -20)));
        assertEquals("south-east", FeatureUse.direction(here, new Location(null, 10, 64, 10)));
        assertEquals("west", FeatureUse.direction(here, new Location(null, -20, 64, 1)));
    }
}
