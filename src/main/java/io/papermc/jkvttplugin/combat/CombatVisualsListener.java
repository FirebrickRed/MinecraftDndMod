package io.papermc.jkvttplugin.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Keeps the cosmetic combat projectiles (#181) purely visual: they never deal damage or knockback,
 * and they clean themselves up the instant they land instead of sticking in the world.
 */
public class CombatVisualsListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCosmeticDamage(EntityDamageByEntityEvent event) {
        if (CombatVisuals.isCosmetic(event.getDamager())) {
            event.setCancelled(true); // the D&D /combat damage step is the real hit, not this arrow
        }
    }

    @EventHandler
    public void onCosmeticLand(ProjectileHitEvent event) {
        if (CombatVisuals.isCosmetic(event.getEntity())) {
            event.setCancelled(true);       // don't embed in the block/entity
            event.getEntity().remove();     // vanish on impact
        }
    }
}
