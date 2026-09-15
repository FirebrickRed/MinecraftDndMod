package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spent ammunition lands in the world and may or may not survive being picked up (#191).
 *
 * <p>The arrow drops wherever the cosmetic projectile actually came down — so a shot that sails
 * over a wall is genuinely awkward to retrieve, and a miss scatters rather than politely returning
 * to you. Recovery then happens by walking over and picking it up: no end-of-combat phase, no
 * prompt, and it works mid-fight or an hour later.
 *
 * <p>Survival odds come from the item's own {@code recovery_chance:}, defaulting to 50% for
 * ammunition (RAW recovers about half) and 100% for a thrown weapon (a javelin is lying right
 * there, not "half recovered"). A fragile homebrew throwable just lowers its own number.
 */
public final class AmmoRecovery implements Listener {

    /** Marks a dropped item as spent ammunition, so only these roll to survive on pickup. */
    public static final NamespacedKey SPENT_KEY = new NamespacedKey("jkvtt", "spent_ammo");

    /**
     * Drop one round of {@code itemId} at {@code where} as recoverable spent ammunition.
     * No-op if the id doesn't resolve — better to lose an arrow than to spawn a mystery item.
     */
    public static void dropSpent(Location where, String itemId) {
        if (where == null || where.getWorld() == null || itemId == null || itemId.isBlank()) return;

        ItemStack stack = buildItem(itemId);
        if (stack == null) return;
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(SPENT_KEY, PersistentDataType.BYTE, (byte) 1));

        Item dropped = where.getWorld().dropItem(where, stack);
        dropped.setCanMobPickup(false); // it's the party's to recover, not a zombie's
    }

    /**
     * Roll whether a piece of spent ammunition survives being picked up. A failure destroys it with
     * a short message, so the player learns the arrow existed and then didn't.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!stack.hasItemMeta()) return;
        if (!stack.getItemMeta().getPersistentDataContainer().has(SPENT_KEY, PersistentDataType.BYTE)) return;

        String itemId = ItemUtil.getItemId(stack);
        int chance = recoveryChance(itemId);
        if (chance >= 100) { clearSpentMark(event.getItem()); return; } // always survives — just take it

        Player player = event.getPlayer();
        if (ThreadLocalRandom.current().nextInt(100) < chance) {
            clearSpentMark(event.getItem()); // survived; from here it's an ordinary item again
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();
        player.sendMessage(Component.text("✗ That " + displayName(itemId)
                + " is too damaged to use.", NamedTextColor.GRAY));
    }

    /**
     * Drop the spent mark once an item has survived, so re-dropping and re-collecting the same
     * arrow can't roll for it again. Recovery is decided once.
     */
    private static void clearSpentMark(Item entity) {
        ItemStack stack = entity.getItemStack();
        stack.editMeta(meta -> meta.getPersistentDataContainer().remove(SPENT_KEY));
        entity.setItemStack(stack);
    }

    /** Odds this id survives recovery: the weapon's or item's own number. */
    private static int recoveryChance(String itemId) {
        if (itemId == null) return 100;
        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null) return weapon.getRecoveryChance();
        DndItem item = ItemLoader.getItem(itemId);
        return item != null ? item.getRecoveryChance() : 100;
    }

    /** Rebuild the real item from its id, so the recovered arrow is a proper tagged item. */
    private static ItemStack buildItem(String itemId) {
        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null) return weapon.createItemStack();
        DndItem item = ItemLoader.getItem(itemId);
        return item != null ? item.createItemStack() : null;
    }

    private static String displayName(String itemId) {
        DndWeapon weapon = itemId != null ? WeaponLoader.getWeapon(itemId) : null;
        if (weapon != null && weapon.getName() != null) return weapon.getName();
        DndItem item = itemId != null ? ItemLoader.getItem(itemId) : null;
        return (item != null && item.getName() != null) ? item.getName() : "projectile";
    }
}
