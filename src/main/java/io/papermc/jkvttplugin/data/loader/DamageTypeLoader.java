package io.papermc.jkvttplugin.data.loader;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * How each damage type looks (fire → flames, cold → snowflakes, …), from {@code DMContent/DamageTypes.yml}.
 * Used when damage of that type lands (a puff on the one hit, fire_ticks sets them alight) and as the
 * default look of a spell of that type (#230). Two entries aren't damage types: {@code healing} (a
 * healing spell) and {@code buff} (Bless, Shield of Faith). A homebrew damage type is just a new entry.
 * Purely visual.
 */
public class DamageTypeLoader {

    /** A particle, and its colour when it's DUST (the only one that takes a colour). */
    public record Look(Particle particle, Color color) {
        public boolean isDust() { return particle == Particle.DUST; }
    }

    private record Effect(int fireTicks, Look look) {}
    private static final Map<String, Effect> effects = new HashMap<>();
    /** Problems the content check reports: a particle that doesn't exist, or needs data we can't give it. */
    private static final List<String> problems = new ArrayList<>();
    private static final Logger LOGGER = Logger.getLogger("DamageTypeLoader");

    public static void loadAll(File file) {
        effects.clear();
        problems.clear();
        if (file == null || !file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = new Yaml().load(reader);
            if (data == null) return;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> m)) continue;
                int fireTicks = m.get("fire_ticks") instanceof Number n ? n.intValue() : 0;
                Look look = parseLook(m.get("particle"), m.get("color"), "DamageTypes.yml '" + e.getKey() + "'");
                effects.put(e.getKey().toLowerCase(), new Effect(fireTicks, look));
            }
        } catch (Exception ex) {
            LOGGER.severe("Failed to load DamageTypes.yml: " + ex.getMessage());
        }
    }

    /**
     * A particle name and optional "#rrggbb" colour → a look, or null. A bad name, or a particle that needs
     * data other than a colour (BLOCK, ITEM…), is noted for the content check under {@code where}.
     */
    public static Look parseLook(Object particleName, Object color, String where) {
        if (particleName == null || String.valueOf(particleName).isBlank()) return null;
        Particle particle;
        try {
            particle = Particle.valueOf(String.valueOf(particleName).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            problems.add(where + ": '" + particleName + "' isn't a Minecraft particle (e.g. FLAME, SNOWFLAKE, END_ROD, DUST).");
            return null;
        }
        Class<?> needs = particle.getDataType();
        if (needs != Void.class && particle != Particle.DUST) {
            problems.add(where + ": " + particle + " needs extra data the plugin doesn't give it; pick another (or DUST with a color).");
            return null;
        }
        Color c = null;
        if (color != null) {
            String hex = String.valueOf(color).trim().replace("#", "");
            try { c = Color.fromRGB(Integer.parseInt(hex, 16)); }
            catch (IllegalArgumentException ex) { problems.add(where + ": color '" + color + "' isn't #rrggbb."); }
        }
        if (particle == Particle.DUST && c == null) c = Color.WHITE;
        return new Look(particle, c);
    }

    /** The look for a damage type (or "healing" / "buff"), or null. */
    public static Look lookFor(String type) {
        Effect fx = type == null ? null : effects.get(type.toLowerCase());
        return fx == null ? null : fx.look();
    }

    /** What the content check should say about this file and the spells' own visual: blocks. */
    public static List<String> problems() { return List.copyOf(problems); }

    /** Show the damage type's cosmetic effect on the hit entity. */
    public static void playHitEffect(String damageType, Entity target) {
        if (damageType == null || target == null) return;
        Effect fx = effects.get(damageType.toLowerCase());
        if (fx == null) return;
        // Flames that don't burn: real fire ticks deal vanilla damage to a player, on top of the D&D damage.
        if (fx.fireTicks() > 0 && org.bukkit.Bukkit.getServer() != null) {
            target.setVisualFire(true);
            org.bukkit.Bukkit.getScheduler().runTaskLater(io.papermc.jkvttplugin.JkVttPlugin.getInstance(),
                    () -> { if (target.isValid()) target.setVisualFire(false); }, fx.fireTicks());
        }
        if (fx.look() != null && target.getWorld() != null) {
            spawn(fx.look(), target.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Spawn a look's particle: with its colour when it's DUST. */
    public static void spawn(Look look, org.bukkit.Location at, int count, double dx, double dy, double dz, double speed) {
        if (look == null || at.getWorld() == null) return;
        if (look.isDust()) {
            at.getWorld().spawnParticle(Particle.DUST, at, count, dx, dy, dz, speed, new Particle.DustOptions(look.color(), 1.2f));
        } else {
            at.getWorld().spawnParticle(look.particle(), at, count, dx, dy, dz, speed);
        }
    }

    public static void clear() { effects.clear(); problems.clear(); }
}
