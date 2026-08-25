package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name entry for the single-pane creation flow (Issue #121). Prompts the player to type a
 * name in chat, sets it on the session, and re-opens the creation menu — it does NOT finalize
 * the character (unlike the classic {@code CharacterNameListener}, where name was the last step).
 *
 * NOTE: an anvil GUI would be the nicer UX, but reliable anvil text input on Paper needs a
 * library (AnvilGUI) or NMS; that's a fast follow-up. Chat keeps creation working today.
 */
public class CreationNameListener implements Listener {

    private static final Map<UUID, UUID> awaiting = new ConcurrentHashMap<>(); // playerId -> sessionId

    /** Prompt the player to enter a character name in chat. */
    public static void request(Player player, UUID sessionId) {
        awaiting.put(player.getUniqueId(), sessionId);
        player.closeInventory();
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✎ Type your character's name in chat", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  (3–30 characters, or type 'cancel')", NamedTextColor.GRAY));
    }

    public static boolean isAwaiting(UUID playerId) {
        return awaiting.containsKey(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!awaiting.containsKey(playerId)) return;

        event.setCancelled(true);
        UUID sessionId = awaiting.remove(playerId);
        Player player = event.getPlayer();
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            reopen(player, sessionId);
            return;
        }

        String error = validate(input);
        if (error != null) {
            awaiting.put(playerId, sessionId); // keep waiting
            player.sendMessage(Component.text(error, NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> {
            CharacterCreationSession session = CharacterCreationService.getSession(playerId);
            if (session != null) {
                session.setCharacterName(input);
                player.sendMessage(Component.text("Name set to " + input + ".", NamedTextColor.GREEN));
            }
            reopen(player, sessionId);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }

    private void reopen(Player player, UUID sessionId) {
        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> {
            if (CharacterCreationService.getSession(player.getUniqueId()) != null) {
                CharacterCreationMenu.open(player, sessionId);
            }
        });
    }

    /** Returns an error message, or null if valid. Mirrors CharacterNameListener's rules. */
    private static String validate(String name) {
        if (name == null || name.isBlank()) return "Name cannot be blank.";
        if (name.length() < 3) return "Name must be at least 3 characters.";
        if (name.length() > 30) return "Name must be at most 30 characters.";
        if (name.matches(".*[<>&\"'].*")) return "Name can't contain < > & \" or '.";
        return null;
    }
}
