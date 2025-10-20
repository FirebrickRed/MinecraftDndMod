package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CharacterCreationSheetMenu {

    public static void open(Player player, UUID sessionId) {
        player.openInventory(build(player, sessionId));
    }

    public static Inventory build(Player player, UUID sessionId) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.CHARACTER_CREATION_SHEET, sessionId),
                54,
                Component.text("Character Sheet")
        );

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());

        ItemStack raceItem = createSelectionItem(Material.PLAYER_HEAD, "Race", session != null ? session.getSelectedRace() : null, "Click to select your race", MenuAction.OPEN_RACE_SELECTION, "race");
        inventory.setItem(10, raceItem);

        ItemStack classItem = createSelectionItem(Material.IRON_SWORD, "Class", session != null ? session.getSelectedClass() : null, "Click to select your class", MenuAction.OPEN_CLASS_SELECTION, "class");
        inventory.setItem(12, classItem);

        ItemStack backgroundItem = createSelectionItem(Material.BOOK, "Background", session != null ? session.getSelectedBackground() : null, "Click to select your background", MenuAction.OPEN_BACKGROUND_SELECTION, "background");
        inventory.setItem(14, backgroundItem);

        if (session != null && session.getSelectedRace() != null) {
            DndRace race = RaceLoader.getRace(session.getSelectedRace());
            if (race != null && race.hasSubraces()) {
                ItemStack subraceItem = createSelectionItem(Material.PLAYER_HEAD, "Subrace", session.getSelectedSubRace(), "Click to select your subrace", MenuAction.OPEN_SUBRACE_SELECTION, "subrace");
                inventory.setItem(19, subraceItem);
            }
        }

        List<Component> abilityLore = new ArrayList<>();
        if (session != null && isBasicSelectionComplete(session)) {
            abilityLore.add(Component.text("Click to allocate ability scores", NamedTextColor.GRAY));
        } else {
            abilityLore.add(Component.text("Complete basic selections first", NamedTextColor.GRAY));
        }

        ItemStack abilityItem;
        if (session != null && isBasicSelectionComplete(session)) {
            abilityItem = ItemUtil.createActionItem(
                    Material.PAPER,
                    Component.text("Ability Scores", NamedTextColor.GOLD),
                    abilityLore,
                    MenuAction.OPEN_ABILITY_ALLOCATION,
                    "abilities"
            );
        } else {
            // Not clickable - just an info item
            abilityItem = new ItemStack(Material.PAPER);
            abilityItem.editMeta(m -> {
                m.displayName(Component.text("Ability Scores", NamedTextColor.GOLD));
                m.lore(abilityLore);
            });
        }
        inventory.setItem(21, abilityItem);

        if (session != null && isBasicSelectionComplete(session)) {
            if (session.getPendingChoices().isEmpty()) {
                var rebuilt = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
                for (var choice : rebuilt) {
                    System.out.println("  - Choice: " + choice.getId() + " (" + choice.getTitle() + ")");
                }
            }
            var pendingChoices = session.getPendingChoices();

            if (!pendingChoices.isEmpty()) {
                List<Component> choicesLore = new ArrayList<>();
                choicesLore.add(Component.text("Pending choices: " + pendingChoices.size(), NamedTextColor.GRAY));
                choicesLore.add(Component.text("Click to make selections", NamedTextColor.GRAY));

                ItemStack choicesItem = ItemUtil.createActionItem(
                        Material.CHEST,
                        Component.text("Equipment & Choices", NamedTextColor.GOLD),
                        choicesLore,
                        MenuAction.OPEN_PLAYER_OPTION_SELECTION,
                        "choices"
                );
                inventory.setItem(23, choicesItem);
            }
        }

        // Only show spell book after ability allocation has been visited
        if (session != null && session.hasVisitedAbilityAllocation()) {
            ItemStack spellItem = createSpellBookItem(session);
            inventory.setItem(32, spellItem);
        }

        ItemStack confirmItem = createConfirmButton(player, session);
        inventory.setItem(49, confirmItem);

        return inventory;
    }

    private static ItemStack createSelectionItem(Material material, String title, String selected, String instruction, MenuAction action, String payload) {
        List<Component> lore = new ArrayList<>();
        if (selected != null && !selected.isEmpty()) {
            lore.add(Component.text("Selected: " + Util.prettify(selected), NamedTextColor.GREEN));
            lore.add(Component.text("Click to change", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Not Selected", NamedTextColor.RED));
            lore.add(Component.text(instruction, NamedTextColor.GRAY));
        }

        return ItemUtil.createActionItem(
                material,
                Component.text(title, NamedTextColor.GOLD),
                lore,
                action,
                payload
        );
    }

    private static ItemStack createConfirmButton(Player player, CharacterCreationSession session) {
        boolean canConfirm = session != null; //&& isCharacterComplete(session);

        ItemStack confirmItem = new ItemStack(canConfirm ? Material.LIME_BED : Material.GRAY_BED);
        confirmItem.editMeta(m -> {
            if (canConfirm) {
                m.displayName(Component.text("Create Character").color(NamedTextColor.GREEN));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("All selections complete!").color(NamedTextColor.GRAY));
                lore.add(Component.text("Click to create your character").color(NamedTextColor.GRAY));
                m.lore(lore);
            } else {
                m.displayName(Component.text("Create Character").color(NamedTextColor.GRAY));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Complete all selections first").color(NamedTextColor.RED));

                if (session != null) {
                    if (session.getSelectedRace() == null) {
                        lore.add(Component.text("• Select a race").color(NamedTextColor.RED));
                    }
                    if (session.getSelectedClass() == null) {
                        lore.add(Component.text("• Select a class").color(NamedTextColor.RED));
                    }
                    if (session.getSelectedBackground() == null) {
                        lore.add(Component.text("• Select a background").color(NamedTextColor.RED));
                    }

                    if (session.getSelectedRace() != null) {
                        DndRace race = RaceLoader.getRace(session.getSelectedRace());
                        if (race != null && race.hasSubraces() && session.getSelectedSubRace() == null) {
                            lore.add(Component.text("• Select a subrace").color(NamedTextColor.RED));
                        }
                    }

//                    var pendingChoices = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
                    var pendingChoices = session.getPendingChoices();
                    if (!pendingChoices.isEmpty() && !session.allChoicesSatisfied()) {
                        lore.add(Component.text("• Complete equipment choices").color(NamedTextColor.RED));
                    }
                }

                m.lore(lore);
            }
        });

        if (canConfirm) {
            confirmItem = ItemUtil.tagAction(confirmItem, MenuAction.CONFIRM_CHARACTER, "confirm");
        }

        return confirmItem;
    }

    private static boolean isBasicSelectionComplete(CharacterCreationSession session) {
        if (session.getSelectedRace() == null || session.getSelectedClass() == null || session.getSelectedBackground() == null) {
            return false;
        }

        DndRace race = RaceLoader.getRace(session.getSelectedRace());
        return race == null || !race.hasSubraces() || session.getSelectedSubRace() != null;
    }

    private static boolean isCharacterComplete(CharacterCreationSession session) {
        return isBasicSelectionComplete(session) && session.allChoicesSatisfied();
    }

    private static ItemStack createSpellBookItem(CharacterCreationSession session) {
        List<Component> lore = new ArrayList<>();
        int cantripCount = session.getSelectedCantrips().size();
        int spellCount = session.getTotalSpellsSelected();

        if (cantripCount > 0 || spellCount > 0) {
            if (cantripCount > 0) {
                lore.add(Component.text("Cantrips: " + cantripCount, NamedTextColor.GREEN));
            }
            if (spellCount > 0) {
                lore.add(Component.text("Spells: " + spellCount, NamedTextColor.GREEN));
            }
            lore.add(Component.text("Click to change", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Not Selected", NamedTextColor.RED));
            lore.add(Component.text("Click to select your spells", NamedTextColor.GRAY));
        }

        return ItemUtil.createActionItem(
                Material.WRITABLE_BOOK,
                Component.text("Spells", NamedTextColor.GOLD),
                lore,
                MenuAction.OPEN_SPELL_SELECTION,
                "spell"
        );
    }
}
