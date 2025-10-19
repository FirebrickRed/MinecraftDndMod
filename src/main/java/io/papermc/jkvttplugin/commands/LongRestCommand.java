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

public class LongRestCommand implements CommandExecutor {
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

        // Store pre-rest HP for display
        int hpBefore = character.getCurrentHealth();

        // Perform long rest
        character.longRest();

        // Display results
        displayLongRestResults(player, character, hpBefore);

        return true;
    }

    private void displayLongRestResults(Player player, CharacterSheet character, int hpBefore) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Long Rest Complete", NamedTextColor.GOLD).append(
                Component.text(" (8 hours)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

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

        // Show recovered resources
        List<ClassResource> resources = character.getClassResources();
        boolean hasResources = !resources.isEmpty();

        if (hasResources) {
            player.sendMessage(Component.text("Resources Restored:", NamedTextColor.YELLOW));
            for (ClassResource resource : resources) {
                String recoveryText = switch (resource.getRecovery()) {
                    case SHORT_REST -> "✓";
                    case LONG_REST -> "✓";
                    case DAWN -> resource.getCurrent() == resource.getMax() ? "✓" : "✗";
                    case NONE -> "✗";
                };

                NamedTextColor color = recoveryText.equals("✓") ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;

                player.sendMessage(Component.text("  " + recoveryText + " ", color)
                        .append(Component.text(resource.getName() + ": ", NamedTextColor.WHITE))
                        .append(Component.text(resource.getCurrent() + "/" + resource.getMax(), NamedTextColor.GRAY)));
            }
        }

        // Show spell slot recovery
        int totalSlots = 0;
        for (int level = 1; level <= 9; level++) {
            totalSlots += character.getMaxSpellSlots(level);
        }

        if (totalSlots > 0) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("All spell slots restored", NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("You recover up to half your Hit Dice.", NamedTextColor.GRAY)
                .append(Component.text(" (Not yet implemented)", NamedTextColor.DARK_GRAY)));

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }
}