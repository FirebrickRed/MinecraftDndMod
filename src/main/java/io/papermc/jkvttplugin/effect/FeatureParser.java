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
            if (m.get("cost") instanceof Map<?, ?> cost) {
                costResource = ParseUtil.asString(cost.get("resource"), null);
                costAmount = ParseUtil.asInt(cost.get("amount"), 1);
            }

            ActiveEffect apply = null;
            if (m.get("apply") instanceof Map<?, ?> applyMap) {
                apply = parseApply(id, name, applyMap);
            }

            out.add(new Feature(id, name, activation, target, costResource, costAmount, apply));
        }
        return out;
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
        if (apply.get("effects") instanceof Map<?, ?> e) {
            resistances.addAll(ParseUtil.normalizeStringList(e.get("resistance")));
            advantageOn.addAll(ParseUtil.normalizeStringList(e.get("advantage_on")));
            disadvantageOn.addAll(ParseUtil.normalizeStringList(e.get("disadvantage_on")));
            if (e.get("bonus_damage") instanceof Map<?, ?> bd) {
                bonusDamage = ParseUtil.asInt(bd.get("amount"), 0);
                bonusDamageWhen = ParseUtil.asString(bd.get("when"), null);
            }
            minecraftEffect = ParseUtil.asString(e.get("minecraft_effect"), null);
            minecraftAmplifier = ParseUtil.asInt(e.get("minecraft_amplifier"), 0);
        }

        return new ActiveEffect(featureId, featureName, resistances, advantageOn, disadvantageOn,
                bonusDamage, bonusDamageWhen, minecraftEffect, minecraftAmplifier, stacks,
                rounds, maintainedBy, untilRest, untilUsed);
    }
}
