package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.UnarmedStrike;
import io.papermc.jkvttplugin.effect.WeaponAbility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Martial Arts from YAML (#221): the unarmed die and DEX, monk weapons using DEX and the bigger die,
 * and the bonus-action attack (which also fixes two-weapon fighting's off-hand attack).
 */
class MartialArtsTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndWeapon w(String id) {
        DndWeapon weapon = WeaponLoader.getWeapon(id);
        assertNotNull(weapon, id);
        return weapon;
    }

    /** DEX 16, STR 10: genasi, so the scores aren't moved by a fixed racial bonus. */
    private static CharacterSheet monk() {
        return character("genasi", "fire", "monk", "acolyte", scores(Ability.DEXTERITY, 16, Ability.WISDOM, 14));
    }

    @Test
    void monkUnarmedStrikeIsD4PlusDex() {
        CharacterSheet m = monk();
        assertEquals(Ability.DEXTERITY, AttackHandler.resolveAttackAbility(m, null));
        assertEquals("1d4+3", AttackHandler.buildPlayerDamageString(m, null));
        assertEquals(3 + 2, AttackHandler.calculatePlayerAttackMod(m, null), "DEX + proficiency");
        assertEquals("+3[DEX] +2[Prof]", AttackHandler.buildPlayerModBreakdown(m, null));
    }

    @Test
    void armorTurnsMartialArtsOff() {
        CharacterSheet m = monk();
        m.equipArmor(ArmorLoader.getArmor("leather_armor"));
        assertEquals(Ability.STRENGTH, AttackHandler.resolveAttackAbility(m, null));
        assertEquals("1", AttackHandler.buildPlayerDamageString(m, null), "back to 1 + STR (STR 10)");
    }

    @Test
    void everyoneElseStillPunchesForOnePlusStr() {
        CharacterSheet f = character("genasi", "fire", "fighter", "acolyte", scores(Ability.STRENGTH, 14, Ability.DEXTERITY, 16));
        assertEquals(Ability.STRENGTH, AttackHandler.resolveAttackAbility(f, null));
        assertEquals("3", AttackHandler.buildPlayerDamageString(f, null));
    }

    /** A quarterstaff isn't finesse, but it's a monk weapon: DEX, and its own d6 beats the d4. */
    @Test
    void monkWeaponsUseDex() {
        CharacterSheet m = monk();
        assertEquals(Ability.DEXTERITY, AttackHandler.resolveAttackAbility(m, w("quarterstaff")));
        assertEquals("1d6+3", AttackHandler.buildPlayerDamageString(m, w("quarterstaff")));
        assertEquals("1d4+3", AttackHandler.buildPlayerDamageString(m, w("club")), "d4 vs d4: stays");

        CharacterSheet f = character("genasi", "fire", "fighter", "acolyte", scores(Ability.DEXTERITY, 16));
        assertEquals(Ability.STRENGTH, AttackHandler.resolveAttackAbility(f, w("quarterstaff")), "only a monk");
    }

    /** Excluded properties and tags, checked on a hand-built rule (no shipped simple weapon is heavy). */
    @Test
    void excludedPropertiesAreNotCovered() {
        WeaponAbility rule = WeaponAbility.parse(Map.of("weapons", List.of("simple_melee_weapon"),
                "exclude_properties", List.of("light"), "ability", List.of("dexterity")));
        assertTrue(rule.getProblems().isEmpty(), rule.getProblems().toString());
        assertTrue(rule.covers(w("quarterstaff")));
        assertFalse(rule.covers(w("club")), "light is excluded");
        assertFalse(rule.covers(w("longsword")), "martial, not in the tag");
    }

    @Test
    void theDieGrowsWithLevel() {
        UnarmedStrike u = UnarmedStrike.parse(Map.of("damage_by_level", List.of("1d4", "1d4", "1d4", "1d4", "1d6")));
        assertEquals("1d4", u.dieAt(1));
        assertEquals("1d6", u.dieAt(5));
        assertEquals("1d6", u.dieAt(20), "past the end, the last one holds");
        assertEquals(List.of(Ability.STRENGTH), u.getAbilities(), "defaults to STR");
    }

    @Test
    void typosAreReported() {
        assertFalse(UnarmedStrike.parse(Map.of("damage_by_level", List.of("d4"))).getProblems().isEmpty());
        assertFalse(WeaponAbility.parse(Map.of("weapons", List.of("club"), "ability", List.of("dex"),
                "min_die", "bigger")).getProblems().isEmpty());
    }

    // ---------- the bonus-action attack ----------

    @Test
    void monkBonusStrikeComesAfterTheAttackAction() {
        CharacterSheet m = monk();
        BonusAttack.Verdict before = BonusAttack.check(m, null, null, null, false, false, false, true);
        assertFalse(before.allowed());
        assertTrue(before.refusal().contains("Attack action"), before.refusal());

        BonusAttack.Verdict after = BonusAttack.check(m, null, null, null, true, false, false, true);
        assertTrue(after.allowed(), after.refusal());
        assertEquals(BonusAttack.Kind.FEATURE, after.kind());

        assertFalse(BonusAttack.check(m, null, null, null, true, true, false, true).allowed(), "bonus action already used");
        assertFalse(BonusAttack.check(m, null, null, null, true, false, true, true).allowed(), "damage still to roll");
    }

    @Test
    void aShieldStopsTheMonkBonusStrike() {
        CharacterSheet m = monk();
        m.equipShield(ArmorLoader.getArmor("shield"));
        assertFalse(BonusAttack.check(m, null, null, null, true, false, false, true).allowed());
    }

    /** Two-weapon fighting: the off-hand light weapon, and no positive ability modifier on the damage. */
    @Test
    void offHandAttackIsABonusActionWithoutTheModifier() {
        CharacterSheet f = character("genasi", "fire", "fighter", "acolyte", scores(Ability.DEXTERITY, 16));
        DndWeapon dagger = w("dagger");
        BonusAttack.Verdict v = BonusAttack.check(f, dagger, w("shortsword"), dagger, true, false, false, true);
        assertTrue(v.allowed(), v.refusal());
        assertEquals(BonusAttack.Kind.OFF_HAND, v.kind());
        assertEquals("1d4", AttackHandler.buildPlayerDamageString(f, dagger, true));
        assertEquals("1d4+3", AttackHandler.buildPlayerDamageString(f, dagger, false));

        assertFalse(BonusAttack.check(f, w("longsword"), w("longsword"), dagger, true, false, false, true).allowed(),
                "the attack has to be with the off-hand weapon");
        assertFalse(BonusAttack.check(f, null, dagger, dagger, true, false, false, true).allowed(), "a fighter has no bonus punch");
    }

    // ---------- #259: "unarmed or wielding only monk weapons", and what the bonus strike follows ----------

    /** A longsword in hand turns Martial Arts off: a punch is back to 1 + STR. */
    @Test
    void aNonMonkWeaponInHandTurnsMartialArtsOff() {
        CharacterSheet m = monk();
        m.setHeldWeapons(w("longsword"), null);
        assertEquals(Ability.STRENGTH, AttackHandler.resolveAttackAbility(m, null));
        assertEquals("1", AttackHandler.buildPlayerDamageString(m, null), "1 + STR 0");
        assertEquals(List.of("Martial Arts is off: you're holding a Longsword"), AttackHandler.weaponRuleNotes(m, null));

        m.setHeldWeapons(null, w("longsword")); // the off hand counts too
        assertEquals("1", AttackHandler.buildPlayerDamageString(m, null));

        m.setHeldWeapons(w("quarterstaff"), w("dagger")); // monk weapons in both hands: still on
        assertEquals("1d4+3", AttackHandler.buildPlayerDamageString(m, null));
    }

    /** A shortsword loses DEX-by-Martial-Arts and the die when the other hand holds a longsword. */
    @Test
    void aMonkWeaponBesideANonMonkWeaponIsJustAWeapon() {
        CharacterSheet m = monk();
        DndWeapon mace = w("mace"); // simple melee, STR only: DEX comes from Martial Arts alone
        m.setHeldWeapons(mace, null);
        assertEquals(Ability.DEXTERITY, AttackHandler.resolveAttackAbility(m, mace));
        m.setHeldWeapons(mace, w("longsword"));
        assertEquals(Ability.STRENGTH, AttackHandler.resolveAttackAbility(m, mace));
        assertEquals(List.of("Martial Arts is off: you're holding a Longsword"), AttackHandler.weaponRuleNotes(m, mace));
    }

    /** The prompt says what Martial Arts changed, or that the weapon's own die is the better one. */
    @Test
    void theAttackSaysWhatMartialArtsDoes() {
        CharacterSheet m = monk();
        m.setHeldWeapons(null, null);
        assertEquals(List.of("Martial Arts: 1d4 + DEX instead of 1 + STR"), AttackHandler.weaponRuleNotes(m, null));

        DndWeapon staff = w("quarterstaff");
        m.setHeldWeapons(staff, null);
        assertEquals(List.of("The Quarterstaff's own 1d6 beats the Martial Arts 1d4", "Martial Arts: DEX instead of STR"),
                AttackHandler.weaponRuleNotes(m, staff));
        assertTrue(AttackHandler.buildPlayerDamageString(m, staff).startsWith("1d6"));

        DndWeapon dagger = w("dagger"); // 1d4 either way, finesse already: nothing to say
        m.setHeldWeapons(dagger, null);
        assertTrue(AttackHandler.weaponRuleNotes(m, dagger).isEmpty(), AttackHandler.weaponRuleNotes(m, dagger).toString());

        CharacterSheet fighter = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 16));
        assertTrue(AttackHandler.weaponRuleNotes(fighter, null).isEmpty(), "no rule, no note");
        assertTrue(AttackHandler.weaponRuleNotes(fighter, staff).isEmpty());
    }

    /** The bonus strike follows an Attack action made with an unarmed strike or a monk weapon, nothing else. */
    @Test
    void theBonusStrikeFollowsAMonkAttack() {
        CharacterSheet m = monk();
        m.setHeldWeapons(null, null);
        // Even with bonus_attack_timing: any_time (needsAttackAction = false), it comes after.
        BonusAttack.Verdict before = BonusAttack.check(m, null, null, null, (String) null, false, false, false);
        assertFalse(before.allowed());
        assertTrue(before.refusal().contains("attack first"), before.refusal());

        assertTrue(BonusAttack.check(m, null, null, null, "unarmed", false, false, false).allowed());
        assertTrue(BonusAttack.check(m, null, null, null, "quarterstaff", false, false, false).allowed(), "a monk weapon");
        assertTrue(BonusAttack.check(m, null, null, null, "shortsword", false, false, false).allowed());

        BonusAttack.Verdict bow = BonusAttack.check(m, null, null, null, "shortbow", false, false, false);
        assertFalse(bow.allowed(), "a shortbow isn't a monk weapon");
        assertTrue(bow.refusal().contains("Shortbow"), bow.refusal());
    }

    /** Holding a longsword: no bonus strike, and the refusal says why. */
    @Test
    void noBonusStrikeWhileHoldingANonMonkWeapon() {
        CharacterSheet m = monk();
        m.setHeldWeapons(w("longsword"), null);
        BonusAttack.Verdict v = BonusAttack.check(m, null, w("longsword"), null, "unarmed", false, false, false);
        assertFalse(v.allowed());
        assertTrue(v.refusal().contains("holding a Longsword"), v.refusal());
    }

    /** Two-weapon fighting still follows the timing setting: it isn't tied to what the Attack action used. */
    @Test
    void offHandAttacksStillFollowTheTimingSetting() {
        CharacterSheet f = character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 16));
        DndWeapon dagger = w("dagger");
        assertTrue(BonusAttack.check(f, dagger, w("shortsword"), dagger, (String) null, false, false, false).allowed(), "any_time");
        assertFalse(BonusAttack.check(f, dagger, w("shortsword"), dagger, (String) null, false, false, true).allowed(), "after_attack_action");
    }

    /** The turn remembers what its Attack action was made with. */
    @Test
    void theTurnRemembersTheAttackActionsWeapon() {
        TurnState t = new TurnState(30, null);
        assertNull(t.getAttackActionWith());
        t.markAttackAction(1, "quarterstaff");
        assertEquals("quarterstaff", t.getAttackActionWith());
    }
}
