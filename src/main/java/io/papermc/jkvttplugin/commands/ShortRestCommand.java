package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.ClassResource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ShortRestCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Get active character
        CharacterSheet character = ActiveCharacterTracker.getActiveCharacter(player);

        if (character == null) {
            player.sendMessage(Component.text("You don't have an active character!", NamedTextColor.RED)
                    .append(Component.text(" Right-click your character sheet to select one.", NamedTextColor.GRAY)));
            return true;
        }

        // Perform short rest
        character.shortRest();

        // Display results
        displayShortRestResults(player, character);

        return true;
    }

    private void displayShortRestResults(Player player, CharacterSheet character) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Short Rest Complete", NamedTextColor.GOLD).append(
                Component.text(" (1 hour)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

        // Show recovered resources
        List<ClassResource> resources = character.getClassResources();
        boolean hasShortRestResources = false;

        for (ClassResource resource : resources) {
            if (resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST) {
                hasShortRestResources = true;
                player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text(resource.getName() + ": ", NamedTextColor.WHITE))
                        .append(Component.text(resource.getCurrent() + "/" + resource.getMax(), NamedTextColor.GRAY))
                        .append(Component.text(" restored", NamedTextColor.GREEN)));
            }
        }

        if (!hasShortRestResources) {
            player.sendMessage(Component.text("No short rest resources to recover.", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("You may spend Hit Dice to recover HP.", NamedTextColor.GRAY)
                .append(Component.text(" (Not yet implemented)", NamedTextColor.DARK_GRAY)));

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }
}