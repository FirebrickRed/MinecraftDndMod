package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import io.papermc.jkvttplugin.ui.menu.TabbedChoiceMenu;
import io.papermc.jkvttplugin.util.EquipmentUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handles click events for the Tabbed Choices menu.
 * This menu displays player choices organized by category (Languages, Skills, Equipment, etc.)
 * with tabs for easy navigation and support for TAG resolution (drilldown menus).
 */
public class TabbedChoicesHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        switch (action) {
            case SWITCH_CHOICE_TAB -> {
                // Payload is the category name (e.g., "LANGUAGE", "SKILL")
                try {
                    ChoiceCategory newTab = ChoiceCategory.valueOf(payload);
                    TabbedChoiceMenu.open(player, sessionId, newTab);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("Invalid category: " + payload);
                }
            }
            case TOGGLE_CHOICE_OPTION -> {
                // Payload format: "CATEGORY|choiceId|optionKey"
                String[] parts = payload.split("\\|", 3);
                if (parts.length != 3) return;

                try {
                    ChoiceCategory category = ChoiceCategory.valueOf(parts[0]);
                    String choiceId = parts[1];
                    String optionKey = parts[2];

                    // Rebuild and merge choices
                    List<PendingChoice<?>> allChoices = session.getPendingChoices();
                    if (allChoices.isEmpty()) {
                        allChoices = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
                    }

                    List<MergedChoice> merged = io.papermc.jkvttplugin.util.ChoiceMerger.mergeChoices(allChoices, session);

                    // For SKILL category: handle moving selections between sections
                    // (e.g., clicking History in Subclass Skills when it's selected in Class Skills moves it)
                    if (category == ChoiceCategory.SKILL) {
                        // Find the target choice where the click occurred
                        MergedChoice targetChoice = merged.stream()
                                .filter(mc -> mc.getCategory() == category && mc.getChoiceId().equals(choiceId))
                                .findFirst()
                                .orElse(null);

                        if (targetChoice != null) {
                            // Check if this skill is selected elsewhere
                            boolean selectedElsewhere = targetChoice.getSelectedElsewhere().contains(optionKey);

                            if (selectedElsewhere) {
                                // Move the selection: deselect from other sections, select in this one
                                for (MergedChoice mc : merged) {
                                    if (mc.getCategory() == ChoiceCategory.SKILL && mc.isSelected(optionKey)) {
                                        // Deselect from the other section
                                        mc.toggleOption(optionKey);
                                    }
                                }
                                // Select in this section
                                targetChoice.toggleOption(optionKey);
                            } else {
                                // Normal toggle within this section
                                targetChoice.toggleOption(optionKey);
                            }
                        }
                    } else {
                        // For other categories, toggle only in the specific choice
                        MergedChoice targetChoice = merged.stream()
                                .filter(mc -> mc.getCategory() == category && mc.getChoiceId().equals(choiceId))
                                .findFirst()
                                .orElse(null);

                        if (targetChoice != null) {
                            targetChoice.toggleOption(optionKey);
                        }
                    }

                    TabbedChoiceMenu.open(player, sessionId, category);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("Invalid choice: " + payload);
                }
            }
            case DRILLDOWN_OPEN -> {
                // Payload format: "choiceId|wildcardKey"
                String[] parts = splitChoicePayload(payload);
                if (parts == null) return;
                String choiceId = parts[0];
                String wildcardKey = parts[1];

                var pc = session.findPendingChoice(choiceId);
                if (pc == null) return;

                Object optObj = pc.optionForKey(wildcardKey);
                if (!(optObj instanceof EquipmentOption eo)) return;

                String tag = EquipmentUtil.extractTag(eo);
                if (tag == null) return;

                var ids = TagRegistry.itemsFor(tag);
                List<String> subKeys = ids.stream().map(id -> "item:" + id).toList();

                Function<String, String> disp = pc::displayFor;
                String title = "Choose specific: " + pc.displayFor(wildcardKey);

                // Determine which category to return to
                ChoiceCategory returnCategory = determineCategory(pc);

                TabbedChoiceMenu.openDrilldown(player, sessionId, choiceId, wildcardKey, title, subKeys, disp, returnCategory);
            }
            case DRILLDOWN_PICK -> {
                // Payload format: "choiceId|wildcardKey|subKey|categoryName"
                String[] parts = (payload != null) ? payload.split("\\|", 4) : null;
                if (parts == null || parts.length < 4) return;
                String choiceId = parts[0];
                String wildcardKey = parts[1];
                String subKey = parts[2];
                String categoryName = parts[3];

                var pc = session.findPendingChoice(choiceId);
                if (pc == null) return;

                Object optObj = pc.optionForKey(wildcardKey);

                if (optObj instanceof EquipmentOption eo) {
                    if (eo.getKind() == EquipmentOption.Kind.TAG) {
                        pc.deselectKey(wildcardKey);
                        session.toggleChoiceByKey(choiceId, subKey);
                    } else if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                        var chosenItem = EquipmentUtil.fromItemKey(subKey);
                        if (chosenItem != null) {
                            List<EquipmentOption> newParts = new ArrayList<>();
                            for (var p : eo.getParts()) {
                                if (p.getKind() == EquipmentOption.Kind.TAG) {
                                    newParts.add(chosenItem);
                                } else {
                                    newParts.add(p);
                                }
                            }
                            var newBundle = EquipmentOption.bundle(newParts);

                            pc.deselectKey(wildcardKey);

                            var rawPc = (PendingChoice) pc;
                            rawPc.toggleOption(newBundle, Collections.emptySet());
                        }
                    }
                } else {
                    pc.deselectKey(wildcardKey);
                    session.toggleChoiceByKey(choiceId, subKey);
                }

                // Return to the tabbed choice menu with the appropriate category
                try {
                    ChoiceCategory category = ChoiceCategory.valueOf(categoryName);
                    TabbedChoiceMenu.open(player, sessionId, category);
                } catch (IllegalArgumentException e) {
                    TabbedChoiceMenu.open(player, sessionId);
                }
            }
            case DRILLDOWN_BACK -> {
                // Payload is the category name to return to
                try {
                    ChoiceCategory category = ChoiceCategory.valueOf(payload);
                    TabbedChoiceMenu.open(player, sessionId, category);
                } catch (IllegalArgumentException e) {
                    TabbedChoiceMenu.open(player, sessionId);
                }
            }
            case VIEW_CHOICE_INFO -> {
                // Do nothing - it's just informational
            }
            case CONFIRM_PLAYER_CHOICES -> {
                if (!session.allChoicesSatisfied()) {
                    player.sendMessage("You still have unpicked options");
                    return;
                }

                player.closeInventory();
                player.sendMessage("Choices saved!");
                CharacterCreationSheetMenu.open(player, sessionId);
            }
        }
    }

    /**
     * Determines the category for a PendingChoice to use when returning from drilldown.
     */
    private ChoiceCategory determineCategory(PendingChoice<?> pc) {
        if (pc.getPlayersChoice() == null) return ChoiceCategory.EQUIPMENT;
        return ChoiceCategory.fromChoiceType(pc.getPlayersChoice().getType(), pc.getId());
    }

    private static String[] splitChoicePayload(String payload) {
        if (payload == null) return null;
        int i = payload.indexOf('|');
        if (i < 0) return null;
        return new String[] { payload.substring(0, i), payload.substring(i + 1) };
    }
}