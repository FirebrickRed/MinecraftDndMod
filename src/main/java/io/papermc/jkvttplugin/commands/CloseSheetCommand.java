package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to save the active character and close any open menus.
 * Provides keyboard-friendly alternative to clicking the Close button in character sheet.
 */
public class CloseSheetCommand implements CommandExecutor {
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

        // Save character
        CharacterPersistenceLoader.saveCharacter(character);

        // Close inventory if open
        player.closeInventory();

        // Confirm
        player.sendMessage(Component.text("Character saved!", NamedTextColor.GREEN));

        return true;
    }
}