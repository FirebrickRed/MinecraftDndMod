package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What picking one option of a custom choice gives (#222). The same idea as an equipment option's
 * {@code give:} bundle, for everything else a choice can mean:
 *
 * <pre>
 * options:
 *   - { label: Wisdom, grants: { innate_casting_ability: wisdom } }          # genasi
 *   - { label: "Red (Fire, 15 ft. cone, DEX save)", grants: { damage_resistances: [fire] } }   # dragonborn
 *   - { label: Efreeti, grants: { expanded_spells: [burning_hands, scorching_ray] } }           # genie: pickable
 * </pre>
 *
 * Grants are re-derived from the saved pick on every load, like every other grant; only the pick
 * itself is saved.
 */
public record ChoiceGrants(Ability innateCastingAbility, List<String> damageResistances,
                           List<String> bonusSpells, List<String> expandedSpells, List<String> problems) {

    /** The grant keys understood, for ContentValidator's message. */
    public static final Set<String> KEYS = Set.of("innate_casting_ability", "damage_resistances", "bonus_spells", "expanded_spells");

    public static ChoiceGrants parse(Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        for (Object k : m.keySet()) {
            if (!KEYS.contains(String.valueOf(k))) {
                problems.add("grant '" + k + "' isn't one of " + String.join(", ", new java.util.TreeSet<>(KEYS)));
            }
        }
        Ability ability = null;
        String abilityName = ParseUtil.asString(m.get("innate_casting_ability"), null);
        if (abilityName != null) {
            ability = Ability.fromString(abilityName.trim());
            if (ability == null) problems.add("innate_casting_ability '" + abilityName + "' isn't an ability");
        }
        List<String> resistances = new ArrayList<>();
        for (String r : ParseUtil.normalizeStringList(m.get("damage_resistances"))) resistances.add(r.trim().toLowerCase());
        List<String> spells = new ArrayList<>();
        for (String s : ParseUtil.normalizeStringList(m.get("bonus_spells"))) spells.add(s.trim().toLowerCase());
        List<String> expanded = new ArrayList<>();
        for (String s : ParseUtil.normalizeStringList(m.get("expanded_spells"))) expanded.add(s.trim().toLowerCase());
        return new ChoiceGrants(ability, List.copyOf(resistances), List.copyOf(spells), List.copyOf(expanded), List.copyOf(problems));
    }
}
