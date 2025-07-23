package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.player.Background.DndBackground;
import io.papermc.jkvttplugin.player.Classes.DndClass;
import io.papermc.jkvttplugin.player.Races.DndRace;
import io.papermc.jkvttplugin.util.Ability;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static io.papermc.jkvttplugin.character.CharacterSheetManager.createCharacterSheet;
import static io.papermc.jkvttplugin.character.CharacterSheetUI.*;

public class CharacterCreationSession {
    private final Player player;
    private DndRace selectedRace;
    private DndClass selectedClass;
    private final Map<Ability, Integer> abilityScores = new HashMap<>();
    private DndBackground selectedBackground;
    private String characterName;

    public CharacterCreationSession(Player player) {
        this.player = player;

        for (Ability ability : Ability.values()) {
            abilityScores.put(ability, 10);
        }
    }

    public void handleRaceSelection(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
//        if (!Util.hasDisplayName(clickedItem)) return;
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

//        String raceName = Util.getDisplayName(clickedItem);
        String raceName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

        DndRace.DndRaceType selectedRaceType = DndRace.DndRaceType.fromString(raceName);
        if (selectedRaceType == null) return;

        this.selectedRace = selectedRaceType.getDndRace();

        // Not sure if this is what we want here...
        System.out.println("Check here if Inventory is closing too soon");
        player.closeInventory();

        if (this.selectedRace.getSubRaces().isEmpty()) {
            player.sendMessage("You have selected " + raceName + " as your race!");
            player.openInventory(buildClassInventory());
        } else {
            player.openInventory(buildSubRaceInventory(this.selectedRace));
        }
    }

    public void handleSubRaceSelection(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String selectedItem = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

        if ("Back to Race".equals(selectedItem)) {
            player.closeInventory();
            player.openInventory(buildRaceInventory());
            return;
        }

        DndRace subRace = selectedRace.getSubRaceByName(selectedItem);

        if (subRace != null) {
            this.selectedRace = subRace;
            player.sendMessage("You have selected " + selectedItem + " as your subrace!");
            player.closeInventory();
            player.openInventory(buildClassInventory());
        }
    }

    public void handleClassSelection(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String className = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
        DndClass.DndClassType selectedClassType = DndClass.DndClassType.fromString(className);

        if (selectedClassType != null) {
            this.selectedClass = selectedClassType.getDndClass();
            player.sendMessage("You have selected " + className + " as your class!");
            player.openInventory(buildAbilityScoreInventory(abilityScores));
        } else {
            player.sendMessage("Invalid class selection: " + className);
        }
    }

    public void handleAbilityScoreSelection(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        System.out.println("Clicked item: " + clickedItem);

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;


        String displayName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

        if (displayName.equalsIgnoreCase("Confirm")) {
            player.sendMessage("You have confirmed your ability scores!");
            player.closeInventory();
            player.openInventory(buildBackgroundInventory());
            return;
        }

        int slot = event.getSlot();
        int row = slot / 9;
        int column = slot % 9;

        if (column < 1 || column > 6) return;

        Ability ability = Ability.values()[column - 1];
        int currentScore = abilityScores.get(ability);

//        int currentScore = abilityMap.get(ability);

        if (row == 0 && currentScore < 20) {
            abilityScores.put(ability, currentScore + 1);
        } else if (row == 2 && currentScore > 1) {
            abilityScores.put(ability, currentScore - 1);
        }

        int newScore = abilityScores.get(ability);
        ItemStack updated = CharacterSheetUI.buildAbilityScoreItem(ability, newScore);
        event.getInventory().setItem(10 + column - 1, updated);
        event.setCancelled(true);
    }

    public void handleBackgroundSelection(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String backgroundName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
        DndBackground.DndBackgroundType selectedBackgroundType = DndBackground.DndBackgroundType.fromString(backgroundName);

        if (selectedBackgroundType != null) {
            selectedBackground = selectedBackgroundType.getDndBackground();
            player.sendMessage("You have selected " + backgroundName + " as your background!");
            player.closeInventory();

            // Create the character sheet
            createCharacterSheet(
                    player,
                    selectedRace,
                    selectedClass,
                    abilityScores,
                    selectedBackground
            );

        }
    }

    public void setCharacterName(String name) {
        this.characterName = name;
    }

    public void finishCreation() {
        // TODO: Construct and register CharacterSheet using selections
        // Possibly invoke CharacterSheetManager.createForPlayer(player, ...);
    }

    public DndRace getSelectedRace() {
        return selectedRace;
    }

    public DndClass getSelectedClass() {
        return selectedClass;
    }

    public DndBackground getSelectedBackground() {
        return selectedBackground;
    }
}
