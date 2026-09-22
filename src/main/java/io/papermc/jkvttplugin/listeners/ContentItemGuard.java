package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.util.ItemUtil;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * D&D items are rendered as vanilla Minecraft items, and some vanilla items DO things on
 * right-click: an empty map draws a new map (a playtest's "Map of Your Home City" turned into a
 * random filled map, losing the item), a potion is drunk, flint and steel lights fires, a bottle
 * fills with water, a torch or lantern places as a block. Each of those swaps our tagged item for
 * a plain vanilla one, or spends it, outside the D&D rules.
 *
 * <p>So the vanilla use of any content item is denied. Our own right-click features (spell focus,
 * potions via /character drink, the sheet paper, weapons) still see the click: this runs last and
 * denies only the item's vanilla use, never the clicked block's (a chest still opens with a map in
 * hand). Armor and shields are the exception, since right-click-to-wear and raising a shield are
 * exactly how equip tracking (#31) sees them.
 */
public class ContentItemGuard implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (isGuarded(event.getItem())) event.setUseItemInHand(Event.Result.DENY);
    }

    /** Eating or drinking one: potions resolve through /character drink, anything else isn't food. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isGuarded(event.getItem())) event.setCancelled(true);
    }

    private static boolean isGuarded(ItemStack stack) {
        String id = ItemUtil.getItemId(stack);
        return id != null && ArmorLoader.getArmor(id) == null;
    }
}
