package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Ammunition for ranged weapons (#128): a bow needs arrows, and runs out.
 *
 * <p>Entirely data-driven. A weapon declares what it fires in its own YAML ({@code ammunition:
 * arrow}), and the ammunition is an ordinary item — so a homebrew weapon firing a homebrew
 * projectile needs no Java change. Nothing here hardcodes "bow means arrow".
 *
 * <p>A DM who doesn't want the bookkeeping turns it off with {@code combat.track_ammunition: false};
 * tracking is on by default, since running dry is a real tactical beat.
 */
public final class AmmunitionManager {

    private AmmunitionManager() {}

    /**
     * The ammunition item id this weapon must spend for a shot, or null if it spends none —
     * because it isn't an ammunition weapon, it names no ammunition, or tracking is switched off.
     */
    private static String requiredAmmo(DndWeapon weapon) {
        if (weapon == null || !PluginConfig.isTrackAmmunition()) return null;
        if (!weapon.usesAmmunition()) return null;
        String ammo = weapon.getAmmunition();
        return (ammo == null || ammo.isBlank()) ? null : ammo;
    }

    /**
     * The item id this shot leaves on the battlefield, or null if it leaves nothing — so the
     * projectile knows what to drop where it lands (#191). Same rules as consumption: a weapon that
     * doesn't spend a round doesn't drop one either.
     */
    public static String spentRoundId(DndWeapon weapon) {
        return requiredAmmo(weapon);
    }

    /** True if the player can make this shot — either the weapon needs nothing, or they have some. */
    public static boolean hasAmmo(Player player, DndWeapon weapon) {
        String ammo = requiredAmmo(weapon);
        return ammo == null || findSlot(player, ammo) >= 0;
    }

    /** Tell the player they're empty, naming what the weapon actually needs. */
    public static void warnEmpty(Player player, DndWeapon weapon) {
        String ammo = requiredAmmo(weapon);
        if (ammo == null) return;
        player.sendMessage(Component.text("✗ Out of " + displayName(ammo) + " — "
                + weapon.getName() + " can't fire.", NamedTextColor.RED));
        player.sendMessage(Component.text("Find or buy more, or attack with another weapon.", NamedTextColor.GRAY));
    }

    /**
     * Spend one round of ammunition. Call only once the attack has actually resolved — an attack
     * that's still waiting on the player's d20 hasn't been made yet and must not cost a round.
     */
    public static void consume(Player player, DndWeapon weapon) {
        String ammo = requiredAmmo(weapon);
        if (ammo == null) return;
        int slot = findSlot(player, ammo);
        if (slot < 0) return;

        ItemStack stack = player.getInventory().getItem(slot);
        int remaining = stack.getAmount() - 1;
        if (remaining <= 0) player.getInventory().setItem(slot, null);
        else stack.setAmount(remaining);

        int left = count(player, ammo);
        player.sendMessage(left == 0
                ? Component.text("That was your last " + displayName(ammo) + ".", NamedTextColor.RED)
                : Component.text(left + " " + displayName(ammo) + " left.", NamedTextColor.DARK_GRAY));

        playtestNotice(player);
    }

    /**
     * One-off nudge that the ammo visuals are new and unverified (#191). Once per player per server
     * run — the point is to get eyes on it during the first session, not to nag all campaign.
     */
    private static final java.util.Set<java.util.UUID> noticed = new java.util.HashSet<>();

    private static void playtestNotice(Player player) {
        if (!noticed.add(player.getUniqueId())) return;
        player.sendMessage(Component.text("ℹ Ammunition is newly added and still being visually tested — "
                + "spent rounds land where the shot comes down and can be picked back up. "
                + "Tell the DM if anything looks wonky.", NamedTextColor.DARK_AQUA));
    }

    /** First inventory slot holding this ammunition, or -1. */
    private static int findSlot(Player player, String ammoId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (ammoId.equalsIgnoreCase(ItemUtil.getItemId(contents[i]))) return i;
        }
        return -1;
    }

    /** Total rounds of this ammunition the player is carrying. */
    private static int count(Player player, String ammoId) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && ammoId.equalsIgnoreCase(ItemUtil.getItemId(stack))) total += stack.getAmount();
        }
        return total;
    }

    /** The ammunition's display name from its item definition, falling back to the raw id. */
    private static String displayName(String ammoId) {
        DndItem item = ItemLoader.getItem(ammoId);
        return (item != null && item.getName() != null) ? item.getName() : ammoId;
    }
}
