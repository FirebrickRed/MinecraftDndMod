package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.TagRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Lets some weapons use a different ability, and optionally a bigger die (#221): Martial Arts' monk
 * weapons (shortswords and simple melee weapons that aren't two-handed or heavy) can use DEX, and
 * roll the unarmed-strike die when it's bigger than the weapon's own.
 *
 * <pre>weapon_ability: { weapons: [shortsword, simple_melee_weapon], exclude_properties: [two_handed, heavy],
 *                  ability: [strength, dexterity], min_die: unarmed, requires: [no_armor, no_shield] }</pre>
 */
public final class WeaponAbility {

    private final List<String> weapons;          // weapon ids and/or tags
    private final Set<String> excludeProperties;
    private final List<Ability> abilities;
    private final boolean minDieUnarmed;         // use the unarmed-strike die when it's bigger
    private final Requirements requires;
    private final List<String> problems;

    WeaponAbility(List<String> weapons, Set<String> excludeProperties, List<Ability> abilities,
                  boolean minDieUnarmed, Requirements requires, List<String> problems) {
        this.weapons = List.copyOf(weapons);
        this.excludeProperties = Set.copyOf(excludeProperties);
        this.abilities = List.copyOf(abilities);
        this.minDieUnarmed = minDieUnarmed;
        this.requires = requires;
        this.problems = List.copyOf(problems);
    }

    public static WeaponAbility parse(Map<?, ?> m) {
        List<String> problems = new ArrayList<>();
        List<String> weapons = new ArrayList<>();
        for (String w : ParseUtil.normalizeStringList(m.get("weapons"))) weapons.add(w.trim().toLowerCase());
        if (weapons.isEmpty()) problems.add("weapons is empty, so it applies to nothing");
        Set<String> exclude = new LinkedHashSet<>();
        for (String p : ParseUtil.normalizeStringList(m.get("exclude_properties"))) exclude.add(p.trim().toLowerCase().replace('-', '_'));
        List<Ability> abilities = UnarmedStrike.parseAbilities(m.get("ability"), problems);
        if (abilities.isEmpty()) problems.add("ability is missing");
        String minDie = ParseUtil.asString(m.get("min_die"), null);
        if (minDie != null && !minDie.equalsIgnoreCase("unarmed")) problems.add("min_die '" + minDie + "' can only be unarmed");
        return new WeaponAbility(weapons, exclude, abilities, "unarmed".equalsIgnoreCase(minDie),
                Requirements.parse(m.get("requires"), problems), problems);
    }

    /** Whether this weapon is one it covers: named by id or by a tag it carries, and no excluded property. */
    public boolean covers(DndWeapon weapon) {
        if (weapon == null || abilities.isEmpty()) return false;
        if (weapon.getProperties() != null) {
            for (String p : weapon.getProperties()) {
                if (p != null && excludeProperties.contains(p.trim().toLowerCase().replace('-', '_'))) return false;
            }
        }
        // A magic weapon counts as its base (a Shortsword +1 is a shortsword), as for proficiency.
        for (String id : new String[]{weapon.getId(), weapon.getBaseId()}) {
            if (id == null) continue;
            id = id.toLowerCase();
            for (String w : weapons) {
                if (w.equals(id)) return true;
                if (TagRegistry.isTag(w) && TagRegistry.itemsFor(w).contains(id)) return true;
            }
        }
        return false;
    }

    /** Names a weapon id or tag that exists, for ContentValidator. */
    public List<String> getWeapons() { return weapons; }
    public Ability ability(ToIntFunction<Ability> modifier) { return UnarmedStrike.best(abilities, modifier); }
    public boolean usesUnarmedDieIfBigger() { return minDieUnarmed; }
    public Requirements getRequires() { return requires; }
    public List<String> getProblems() { return problems; }
}
