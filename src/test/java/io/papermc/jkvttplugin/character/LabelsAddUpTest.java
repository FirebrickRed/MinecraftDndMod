package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * "Where did the +5 come from?" Every roll shows its bonus as labelled parts ("+3[DEX] +2[Prof]"),
 * and the game adds a separate number. They're built by different methods, so they can drift: a
 * label that says +5 while the game adds +7 is worse than no label. The parts must add up.
 */
class LabelsAddUpTest {

    private static final Pattern PART = Pattern.compile("([+-]\\d+)\\[");

    /** "+3[DEX] +2[Prof] +4[Prof ×2]" → 9. */
    private static int sum(String label) {
        Matcher m = PART.matcher(label);
        int total = 0;
        while (m.find()) total += Integer.parseInt(m.group(1));
        return total;
    }

    /** A spread of characters: proficient and not, expertise, low and high scores, a caster. */
    private static List<CharacterSheet> characters() {
        List<CharacterSheet> out = new ArrayList<>();
        out.add(character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 16, Ability.DEXTERITY, 8),
                Skill.ATHLETICS, Skill.INTIMIDATION));
        CharacterSheet rogue = character("tiefling", null, "rogue", "criminal", scores(Ability.DEXTERITY, 17, Ability.CHARISMA, 14),
                Skill.STEALTH, Skill.PERSUASION, Skill.DECEPTION);
        rogue.restoreExpertise(List.of("stealth", "thieves_tools")); // ×2 proficiency
        out.add(rogue);
        out.add(character("elf", "high_elf", "wizard", "sage", scores(Ability.INTELLIGENCE, 16, Ability.STRENGTH, 6),
                Skill.ARCANA, Skill.HISTORY));
        return out;
    }

    @Test
    void skillLabelsAddUpToTheSkillBonus() {
        for (CharacterSheet c : characters()) {
            for (Skill s : Skill.values()) {
                assertEquals(c.getSkillBonus(s), sum(c.getSkillBonusBreakdown(s)),
                        c.getMainClass().getName() + " " + s + ": " + c.getSkillBonusBreakdown(s));
            }
        }
    }

    @Test
    void saveAndCheckLabelsAddUp() {
        for (CharacterSheet c : characters()) {
            for (Ability a : Ability.values()) {
                assertEquals(c.getSavingThrowBonus(a), sum(c.getSaveBreakdown(a)),
                        c.getMainClass().getName() + " " + a + " save: " + c.getSaveBreakdown(a));
                assertEquals(c.getModifier(a), sum(c.getAbilityCheckBreakdown(a)),
                        c.getMainClass().getName() + " " + a + " check: " + c.getAbilityCheckBreakdown(a));
            }
        }
    }

    @Test
    void toolCheckLabelsAddUp() {
        for (CharacterSheet c : characters()) {
            for (String tool : List.of("thieves_tools", "smiths_tools")) {
                assertEquals(c.getToolCheckBonus(Ability.DEXTERITY, tool), sum(c.getToolCheckBreakdown(Ability.DEXTERITY, tool)),
                        c.getMainClass().getName() + " " + tool + ": " + c.getToolCheckBreakdown(Ability.DEXTERITY, tool));
            }
        }
    }

    /** The spell attack label must equal what's added to the d20 (proficiency + casting ability). */
    @Test
    void spellLabelsAddUp() {
        CharacterSheet wizard = characters().get(2);
        DndSpell fireBolt = SpellLoader.getSpell("fire_bolt");
        Ability casting = wizard.castingAbilityFor(fireBolt);
        assertEquals(wizard.getProficiencyBonus() + wizard.getModifier(casting), sum(wizard.getSpellAttackBreakdown(fireBolt)));
        assertEquals(wizard.getModifier(casting), sum(wizard.getSpellModBreakdown(fireBolt)));
    }
}
