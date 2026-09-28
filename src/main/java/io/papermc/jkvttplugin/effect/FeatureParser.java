package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a class/race {@code features:} YAML block into {@link Feature}s (the Effect Engine, #70).
 * Data-driven: a feature is activation + cost + an {@code apply} buff composed from primitive keys.
 *
 * <pre>
 * features:
 *   - id: rage
 *     name: Rage
 *     activation: bonus_action
 *     cost: { resource: rage, amount: 1 }
 *     apply:
 *       duration: { rounds: 10, maintained_by: [attacked, took_damage] }
 *       effects:
 *         resistance: [bludgeoning, piercing, slashing]
 *         advantage_on: [str_checks, str_saves]
 *         bonus_damage: { amount: 2, when: melee_str }
 *         minecraft_effect: STRENGTH
 * </pre>
 */
public final class FeatureParser {

    private FeatureParser() {}

    /** Always-on boolean primitives an {@code effects:} block may set true (the passive vocabulary). */
    private static final List<String> BOOLEAN_FLAGS = List.of(
            "reroll_natural_1",  // Halfling Lucky — reroll a natural 1 on a d20
            "extra_crit_die",    // Half-Orc Savage Attacks — one extra weapon die on a melee crit
            "endure_below_1",    // Half-Orc Relentless Endurance — drop to 1 HP instead of 0, 1×/long rest
            "offhand_ability_damage",   // Two-Weapon Fighting style — the off-hand attack adds its ability modifier
            "reroll_low_damage",        // Great Weapon Fighting — reroll 1s and 2s on a two-handed melee weapon's damage
            "heavy_armor_no_speed_penalty"); // Dwarf — heavy armor too heavy for your Strength doesn't slow you (#34)

    public static List<Feature> parseFeatures(Object node) {
        List<Feature> out = new ArrayList<>();
        if (!(node instanceof List<?> list)) return out;

        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> m)) continue;
            String id = ParseUtil.asString(m.get("id"), null);
            if (id == null || id.isBlank()) continue;
            String name = ParseUtil.asString(m.get("name"), id);
            String activation = ParseUtil.asString(m.get("activation"), null);
            String target = ParseUtil.asString(m.get("target"), "self");

            String costResource = null;
            int costAmount = 1;
            int grantedMax = -1;
            boolean grantedByProf = false;
            String grantedRecovery = null;
            if (m.get("cost") instanceof Map<?, ?> cost) {
                costResource = ParseUtil.asString(cost.get("resource"), null);
                costAmount = ParseUtil.asInt(cost.get("amount"), 1);
                // A feature may also declare the pool it spends from, so a race can grant its own
                // limited-use feature in pure YAML: max: <int> | proficiency_bonus, recovery: <type>.
                Object max = cost.get("max");
                if (max instanceof String s && s.equalsIgnoreCase("proficiency_bonus")) {
                    grantedByProf = true;
                } else if (max != null) {
                    grantedMax = ParseUtil.asInt(max, -1);
                }
                grantedRecovery = ParseUtil.asString(cost.get("recovery"), null);
            }

            ActiveEffect apply = null;
            if (m.get("apply") instanceof Map<?, ?> applyMap) {
                apply = parseApply(id, name, applyMap);
            }

            FeatureAction action = null;
            if (m.get("action") instanceof Map<?, ?> actionMap) {
                action = parseAction(actionMap);
            }

            FeatureAttack attack = m.get("attack") instanceof Map<?, ?> attackMap ? FeatureAttack.parse(attackMap) : null;

            // heal: { dice: 1d10, add_level: true } (Second Wind) | { from_pool: true, range: 5, not: [undead] } (Lay on Hands)
            Feature.Heal heal = null;
            if (m.get("heal") instanceof Map<?, ?> h) {
                heal = new Feature.Heal(ParseUtil.asString(h.get("dice"), null), ParseUtil.asBoolean(h.get("add_level"), false),
                        ParseUtil.asBoolean(h.get("from_pool"), false), ParseUtil.asInt(h.get("range"), 0),
                        new HashSet<>(ParseUtil.normalizeStringList(h.get("not"))));
            }
            // sense: { creature_types: [celestial, fiend, undead], range: 60 } (Divine Sense)
            Feature.Sense sense = null;
            if (m.get("sense") instanceof Map<?, ?> s) {
                sense = new Feature.Sense(new HashSet<>(ParseUtil.normalizeStringList(s.get("creature_types"))),
                        ParseUtil.asInt(s.get("range"), 60));
            }

            // recover_slots: { max_slot_level: 5 } (Arcane Recovery)
            Feature.RecoverSlots recover = m.get("recover_slots") instanceof Map<?, ?> r
                    ? new Feature.RecoverSlots(ParseUtil.asInt(r.get("max_slot_level"), 5)) : null;

            out.add(new Feature(id, name, activation, target, costResource, costAmount, apply,
                    action, grantedMax, grantedByProf, grantedRecovery, attack).withHealAndSense(heal, sense)
                    .withRecoverSlots(recover));
        }
        return out;
    }

    /** Parses an {@code action:} block (area/save/damage), including per-choice {@code variants}. */
    private static FeatureAction parseAction(Map<?, ?> a) {
        String byChoice = ParseUtil.asString(a.get("by_choice"), null);
        java.util.Map<String, FeatureAction> variants = new java.util.HashMap<>();
        if (a.get("variants") instanceof Map<?, ?> vs) {
            for (Map.Entry<?, ?> e : vs.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> vm)) continue;
                variants.put(FeatureAction.normalizeKey(String.valueOf(e.getKey())), parseActionFields(vm, null, null));
            }
        }
        return parseActionFields(a, byChoice, variants);
    }

    /** Reads the shape/size/save/damage fields from an action or one of its variant maps. */
    private static FeatureAction parseActionFields(Map<?, ?> a, String byChoice,
                                                   java.util.Map<String, FeatureAction> variants) {
        String shape = ParseUtil.asString(a.get("shape"), null);
        double size = a.get("size") == null ? 0 : ParseUtil.asInt(a.get("size"), 0);
        String dcAbility = ParseUtil.asString(a.get("dc_ability"), null);
        String saveAbility = ParseUtil.asString(a.get("save"), null);
        String saveEffect = ParseUtil.asString(a.get("save_effect"), null);
        String damage = ParseUtil.asString(a.get("damage"), null);
        String damageType = ParseUtil.asString(a.get("damage_type"), null);
        String targets = ParseUtil.asString(a.get("targets"), null);
        return new FeatureAction(shape, size, dcAbility, saveAbility, saveEffect, damage, damageType,
                targets, byChoice, variants);
    }

    private static ActiveEffect parseApply(String featureId, String featureName, Map<?, ?> apply) {
        // duration
        int rounds = -1;                 // -1 = no round timer (DM/rest-ended)
        Set<String> maintainedBy = new HashSet<>();
        String untilRest = null;
        boolean untilUsed = false;
        boolean stacks = false;
        if (apply.get("duration") instanceof Map<?, ?> d) {
            if (d.get("rounds") != null) rounds = ParseUtil.asInt(d.get("rounds"), -1);
            maintainedBy.addAll(ParseUtil.normalizeStringList(d.get("maintained_by")));
            untilRest = ParseUtil.asString(d.get("until_rest"), null);
            untilUsed = ParseUtil.asBoolean(d.get("until_used"), false);
            stacks = ParseUtil.asBoolean(d.get("stacks"), false);
        }

        // effects (the primitive vocabulary)
        Set<String> resistances = new HashSet<>();
        Set<String> advantageOn = new HashSet<>();
        Set<String> disadvantageOn = new HashSet<>();
        int bonusDamage = 0;
        String bonusDamageWhen = null;
        String minecraftEffect = null;
        int minecraftAmplifier = 0;
        Set<String> flags = new HashSet<>();
        AcFormula armorClass = null;
        UnarmedStrike unarmedStrike = null;
        WeaponAbility weaponAbility = null;
        int maxHpPerLevel = 0;
        int attackBonus = 0, acBonus = 0;
        String attackBonusWhen = null;
        boolean acBonusNeedsArmor = false;
        List<String> sneakDice = List.of();
        if (apply.get("effects") instanceof Map<?, ?> e) {
            // attack_bonus: { amount: 2, when: ranged }   (Archery)
            if (e.get("attack_bonus") instanceof Map<?, ?> ab) {
                attackBonus = ParseUtil.asInt(ab.get("amount"), 0);
                attackBonusWhen = ParseUtil.asString(ab.get("when"), null);
            }
            // ac_bonus: { amount: 1, requires: [armor] }   (Defense)
            if (e.get("ac_bonus") instanceof Map<?, ?> acb) {
                acBonus = ParseUtil.asInt(acb.get("amount"), 0);
                acBonusNeedsArmor = ParseUtil.normalizeStringList(acb.get("requires")).contains("armor");
            }
            // sneak_attack: { dice_by_level: [1d6, 1d6, 2d6, …] }
            if (e.get("sneak_attack") instanceof Map<?, ?> sa) sneakDice = ParseUtil.normalizeStringList(sa.get("dice_by_level"));
            resistances.addAll(ParseUtil.normalizeStringList(e.get("resistance")));
            advantageOn.addAll(ParseUtil.normalizeStringList(e.get("advantage_on")));
            disadvantageOn.addAll(ParseUtil.normalizeStringList(e.get("disadvantage_on")));
            if (e.get("bonus_damage") instanceof Map<?, ?> bd) {
                bonusDamage = ParseUtil.asInt(bd.get("amount"), 0);
                bonusDamageWhen = ParseUtil.asString(bd.get("when"), null);
            }
            minecraftEffect = ParseUtil.asString(e.get("minecraft_effect"), null);
            minecraftAmplifier = ParseUtil.asInt(e.get("minecraft_amplifier"), 0);
            // Boolean passive primitives — each true key becomes a flag by its own name.
            for (String flag : BOOLEAN_FLAGS) {
                if (ParseUtil.asBoolean(e.get(flag), false)) flags.add(flag);
            }
            if (e.get("armor_class") instanceof Map<?, ?> ac) armorClass = AcFormula.parse(ac);
            if (e.get("unarmed_strike") instanceof Map<?, ?> us) unarmedStrike = UnarmedStrike.parse(us);
            if (e.get("weapon_ability") instanceof Map<?, ?> wa) weaponAbility = WeaponAbility.parse(wa);
            maxHpPerLevel = ParseUtil.asInt(e.get("max_hp_per_level"), 0);
        }

        return new ActiveEffect(featureId, featureName, resistances, advantageOn, disadvantageOn,
                bonusDamage, bonusDamageWhen, minecraftEffect, minecraftAmplifier, flags, stacks,
                rounds, maintainedBy, untilRest, untilUsed, armorClass, unarmedStrike, weaponAbility, maxHpPerLevel)
                .withBonuses(attackBonus, attackBonusWhen, acBonus, acBonusNeedsArmor, sneakDice);
    }
}
