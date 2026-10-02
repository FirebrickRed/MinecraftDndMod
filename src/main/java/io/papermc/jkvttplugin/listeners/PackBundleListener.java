package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs (Dungeoneer's Pack, Explorer's Pack, a component pouch…) render as vanilla bundles, and a
 * vanilla bundle swallows whatever you click it with. An item tucked inside a pack disappears from
 * everything the plugin reads (equipment, keys, thieves' tools), so a D&D item that's a bundle
 * holds nothing.
 *
 * <p>Two layers: the click that would put something in is cancelled (survival), and a tick after any
 * inventory click or drag, every pack the player carries is emptied back into their inventory. The
 * second is what holds in <b>creative</b>, where the client applies an inventory click itself and
 * cancelling it on the server doesn't take the item back out (playtest: the message showed, the item
 * went in anyway).
 *
 * <p>Until packs can be unpacked into their contents: see the cleanup register (#193).
 */
public class PackBundleListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = event.getCursor();
        ItemStack slot = event.getCurrentItem();
        boolean intoPack = (isPack(slot) && !isEmpty(cursor)) || (isPack(cursor) && !isEmpty(slot));
        if (intoPack) event.setCancelled(true);
        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> emptyPacks(p, intoPack));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> emptyPacks(p, false));
    }

    /** Take anything out of every pack the player carries and give it back. */
    private static void emptyPacks(Player p, boolean alreadyTold) {
        if (!p.isOnline()) return;
        List<ItemStack> spilled = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (!isPack(item) || !(item.getItemMeta() instanceof BundleMeta meta) || !meta.hasItems()) continue;
            spilled.addAll(meta.getItems());
            meta.setItems(null);
            item.setItemMeta(meta);
        }
        if (spilled.isEmpty()) {
            if (alreadyTold) p.sendActionBar(Component.text("Packs don't hold other items.", NamedTextColor.GRAY));
            return;
        }
        for (ItemStack left : p.getInventory().addItem(spilled.toArray(ItemStack[]::new)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.sendActionBar(Component.text("Packs don't hold other items: it's back in your inventory.", NamedTextColor.GRAY));
    }

    private static boolean isPack(ItemStack item) {
        return item != null && item.getType() == Material.BUNDLE && ItemUtil.getItemId(item) != null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
