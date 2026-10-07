package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #265: an attack roll gives a Hidden attacker away, out of a fight as in one. Out of a fight the spell
 * attack kept Hidden's advantage and left the caster Hidden, so every later attack had it too.
 *
 * <p>The mechanic is {@link Combatant#afterAttackRoll}, the one a fight already uses. Rolling a real
 * out-of-combat cast needs a player and the DM's approval (TEST_PLAN); here it's the mechanic itself, and
 * where the out-of-combat path calls it.
 */
class HiddenAfterAttackTest {

    private static Combatant combatant(String name, String... conditions) {
        return Combatant.fromSavedData(UUID.randomUUID(), Combatant.CombatantType.PLAYER, name, name,
                10, 2, false, false, false, false, List.of(conditions), 0, 0, false, true);
    }

    private static String text(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    // ---------- the mechanic ----------

    @Test
    void anAttackRollEndsHidden() {
        Combatant zek = combatant("Zek", "hidden");
        assertTrue(zek.afterAttackRoll(null), "out of a fight: no session");
        assertFalse(zek.hasCondition("hidden"));
        assertFalse(zek.afterAttackRoll(null), "already revealed: nothing more to say");
    }

    @Test
    void someoneWhoIsNotHiddenIsUnaffected() {
        Combatant zek = combatant("Zek", "poisoned");
        assertFalse(zek.afterAttackRoll(null));
        assertTrue(zek.hasCondition("poisoned"), "only Hidden ends with the attack");
    }

    @Test
    void otherConditionsOutlastTheAttack() {
        Combatant zek = combatant("Zek", "hidden", "poisoned");
        assertTrue(zek.afterAttackRoll(null));
        assertTrue(zek.hasCondition("poisoned"));
        assertFalse(zek.hasCondition("hidden"));
    }

    /** The out-of-combat path's half: the line to say, for it to send to its own audience; nothing when nobody was revealed. */
    @Test
    void outOfAFightTheCallerIsHandedTheLineToSay() {
        Combatant zek = combatant("Zek", "hidden");
        Component line = OutOfCombatAttack.afterAttackRoll(zek);
        assertNotNull(line);
        assertEquals("Zek is no longer hidden: the attack gave them away.", text(line));
        assertFalse(zek.hasCondition("hidden"));
        assertNull(OutOfCombatAttack.afterAttackRoll(zek), "a second attack has nothing to reveal");
        assertNull(OutOfCombatAttack.afterAttackRoll(combatant("Borin")), "nor does an attacker who wasn't hidden");
    }

    /** In a fight nothing changed: a Help is still used up by the attack roll, and the same line is what's broadcast. */
    @Test
    void helpIsStillUsedUpByAnAttackRollInAFight() {
        Combatant zek = combatant("Zek");
        zek.setHelpedBy(combatant("Borin"));
        assertEquals("Borin", zek.getHelpedByName());
        assertFalse(zek.afterAttackRoll(null), "not hidden");
        assertNull(zek.getHelpedByName(), "the Help was for this roll");
        assertEquals("Zek is no longer hidden: the attack gave them away.", text(zek.noLongerHiddenLine()));
    }

    // ---------- where the out-of-combat path calls it ----------

    /**
     * Hidden ends when a roll is made, and only then. In both out-of-combat attack branches the call comes
     * straight after the roll resolves; everything that can stop a cast first (no roll words yet, out of
     * reach, the DM not having approved, the "not aiming at a creature" question) returns before it.
     */
    @Test
    void hiddenEndsOnlyOnceARollHasBeenMade() throws Exception {
        String src = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/OutOfCombatAttack.java")).replace("\r\n", "\n");

        // Both attack branches: the prompt-and-return for a missing roll, then the reveal, on consecutive lines.
        String atCreature = "if (r == null) { rollPrompt(player, sheet, spell, retry, adv); return true; }\n"
                + "            revealAfterAttackRoll(player, caster);";
        String atThing = "if (r == null) { rollPrompt(player, sheet, spell, retry, Advantage.NONE); return true; }\n"
                + "            revealAfterAttackRoll(player, CombatTargets.forPlayer(player).combatant());";
        assertTrue(src.contains(atCreature), "a hit or a miss at a creature reveals; a prompt doesn't");
        assertTrue(src.contains(atThing), "the same for an attack at a thing");
        assertEquals(2, count(src, "revealAfterAttackRoll(player,"), "and nowhere else");

        // cast() is where a cast is refused, sent to the DM, or asked about: it never reveals.
        int cast = src.indexOf("public static boolean cast(Player player, CharacterSheet sheet, DndSpell spell, Integer castLevel,");
        int castEnd = src.indexOf("private static boolean resolveAtCreature(", cast);
        assertTrue(cast > 0 && castEnd > cast);
        String gate = src.substring(cast, castEnd);
        assertTrue(gate.contains("inRange(") && gate.contains("permitted(") && gate.contains("askDm("), "the refusals and the DM's approval live here");
        assertFalse(gate.contains("afterAttackRoll") || gate.contains("revealAfterAttackRoll"), "and nothing here ends Hidden");

        // A fight still goes through the same mechanic, announced to the table by the session.
        String fight = Files.readString(Path.of("src/main/java/io/papermc/jkvttplugin/combat/SpellCastHandler.java"));
        assertTrue(fight.contains("caster.afterAttackRoll(session);"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }
}
