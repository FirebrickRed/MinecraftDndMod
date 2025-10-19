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

public class RestoreResourceCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Usage: /restoreresource <character> <resourceName|all>
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /restoreresource <character> <resourceName|all>", NamedTextColor.RED));
            return true;
        }

        // Determine if last arg is "all" or a resource name
        String lastArg = args[args.length - 1];
        String characterName;
        String resourceName;

        // Check if last arg is "all"
        if (lastArg.equalsIgnoreCase("all")) {
            characterName = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1));
            resourceName = "all";
        } else {
            // Try to find where character name ends and resource name begins
            // This is tricky with spaces - for now, try the first arg as character name
            characterName = args[0];
            resourceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

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

        // Check if restoring all resources
        if (resourceName.equalsIgnoreCase("all")) {
            int restoredCount = 0;
            for (ClassResource resource : resources) {
                if (resource.getCurrent() < resource.getMax()) {
                    resource.restore();
                    restoredCount++;
                }
            }

            if (restoredCount > 0) {
                sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text("Restored all resources for ", NamedTextColor.WHITE))
                        .append(Component.text(character.getCharacterName(), NamedTextColor.YELLOW))
                        .append(Component.text(" (" + restoredCount + " resources)", NamedTextColor.GRAY)));

                // Notify player if online
                Player targetPlayer = Bukkit.getPlayer(character.getPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                            .append(Component.text("DM restored all your resources!", NamedTextColor.WHITE)));
                }
            } else {
                sender.sendMessage(Component.text("All resources are already at maximum.", NamedTextColor.YELLOW));
            }
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

        // Restore the resource
        int before = targetResource.getCurrent();
        targetResource.restore();
        int restored = targetResource.getCurrent() - before;

        if (restored > 0) {
            sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("Restored ", NamedTextColor.WHITE))
                    .append(Component.text(targetResource.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" for " + character.getCharacterName() + " (+" + restored + ")", NamedTextColor.GRAY)));

            // Notify player if online
            Player targetPlayer = Bukkit.getPlayer(character.getPlayerId());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text("DM restored your ", NamedTextColor.WHITE))
                        .append(Component.text(targetResource.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" (" + targetResource.getCurrent() + "/" + targetResource.getMax() + ")", NamedTextColor.GRAY)));
            }
        } else {
            sender.sendMessage(Component.text(targetResource.getName() + " is already at maximum.", NamedTextColor.YELLOW));
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
            // Suggest "all" and resource names for the target character
            CharacterSheet character = CharacterSheetManager.findCharacterByName(args[0]);
            if (character != null) {
                String partial = args[1].toLowerCase();

                if ("all".startsWith(partial)) {
                    completions.add("all");
                }

                for (ClassResource resource : character.getClassResources()) {
                    if (resource.getName().toLowerCase().startsWith(partial)) {
                        completions.add(resource.getName());
                    }
                }
            }
        }

        return completions;
    }
}