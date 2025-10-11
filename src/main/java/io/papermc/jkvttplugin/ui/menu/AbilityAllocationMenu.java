package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.AbilityScoreChoice;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AbilityAllocationMenu {

    private AbilityAllocationMenu() {}

    public static void open(Player player, CharacterCreationSession session) {
        player.openInventory(build(session));
    }

    public static Inventory build(CharacterCreationSession session) {
        int inventorySize = 54;

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.ABILITY_ALLOCATION, session.getSessionId()),
                inventorySize,
                Component.text("Set your Ability Scores")
        );

        // Get race and subrace data
        DndRace race = session.getSelectedRace() != null ? RaceLoader.getRace(session.getSelectedRace()) : null;
        DndSubRace subrace = null;
        if (race != null && session.getSelectedSubRace() != null) {
            subrace = race.getSubraces().get(session.getSelectedSubRace());
        }

        EnumMap<Ability, Integer> baseScores = session.getAbilityScores();
        EnumMap<Ability, Integer> racialBonuses = session.getRacialBonusAllocations();

        // Row assignments
        final int RACE_BONUS_ROW = 0;
        final int SUBRACE_BONUS_ROW = 1;
        final int UP_ROW = 2;
        final int ABILITY_ROW = 3;
        final int DOWN_ROW = 4;
        final int THIRD_BONUS_ROW = 5;  // Currently unused - reserved for +1/+1/+1 distributions

        // Column 0: Race info and distribution buttons
        buildColumn0(inventory, race, subrace, session);

        // Columns 1-6: Abilities (STR, DEX, CON, INT, WIS, CHA)
        int col = 1;
        for (Ability ability : Ability.values()) {
            buildAbilityColumn(inventory, ability, col, baseScores, racialBonuses, race, subrace, session,
                    RACE_BONUS_ROW, SUBRACE_BONUS_ROW, THIRD_BONUS_ROW, UP_ROW, ABILITY_ROW, DOWN_ROW);
            col++;
        }

        // Confirm button (bottom right)
        ItemStack confirm = new ItemStack(Material.LIME_BED);
        confirm.editMeta(m -> m.displayName(Component.text("Confirm Character")));
        confirm = ItemUtil.tagAction(confirm, MenuAction.CONFIRM_CHARACTER, "ok");
        inventory.setItem(slot(5, 0), confirm);

        return inventory;
    }

    /**
     * Build column 0 with race info and distribution choice buttons
     */
    private static void buildColumn0(Inventory inv, DndRace race, DndSubRace subrace, CharacterCreationSession session) {
        // Row 0-1: Race/subrace display
        if (race != null) {
            ItemStack raceItem = new ItemStack(Material.PLAYER_HEAD);
            raceItem.editMeta(m -> {
                m.displayName(Component.text(race.getName()).color(NamedTextColor.GOLD));
                m.lore(List.of(Component.text("Race").color(NamedTextColor.GRAY)));
            });
            inv.setItem(slot(0, 0), raceItem);
        }

        if (subrace != null) {
            ItemStack subraceItem = new ItemStack(Material.PLAYER_HEAD);
            subraceItem.editMeta(m -> {
                m.displayName(Component.text(subrace.getName()).color(NamedTextColor.YELLOW));
                m.lore(List.of(Component.text("Subrace").color(NamedTextColor.GRAY)));
            });
            inv.setItem(slot(1, 0), subraceItem);
        }

        // Row 2-3: Distribution choice buttons (if race or subrace has choices)
        boolean raceHasChoice = race != null && race.getAbilityScoreChoice() != null && race.getAbilityScoreChoice().hasChoices();
        boolean subraceHasChoice = subrace != null && subrace.getAbilityScoreChoice() != null && subrace.getAbilityScoreChoice().hasChoices();

        if (raceHasChoice || subraceHasChoice) {
            AbilityScoreChoice choice = raceHasChoice ? race.getAbilityScoreChoice() : subrace.getAbilityScoreChoice();
            List<List<Integer>> distributions = choice.getDistributions();
            String selectedDist = session.getRacialBonusDistribution();

            int rowIndex = 2;
            for (List<Integer> dist : distributions) {
                String distLabel = AbilityScoreChoice.getDistributionLabel(dist);
                String distKey = dist.toString(); // e.g., "[2, 1]"

                boolean isSelected = distKey.equals(selectedDist);
                Material material = isSelected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;

                ItemStack distButton = new ItemStack(material);
                distButton.editMeta(m -> {
                    m.displayName(Component.text(distLabel).color(isSelected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                    m.lore(List.of(Component.text("Click to select this distribution").color(NamedTextColor.DARK_GRAY)));
                });
                distButton = ItemUtil.tagAction(distButton, MenuAction.SELECT_RACIAL_BONUS_DISTRIBUTION, distKey);

                inv.setItem(slot(rowIndex, 0), distButton);
                rowIndex++;
                if (rowIndex > 3) break; // Only 2 distribution buttons max
            }
        }
    }

    /**
     * Build a single ability column with racial bonuses and base score controls
     */
    private static void buildAbilityColumn(
            Inventory inv,
            Ability ability,
            int col,
            EnumMap<Ability, Integer> baseScores,
            EnumMap<Ability, Integer> racialBonuses,
            DndRace race,
            DndSubRace subrace,
            CharacterCreationSession session,
            int raceBonusRow,
            int subraceBonusRow,
            int thirdBonusRow,
            int upRow,
            int abilityRow,
            int downRow
    ) {
        // Row 0: Race bonus (fixed or choice button)
        if (race != null) {
            buildBonusSlot(inv, raceBonusRow, col, ability, race.getFixedAbilityScores(),
                    race.getAbilityScoreChoice(), session, "race", racialBonuses);
        }

        // Row 1: Subrace bonus (fixed or choice button)
        if (subrace != null) {
            buildBonusSlot(inv, subraceBonusRow, col, ability, subrace.getFixedAbilityScores(),
                    subrace.getAbilityScoreChoice(), session, "subrace", racialBonuses);
        }

        // Row 2: Third bonus row (only for +1/+1/+1 distributions)
        String selectedDist = session.getRacialBonusDistribution();
        if (selectedDist != null && selectedDist.contains("1, 1, 1")) {
            // This row is used for the third +1 bonus in a [1, 1, 1] distribution
            // We'll handle it as part of the same choice system
            // For now, leave empty or add a third bonus button if needed
        }

        // Row 3: Increase button
        int baseVal = baseScores.get(ability);
        boolean canIncrease = baseVal < 20;
        ItemStack up = new ItemStack(canIncrease ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        up.editMeta(m -> m.displayName(Component.text("Increase " + ability.name())
                .color(canIncrease ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        if (canIncrease) up = ItemUtil.tagAction(up, MenuAction.INCREASE_ABILITY, ability.name());
        inv.setItem(slot(upRow, col), up);

        // Row 4: Ability tile showing total score
        // Calculate total racial bonus = fixed (race) + fixed (subrace) + player-chosen
        int racialBonus = 0;

        // Add fixed race bonuses
        if (race != null && race.getFixedAbilityScores().containsKey(ability)) {
            racialBonus += race.getFixedAbilityScores().get(ability);
        }

        // Add fixed subrace bonuses
        if (subrace != null && subrace.getFixedAbilityScores().containsKey(ability)) {
            racialBonus += subrace.getFixedAbilityScores().get(ability);
        }

        // Add player-chosen bonuses (from distribution selection)
        racialBonus += racialBonuses.getOrDefault(ability, 0);

        int totalScore = baseVal + racialBonus;

        ItemStack tile = new ItemStack(Material.PAPER);
        tile.setAmount(Math.max(1, Math.min(64, totalScore)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Base: " + baseVal).color(NamedTextColor.GRAY));
        if (racialBonus > 0) {
            lore.add(Component.text("Racial: +" + racialBonus).color(NamedTextColor.GREEN));
        }
        lore.add(Component.text("Total: " + totalScore).color(NamedTextColor.YELLOW));
        int mod = abilityMod(totalScore);
        lore.add(Component.text("Modifier: " + formatMod(mod)).color(NamedTextColor.AQUA));

        tile.editMeta(m -> {
            m.displayName(Component.text(ability.name() + ": " + totalScore).color(NamedTextColor.WHITE));
            m.lore(lore);
        });
        inv.setItem(slot(abilityRow, col), tile);

        // Row 5: Decrease button
        boolean canDecrease = baseVal > 0;
        ItemStack down = new ItemStack(canDecrease ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        down.editMeta(m -> m.displayName(Component.text("Decrease " + ability.name()).color(canDecrease ? NamedTextColor.RED : NamedTextColor.GRAY)));
        if (canDecrease) {
            down = ItemUtil.tagAction(down, MenuAction.DECREASE_ABILITY, ability.name());
        }
        inv.setItem(slot(downRow, col), down);
    }

    /**
     * Build a racial bonus slot - either a fixed label or a choice button
     */
    private static void buildBonusSlot(
            Inventory inv,
            int row,
            int col,
            Ability ability,
            Map<Ability, Integer> fixedBonuses,
            AbilityScoreChoice choiceBonuses,
            CharacterCreationSession session,
            String source,
            EnumMap<Ability, Integer> currentAllocations
    ) {
        // Check if this ability has a fixed bonus
        Integer fixedBonus = fixedBonuses.get(ability);
        if (fixedBonus != null && fixedBonus > 0) {
            // Show a fixed bonus label (only in this column)
            ItemStack label = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            label.editMeta(m -> {
                m.displayName(Component.text("+" + fixedBonus + " " + source).color(NamedTextColor.GREEN));
                m.lore(List.of(Component.text("Fixed bonus").color(NamedTextColor.DARK_GRAY)));
            });
            inv.setItem(slot(row, col), label);
            return;
        }

        // Check if this ability has a choice bonus
        if (choiceBonuses != null && choiceBonuses.hasChoices() && session.getRacialBonusDistribution() != null) {
            // Show a choice button
            int currentBonus = currentAllocations.getOrDefault(ability, 0);

            Material material;
            NamedTextColor color;
            String displayText;

            // ToDo: the 2 should apply to the same ability not different ones. right?
            if (currentBonus > 0) {
                // This ability has a bonus applied - show LIME
                material = Material.LIME_STAINED_GLASS_PANE;
                color = NamedTextColor.GREEN;
                displayText = "+" + currentBonus;
            } else {
                // Check if there are bonuses left to apply
                int totalApplied = session.getTotalRacialBonusesApplied();
                String distKey = session.getRacialBonusDistribution();
                int totalAllowed = calculateTotalBonusPoints(distKey);

                if (totalApplied < totalAllowed) {
                    // Can still apply bonuses - show GREEN
                    material = Material.GREEN_STAINED_GLASS_PANE;
                    color = NamedTextColor.DARK_GREEN;
                    displayText = "Click";
                } else {
                    // All bonuses applied elsewhere - show RED to move
                    material = Material.RED_STAINED_GLASS_PANE;
                    color = NamedTextColor.RED;
                    displayText = "Move";
                }
            }

            ItemStack button = new ItemStack(material);
            button.editMeta(m -> {
                m.displayName(Component.text(displayText).color(color));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(source + " bonus").color(NamedTextColor.GRAY));
                if (currentBonus > 0) {
                    lore.add(Component.text("Click to remove").color(NamedTextColor.DARK_GRAY));
                } else {
                    lore.add(Component.text("Click to apply").color(NamedTextColor.DARK_GRAY));
                }
                m.lore(lore);
            });

            button = ItemUtil.tagAction(button, MenuAction.APPLY_RACIAL_BONUS, ability.name() + ":" + source);
            inv.setItem(slot(row, col), button);
        }
        // If no fixed bonus and no choice, leave the slot empty
    }

    /**
     * Calculate total bonus points from a distribution key like "[2, 1]"
     */
    private static int calculateTotalBonusPoints(String distKey) {
        if (distKey == null) return 0;
        // Parse the distribution string: "[2, 1]" -> 3, "[1, 1, 1]" -> 3
        int total = 0;
        String[] parts = distKey.replace("[", "").replace("]", "").split(",");
        for (String part : parts) {
            try {
                total += Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {}
        }
        return total;
    }

    private static int slot(int row, int col) {
        return row * 9 + col;
    }

    private static int abilityMod(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    private static String formatMod(int mod) {
        return (mod >= 0 ? "+" : "") + mod;
    }
}