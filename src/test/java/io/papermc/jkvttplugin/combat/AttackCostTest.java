package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * /combat attack works out what an attack costs: the Action first (starting the Attack action), then
 * the Attack action's further attacks, then the bonus action when a bonus attack fits. "bonus" asks
 * for the bonus action outright. Timing follows combat.bonus_attack_timing: any time (BG3, default)
 * or only after the Attack action (the tabletop rule).
 */
class AttackCostTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndWeapon w(String id) { return WeaponLoader.getWeapon(id); }

    private static CharacterSheet monk() {
        return character("genasi", "fire", "monk", "acolyte", scores(Ability.DEXTERITY, 16));
    }

    private static CharacterSheet fighter() {
        return character("genasi", "fire", "fighter", "acolyte", scores(Ability.DEXTERITY, 16));
    }

    private static TurnState turn() { return new TurnState(30, null); }

    private static AttackCost.Decision attack(CharacterSheet c, DndWeapon weapon, DndWeapon main, DndWeapon off,
                                              TurnState t, boolean bonus, boolean tabletopTiming) {
        return AttackCost.decide(c, weapon, main, off, t, bonus, tabletopTiming);
    }

    @Test
    void firstAttackUsesTheAction() {
        AttackCost.Decision d = attack(fighter(), w("longsword"), w("longsword"), null, turn(), false, false);
        assertEquals(AttackCost.Kind.ACTION, d.kind());
    }

    /** After the Attack action, the monk's next unarmed strike is worked out as the bonus action. */
    @Test
    void secondAttackBecomesTheBonusStrike() {
        TurnState t = turn();
        t.useAction();
        t.markAttackAction(1);
        AttackCost.Decision d = attack(monk(), null, null, null, t, false, true);
        assertEquals(AttackCost.Kind.BONUS, d.kind());
        assertEquals("Martial Arts: bonus unarmed strike", d.source());
        assertFalse(d.offHand(), "a monk strike keeps its ability modifier");
    }

    @Test
    void offHandAfterTheMainHandAttack() {
        TurnState t = turn();
        t.useAction();
        t.markAttackAction(1);
        AttackCost.Decision d = attack(fighter(), w("dagger"), w("shortsword"), w("dagger"), t, false, true);
        assertEquals(AttackCost.Kind.BONUS, d.kind());
        assertTrue(d.offHand());
    }

    /** Nothing left: refused, and the refusal says why. */
    @Test
    void nothingLeft() {
        TurnState t = turn();
        t.useAction();
        t.markAttackAction(1);
        AttackCost.Decision d = attack(fighter(), w("longsword"), w("longsword"), null, t, false, false);
        assertFalse(d.allowed());
        assertTrue(d.refusal().startsWith("You've already used your Action"), d.refusal());
    }

    /** BG3-style (the default): the off-hand attack first, while the Action is still free. */
    @Test
    void bonusFirstWhenTheTableAllowsIt() {
        AttackCost.Decision d = attack(fighter(), w("dagger"), w("shortsword"), w("dagger"), turn(), true, false);
        assertEquals(AttackCost.Kind.BONUS, d.kind());
    }

    /** The tabletop rule: a bonus attack only after the Attack action, even when asked for. */
    @Test
    void bonusFirstRefusedUnderTheTabletopRule() {
        AttackCost.Decision d = attack(fighter(), w("dagger"), w("shortsword"), w("dagger"), turn(), true, true);
        assertFalse(d.allowed());
        assertTrue(d.refusal().contains("Attack action"), d.refusal());
    }

    /** Casting a spell with the Action isn't the Attack action: no Martial Arts strike, under either timing setting (#259). */
    @Test
    void aSpellIsNotTheAttackAction() {
        TurnState t = turn();
        t.useAction(); // cast something
        assertFalse(attack(monk(), null, null, null, t, false, true).allowed());
        AttackCost.Decision d = attack(monk(), null, null, null, t, false, false);
        assertFalse(d.allowed(), "any_time is for two-weapon fighting; the monk's strike follows a monk attack");
        assertTrue(d.refusal().contains("attack first"), d.refusal());
    }

    /** Extra Attack (#153): the Attack action's further attacks come before the bonus action. */
    @Test
    void extraAttacksComeFirst() {
        TurnState t = turn();
        t.useAction();
        t.markAttackAction(2);
        assertEquals(1, t.getAttacksLeftInAction());
        assertEquals(AttackCost.Kind.EXTRA_ATTACK, attack(monk(), null, null, null, t, false, false).kind());
        t.useExtraAttack();
        assertEquals(AttackCost.Kind.BONUS, attack(monk(), null, null, null, t, false, false).kind());
    }

    @Test
    void damageFirst() {
        TurnState t = turn();
        t.markAttackHit(java.util.UUID.randomUUID());
        assertFalse(attack(fighter(), w("longsword"), w("longsword"), null, t, false, false).allowed());
    }
}
