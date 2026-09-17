package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A thrown weapon (javelin, handaxe, dagger, dart) leaves the thrower's hand and lands in the world
 * where it can be picked back up (#192).
 *
 * <p>The same weapon can be STABbed (kept in hand) or THROWN (leaves your hand). We infer from range
 * — adjacent uses melee, distant is a throw — and the attack prompt offers an explicit override,
 * because throwing at an adjacent enemy is legal (and at disadvantage). The ability used doesn't
 * change: a thrown weapon uses its melee ability (finesse still picks the better of STR/DEX), which
 * {@link DndWeapon#getPrimaryAbility()} already handles.
 */
public final class ThrownWeaponManager {

    private ThrownWeaponManager() {}

    /** How the player chose to use a throwable weapon this attack. AUTO = infer from range. */
    public enum Mode { AUTO, THROW, STAB }

    /** True if this attack should be resolved as a throw (weapon leaves the hand). */
    public static boolean isThrow(DndWeapon weapon, Combatant attacker, Combatant target, Mode mode) {
        if (weapon == null || !weapon.hasProperty("thrown")) return false;
        if (mode == Mode.THROW) return true;
        if (mode == Mode.STAB) return false;

        // AUTO: throw only if the target is beyond the weapon's melee reach.
        Location a = attacker.getLocation();
        Location t = target.getLocation();
        if (a == null || t == null || a.getWorld() == null || !a.getWorld().equals(t.getWorld())) {
            return false; // can't measure → treat as a melee stab (never silently consume the weapon)
        }
        double feet = a.distance(t) * 5.0;
        return feet > weapon.getReachFeet() + 2.5;
    }

    /**
     * Apply a throw: remove one of the weapon from the thrower's inventory and drop it at the
     * target's feet (hit or miss — you threw it either way). No-op if tracking is off (the weapon
     * just stays in hand, BG-style) or the weapon isn't actually in the inventory.
     *
     * <p>Reuses the ammo-recovery drop, so the thrown weapon lands as a recoverable item and starts
     * its despawn clock at combat end. A weapon's {@code recovery_chance} defaults to 100, so unlike
     * an arrow it never breaks on pickup — it's lying right there.
     */
    public static void applyThrow(Player player, DndWeapon weapon, Combatant target) {
        if (!PluginConfig.isTrackThrownWeapons() || weapon == null) return;
        if (!removeOne(player, weapon.getId())) return; // nothing to throw (already used?) — say nothing

        Location where = target.getLocation();
        if (where != null) AmmoRecovery.dropSpent(where, weapon.getId());
        player.sendMessage(Component.text("You throw the " + weapon.getName() + " — it lands near "
                + target.getDisplayName() + ". Pick it back up to reuse it.", NamedTextColor.GRAY));
    }

    /** Remove a single item with this id from the player's inventory. Returns true if one was removed. */
    private static boolean removeOne(Player player, String weaponId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (weaponId.equalsIgnoreCase(ItemUtil.getItemId(contents[i]))) {
                ItemStack stack = contents[i];
                if (stack.getAmount() <= 1) player.getInventory().setItem(i, null);
                else stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }
}
