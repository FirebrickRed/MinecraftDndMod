package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.AbilityAllocationMenu;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * Handles click events for the Ability Allocation menu.
 * Allows players to set their base ability scores and apply racial bonuses.
 */
public class AbilityAllocationHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        switch (action) {
            case CONFIRM_CHARACTER -> {
                player.closeInventory();
                CharacterCreationSheetMenu.open(player, sessionId);
            }
            case SELECT_RACIAL_BONUS_DISTRIBUTION -> {
                // Payload is the distribution key like "[2, 1]"
                session.setRacialBonusDistribution(payload);
                AbilityAllocationMenu.open(player, session);
            }
            case APPLY_RACIAL_BONUS -> {
                // Payload format: "STRENGTH:race" or "DEXTERITY:subrace"
                String[] parts = payload.split(":");
                if (parts.length == 2) {
                    Ability ability = Ability.fromString(parts[0]);
                    String source = parts[1]; // "race" or "subrace"

                    // Toggle bonus application
                    int currentBonus = session.getRacialBonus(ability);
                    if (currentBonus > 0) {
                        // Remove the bonus
                        session.clearRacialBonus(ability);
                    } else {
                        // Apply a bonus - determine which value based on distribution
                        String distKey = session.getRacialBonusDistribution();
                        if (distKey != null) {
                            List<Integer> bonusValues = Util.parseDistribution(distKey);
                            int bonusToApply = findNextAvailableBonus(session, bonusValues);
                            if (bonusToApply > 0) {
                                session.setRacialBonus(ability, bonusToApply);
                            }
                        }
                    }
                }
                AbilityAllocationMenu.open(player, session);
            }
            case INCREASE_ABILITY -> {
                Ability ability = Ability.fromString(payload);
                EnumMap<Ability, Integer> base = session.getAbilityScores();
                int current = base.getOrDefault(ability, 10);
                if (current < 20) {
                    current++;
                }
                base.put(ability, current);
                session.setAbilityScores(base);
                AbilityAllocationMenu.open(player, session);
            }
            case DECREASE_ABILITY -> {
                Ability ability = Ability.fromString(payload);
                EnumMap<Ability, Integer> base = session.getAbilityScores();
                int current = base.getOrDefault(ability, 10);
                if (current > 0) {
                    current--;
                }
                base.put(ability, current);
                session.setAbilityScores(base);
                AbilityAllocationMenu.open(player, session);
            }
        }
    }

    /**
     * Find the next bonus value to apply based on what's already been used
     */
    private int findNextAvailableBonus(CharacterCreationSession session, List<Integer> bonusValues) {
        // Count how many of each bonus value have been used
        EnumMap<Ability, Integer> allocations = session.getRacialBonusAllocations();
        List<Integer> usedBonuses = new ArrayList<>(allocations.values());

        // Find the first bonus from bonusValues that hasn't been fully used
        for (Integer bonusValue : bonusValues) {
            if (usedBonuses.contains(bonusValue)) {
                usedBonuses.remove(bonusValue); // Remove one instance
            } else {
                return bonusValue; // This bonus value is still available
            }
        }
        return 0; // No bonuses available
    }
}