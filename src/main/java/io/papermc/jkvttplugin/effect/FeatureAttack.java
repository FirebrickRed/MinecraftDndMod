package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A feature that <b>is</b> an attack (#221), such as Martial Arts' bonus-action unarmed strike: using
 * it fills {@code /combat attack <target> <weapon> bonus} rather than doing anything itself, so the
 * roll goes through the one attack path. Only after the Attack action, like two-weapon fighting.
 *
 * <pre>attack: { weapon: unarmed, requires: [no_armor, no_shield] }</pre>
 */
public record FeatureAttack(String weapon, Requirements requires, List<String> problems) {

    public static FeatureAttack parse(Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        String weapon = ParseUtil.asString(m.get("weapon"), null);
        if (weapon == null || weapon.isBlank()) {
            problems.add("weapon is missing (a weapon id, or unarmed)");
            weapon = "unarmed";
        }
        return new FeatureAttack(weapon.trim().toLowerCase(), Requirements.parse(m.get("requires"), problems), List.copyOf(problems));
    }

    public boolean isUnarmed() { return "unarmed".equals(weapon); }
}
