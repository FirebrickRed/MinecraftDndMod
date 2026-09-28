package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the player sees matches who they are (#148): a character with darkvision gets Minecraft night
 * vision, one without stays in the dark. While a DM possesses a creature, the creature's darkvision
 * decides instead.
 *
 * <p>Ours is marked by its shape (infinite, ambient, no particles or icon), so a night-vision potion
 * someone actually drank is never taken away; only the one this class gave is removed.
 *
 * <p>Night vision has no range, so in the dark the world is also cut off near the darkvision range:
 * the player's render distance drops to that many chunks (at least 2, Minecraft's floor, so 60 ft
 * really shows ~32 blocks). Only in the dark: in light everyone sees as far as the server allows.
 * Off with {@code sight.darkvision_view_limit: false}.
 */
public final class CharacterSight {

    /** Light at eye level at or below this counts as dark (dim light and darkness, where darkvision works). */
    static final int DARK_AT_OR_BELOW = 7;
    /** Seconds the light has to stay changed before the view distance follows, so dusk or a torch at
     *  the edge of a step doesn't resend chunks every second. */
    static final int SETTLE_SECONDS = 3;

    private static final Set<UUID> limited = new HashSet<>();
    private static final Map<UUID, Integer> pending = new HashMap<>(); // seconds the other state has held

    private CharacterSight() {}

    public static void set(Player player, boolean darkvision) {
        if (player == null) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (darkvision) {
            if (current == null || isOurs(current)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        PotionEffect.INFINITE_DURATION, 0, true, false, false));
            }
        } else if (current != null && isOurs(current)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    static boolean isOurs(PotionEffect effect) {
        return effect.isInfinite() && effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon();
    }

    // ==================== VIEW DISTANCE IN THE DARK ====================

    /** Checks every online player once a second. Called once from onEnable. */
    public static void startViewLimit() {
        Bukkit.getScheduler().runTaskTimer(JkVttPlugin.getInstance(), CharacterSight::tick, 40L, 20L);
    }

    private static void tick() {
        limited.removeIf(id -> Bukkit.getPlayer(id) == null);
        pending.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player p : Bukkit.getOnlinePlayers()) {
            int dv = darkvisionFeet(p);
            Block eye = p.getEyeLocation().getBlock();
            boolean want = PluginConfig.isDarkvisionViewLimit() && dv > 0
                    && isDark(eye.getLightFromBlocks(), eye.getLightFromSky(), p.getWorld().isDayTime());
            boolean now = limited.contains(p.getUniqueId());
            if (want == now) { pending.remove(p.getUniqueId()); continue; }
            int held = pending.merge(p.getUniqueId(), 1, Integer::sum);
            // Switching off the limit (or the setting) needs no wait: never leave someone short-sighted.
            if (want && held < SETTLE_SECONDS) continue;
            pending.remove(p.getUniqueId());
            if (want) {
                limited.add(p.getUniqueId());
                p.setSendViewDistance(viewChunks(dv));
            } else {
                limited.remove(p.getUniqueId());
                p.setSendViewDistance(-1); // back to the world's
            }
        }
    }

    /** The darkvision the player sees with: the creature they possess, else their active character's. */
    private static int darkvisionFeet(Player p) {
        var stand = io.papermc.jkvttplugin.dm.PossessionManager.getPossessedArmorStand(p.getUniqueId());
        if (stand != null) {
            var inst = io.papermc.jkvttplugin.data.model.DndEntityInstance.getByArmorStand(stand);
            return inst != null ? inst.getTemplate().getDarkvision() : 0;
        }
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(p);
        return sheet != null ? sheet.getDarkvision() : 0;
    }

    /** Dim or dark at eye level. Sky light is full at night too, so it's dimmed by night the way Minecraft does. */
    static boolean isDark(int blockLight, int skyLight, boolean day) {
        int sky = day ? skyLight : Math.max(0, skyLight - 11);
        return Math.max(blockLight, sky) <= DARK_AT_OR_BELOW;
    }

    /** Darkvision range in whole chunks (16 blocks = 80 ft), never under Minecraft's minimum of 2. */
    static int viewChunks(int darkvisionFeet) {
        return Math.max(2, (int) Math.ceil(darkvisionFeet / 5.0 / 16.0));
    }
}
