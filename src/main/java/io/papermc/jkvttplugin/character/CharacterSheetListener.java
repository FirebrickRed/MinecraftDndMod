package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.util.DndSpell;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent; // <- deprecated
import org.bukkit.inventory.InventoryView;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterSheetListener implements Listener {

    private final Map<Player, CharacterCreationSession> creationSessions = new ConcurrentHashMap<>();

    private final Map<Player, Set<DndSpell>> knownSpells = new ConcurrentHashMap<>();

    private final Map<Player, Boolean> awaitingNameInput = new ConcurrentHashMap<>();

    private boolean isTitle(InventoryView view, String title) {
        return PlainTextComponentSerializer.plainText().serialize(view.title()).equalsIgnoreCase(title);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
//        CharacterCreationSession session = creationSessions.computeIfAbsent(player, CharacterCreationSession::new);

        // ToDo:
        // sub race clicks do not work

//        if(isTitle(event.getView(), "Choose your Race")) {
//            event.setCancelled(true);
//            session.handleRaceSelection(event);
//        }
//        else if(isTitle(event.getView(), "Choose your Sub Race")) {
//            event.setCancelled(true);
//            session.handleSubRaceSelection(event);
//        }
//        else if(isTitle(event.getView(), "Choose your Class")) {
//            event.setCancelled(true);
//            session.handleClassSelection(event);
//        }
//        else if(isTitle(event.getView(), "Choose your Ability Scores")) {
//            event.setCancelled(true);
//            session.handleAbilityScoreSelection(event);
//        }
//        else if(isTitle(event.getView(), "Choose your Background")) {
//            event.setCancelled(true);
//            session.handleBackgroundSelection(event);
//        }
//        else if(isTitle(event.getView(), "Choose your Spells")) {
//            event.setCancelled(true);
//            session.handleSpellSelection(event);
//        }
//        else if(isTitle(event.getView(), "Confirm Character")) {
//            event.setCancelled(true);
//            session.confirmCharacterCreation(event);
//        }
//        } else {
//            CharacterSheetUI.handleSheetInventoryClick(player, event);
//        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // This method can be used to clean up any resources or sessions when the inventory is closed.
        // Currently, it does not perform any actions, but it can be extended in the future if needed.
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingNameInput.containsKey(player)) {
            String name = event.getMessage();
            event.setCancelled(true);
            CharacterCreationSession session = creationSessions.get(player);
            if (session != null) {
//                session.setCharacterName(name);
//                session.finishCreation();
                awaitingNameInput.put(player, false);
            }
        }
    }
}
