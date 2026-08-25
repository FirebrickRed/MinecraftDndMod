package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anvil-based character-name entry (Issue #121) — the one vanilla screen that accepts typed text.
 * The player types in the anvil's rename field and clicks the output to confirm.
 *
 * This is best-effort: if a Paper build doesn't cooperate with a container-only anvil, the Name
 * tab also offers a chat option ({@link CreationNameListener}), so naming always works.
 */
public class AnvilNameListener implements Listener {

    private static final Map<UUID, UUID> active = new ConcurrentHashMap<>(); // playerId -> sessionId

    /** Open the anvil name prompt for a player. */
    public static void open(Player player, UUID sessionId) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        String current = session != null ? session.getCharacterName() : null;

        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text("Name your character"));
        anvil.setItem(0, namedPaper(current != null && !current.isBlank() ? current : "New Character"));

        active.put(player.getUniqueId(), sessionId);
        player.openInventory(anvil);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!active.containsKey(player.getUniqueId())) return;

        String text = event.getInventory().getRenameText();
        event.setResult(namedPaper(text == null || text.isBlank() ? "New Character" : text));
        event.getInventory().setRepairCost(0); // no XP cost to confirm
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!active.containsKey(player.getUniqueId())) return;
        // Gate on inventory TYPE, not `instanceof AnvilInventory` — container-anvils don't
        // always report as AnvilInventory, which let the output item get picked up.
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;

        event.setCancelled(true);                 // never let items be pulled out of the anvil
        if (event.getRawSlot() != 2) return;       // only clicking the output confirms the name

        String name = resultName(event);
        String error = validate(name);
        if (error != null) {
            player.sendMessage(Component.text(error, NamedTextColor.RED));
            return;
        }

        UUID sessionId = active.remove(player.getUniqueId());
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session != null) session.setCharacterName(name);
        player.sendMessage(Component.text("Name set to " + name + ".", NamedTextColor.GREEN));

        player.closeInventory();
        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> {
            if (CharacterCreationService.getSession(player.getUniqueId()) != null) {
                CharacterCreationMenu.open(player, sessionId);
            }
        });
    }

    /** Read the typed name: prefer the output item's name (set in PrepareAnvil), else the rename field. */
    private static String resultName(InventoryClickEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) result = event.getView().getTopInventory().getItem(2);
        if (result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            String n = PlainTextComponentSerializer.plainText()
                    .serialize(result.getItemMeta().displayName()).trim();
            if (!n.isBlank() && !n.equals("New Character")) return n;
        }
        if (event.getView().getTopInventory() instanceof AnvilInventory anvil && anvil.getRenameText() != null) {
            return anvil.getRenameText().trim();
        }
        return null;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    private static ItemStack namedPaper(String name) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        paper.setItemMeta(meta);
        return paper;
    }

    /** Returns an error message, or null if valid. Mirrors CreationNameListener's rules. */
    private static String validate(String name) {
        if (name == null || name.isBlank()) return "Type a name in the field first.";
        if (name.length() < 3) return "Name must be at least 3 characters.";
        if (name.length() > 30) return "Name must be at most 30 characters.";
        if (name.matches(".*[<>&\"'].*")) return "Name can't contain < > & \" or '.";
        return null;
    }
}
