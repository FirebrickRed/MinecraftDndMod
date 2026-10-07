package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.combat.SaveOutcome;
import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #272, the creature's side: a save the DM rolls for a creature with a request id has to be that
 * request's save (this creature, a save, its ability, its DC), and that's settled before anything is
 * prompted, rolled or used up. It used to be checked after the roll, so a stale id spent a fresh
 * Resistance, and a live id took any ability and DC.
 *
 * <p>These run the real {@code /dm check} for a creature, as the console would send it.
 */
class CreatureSaveRequestTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private final List<DndEntityInstance> spawned = new ArrayList<>();
    private final List<String> said = new ArrayList<>();

    @AfterEach
    void despawn() { spawned.forEach(DndEntityInstance::unregister); }

    private DndEntityInstance spawn(String name) {
        DndEntityInstance c = new DndEntityInstance(EntityLoader.getEntity("balin_blacksmith"), null, name, 10);
        spawned.add(c);
        return c;
    }

    /** Resistance on the creature: +1d4 to one save, then it's gone. */
    private static void resist(DndEntityInstance c) {
        c.getEffects().add(SpellLoader.getSpell("resistance").getEffect().copy());
        assertTrue(hasResistance(c));
    }

    private static boolean hasResistance(DndEntityInstance c) {
        return c.getEffects().stream().anyMatch(e -> e.isUntilUsed() && e.rollBonusFor(ActiveEffect.SAVES) != null);
    }

    /** A console-like sender that remembers what it was told. */
    private CommandSender sender() {
        return (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args != null && args.length > 0 && args[0] instanceof Component c) {
                        said.add(PlainTextComponentSerializer.plainText().serialize(c));
                    }
                    if (method.getName().equals("getName")) return "CONSOLE";
                    if (method.getReturnType() == boolean.class) return method.getName().equals("isOp");
                    return null;
                });
    }

    private void dmCheck(String line) {
        said.clear();
        new CheckCommand().onCommand(sender(), null, "dm", line.split(" "));
    }

    private String heard() { return String.join("\n", said); }

    // ---------- a request that's gone ----------

    @Test
    void aStaleRequestIsRefusedBeforeTheRollAndKeepsResistance() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> ran = new ArrayList<>();
        String request = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of("poison"), ran::add);
        dmCheck("Wolf save con dc 13 request " + request + " total 5");
        assertEquals(List.of(false), ran, "the real answer: failed");

        resist(wolf); // cast on it afterwards
        dmCheck("Wolf save con dc 13 request " + request + " total 5"); // the old buttons, clicked again
        assertTrue(heard().contains("already been settled"), heard());
        assertTrue(hasResistance(wolf), "nothing was rolled, so Resistance wasn't spent");
        assertEquals(List.of(false), ran, "and nothing ran twice");
    }

    @Test
    void aRequestTheDmRuledIsRefusedAndKeepsResistance() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> ran = new ArrayList<>();
        String request = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of(), ran::add);
        assertTrue(SaveOutcome.rule(request, true), "[saved], no roll");
        resist(wolf);
        dmCheck("Wolf save con dc 13 request " + request + " total 20");
        assertTrue(heard().contains("already been settled"), heard());
        assertTrue(hasResistance(wolf));
        assertEquals(List.of(true), ran);
    }

    @Test
    void anIdNobodyIssuedIsRefused() {
        DndEntityInstance wolf = spawn("Wolf");
        resist(wolf);
        dmCheck("Wolf save con dc 13 request rmadeup total 5");
        assertTrue(heard().contains("already been settled"), heard());
        assertTrue(hasResistance(wolf));
    }

    // ---------- a live request, the wrong save ----------

    @Test
    void theWrongCreatureAbilityDcOrKindOfRollIsRefusedWithNothingTouched() {
        DndEntityInstance wolf = spawn("Wolf"), bear = spawn("Bear");
        List<Boolean> ran = new ArrayList<>();
        String request = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of("poison"), ran::add);
        resist(wolf);
        resist(bear);

        dmCheck("Bear save con dc 13 request " + request + " total 5");
        assertTrue(heard().contains("someone else's"), "another creature: " + heard());

        dmCheck("Wolf save dex dc 13 request " + request + " total 5");
        assertTrue(heard().contains("waiting on a CON save"), "another ability: " + heard());

        dmCheck("Wolf save con dc 15 request " + request + " total 5");
        assertTrue(heard().contains("DC 13"), "another DC: " + heard());

        dmCheck("Wolf save con request " + request + " total 5");
        assertTrue(heard().contains("DC 13"), "no DC at all: " + heard());

        dmCheck("Wolf check con dc 13 request " + request + " total 5");
        assertTrue(heard().contains("saving throw"), "an ability check, not a save: " + heard());

        dmCheck("Wolf skill perception dc 13 request " + request + " total 5");
        assertTrue(heard().contains("saving throw"), "a skill: " + heard());

        assertTrue(ran.isEmpty(), "none of those resolved the request");
        assertTrue(SaveOutcome.isWaiting(request), "it's still waiting on its own save");
        assertTrue(hasResistance(wolf) && hasResistance(bear), "and no one-use effect was spent");

        dmCheck("Wolf save con dc 13 request " + request + " total 5"); // the real one
        assertEquals(List.of(false), ran);
        assertFalse(hasResistance(wolf), "the save that counted used the Resistance");
        assertTrue(hasResistance(bear));
    }

    @Test
    void aPromptForARequestRollsNothing() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> ran = new ArrayList<>();
        String request = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of(), ran::add);
        resist(wolf);
        dmCheck("Wolf save con dc 13 request " + request); // no roll words: the DM gets the buttons
        assertTrue(heard().contains("You roll for Wolf"), heard());
        assertTrue(hasResistance(wolf));
        assertTrue(ran.isEmpty() && SaveOutcome.isWaiting(request));
    }

    // ---------- overlapping requests ----------

    @Test
    void twoRequestsOnOneCreatureResolveIndependently() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> poison = new ArrayList<>(), fire = new ArrayList<>();
        String first = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of("poison"), poison::add);
        String second = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.DEXTERITY, Set.of("fire"), fire::add);

        dmCheck("Wolf save dex dc 13 request " + second + " total 20"); // the newer one first
        assertEquals(List.of(true), fire);
        assertTrue(poison.isEmpty(), "the older request wasn't touched");

        dmCheck("Wolf save con dc 13 request " + first + " total 2");
        assertEquals(List.of(false), poison);
        assertEquals(List.of(true), fire, "and the newer one didn't run again");
    }

    @Test
    void twoRequestsWithTheSameAbilityAndDcStillResolveByTheirOwnId() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> a = new ArrayList<>(), b = new ArrayList<>();
        String first = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of(), a::add);
        String second = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of(), b::add);
        dmCheck("Wolf save con dc 13 request " + second + " total 20");
        assertTrue(a.isEmpty());
        assertEquals(List.of(true), b);
        dmCheck("Wolf save con dc 13 request " + first + " total 1");
        assertEquals(List.of(false), a);
    }

    // ---------- ordinary checks ----------

    @Test
    void anOrdinaryCreatureSaveStillWorksAndResolvesNoRequest() {
        DndEntityInstance wolf = spawn("Wolf");
        List<Boolean> ran = new ArrayList<>();
        String request = SaveOutcome.await(wolf.getInstanceId(), 13, Ability.CONSTITUTION, Set.of("poison"), ran::add);
        resist(wolf);
        dmCheck("Wolf save con dc 13 total 5"); // same creature, ability and DC, but no id: a ledge
        assertTrue(heard().contains("fails DC 13"), heard());
        assertFalse(hasResistance(wolf), "an ordinary save is a save: it uses the Resistance, as before");
        assertTrue(ran.isEmpty() && SaveOutcome.isWaiting(request), "and the waiting spell isn't set off by it");

        dmCheck("Wolf skill perception dc 12 total 15");
        assertTrue(heard().contains("success vs DC 12"), heard());
    }

    // ---------- the shared rule the player's path uses too ----------

    @Test
    void theSameTestRefusesAPlayersMismatchedSave() {
        UUID player = UUID.randomUUID();
        String request = SaveOutcome.await(player, 13, Ability.CONSTITUTION, Set.of(), saved -> {});
        assertNull(SaveOutcome.refusal(request, player, true, Ability.CONSTITUTION, 13));
        assertNotNull(SaveOutcome.refusal(request, UUID.randomUUID(), true, Ability.CONSTITUTION, 13), "someone else");
        assertNotNull(SaveOutcome.refusal(request, player, true, Ability.WISDOM, 13), "another ability");
        assertNotNull(SaveOutcome.refusal(request, player, true, Ability.CONSTITUTION, 15), "another DC");
        assertNotNull(SaveOutcome.refusal(request, player, true, Ability.CONSTITUTION, null), "no DC");
        assertNotNull(SaveOutcome.refusal(request, player, false, null, 13), "not a save");
        assertNotNull(SaveOutcome.refusal(null, player, true, Ability.CONSTITUTION, 13), "no id is no request");

        // A request that didn't say which ability takes any save at its DC from its saver.
        String anyAbility = SaveOutcome.await(player, 13, saved -> {});
        assertNull(SaveOutcome.refusal(anyAbility, player, true, Ability.WISDOM, 13));
    }
}
