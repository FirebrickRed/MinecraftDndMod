package io.papermc.jkvttplugin.data.loader;

import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Cosmetic on-hit effects per damage type (fire → burning, cold → snowflakes, …). Loaded from
 * DMContent/DamageTypes.yml; applied when damage of that type lands. Purely visual.
 */
public class DamageTypeLoader {

    private record Effect(int fireTicks, Particle particle) {}
    private static final Map<String, Effect> effects = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("DamageTypeLoader");

    public static void loadAll(File file) {
        effects.clear();
        if (file == null || !file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = new Yaml().load(reader);
            if (data == null) return;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> m)) continue;
                int fireTicks = m.get("fire_ticks") instanceof Number n ? n.intValue() : 0;
                Particle particle = null;
                if (m.get("particle") instanceof String p) {
                    try { particle = Particle.valueOf(p.trim().toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }
                effects.put(e.getKey().toLowerCase(), new Effect(fireTicks, particle));
            }
        } catch (Exception ex) {
            LOGGER.severe("Failed to load DamageTypes.yml: " + ex.getMessage());
        }
    }

    /** Show the damage type's cosmetic effect on the hit entity. */
    public static void playHitEffect(String damageType, Entity target) {
        if (damageType == null || target == null) return;
        Effect fx = effects.get(damageType.toLowerCase());
        if (fx == null) return;
        if (fx.fireTicks() > 0) target.setFireTicks(fx.fireTicks());
        if (fx.particle() != null && target.getWorld() != null) {
            target.getWorld().spawnParticle(fx.particle(), target.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    public static void clear() { effects.clear(); }
}
