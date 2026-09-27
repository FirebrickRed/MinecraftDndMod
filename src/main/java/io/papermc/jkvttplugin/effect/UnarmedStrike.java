package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What an unarmed strike does when an effect says so (#221), e.g. Martial Arts: a damage die that
 * grows with level, and the ability to use. Without one, an unarmed strike is 1 + STR (PHB p.195).
 *
 * <pre>unarmed_strike: { damage_by_level: [1d4, 1d4, …], ability: [strength, dexterity], requires: [no_armor, no_shield] }</pre>
 */
public final class UnarmedStrike {

    private final List<String> damageByLevel;
    private final List<Ability> abilities;
    private final Requirements requires;
    private final List<String> problems;

    UnarmedStrike(List<String> damageByLevel, List<Ability> abilities, Requirements requires, List<String> problems) {
        this.damageByLevel = List.copyOf(damageByLevel);
        this.abilities = List.copyOf(abilities);
        this.requires = requires;
        this.problems = List.copyOf(problems);
    }

    public static UnarmedStrike parse(Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        List<String> dice = new ArrayList<>();
        for (String d : ParseUtil.normalizeStringList(m.get("damage_by_level"))) {
            String t = d.trim().toLowerCase();
            if (faces(t) > 0) dice.add(t);
            else problems.add("damage_by_level '" + d + "' isn't a die like 1d4");
        }
        if (dice.isEmpty()) problems.add("damage_by_level is empty");
        List<Ability> abilities = parseAbilities(m.get("ability"), problems);
        if (abilities.isEmpty()) abilities.add(Ability.STRENGTH);
        return new UnarmedStrike(dice, abilities, Requirements.parse(m.get("requires"), problems), problems);
    }

    /** {@code ability: dexterity} or {@code ability: [strength, dexterity]} (the better one is used). */
    static List<Ability> parseAbilities(Object node, List<String> problems) {
        List<Ability> out = new ArrayList<>();
        for (String s : ParseUtil.normalizeStringList(node)) {
            Ability a = Ability.fromString(s.trim());
            if (a == null) problems.add("ability '" + s + "' isn't an ability (use full names: strength, dexterity…)");
            else out.add(a);
        }
        return out;
    }

    /** The better of the listed abilities for this character (the first one on a tie). */
    static Ability best(List<Ability> abilities, ToIntFunction<Ability> modifier) {
        Ability best = abilities.get(0);
        for (Ability a : abilities) if (modifier.applyAsInt(a) > modifier.applyAsInt(best)) best = a;
        return best;
    }

    /** The die at a character level; past the end of the list, the last entry holds. Null if there's none. */
    public String dieAt(int level) {
        if (damageByLevel.isEmpty()) return null;
        return damageByLevel.get(Math.max(0, Math.min(level, damageByLevel.size()) - 1));
    }

    public Ability ability(ToIntFunction<Ability> modifier) { return best(abilities, modifier); }

    /** 4 for "1d4", 0 if it isn't a single die. For comparing a weapon's die against the unarmed one. */
    public static int faces(String dice) {
        if (dice == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^1d(\\d+)$").matcher(dice.trim().toLowerCase());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    public Requirements getRequires() { return requires; }
    public List<Ability> getAbilities() { return abilities; }
    public List<String> getProblems() { return problems; }
}
