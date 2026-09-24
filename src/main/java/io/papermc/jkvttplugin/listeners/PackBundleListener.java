package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Packs (Dungeoneer's Pack, Explorer's Pack, a component pouch…) render as vanilla bundles, and a
 * vanilla bundle swallows whatever you click it with. An item tucked inside a pack disappears from
 * everything the plugin reads (equipment, keys, thieves' tools), so a D&D item that's a bundle
 * takes nothing in. Taking out what's already inside still works (the other side is empty then).
 *
 * <p>Until packs can be unpacked into their contents: see the cleanup register (#193).
 */
public class PackBundleListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack slot = event.getCurrentItem();
        boolean intoPack = (isPack(slot) && !isEmpty(cursor)) || (isPack(cursor) && !isEmpty(slot));
        if (!intoPack) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p) {
            p.sendActionBar(Component.text("Packs don't hold other items.", NamedTextColor.GRAY));
        }
    }

    private static boolean isPack(ItemStack item) {
        return item != null && item.getType() == Material.BUNDLE && ItemUtil.getItemId(item) != null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
