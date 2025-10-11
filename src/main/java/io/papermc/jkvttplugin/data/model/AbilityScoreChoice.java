package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents player choice for ability score bonuses.
 * Contains available distributions like [2, 1] or [1, 1, 1].
 */
public class AbilityScoreChoice {
    private final List<List<Integer>> distributions;

    public AbilityScoreChoice(List<List<Integer>> distributions) {
        this.distributions = distributions != null ?
            Collections.unmodifiableList(distributions) :
            Collections.emptyList();
    }

    public List<List<Integer>> getDistributions() {
        return distributions;
    }

    public boolean hasChoices() {
        return !distributions.isEmpty();
    }

    /**
     * Get a user-friendly label for a distribution.
     * Example: [2, 1] → "+2/+1"
     */
    public static String getDistributionLabel(List<Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < distribution.size(); i++) {
            if (i > 0) sb.append("/");
            sb.append("+").append(distribution.get(i));
        }
        return sb.toString();
    }

    /**
     * Get the maximum number of rows needed for this choice.
     * Example: [2, 1] needs 2 rows, [1, 1, 1] needs 3 rows
     */
    public int getMaxRows() {
        return distributions.stream()
            .mapToInt(List::size)
            .max()
            .orElse(0);
    }

    @Override
    public String toString() {
        return "AbilityScoreChoice{distributions=" + distributions + "}";
    }
}
