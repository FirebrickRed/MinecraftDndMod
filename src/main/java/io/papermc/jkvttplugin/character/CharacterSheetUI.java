package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.player.Background.DndBackground;
import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.player.Classes.DndClass;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CharacterSheetUI {
    // Need to rename to CharacterSheetInventoryBuilder or similar

    public static void handleSheetInventoryClick(Player player, InventoryClickEvent event) {

    }

    public static void openCharacterSheetInventory(Player player, CharacterSheet sheet) {

    }

    public static Inventory buildCharacterSheetInventory(CharacterSheet sheet) {

        return null;
    }

    public static Inventory buildRaceInventory() {
        Collection<DndRace> allRaces = RaceLoader.getAllRaces();
        int inventorySize = Util.getInventorySize(allRaces.size());
        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Choose your Race"));

        int slot = 0;
        for (DndRace race : allRaces) {
            inventory.setItem(slot, race.getRaceIcon());
            slot++;
        }

        return inventory;
    }

    public static Inventory buildSubRaceInventory(DndRace selectedRace) {
        Collection<DndSubRace> subRaces = selectedRace.getSubraces().values();
        int totalItems = subRaces.size() + 1;
        int inventorySize = Util.getInventorySize(totalItems);

        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Choose your Sub Race"));

        int i = 0;
        for (DndSubRace subRace : subRaces) {
            inventory.setItem(i++, subRace.getRaceIcon());
        }

        ItemStack backIcon = Util.createItem(Component.text("Back to Race"), null, "back_arrow_icon", 0);
        inventory.setItem(inventorySize - 1, backIcon);

        return inventory;
    }

    public static Inventory buildClassInventory() {
        int inventorySize = Util.getInventorySize(DndClass.DndClassType.values().length - 1);
        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Choose your Class"));

        int slot = 0;
        for (DndClass.DndClassType classType : DndClass.DndClassType.values()) {
            inventory.setItem(slot, classType.getDndClass().getClassIcon());
            slot++;
        }

        return inventory;
    }

    public static Inventory buildBackgroundInventory() {
        int inventorySize = Util.getInventorySize(DndBackground.DndBackgroundType.values().length);
        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Select Background"));

        int slot = 0;
        for (DndBackground.DndBackgroundType backgroundType : DndBackground.DndBackgroundType.values()) {
            inventory.setItem(slot, backgroundType.getDndBackground().getBackgroundIcon());
            slot++;
        }

        return inventory;
    }

    public static Inventory buildAbilityScoreInventory(Map<Ability, Integer> abilityScores) {
        int inventorySize = Util.getInventorySize(Ability.values().length * 3 + 1); // 3 rows for each ability (increase, score, decrease) + confirm button
        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Choose your Ability Scores"));

        for (int i = 0; i < Ability.values().length; i++) {
            Ability ability = Ability.values()[i];
            int baseSlot = i + 1;
            int score = abilityScores.getOrDefault(ability, 10);

            // Row 1 - Increase by 1, Row 2 - Score, Row 3 - Decrease by 1
            inventory.setItem(baseSlot, Util.createItem(
                    Component.text("Increase by 1"),
                    null,
                    "up_icon",
                    0
            ));

            inventory.setItem(baseSlot + 9, buildAbilityScoreItem(ability, score));

            inventory.setItem(baseSlot + 18, Util.createItem(
                    Component.text("Decrease by 1"),
                    null,
                    "down_icon",
                    0
            ));

            inventory.setItem(inventorySize-1, Util.createItem(
                    Component.text("Confirm"),
                    null,
                    "confirm_icon",
                    0
            ));
        }

        return inventory;
    }

    public static ItemStack buildAbilityScoreItem(Ability ability, int score) {
        int modifier = (score - 10) / 2;
        String modText = (modifier >= 0 ? "+" : "") + modifier;
        List<Component> lore = List.of(Component.text(modText));

        return Util.createItem(
                Component.text(ability.name() + ": " + score),
                lore,
                ability.name().toLowerCase().substring(0, 3) + "_icon",
                Math.max(1, score)
        );
    }
}
