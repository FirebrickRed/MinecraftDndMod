package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ChoiceMerger;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tabbed choice menu that groups player choices by category (Languages, Skills, Equipment, etc.).
 * This replaces the linear PlayersChoiceMenu with a more organized, scalable interface.
 */
public class TabbedChoiceMenu {

    /**
     * Opens the tabbed choice menu with the first available category selected.
     */
    public static void open(Player player, UUID sessionId) {
        open(player, sessionId, null);
    }

    /**
     * Opens the tabbed choice menu with a specific category tab active.
     *
     * @param player The player to show the menu to
     * @param sessionId The session ID
     * @param activeTab The category to show, or null to default to first category
     */
    public static void open(Player player, UUID sessionId, ChoiceCategory activeTab) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("No active character creation session!").color(NamedTextColor.RED));
            return;
        }

        // Get pending choices (rebuild only if not yet initialized)
        List<PendingChoice<?>> allChoices = session.getPendingChoices();
        if (allChoices.isEmpty()) {
            allChoices = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
        }
        List<MergedChoice> merged = ChoiceMerger.mergeChoices(allChoices, session);

        if (merged.isEmpty()) {
            player.sendMessage(Component.text("No choices to make!").color(NamedTextColor.GOLD));
            CharacterCreationSheetMenu.open(player, sessionId);
            return;
        }

        // Default to first category if none specified
        if (activeTab == null) {
            activeTab = merged.get(0).getCategory();
        }

        player.openInventory(build(merged, sessionId, activeTab));
    }

    /**
     * Builds the tabbed choice inventory.
     */
    private static Inventory build(List<MergedChoice> mergedChoices, UUID sessionId, ChoiceCategory activeTab) {
        Inventory inv = Bukkit.createInventory(
                new MenuHolder(MenuType.TABBED_CHOICES, sessionId),
                54,
                Component.text("Make Your Choices")
        );

        // Row 0: Category tabs (slots 0-8)
        buildTabRow(inv, mergedChoices, activeTab);

        // Rows 1-4: Content for active tab (slots 9-44)
        // For equipment, we may have multiple MergedChoice objects (one per choice)
        // For other categories, there's only one MergedChoice
        List<MergedChoice> activeChoices = findAllMergedChoices(mergedChoices, activeTab);
        if (!activeChoices.isEmpty()) {
            buildContentArea(inv, activeChoices, sessionId);
        }

        // Row 5: Confirm button (slot 53)
        buildConfirmButton(inv, mergedChoices);

        return inv;
    }

    /**
     * Builds the top row of category tabs.
     * Groups equipment choices into a single tab.
     */
    private static void buildTabRow(
            Inventory inv,
            List<MergedChoice> merged,
            ChoiceCategory activeTab
    ) {
        int slot = 0;

        // Group by category to avoid duplicate tabs (especially for equipment)
        var byCategory = new java.util.LinkedHashMap<ChoiceCategory, List<MergedChoice>>();
        for (MergedChoice choice : merged) {
            byCategory.computeIfAbsent(choice.getCategory(), k -> new ArrayList<>()).add(choice);
        }

        for (var entry : byCategory.entrySet()) {
            if (slot >= 9) break; // Max 9 tabs

            ChoiceCategory cat = entry.getKey();
            List<MergedChoice> choicesInCategory = entry.getValue();

            // Calculate aggregate stats for this category
            int totalSelected = choicesInCategory.stream().mapToInt(MergedChoice::getSelectedCount).sum();
            int totalRequired = choicesInCategory.stream().mapToInt(MergedChoice::getTotalChooseCount).sum();
            boolean allComplete = choicesInCategory.stream().allMatch(MergedChoice::isComplete);

            NamedTextColor statusColor = totalSelected >= totalRequired ? NamedTextColor.GREEN :
                                         totalSelected > 0 ? NamedTextColor.YELLOW : NamedTextColor.RED;

            ItemStack tab = new ItemStack(cat.getIcon());
            tab.editMeta(m -> {
                m.displayName(Component.text(cat.getDisplayName()).color(statusColor));

                List<Component> lore = new ArrayList<>();

                // Progress
                lore.add(Component.text("Progress: " + totalSelected + "/" + totalRequired)
                        .color(statusColor));

                // For equipment, show count of choices
                if (cat == ChoiceCategory.EQUIPMENT && choicesInCategory.size() > 1) {
                    lore.add(Component.text(choicesInCategory.size() + " equipment choices")
                            .color(NamedTextColor.GRAY));
                }

                // Status message
                if (allComplete) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("✓ Complete").color(NamedTextColor.GREEN));
                } else {
                    lore.add(Component.text(""));
                    lore.add(Component.text("Click to view").color(NamedTextColor.YELLOW));
                }

                m.lore(lore);

                // Glow active tab
                if (cat == activeTab) {
                    m.addEnchant(Enchantment.UNBREAKING, 1, true);
                    m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            });

            ItemUtil.setAction(tab, MenuAction.SWITCH_CHOICE_TAB, cat.name());

            inv.setItem(slot++, tab);
        }
    }

    /**
     * Builds the content area showing options for the active category.
     * Supports multiple MergedChoice objects for equipment (each with its own header).
     */
    private static void buildContentArea(
            Inventory inv,
            List<MergedChoice> choices,
            UUID sessionId
    ) {
        int slot = 9;

        for (MergedChoice choice : choices) {
            if (slot >= 45) break; // Reserve row 5 for buttons

            // For equipment choices with titles, add a section header
            if (choice.getCategory() == ChoiceCategory.EQUIPMENT && !choice.getSourcePendingChoices().isEmpty()) {
                PendingChoice<?> pc = choice.getSourcePendingChoices().get(0);
                if (pc.getTitle() != null && !pc.getTitle().isEmpty()) {
                    ItemStack header = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
                    header.editMeta(m -> {
                        m.displayName(Component.text("─ " + pc.getTitle() + " ─")
                                .color(NamedTextColor.GOLD));
                        m.lore(List.of(
                                Component.text("Choose " + choice.getTotalChooseCount())
                                        .color(NamedTextColor.GRAY),
                                Component.text("Progress: " + choice.getProgressText())
                                        .color(choice.getStatusColor())
                        ));
                    });
                    ItemUtil.setAction(header, MenuAction.VIEW_CHOICE_INFO, "header");
                    inv.setItem(slot++, header);
                }
            }

            // Show already-known items as gray glass panes (informational only)
            for (String knownKey : choice.getAlreadyKnown()) {
                if (slot >= 45) break;

                ItemStack known = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                known.editMeta(m -> {
                    m.displayName(Component.text(Util.prettify(knownKey)).color(NamedTextColor.GRAY));
                    m.lore(List.of(
                            Component.text("Already Known").color(NamedTextColor.DARK_GRAY),
                            Component.text("(Cannot select)").color(NamedTextColor.DARK_GRAY)
                    ));
                });

                ItemUtil.setAction(known, MenuAction.VIEW_CHOICE_INFO, "already_known");
                inv.setItem(slot++, known);
            }

            // Show all available options
            for (String optionKey : choice.getAvailableOptionKeys()) {
                if (slot >= 45) break;

                boolean selected = choice.isSelected(optionKey);
                String displayLabel = choice.displayFor(optionKey);

                // Check if this is a TAG or BUNDLE option that needs drilldown
                boolean needsDrilldown = isWildcardOption(choice, optionKey);

                // Check if this TAG has been resolved to a specific item
                boolean isResolved = needsDrilldown && choice.isTagResolved(optionKey);
                EquipmentOption resolvedItem = isResolved ? choice.getResolvedItem(optionKey) : null;

                // Use glass panes for visual status
                // Resolved TAGs show as green (selected state)
                Material material = (selected || isResolved) ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

                ItemStack option = new ItemStack(material);
                option.editMeta(m -> {
                    m.displayName(Component.text(displayLabel));

                    List<Component> lore = new ArrayList<>();

                    if (isResolved && resolvedItem != null) {
                        // Show the resolved item below the TAG label
                        lore.add(Component.text("✓ Selected").color(NamedTextColor.GREEN));
                        lore.add(Component.text("→ " + resolvedItem.prettyLabel()).color(NamedTextColor.WHITE));
                        lore.add(Component.text("Click to change").color(NamedTextColor.GRAY));
                    } else if (selected) {
                        lore.add(Component.text("✓ Selected").color(NamedTextColor.GREEN));
                        lore.add(Component.text("Click to deselect").color(NamedTextColor.GRAY));
                    } else if (needsDrilldown) {
                        lore.add(Component.text("Click to choose specific item").color(NamedTextColor.AQUA));
                    } else {
                        lore.add(Component.text("Click to select").color(NamedTextColor.GRAY));
                    }

                    m.lore(lore);
                });

                // Decide action type based on whether it's a wildcard option
                if (needsDrilldown && !selected) {
                    // For TAG/BUNDLE options (resolved or not), use DRILLDOWN_OPEN
                    String payload = choice.getChoiceId() + "|" + optionKey;
                    ItemUtil.setAction(option, MenuAction.DRILLDOWN_OPEN, payload);
                } else {
                    // For regular options or already-selected non-TAG options, use TOGGLE_CHOICE_OPTION
                    // Payload format: "CATEGORY|choiceId|optionKey"
                    // The choiceId disambiguates between multiple equipment choices with overlapping options
                    String payload = choice.getCategory().name() + "|" + choice.getChoiceId() + "|" + optionKey;
                    ItemUtil.setAction(option, MenuAction.TOGGLE_CHOICE_OPTION, payload);
                }

                inv.setItem(slot++, option);
            }
        }
    }

    /**
     * Checks if an option key represents a TAG or BUNDLE equipment option that requires drilldown.
     */
    private static boolean isWildcardOption(MergedChoice choice, String optionKey) {
        // Get the first PendingChoice that has this option
        for (PendingChoice<?> pc : choice.getSourcePendingChoices()) {
            if (pc.optionKeys().contains(optionKey)) {
                Object opt = pc.optionForKey(optionKey);
                if (opt instanceof EquipmentOption eo) {
                    if (eo.getKind() == EquipmentOption.Kind.TAG) return true;
                    if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                        // Check if bundle contains any TAG parts
                        for (var part : eo.getParts()) {
                            if (part.getKind() == EquipmentOption.Kind.TAG) return true;
                        }
                    }
                }
                break;
            }
        }
        return false;
    }

    /**
     * Builds the confirm button at the bottom of the menu.
     */
    private static void buildConfirmButton(Inventory inv, List<MergedChoice> merged) {
        boolean allComplete = merged.stream().allMatch(MergedChoice::isComplete);

        ItemStack confirm = new ItemStack(allComplete ? Material.LIME_BED : Material.GRAY_BED);
        confirm.editMeta(m -> {
            if (allComplete) {
                m.displayName(Component.text("Confirm Choices").color(NamedTextColor.GREEN));
                m.lore(List.of(
                        Component.text("All choices complete!").color(NamedTextColor.GRAY),
                        Component.text("Click to return to character sheet").color(NamedTextColor.GRAY)
                ));
            } else {
                m.displayName(Component.text("Confirm Choices").color(NamedTextColor.GRAY));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Complete all choices first:").color(NamedTextColor.RED));
                lore.add(Component.text(""));

                for (MergedChoice mc : merged) {
                    if (!mc.isComplete()) {
                        lore.add(Component.text("  • " + mc.getCategory().getDisplayName() +
                                " (" + mc.getProgressText() + ")")
                                .color(NamedTextColor.RED));
                    }
                }

                m.lore(lore);
            }
        });

        if (allComplete) {
            ItemUtil.setAction(confirm, MenuAction.CONFIRM_PLAYER_CHOICES, "ok");
        }

        inv.setItem(53, confirm);
    }

    /**
     * Finds all MergedChoice objects for a specific category.
     * For equipment, there may be multiple (one per choice). For other categories, just one.
     */
    private static List<MergedChoice> findAllMergedChoices(List<MergedChoice> merged, ChoiceCategory category) {
        return merged.stream()
                .filter(mc -> mc.getCategory() == category)
                .toList();
    }

    /**
     * Opens a drilldown submenu for TAG/BUNDLE equipment options.
     * This allows the player to select a specific item from a category (e.g., "Any Simple Weapon" -> "Dagger", "Shortsword", etc.)
     *
     * @param player The player viewing the menu
     * @param sessionId The session ID
     * @param choiceId The ID of the choice being modified
     * @param wildcardKey The key of the TAG/BUNDLE option being expanded
     * @param title The title to display in the menu
     * @param subOptionKeys List of specific item keys to choose from
     * @param displayFunction Function to convert keys to display labels
     * @param returnCategory The category tab to return to when done
     */
    public static void openDrilldown(
            Player player,
            UUID sessionId,
            String choiceId,
            String wildcardKey,
            String title,
            List<String> subOptionKeys,
            java.util.function.Function<String, String> displayFunction,
            ChoiceCategory returnCategory
    ) {
        int size = 54;
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.TABBED_CHOICES, sessionId),
                size,
                Component.text(title)
        );

        int slot = 0;
        for (String sub : subOptionKeys) {
            String payload = choiceId + "|" + wildcardKey + "|" + sub + "|" + returnCategory.name();
            ItemStack it = ItemUtil.createActionItem(
                    Material.PAPER,
                    Component.text(displayFunction.apply(sub)),
                    null,
                    MenuAction.DRILLDOWN_PICK,
                    payload
            );
            inventory.setItem(slot++, it);
            if (slot >= size - 9) break;
        }

        ItemStack back = ItemUtil.createActionItem(
                Material.ARROW,
                Component.text("Back"),
                null,
                MenuAction.DRILLDOWN_BACK,
                returnCategory.name()
        );
        inventory.setItem(size - 9, back);

        player.openInventory(inventory);
    }
}