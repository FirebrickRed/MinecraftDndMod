package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.JkVttPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Trident;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Cosmetic combat flair (#181): the visual/audio that makes an attack feel physical without letting
 * Minecraft do the actual damage — the D&D rolls stay authoritative.
 *
 * <ul>
 *   <li>On a hit with a fired/thrown weapon, a purely-cosmetic arrow (or trident) flies at the
 *       target. It's tagged and deals no damage; {@link CombatVisualsListener} neutralises it.</li>
 *   <li>When damage is applied via {@code /combat damage}, the target plays the vanilla hurt
 *       animation and sound, so the hit reads on the body at the moment HP actually changes.</li>
 * </ul>
 */
public final class CombatVisuals {

    private CombatVisuals() {}

    /** Marks a projectile as ours-and-cosmetic so the listener cancels its damage and cleans it up. */
    public static final NamespacedKey COSMETIC_KEY = new NamespacedKey("jkvtt", "cosmetic_projectile");
    private static final long PROJECTILE_LIFETIME_TICKS = 40L;

    /**
     * Launch a cosmetic {@code projectile} ("arrow" | "trident") from the attacker toward the target.
     * No-op when {@code projectile} is null (a melee attack) or either body is missing.
     */
    public static void projectileOnHit(Combatant attacker, Combatant target, String projectile) {
        if (projectile == null || attacker == null || target == null) return;
        if (attacker.getId().equals(target.getId())) return;
        Location from = attacker.getLocation();
        Location to = target.getLocation();
        if (from == null || to == null || from.getWorld() == null) return;

        Location origin = from.clone().add(0, 1.2, 0);
        Vector velocity = to.clone().add(0, 1.0, 0).toVector().subtract(origin.toVector());
        if (velocity.lengthSquared() < 1.0E-4) return;
        velocity.normalize().multiply(2.8);

        Class<? extends AbstractArrow> type = "trident".equalsIgnoreCase(projectile) ? Trident.class : Arrow.class;
        AbstractArrow proj = origin.getWorld().spawn(origin, type);
        proj.setVelocity(velocity);
        proj.setDamage(0.0);                 // cosmetic only — the /combat damage step deals the real hit
        proj.setGravity(false);
        proj.setPersistent(false);
        proj.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        proj.getPersistentDataContainer().set(COSMETIC_KEY, PersistentDataType.BYTE, (byte) 1);

        // Fail-safe cleanup in case it never collides (missed the hitbox, flew off, etc.).
        JkVttPlugin.getInstance().getServer().getScheduler().runTaskLater(
                JkVttPlugin.getInstance(), () -> { if (proj.isValid()) proj.remove(); }, PROJECTILE_LIFETIME_TICKS);
    }

    /** True if this projectile is one of our cosmetic ones (so it must never deal damage). */
    public static boolean isCosmetic(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(COSMETIC_KEY, PersistentDataType.BYTE);
    }

    /**
     * Play the vanilla hurt animation + sound on the target, timed with real HP loss. Players flinch
     * red; entities (armor stands) can't animate, so they just get the sound at their location.
     */
    public static void hurtOnDamage(Combatant target) {
        if (target == null) return;
        Entity body = target.isPlayer() ? target.getPlayer()
                : (target.getEntityInstance() != null ? target.getEntityInstance().getArmorStand() : null);
        if (body instanceof LivingEntity living) {
            try { living.playHurtAnimation(0.0f); } catch (Throwable ignored) { /* older API / unsupported body */ }
        }
        Location loc = target.getLocation();
        if (loc != null && loc.getWorld() != null) {
            Sound sound = target.isPlayer() ? Sound.ENTITY_PLAYER_HURT : Sound.ENTITY_GENERIC_HURT;
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        }
    }
}
