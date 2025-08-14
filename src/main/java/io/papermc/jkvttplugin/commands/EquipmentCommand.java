package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.player.CharacterSheet;
//import io.papermc.jkvttplugin.player.Classes.DndClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquipmentCommand implements CommandExecutor {
    private final Map<Player, Map<String, String>> pendingChoices = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        CharacterSheet sheet = CharacterSheetManager.getCharacterSheet(player);
        if (sheet == null) {
            player.sendMessage("You must create a character first!");
            return true;
        }

//        DndClass playerClass = sheet.getMainDndClass();
//        if (playerClass == null) {
//            player.sendMessage("Your class could not be determined.");
//            return true;
//        }

        if (label.equalsIgnoreCase("takeequipment")) {
            handleTakeEquipment(player, sheet);
        } else if (label.equalsIgnoreCase("choosegear") && args.length >= 2) {
            handleChooseGear(player, args[0], args[1]);
        } else {
            player.sendMessage("Usage: /takeequipment OR /choosegear <choiceKey> <option>");
        }

        return true;
    }

    private void handleTakeEquipment(Player player, CharacterSheet sheet) {
        Map<String, List<ItemStack>> gearChoices = sheet.getEquipmentChoicesList();

        if (!gearChoices.isEmpty()) {
            pendingChoices.put(player, new HashMap<>());
            promptGearChoices(player, sheet);
        } else {
            player.sendMessage("No choices needed. Giving default equipment.");
            List<ItemStack> equipment = sheet.giveStartingEquipment(new HashMap<>());
            for (ItemStack item : equipment) {
                player.getInventory().addItem(item);
            }
        }
    }

    private void promptGearChoices(Player player, CharacterSheet sheet) {
        player.sendMessage("Select your starting equipment:");

        for (Map.Entry<String, List<ItemStack>> entry : sheet.getEquipmentChoicesList().entrySet()) {
            String choiceKey = entry.getKey();
            List<ItemStack> options = entry.getValue();

            if (options.isEmpty()) continue;

            Component choiceMessage = Component.text("Choose between:\n");
            for (int i = 0; i < options.size(); i++) {
                ItemStack optionItem = options.get(i);
                String optionName = getItemName(optionItem);

                choiceMessage = choiceMessage.append(
                        Component.text("[" + optionName + "]")
                                .clickEvent(ClickEvent.runCommand("/choosegear " + choiceKey + " " + i))
                );

                if ( i < options.size() - 1) {
                    choiceMessage = choiceMessage.append(Component.text(" or "));
                }
            }

            player.sendMessage(choiceMessage);
        }
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private void handleChooseGear(Player player, String choiceKey, String selectedIndexString) {
        CharacterSheet sheet = CharacterSheetManager.getCharacterSheet(player);
        if (!pendingChoices.containsKey(player)) {
            player.sendMessage("You have no equipment choices pending.");
            return;
        }

        Map<String, List<ItemStack>> choices = sheet.getEquipmentChoicesList();
        List<ItemStack> options = choices.get(choiceKey);

        if (options == null) {
            player.sendMessage("Invalid choice key: " + choiceKey);
            return;
        }

        int selectedIndex;
        try {
            selectedIndex = Integer.parseInt(selectedIndexString);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid selection. Please try again.");
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= options.size()) {
            player.sendMessage("Selection out of range. Please try again");
            return;
        }

        pendingChoices.get(player).put(choiceKey, selectedIndexString);
        player.sendMessage("You selected: " + getItemName(options.get(selectedIndex)));

        if (pendingChoices.get(player).size() == choices.size()) {
            giveFinalEquipment(player, pendingChoices.get(player));
            pendingChoices.remove(player);
        }
    }

    private void giveFinalEquipment(Player player, Map<String, String> selections) {
        CharacterSheet sheet = CharacterSheetManager.getCharacterSheet(player);
        if (sheet == null) return;

        player.sendMessage("Granting your chosen equipment...");
        List<ItemStack> equipment = sheet.giveStartingEquipment(selections);

        for (ItemStack item : equipment) {
            player.getInventory().addItem(item);
            player.sendMessage("Added: " + getItemName(item));
        }

        player.sendMessage("Equipment successfully granted!");
    }
}
