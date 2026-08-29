package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM entity possession (Issue #78), rebuilt on {@link DndEntityInstance}. Replaces the old
 * NpcListener/NpcManager/NpcData system. A DM in DM mode holds the Possess tool and right-clicks an
 * entity to "become" it: they go invisible, the entity follows their movement, and their hotbar
 * swaps to the entity's kit. Sneak (or exit DM mode) to stop; the DM toolbar comes back.
 */
public class PossessionManager {

    private static final Map<UUID, ArmorStand> possessedByDm = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> followTasks = new HashMap<>();
    // The entity the DM is currently aiming at (glowing) so we can clear it when the aim changes.
    private static final Map<UUID, Entity> aimHighlight = new HashMap<>();

    public static boolean isPossessing(UUID dmId) {
        return possessedByDm.containsKey(dmId);
    }

    /** The armor stand a DM is currently possessing, or null (used by combat movement tracking). */
    public static ArmorStand getPossessedArmorStand(UUID dmId) {
        return possessedByDm.get(dmId);
    }

    public static void possess(Player dm, ArmorStand stand) {
        DndEntityInstance instance = DndEntityInstance.getByArmorStand(stand);
        if (instance == null) {
            dm.sendMessage(Component.text("That isn't a controllable entity.", NamedTextColor.RED));
            return;
        }
        if (isPossessing(dm.getUniqueId())) endPossession(dm, false); // switch targets cleanly

        possessedByDm.put(dm.getUniqueId(), stand);
        dm.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        applyScale(dm, instance.getTemplate().getSize()); // stand at the entity's height (sword lines up)
        giveEntityKit(dm, instance);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!dm.isOnline() || !possessedByDm.containsKey(dm.getUniqueId()) || !stand.isValid()) {
                    cancel();
                    return;
                }
                stand.teleport(dm.getLocation());
                updateAimHighlight(dm, stand); // glow whatever attackable entity the DM is aiming at
            }
        };
        task.runTaskTimer(JkVttPlugin.getInstance(), 0L, 1L);
        followTasks.put(dm.getUniqueId(), task);

        dm.sendMessage(Component.text("You are now possessing " + instance.getDisplayName()
                + " — sneak to stop.", NamedTextColor.GREEN));
    }

    /** Stop possessing and restore the DM toolbar. */
    public static void unpossess(Player dm) {
        if (endPossession(dm, false)) {
            dm.getInventory().clear();
            DmModeManager.giveTools(dm);
            dm.sendMessage(Component.text("You are no longer possessing.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Tear down possession state (invisibility, follow task, registry) WITHOUT touching the hotbar.
     * Used when exiting DM mode entirely (the caller then restores the real inventory).
     * @return true if the DM was possessing.
     */
    public static boolean endPossession(Player dm, boolean silent) {
        ArmorStand stand = possessedByDm.remove(dm.getUniqueId());
        BukkitRunnable task = followTasks.remove(dm.getUniqueId());
        if (task != null) task.cancel();
        clearAimHighlight(dm);
        if (stand == null) return false;
        clearPossessionEffects(dm);
        return true;
    }

    /**
     * Remove the visual/physical side effects of possession (invisibility + shrunk scale). Safe to
     * call any time — used both on a clean stop and on login recovery after a crash, where the
     * in-memory possession state is gone but the persisted potion/attribute may linger on the player.
     */
    public static void clearPossessionEffects(Player dm) {
        dm.removePotionEffect(PotionEffectType.INVISIBILITY);
        AttributeInstance scale = dm.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(1.0);
    }

    // ==================== HEIGHT / AIM HIGHLIGHT ====================

    /** Scale the DM to roughly the creature's size so a held weapon lines up with the body. */
    private static void applyScale(Player dm, String size) {
        AttributeInstance attr = dm.getAttribute(Attribute.SCALE);
        if (attr == null) return;
        double scale = switch (size == null ? "medium" : size.toLowerCase()) {
            case "tiny" -> 0.4;
            case "small" -> 0.6;
            case "large" -> 2.0;
            case "huge" -> 3.0;
            case "gargantuan" -> 4.0;
            default -> 1.0; // medium
        };
        attr.setBaseValue(scale);
    }

    /** Glow the attackable entity the DM is currently aiming at; clear the previous one. */
    private static void updateAimHighlight(Player dm, ArmorStand possessed) {
        Location eye = dm.getEyeLocation();
        RayTraceResult result = dm.getWorld().rayTraceEntities(eye, eye.getDirection(), 30, 0.6,
                e -> !e.equals(dm) && !e.equals(possessed)
                        && (e instanceof Player || (e instanceof ArmorStand a && DndEntityInstance.getByArmorStand(a) != null)));
        Entity aimed = result != null ? result.getHitEntity() : null;
        Entity previous = aimHighlight.get(dm.getUniqueId());
        if (aimed == previous) return;
        if (previous != null && previous.isValid()) previous.setGlowing(false);
        if (aimed != null) {
            aimed.setGlowing(true);
            aimHighlight.put(dm.getUniqueId(), aimed);
        } else {
            aimHighlight.remove(dm.getUniqueId());
        }
    }

    private static void clearAimHighlight(Player dm) {
        Entity previous = aimHighlight.remove(dm.getUniqueId());
        if (previous != null && previous.isValid()) previous.setGlowing(false);
    }

    // ==================== ENTITY KIT ====================

    private static void giveEntityKit(Player dm, DndEntityInstance instance) {
        dm.getInventory().clear();
        List<String> inventory = instance.getTemplate().getInventory();
        int slot = 0;
        if (inventory != null) {
            for (String id : inventory) {
                if (slot > 8) break;
                ItemStack stack = itemById(id);
                if (stack != null) dm.getInventory().setItem(slot++, stack);
            }
        }
    }

    /** Resolve an item id to an ItemStack via the weapon/armor/item registries. */
    private static ItemStack itemById(String id) {
        DndWeapon weapon = WeaponLoader.getWeapon(id);
        if (weapon != null) return weapon.createItemStack();
        DndArmor armor = ArmorLoader.getArmor(id);
        if (armor != null) return armor.createItemStack();
        DndItem item = ItemLoader.getItem(id);
        if (item != null) return item.createItemStack();
        return null;
    }
}
