package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.AttackHandler;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.TagRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Magic weapons (#188): a base weapon plus a magic block, and the attack math that reads it. */
class MagicWeaponTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndWeapon w(String id) {
        DndWeapon weapon = WeaponLoader.getWeapon(id);
        assertNotNull(weapon, id);
        return weapon;
    }

    @Test
    void aPlusTwoLongswordInheritsTheLongsword() {
        DndWeapon sword = w("longsword_plus_2");
        assertEquals("Longsword +2", sword.getName(), "named from the base");
        assertEquals(w("longsword").getDamage(), sword.getDamage());
        assertEquals(w("longsword").getDamageType(), sword.getDamageType());
        assertEquals(w("longsword").getProperties(), sword.getProperties());
        assertEquals("martial", sword.getCategory());
        assertEquals("rare", sword.getRarity());
        assertEquals(2, sword.getAttackBonus());
        assertEquals(2, sword.getDamageBonus());
        assertTrue(sword.isMagic());
        assertNull(sword.getCost(), "a longsword's 15 gp isn't inherited");
    }

    @Test
    void rangedMagicWeaponsKeepRangeAndAmmunition() {
        DndWeapon bow = w("longbow_plus_1");
        assertEquals(w("longbow").getNormalRange(), bow.getNormalRange());
        assertEquals(w("longbow").getAmmunition(), bow.getAmmunition(), "still fires arrows");
        assertTrue(bow.isRanged());
    }

    /** Or every "choose any martial weapon" starting-kit pick would offer a Longsword +3. */
    @Test
    void magicWeaponsStayOutOfTheCategoryTags() {
        assertTrue(TagRegistry.itemsFor("martial_weapon").contains("longsword"));
        assertFalse(TagRegistry.itemsFor("martial_weapon").contains("longsword_plus_3"));
        assertFalse(TagRegistry.itemsFor("martial_melee_weapon").contains("vicious_greataxe"));
    }

    /** Proficiency comes from the base: an elf's "longsword" proficiency covers a Longsword +2. */
    @Test
    void proficiencyComesFromTheBase() {
        CharacterSheet fighter = character("human", null, "fighter", "soldier", scores());
        assertTrue(w("longsword_plus_2").isProficient(fighter.getWeaponProficiencies()), "martial weapons");

        CharacterSheet elfWizard = character("elf", "high_elf", "wizard", "sage", scores());
        assertTrue(w("longsword_plus_2").isProficient(elfWizard.getWeaponProficiencies()), "Elf Weapon Training: longsword");
        assertFalse(w("greataxe_plus_1").isProficient(elfWizard.getWeaponProficiencies()));
    }

    @Test
    void theBonusReachesAttackAndDamage() {
        CharacterSheet fighter = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 16));
        assertEquals(3 + 2 + 2, AttackHandler.calculatePlayerAttackMod(fighter, w("longsword_plus_2")), "STR +3, prof +2, magic +2");
        assertEquals("1d8+5", AttackHandler.buildPlayerDamageString(fighter, w("longsword_plus_2")), "STR +3, magic +2");
        assertTrue(AttackHandler.buildPlayerModBreakdown(fighter, w("longsword_plus_2")).contains("+2[Longsword +2]"));
        assertEquals("1d8+3", AttackHandler.buildPlayerDamageString(fighter, w("longsword")), "mundane unchanged");
    }

    /** DMG: the magic bonus applies even without proficiency. */
    @Test
    void theBonusAppliesWithoutProficiency() {
        CharacterSheet wizard = character("human", null, "wizard", "sage", scores(Ability.STRENGTH, 10));
        assertEquals(0 + 0 + 3, AttackHandler.calculatePlayerAttackMod(wizard, w("greataxe_plus_3")));
    }

    @Test
    void viciousWeaponsAddSevenOnANaturalTwenty() {
        DndWeapon vicious = w("vicious_longsword");
        assertEquals(7, vicious.getCritBonusDamage());
        assertEquals(0, vicious.getAttackBonus(), "no +N");
        assertEquals("rare", vicious.getRarity());
    }
}
