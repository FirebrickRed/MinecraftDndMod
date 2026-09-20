package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

/**
 * Outlines annotated blocks for a DM who is holding the Annotate Object tool (#185), so a build can
 * be read at a glance instead of by aiming at every block and typing {@code /dm object info}.
 *
 * <p>Drawn with per-player particles rather than glowing marker entities. {@code setGlowing} only
 * works on entities, so an outline would mean spawning something at every annotated coordinate —
 * entities that then need scrubbing on unload, restart and crash. The plugin already has that bug
 * once (stray turn-glow surviving a restart, #105); particles leave nothing behind to clean up, and
 * {@link Player#spawnParticle} sends to that player alone, so the table never sees the DM's markup.
 *
 * <p>An outline hanging in empty air is an annotation whose block is gone — see
 * {@code /dm object list}.
 */
public final class AnnotationGlow {

    private AnnotationGlow() {}

    private static final int PERIOD_TICKS = 10;   // twice a second is enough for a static outline
    private static final double STEP = 0.5;       // particle spacing along each cube edge
    private static BukkitTask task;

    // Precedence runs most-dangerous first: whatever a DM most needs to notice wins the color.
    private static final Particle.DustOptions ARMED_TRAP = dust(255, 60, 60);
    private static final Particle.DustOptions HIDDEN = dust(190, 90, 255);
    private static final Particle.DustOptions LOCKED = dust(255, 190, 40);
    private static final Particle.DustOptions SEALED = dust(150, 150, 150);
    private static final Particle.DustOptions PLAIN = dust(110, 220, 130);

    private static Particle.DustOptions dust(int r, int g, int b) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), 0.9f);
    }

    public static void start(Plugin plugin) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, AnnotationGlow::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private static void tick() {
        if (!PluginConfig.isAnnotationGlow()) return;

        // Cheap exit: almost every tick there is no DM holding the tool, and we don't want to walk
        // the annotation map for nothing.
        java.util.List<Player> watchers = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!DmModeManager.isInDmMode(p)) continue;
            if (!DmModeManager.TOOL_OBJECT.equals(DmModeManager.getToolType(p.getInventory().getItemInMainHand()))) continue;
            if (watchers == null) watchers = new java.util.ArrayList<>(2);
            watchers.add(p);
        }
        if (watchers == null) return;

        int radius = PluginConfig.getAnnotationGlowRadius();
        double radiusSq = (double) radius * radius;
        Map<String, InteractiveObjectManager.Obj> all = InteractiveObjectManager.all();

        for (Player dm : watchers) {
            for (Map.Entry<String, InteractiveObjectManager.Obj> e : all.entrySet()) {
                Location loc = InteractiveObjectManager.locationFromKey(e.getKey());
                if (loc == null || !loc.getWorld().equals(dm.getWorld())) continue;
                if (loc.distanceSquared(dm.getLocation()) > radiusSq) continue;
                outline(dm, loc, colorFor(e.getValue()));
            }
        }
    }

    /** Most-dangerous-first, so a trapped-and-locked chest reads as trapped. */
    private static Particle.DustOptions colorFor(InteractiveObjectManager.Obj o) {
        if (o.hasArmedTrap()) return ARMED_TRAP;
        if (o.hidden) return HIDDEN;
        return switch (o.opening) {
            case LOCKED -> LOCKED;
            case SEALED -> SEALED;
            case OPENS -> PLAIN;
        };
    }

    /** The twelve edges of the block's cube, drawn for one player only. */
    private static void outline(Player dm, Location block, Particle.DustOptions color) {
        double x = block.getBlockX(), y = block.getBlockY(), z = block.getBlockZ();
        for (double t = 0; t <= 1.0001; t += STEP) {
            // 4 edges along X, 4 along Z, 4 vertical — the corners double up, which is fine.
            for (double dy : new double[]{0, 1}) {
                for (double dz : new double[]{0, 1}) dm.spawnParticle(Particle.DUST, x + t, y + dy, z + dz, 1, 0, 0, 0, 0, color);
                for (double dx : new double[]{0, 1}) dm.spawnParticle(Particle.DUST, x + dx, y + dy, z + t, 1, 0, 0, 0, 0, color);
            }
            for (double dx : new double[]{0, 1}) {
                for (double dz : new double[]{0, 1}) dm.spawnParticle(Particle.DUST, x + dx, y + t, z + dz, 1, 0, 0, 0, 0, color);
            }
        }
    }
}
