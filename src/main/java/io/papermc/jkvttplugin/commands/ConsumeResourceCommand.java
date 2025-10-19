package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.ClassResource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsumeResourceCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Usage: /consumeresource <character> <resourceName> [amount]
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /consumeresource <character> <resourceName> [amount]", NamedTextColor.RED));
            return true;
        }

        // Determine if last arg is a number (amount)
        int amount = 1;
        int resourceNameEndIndex = args.length;

        // Try to parse the last argument as an amount
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[args.length - 1]);
                resourceNameEndIndex = args.length - 1;
            } catch (NumberFormatException e) {
                // Last arg is not a number, it's part of the resource name
                amount = 1;
            }
        }

        if (amount < 1) {
            sender.sendMessage(Component.text("Amount must be at least 1.", NamedTextColor.RED));
            return true;
        }

        // For now, assume first arg is character name, rest is resource name (before optional amount)
        String characterName = args[0];
        String resourceName = String.join(" ", Arrays.copyOfRange(args, 1, resourceNameEndIndex));

        // Find character by name
        CharacterSheet character = CharacterSheetManager.findCharacterByName(characterName);

        if (character == null) {
            sender.sendMessage(Component.text("Character '" + characterName + "' not found.", NamedTextColor.RED));
            return true;
        }
        List<ClassResource> resources = character.getClassResources();

        if (resources.isEmpty()) {
            sender.sendMessage(Component.text(character.getCharacterName() + " has no class resources.", NamedTextColor.YELLOW));
            return true;
        }

        // Find specific resource (case-insensitive)
        ClassResource targetResource = null;
        for (ClassResource resource : resources) {
            if (resource.getName().equalsIgnoreCase(resourceName)) {
                targetResource = resource;
                break;
            }
        }

        if (targetResource == null) {
            sender.sendMessage(Component.text("Resource '" + resourceName + "' not found.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available resources: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", resources.stream().map(ClassResource::getName).toList()), NamedTextColor.WHITE)));
            return true;
        }

        // Consume the resource
        boolean success = targetResource.consume(amount);

        if (success) {
            sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("Consumed ", NamedTextColor.WHITE))
                    .append(Component.text(amount + "x ", NamedTextColor.YELLOW))
                    .append(Component.text(targetResource.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" from " + character.getCharacterName(), NamedTextColor.GRAY))
                    .append(Component.text(" (" + targetResource.getCurrent() + "/" + targetResource.getMax() + " remaining)", NamedTextColor.DARK_GRAY)));

            // Notify player if online
            Player targetPlayer = Bukkit.getPlayer(character.getPlayerId());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(Component.text("✗ ", NamedTextColor.RED)
                        .append(Component.text("DM consumed ", NamedTextColor.WHITE))
                        .append(Component.text(amount + "x ", NamedTextColor.YELLOW))
                        .append(Component.text(targetResource.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" (" + targetResource.getCurrent() + "/" + targetResource.getMax() + " remaining)", NamedTextColor.GRAY)));
            }
        } else {
            sender.sendMessage(Component.text("✗ ", NamedTextColor.RED)
                    .append(Component.text("Not enough ", NamedTextColor.WHITE))
                    .append(Component.text(targetResource.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" to consume. ", NamedTextColor.WHITE))
                    .append(Component.text("(" + targetResource.getCurrent() + "/" + targetResource.getMax() + " available)", NamedTextColor.GRAY)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest character names
            String partial = args[0].toLowerCase();
            for (String charName : CharacterSheetManager.getAllCharacterNames()) {
                if (charName.toLowerCase().startsWith(partial)) {
                    completions.add(charName);
                }
            }
        } else if (args.length == 2) {
            // Suggest resource names for the target character
            CharacterSheet character = CharacterSheetManager.findCharacterByName(args[0]);
            if (character != null) {
                String partial = args[1].toLowerCase();

                for (ClassResource resource : character.getClassResources()) {
                    if (resource.getName().toLowerCase().startsWith(partial)) {
                        completions.add(resource.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            // Suggest amounts (1-10)
            String partial = args[2];
            for (int i = 1; i <= 10; i++) {
                if (String.valueOf(i).startsWith(partial)) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}