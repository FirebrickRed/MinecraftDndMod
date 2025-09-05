package io.papermc.jkvttplugin.character;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

//import static io.papermc.jkvttplugin.character.CharacterSheetUI.buildRaceInventory;

public class CharacterSheetItemListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only check main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Only match paper with custom name
        if (item == null || item.getType() != org.bukkit.Material.PAPER) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (!name.equalsIgnoreCase("Character Sheet")) return;

        event.setCancelled(true);

        if (CharacterSheetManager.hasCharacterSheet(player)) {
//            CharacterSheetUI.openCharacterSheetInventory(player, CharacterSheetManager.getCharacterSheet(player));
        } else {
//            player.openInventory(buildRaceInventory());
        }
    }
}
