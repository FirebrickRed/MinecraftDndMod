package io.papermc.jkvttplugin.character;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What the player sees matches who they are (#148): a character with darkvision gets Minecraft night
 * vision, one without stays in the dark. While a DM possesses a creature, the creature's darkvision
 * decides instead.
 *
 * <p>Ours is marked by its shape (infinite, ambient, no particles or icon), so a night-vision potion
 * someone actually drank is never taken away; only the one this class gave is removed.
 * Minecraft has no range for it, so 60 ft and 120 ft of darkvision look the same.
 */
public final class CharacterSight {

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
}
