package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * An alternative way to work out AC (#220), from an {@code armor_class:} effect: Unarmored Defense
 * (10 + DEX + WIS, no armor or shield), natural armor (13 + DEX), a flat 17, Mage Armor. The
 * character's AC is the best of the normal calculation and every formula that applies, so a formula
 * can only ever help. A shield adds on top unless the formula {@code requires: [no_shield]}.
 *
 * <pre>armor_class: { base: 10, add: [dexterity, wisdom], requires: [no_armor, no_shield] }</pre>
 */
public final class AcFormula {

    private final int base;
    private final List<Ability> add;
    private final Requirements requires;
    // What the YAML said that wasn't understood, for ContentValidator to report.
    private final List<String> problems;

    AcFormula(int base, List<Ability> add, Requirements requires, List<String> problems) {
        this.base = base;
        this.add = List.copyOf(add);
        this.requires = requires;
        this.problems = List.copyOf(problems);
    }

    /** Parses an {@code armor_class:} map. Unknown abilities or requirements are kept as problems, not thrown. */
    public static AcFormula parse(java.util.Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        int base = 10;
        Object b = m.get("base");
        if (b instanceof Number n) base = n.intValue();
        else if (b != null) problems.add("base '" + b + "' isn't a number");

        List<Ability> add = new ArrayList<>();
        for (String s : io.papermc.jkvttplugin.data.loader.util.ParseUtil.normalizeStringList(m.get("add"))) {
            Ability a = Ability.fromString(s.trim());
            if (a == null) problems.add("add '" + s + "' isn't an ability (use full names: dexterity, wisdom…)");
            else add.add(a);
        }

        Requirements requires = Requirements.parse(m.get("requires"), problems);
        return new AcFormula(base, add, requires, problems);
    }

    /** Whether this formula can be used right now. */
    public boolean applies(boolean wearingArmor, boolean holdingShield) {
        return requires.met(wearingArmor, holdingShield);
    }

    /** The AC this formula gives, before a shield. */
    public int value(ToIntFunction<Ability> modifier) {
        int ac = base;
        for (Ability a : add) ac += modifier.applyAsInt(a);
        return ac;
    }

    /** "10 + DEX + WIS", for the sheet. */
    public String describe() {
        StringBuilder sb = new StringBuilder(String.valueOf(base));
        for (Ability a : add) sb.append(" + ").append(a.getAbbreviation());
        return sb.toString();
    }

    public int getBase() { return base; }
    public List<Ability> getAdd() { return add; }
    public Requirements getRequires() { return requires; }
    public List<String> getProblems() { return problems; }
}
