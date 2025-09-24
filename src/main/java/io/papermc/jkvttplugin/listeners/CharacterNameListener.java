package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterNameListener implements Listener {

    private static final Set<UUID> awaitingNameInput = ConcurrentHashMap.newKeySet();

    public static void requestCharacterName(Player player) {
        awaitingNameInput.add(player.getUniqueId());

        player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("CHARACTER NAME INPUT").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Please type your character's name in chat:").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("• 3-30 characters allowed").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Spaces and most symbols are allowed").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Type 'cancel' to abort character creation").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
    }

    public static boolean isAwaitingNameInput(UUID playerId) {
        return awaitingNameInput.contains(playerId);
    }

    public static void cancleNameInput(UUID playerId) {
        awaitingNameInput.remove(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!awaitingNameInput.contains(playerId)) return;

        event.setCancelled(true);

        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            awaitingNameInput.remove(playerId);
            CharacterCreationService.removeSession(playerId);

            player.sendMessage(Component.text("Character creation cancelled.").color(NamedTextColor.RED));
            return;
        }

        ValidationResult validation = validateCharacterName(input);
        if (!validation.isValid()) {
            player.sendMessage(Component.text("Invalid name: " + validation.getError()).color(NamedTextColor.RED));
            player.sendMessage(Component.text("Please try again, or type 'cancel' to abort.").color(NamedTextColor.YELLOW));
            return;
        }

        awaitingNameInput.remove(playerId);

        CharacterCreationSession session = CharacterCreationService.getSession(playerId);
        if (session == null) {
            player.sendMessage(Component.text("Character creation session expired. please restart.").color(NamedTextColor.RED));
            return;
        }

        session.setCharacterName(input);

        player.sendMessage(Component.text("Character name set to: ").color(NamedTextColor.GREEN).append(Component.text(input).color(NamedTextColor.WHITE)));

        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("JkVttPlugin"), () -> completeCharacterCreation(player, session)
        );
    }

    private ValidationResult validateCharacterName(String name) {
        if (name == null || name.isEmpty()) {
            return ValidationResult.invalid("Name cannot be empty");
        }

        if (name.length() < 3) {
            return ValidationResult.invalid("Name must be at least 3 characters long");
        }

        if (name.length() > 30) {
            return ValidationResult.invalid("Name cannot exceed 30 characters");
        }

        if (name.matches(".*[<>&\"'].*")) {
            return ValidationResult.invalid("Name contains invalid characters");
        }

        String lowerName = name.toLowerCase();
        // Check for inappropriate content (will add more as the need arises)
        if (lowerName.contains("admin") || lowerName.contains("moderator") || lowerName.contains("staff")) {
            return ValidationResult.invalid("Name cannot contain reserved words");
        }

        return ValidationResult.valid();
    }

    private void completeCharacterCreation(Player player, CharacterCreationSession session) {
        try {
            CharacterSheet characterSheet = CharacterSheetManager.createCharacterFromSession(player, session);

            ItemStack characterSheetItem = CharacterSheetManager.createCharacterSheetItem(characterSheet);
            player.getInventory().addItem(characterSheetItem);

            CharacterCreationService.removeSession(player.getUniqueId());

            player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("CHARACTER CREATED SUCCESSFULLY!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("Character: ").color(NamedTextColor.WHITE).append(Component.text(session.getCharacterName()).color(NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("You've received your character sheet item!").color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Right-click it to view your character details.").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("=".repeat(50)).color(NamedTextColor.GOLD));
        } catch (Exception e) {
            player.sendMessage(Component.text("An error occurred while creating your character. Please try again.").color(NamedTextColor.RED));
            // Log the error for debugging
            e.printStackTrace();
        }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }
}
