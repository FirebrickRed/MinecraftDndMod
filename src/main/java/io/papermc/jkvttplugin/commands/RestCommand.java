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

public class RestCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Usage: /rest <character> <short|long>
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /rest <character> <short|long>", NamedTextColor.RED));
            return true;
        }

        // Join all args except the last as character name (allows names with spaces)
        String restType = args[args.length - 1].toLowerCase();
        String characterName = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1));

        // Validate rest type
        if (!restType.equals("short") && !restType.equals("long")) {
            sender.sendMessage(Component.text("Invalid rest type! Use 'short' or 'long'.", NamedTextColor.RED));
            return true;
        }

        // Find character by name
        CharacterSheet character = CharacterSheetManager.findCharacterByName(characterName);

        if (character == null) {
            sender.sendMessage(Component.text("Character '" + characterName + "' not found.", NamedTextColor.RED));
            return true;
        }

        // Store pre-rest HP for display
        int hpBefore = character.getCurrentHealth();

        // Perform rest
        if (restType.equals("short")) {
            character.shortRest();
        } else {
            character.longRest();
        }

        // Notify sender
        sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                .append(Component.text("Triggered " + restType + " rest for ", NamedTextColor.WHITE))
                .append(Component.text(character.getCharacterName(), NamedTextColor.YELLOW)));

        // Notify target player if online
        Player targetPlayer = Bukkit.getPlayer(character.getPlayerId());
        if (targetPlayer != null && targetPlayer.isOnline()) {
            notifyPlayer(targetPlayer, character, restType, hpBefore);
        }

        return true;
    }

    private void notifyPlayer(Player player, CharacterSheet character, String restType, int hpBefore) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("DM Triggered " + (restType.equals("short") ? "Short" : "Long") + " Rest", NamedTextColor.GOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

        if (restType.equals("long")) {
            // Show HP recovery
            int hpRecovered = character.getCurrentHealth() - hpBefore;
            if (hpRecovered > 0) {
                player.sendMessage(Component.text("❤ ", NamedTextColor.RED)
                        .append(Component.text("Hit Points: ", NamedTextColor.WHITE))
                        .append(Component.text("+" + hpRecovered + " HP ", NamedTextColor.GREEN))
                        .append(Component.text("(" + character.getCurrentHealth() + "/" + character.getMaxHealth() + ")", NamedTextColor.GRAY)));
            } else {
                player.sendMessage(Component.text("❤ ", NamedTextColor.RED)
                        .append(Component.text("Hit Points: ", NamedTextColor.WHITE))
                        .append(Component.text("Already at full health", NamedTextColor.GREEN)));
            }
            player.sendMessage(Component.empty());
        }

        // Show recovered resources
        List<ClassResource> resources = character.getClassResources();
        boolean hasRecoveredResources = false;

        for (ClassResource resource : resources) {
            boolean recovered = (restType.equals("short") && resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST)
                    || (restType.equals("long") && (resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST
                    || resource.getRecovery() == ClassResource.RecoveryType.LONG_REST));

            if (recovered) {
                hasRecoveredResources = true;
                player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text(resource.getName() + ": ", NamedTextColor.WHITE))
                        .append(Component.text(resource.getCurrent() + "/" + resource.getMax(), NamedTextColor.GRAY)));
            }
        }

        if (!hasRecoveredResources) {
            player.sendMessage(Component.text("No resources recovered.", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        // Join all args except potentially the last one to match partial character names
        String partialName = String.join(" ", args).toLowerCase();

        // Check if the last arg looks like it might be "short" or "long"
        String lastArg = args[args.length - 1].toLowerCase();
        boolean mightBeRestType = "short".startsWith(lastArg) || "long".startsWith(lastArg);

        if (mightBeRestType && args.length > 1) {
            // User might be typing rest type - suggest both character names and rest types
            String characterNamePart = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)).toLowerCase();

            // Suggest rest types
            if ("short".startsWith(lastArg)) completions.add("short");
            if ("long".startsWith(lastArg)) completions.add("long");

            // Also suggest character names that match the full input
            for (String charName : CharacterSheetManager.getAllCharacterNames()) {
                if (charName.toLowerCase().startsWith(partialName)) {
                    completions.add(charName);
                }
            }
        } else {
            // Suggest character names
            for (String charName : CharacterSheetManager.getAllCharacterNames()) {
                if (charName.toLowerCase().startsWith(partialName)) {
                    completions.add(charName);
                }
            }
        }

        return completions;
    }
}