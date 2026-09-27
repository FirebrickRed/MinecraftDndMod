package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What an effect's {@code advantage_on:} / {@code disadvantage_on:} can name (#223). Each d20 roll
 * carries the tags that describe it, and an effect naming any of them applies:
 * <ul>
 *   <li>{@code str_checks} … {@code cha_checks}, or {@code checks} for every ability check
 *       (skills and tool checks count as their ability's check)</li>
 *   <li>{@code str_saves} … {@code cha_saves}, or {@code saves}</li>
 *   <li>{@code attacks}: attack rolls</li>
 *   <li>{@code initiative}: also a DEX check, so {@code dex_checks} covers it too</li>
 * </ul>
 */
public final class RollTags {

    private RollTags() {}

    public static final Set<String> ALL = build();

    private static Set<String> build() {
        Set<String> out = new LinkedHashSet<>(List.of("checks", "saves", "attacks", "initiative"));
        for (Ability a : Ability.values()) {
            out.add(a.getAbbreviation().toLowerCase() + "_checks");
            out.add(a.getAbbreviation().toLowerCase() + "_saves");
        }
        return Set.copyOf(out);
    }

    /** An ability check (a skill or tool check uses its ability's). */
    public static List<String> check(Ability a) {
        return a == null ? List.of("checks") : List.of(a.getAbbreviation().toLowerCase() + "_checks", "checks");
    }

    public static List<String> save(Ability a) {
        return a == null ? List.of("saves") : List.of(a.getAbbreviation().toLowerCase() + "_saves", "saves");
    }

    public static List<String> attack() { return List.of("attacks"); }

    public static List<String> initiative() { return List.of("initiative", "dex_checks", "checks"); }
}
