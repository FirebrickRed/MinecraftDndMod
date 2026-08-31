package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.model.AbilityScoreChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses ability-score YAML blocks (fixed bonuses, choice distributions, ability lists,
 * and the spellcasting casting ability). Split out of {@code LoaderUtils} (issue #12).
 */
public final class AbilityParser {

    private AbilityParser() {}

    /** Result of parsing an {@code ability_scores} block: fixed bonuses plus an optional choice. */
    public static class AbilityScoreParseResult {
        public final Map<Ability, Integer> fixedBonuses;
        public final AbilityScoreChoice choiceBonuses;

        public AbilityScoreParseResult(Map<Ability, Integer> fixedBonuses, AbilityScoreChoice choiceBonuses) {
            this.fixedBonuses = fixedBonuses != null ? fixedBonuses : Map.of();
            this.choiceBonuses = choiceBonuses;
        }
    }

    /**
     * Parses an {@code ability_scores} YAML block which can contain:
     * - fixed: { Dexterity: 2, ... }
     * - choice: { distributions: [[2, 1], [1, 1, 1]] }
     */
    public static AbilityScoreParseResult parseAbilityScores(Object rawObject) {
        if (!(rawObject instanceof Map<?, ?> abilityScoresMap)) {
            return new AbilityScoreParseResult(null, null);
        }

        // Parse fixed bonuses
        Map<Ability, Integer> fixedBonuses = new HashMap<>();
        Object fixedObj = abilityScoresMap.get("fixed");
        if (fixedObj instanceof Map<?, ?> fixedMap) {
            for (Map.Entry<?, ?> entry : fixedMap.entrySet()) {
                if (entry.getKey() instanceof String keyString && entry.getValue() instanceof Number num) {
                    try {
                        Ability ability = Ability.valueOf(keyString.toUpperCase());
                        fixedBonuses.put(ability, num.intValue());
                    } catch (IllegalArgumentException ignored) {
                        // Skip unknown ability
                    }
                }
            }
        }

        // Parse choice bonuses
        AbilityScoreChoice choiceBonuses = null;
        Object choiceObj = abilityScoresMap.get("choice");
        if (choiceObj instanceof Map<?, ?> choiceMap) {
            choiceBonuses = parseAbilityChoiceDistributions(choiceMap);
        }

        return new AbilityScoreParseResult(fixedBonuses, choiceBonuses);
    }

    /**
     * Parses a choice block: { distributions: [[2, 1], [1, 1, 1]] }
     */
    private static AbilityScoreChoice parseAbilityChoiceDistributions(Map<?, ?> choiceMap) {
        Object distributionsObj = choiceMap.get("distributions");
        if (!(distributionsObj instanceof List<?> distributionsList)) {
            return null;
        }

        List<List<Integer>> distributions = new ArrayList<>();
        for (Object distObj : distributionsList) {
            if (distObj instanceof List<?> singleDist) {
                List<Integer> bonuses = new ArrayList<>();
                for (Object bonusObj : singleDist) {
                    if (bonusObj instanceof Number num) {
                        bonuses.add(num.intValue());
                    }
                }
                if (!bonuses.isEmpty()) {
                    distributions.add(bonuses);
                }
            }
        }

        return distributions.isEmpty() ? null : new AbilityScoreChoice(distributions);
    }

    /**
     * Legacy method - kept for backwards compatibility. Only returns fixed bonuses.
     */
    public static Map<Ability, Integer> parseAbilityScoreMap(Object rawObject) {
        AbilityScoreParseResult result = parseAbilityScores(rawObject);
        return result.fixedBonuses;
    }

    public static List<Ability> parseAbilityList(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<Ability> abilities = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof String str) {
                try {
                    abilities.add(Ability.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Optionally log the invalid entry
                }
            }
        }
        return abilities;
    }

    public static PlayersChoice<Ability> parseAbilityPlayersChoice(Object input) {
        if (!(input instanceof Map<?, ?> choiceMap)) return null;

        Object chooseObj = choiceMap.get("choose");
        Object optionsObj = choiceMap.get("options");

        if (!(chooseObj instanceof Number chooseNum) || !(optionsObj instanceof List<?> optionList)) {
            return null;
        }

        int choose = chooseNum.intValue();
        List<Ability> options = new ArrayList<>();

        for (Object option : optionList) {
            if (option instanceof String str) {
                try {
                    options.add(Ability.valueOf(str.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Optionally log or ignore invalid entries
                }
            }
        }

        return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.ABILITY_SCORE);
    }

    /**
     * Extracts the casting ability from the spellcasting object in the YAML data.
     * @param data The class YAML data map
     * @return The casting ability, or null if the class is not a spellcaster
     */
    public static Ability extractCastingAbility(Map<String, Object> data) {
        Object spellcasting = data.get("spellcasting");
        if (spellcasting instanceof Map<?, ?> map) {
            String castingAbility = (String) map.get("casting_ability");
            if (castingAbility != null) {
                return Ability.fromString(castingAbility);
            }
        }
        return null;
    }
}
