package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A feature that <b>is</b> an attack (#221), such as Martial Arts' bonus-action unarmed strike: using
 * it fills {@code /combat attack <target> <weapon> bonus} rather than doing anything itself, so the
 * roll goes through the one attack path.
 *
 * <pre>attack: { weapon: unarmed, requires: [no_armor, no_shield, only_monk_weapons],
 *          after_attack_with: [unarmed, monk_weapon] }</pre>
 *
 * {@code after_attack_with} (#259): it only follows an Attack action made with one of these, whatever
 * {@code combat.bonus_attack_timing} says. {@code unarmed}, and {@code monk_weapon} for any weapon the
 * character's {@code weapon_ability} covers. Absent: it follows the timing setting, like an off-hand attack.
 */
public record FeatureAttack(String weapon, Requirements requires, Set<String> afterAttackWith, List<String> problems) {

    public static final Set<String> AFTER_KNOWN = Set.of("unarmed", "monk_weapon");

    public static FeatureAttack parse(Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        String weapon = ParseUtil.asString(m.get("weapon"), null);
        if (weapon == null || weapon.isBlank()) {
            problems.add("weapon is missing (a weapon id, or unarmed)");
            weapon = "unarmed";
        }
        Set<String> after = new LinkedHashSet<>();
        for (String s : ParseUtil.normalizeStringList(m.get("after_attack_with"))) {
            String a = s.trim().toLowerCase();
            if (AFTER_KNOWN.contains(a)) after.add(a);
            else problems.add("after_attack_with '" + s + "' isn't one of monk_weapon, unarmed");
        }
        return new FeatureAttack(weapon.trim().toLowerCase(), Requirements.parse(m.get("requires"), problems),
                Set.copyOf(after), List.copyOf(problems));
    }

    public boolean isUnarmed() { return "unarmed".equals(weapon); }

    /** Whether it needs a particular kind of Attack action before it. */
    public boolean followsAnAttack() { return !afterAttackWith.isEmpty(); }
}
