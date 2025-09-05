package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
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

public class CharacterSheetMenu {

    public static void open(Player player, UUID sessionId) {
        player.openInventory(build(player, sessionId));
    }

    public static Inventory build(Player player, UUID sessionId) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.CHARACTER_SHEET, sessionId),
                54,
                Component.text("Character Sheet")
        );

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());

        ItemStack raceItem = createSelectionItem(Material.PLAYER_HEAD, "Race", session != null ? session.getSelectedRace() : null, "Click to select your race");
        raceItem = ItemUtil.tagAction(raceItem, MenuAction.OPEN_RACE_SELECTION, "race");
        inventory.setItem(10, raceItem);

        ItemStack classItem = createSelectionItem(Material.IRON_SWORD, "Class", session != null ? session.getSelectedClass() : null, "Click to select your class");
        classItem = ItemUtil.tagAction(classItem, MenuAction.OPEN_CLASS_SELECTION, "class");
        inventory.setItem(12, classItem);

        ItemStack backgroundItem = createSelectionItem(Material.BOOK, "Background", session != null ? session.getSelectedBackground() : null, "Click to select your background");
        backgroundItem = ItemUtil.tagAction(backgroundItem, MenuAction.OPEN_BACKGROUND_SELECTION, "background");
        inventory.setItem(14, backgroundItem);

        if (session != null && session.getSelectedRace() != null) {
            DndRace race = RaceLoader.getRace(session.getSelectedRace());
            if (race != null && race.hasSubraces()) {
                ItemStack subraceItem = createSelectionItem(Material.PLAYER_HEAD, "Subrace", session.getSelectedSubRace(), "Click to select your subrace");
                subraceItem = ItemUtil.tagAction(subraceItem, MenuAction.OPEN_SUBRACE_SELECTION, "subrace");
                inventory.setItem(19, subraceItem);
            }
        }

        ItemStack abilityItem = new ItemStack(Material.PAPER);
        abilityItem.editMeta(m -> {
            m.displayName(Component.text("Ability Scores").color(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            if (session != null && isBasicSelectionComplete(session)) {
                lore.add(Component.text("Click to allocate ability scores").color(NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Complete basic selections first").color(NamedTextColor.GRAY));
            }
            m.lore(lore);
        });
        if (session != null && isBasicSelectionComplete(session)) {
            abilityItem = ItemUtil.tagAction(abilityItem, MenuAction.OPEN_ABILITY_ALLOCATION, "abilities");
        }
        inventory.setItem(21, abilityItem);

        if (session != null && isBasicSelectionComplete(session)) {
            var pendingChoices = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
            if (!pendingChoices.isEmpty()) {
                ItemStack choicesItem = new ItemStack(Material.CHEST);
                choicesItem.editMeta(m -> {
                    m.displayName(Component.text("Equipment & Choices").color(NamedTextColor.GOLD));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Pending choices: " + pendingChoices.size()).color(NamedTextColor.GRAY));
                    lore.add(Component.text("Click to make selections").color(NamedTextColor.GRAY));
                    m.lore(lore);
                });
                choicesItem = ItemUtil.tagAction(choicesItem, MenuAction.OPEN_PLAYER_OPTION_SELECTION, "choices");
                inventory.setItem(23, choicesItem);
            }
        }

        ItemStack confirmItem = createConfirmButton(player, session);
        inventory.setItem(49, confirmItem);

        return inventory;
    }

    private static ItemStack createSelectionItem(Material material, String title, String selected, String instruction) {
        ItemStack item = new ItemStack(material);
        item.editMeta(m -> {
            m.displayName(Component.text(title).color(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();

            if (selected != null && !selected.isEmpty()) {
                lore.add(Component.text("Selected: " + prettifySelection(selected)).color(NamedTextColor.GREEN));
                lore.add(Component.text("Click to change").color(NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Not Selected").color(NamedTextColor.RED));
                lore.add(Component.text(instruction).color(NamedTextColor.GRAY));
            }

            m.lore(lore);
        });
        return item;
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

                    var pendingChoices = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
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

    private static String prettifySelection(String selection) {
        if (selection == null) return "";
        return selection.replace('_', ' ')
                .substring(0, 1).toUpperCase() +
                selection.replace('_', ' ').substring(1);
    }
}
