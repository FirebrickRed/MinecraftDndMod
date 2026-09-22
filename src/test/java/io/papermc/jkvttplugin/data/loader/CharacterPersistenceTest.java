package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A character survives save → YAML → load. Everything that exists only on the sheet — the player's
 * picks, expertise, a buff in progress — has to be in the file, or it's gone after a restart.
 */
class CharacterPersistenceTest {

    /** Serialize, dump to YAML text, parse, deserialize — the same path a restart takes. */
    @SuppressWarnings("unchecked")
    private static CharacterSheet roundTrip(CharacterSheet sheet) {
        String yaml = new Yaml().dump(CharacterPersistenceLoader.serializeCharacterSheet(sheet));
        CharacterSheet back = CharacterPersistenceLoader.deserializeCharacterSheet((Map<String, Object>) new Yaml().load(yaml));
        assertNotNull(back, "deserialized");
        return back;
    }

    @Test
    void identityAndScoresSurvive() {
        CharacterSheet zek = character("elf", "wood_elf", "rogue", "urchin", scores(Ability.DEXTERITY, 16), Skill.STEALTH);
        CharacterSheet back = roundTrip(zek);
        assertEquals(zek.getCharacterId(), back.getCharacterId());
        assertEquals("elf", back.getRace().getId());
        assertEquals("rogue", back.getMainClass().getId());
        assertEquals("urchin", back.getBackground().getId());
        assertEquals(16, back.getAbility(Ability.DEXTERITY));
        assertTrue(back.isProficientInSkill(Skill.STEALTH));
        assertTrue(back.isProficientWithTool("thieves_tools"), "granted tools re-derive from the background");
    }

    /** #17: chosen languages and tools used to vanish on restart. */
    @Test
    void chosenToolsAndLanguagesSurvive() {
        CharacterSheet c = character("human", null, "fighter", "sage", scores());
        c.restoreChosenProficiencies(List.of("smiths_tools"), List.of("deep_speech", "draconic"));
        CharacterSheet back = roundTrip(c);
        assertTrue(back.isProficientWithTool("smiths_tools"));
        assertTrue(back.getLanguages().containsAll(List.of("deep_speech", "draconic")));
    }

    @Test
    void expertiseSurvives() {
        CharacterSheet rogue = character("human", null, "rogue", "urchin", scores(Ability.DEXTERITY, 16), Skill.STEALTH);
        rogue.restoreExpertise(List.of("stealth", "thieves_tools"));
        CharacterSheet back = roundTrip(rogue);
        assertEquals(7, back.getSkillBonus(Skill.STEALTH));
        assertEquals(7, back.getToolCheckBonus(Ability.DEXTERITY, "thieves_tools"));
    }

    /** #212: a barbarian raging when the server stopped is still raging, with the same rounds left. */
    @Test
    void rageInProgressSurvives() {
        CharacterSheet barb = character("human", null, "barbarian", "outlander", scores(Ability.STRENGTH, 16));
        ActiveEffect rage = barb.getFeature("rage").getApplyTemplate().copy();
        rage.markMaintained("attacked");
        rage.tickTurnStartAndCheckExpiry(); // one round gone: 10 → 9
        rage.markMaintained("attacked");     // and they've attacked this round
        barb.addEffect(rage);

        CharacterSheet back = roundTrip(barb);
        assertTrue(back.hasEffect("rage"));
        ActiveEffect restored = back.getActiveEffects().get(0);
        assertEquals(9, restored.getRoundsRemaining());
        assertTrue(restored.isMaintainedThisRound(), "a maintained Rage isn't dropped at the next turn start");
        assertTrue(back.resistsDamage("slashing"), "rebuilt from the feature's YAML, so it still resists");
        assertEquals(2, back.bonusDamageFor("melee_str"));
    }

    /** A saved effect whose feature was renamed or removed is dropped, not a crash. */
    @Test
    void unknownEffectIsDropped() {
        CharacterSheet c = character("human", null, "fighter", "sage", scores());
        CharacterPersistenceLoader.restoreActiveEffects(c, List.of(Map.of("source", "no_such_feature", "roundsRemaining", 3)));
        assertTrue(c.getActiveEffects().isEmpty());
    }

    /** #101: death used to live on the combatant, so it ended with the fight. It's on the sheet now. */
    @Test
    void deathSurvives() {
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        c.takeDamage(c.getCurrentHealth());
        c.addDeathSaveFailures(3);
        CharacterSheet back = roundTrip(c);
        assertTrue(back.isDead());
        assertEquals(0, back.getCurrentHealth());
        back.heal(5);
        assertEquals(0, back.getCurrentHealth(), "still can't be healed after a reload");
    }

    /** Someone dying when the server stops is still dying, with the same tally. */
    @Test
    void deathSaveTallySurvives() {
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        c.takeDamage(c.getCurrentHealth());
        c.addDeathSaveSuccess();
        c.addDeathSaveFailures(2);
        CharacterSheet back = roundTrip(c);
        assertFalse(back.isDead());
        assertEquals(1, back.getDeathSaveSuccesses());
        assertEquals(2, back.getDeathSaveFailures());
    }

    @Test
    void aHealthySheetWritesNoDeathKeys() {
        Map<String, Object> data = CharacterPersistenceLoader.serializeCharacterSheet(
                character("human", null, "fighter", "soldier", scores()));
        assertFalse(data.containsKey("dead"));
        assertFalse(data.containsKey("deathSaves"));
    }

    /** Temp HP wasn't saved, so a restart mid-adventure quietly took it away. */
    @Test
    void tempHpSurvives() {
        CharacterSheet c = character("human", null, "fighter", "soldier", scores());
        c.setTemporaryHp(7);
        assertEquals(7, roundTrip(c).getTempHealth());
    }

    /** Relentless Endurance wasn't saved either, so a restart handed a spent use back. */
    @Test
    void spentRelentlessEnduranceSurvives() {
        CharacterSheet orc = character("half_orc", null, "barbarian", "outlander", scores());
        assertTrue(orc.canEndureLethalHit());
        orc.markRelentlessEnduranceUsed();
        CharacterSheet back = roundTrip(orc);
        assertFalse(back.canEndureLethalHit(), "still spent until a long rest");
        back.longRest();
        assertTrue(back.canEndureLethalHit());
    }
}
