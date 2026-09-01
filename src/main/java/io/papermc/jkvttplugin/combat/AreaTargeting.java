package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.JkVttPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aim-and-confirm preview for area effects (#173). When a player triggers an area ability — a
 * cone/line/burst spell, or a dragonborn's breath weapon — we don't resolve it blindly from wherever
 * they happen to be looking. Instead we enter a pending state that, every few ticks, redraws the
 * area from their live aim and glows the creatures currently caught. A right-click confirms (runs the
 * caller's resolution callback); sneak (or leaving combat) cancels with nothing spent.
 *
 * <p>The caller keeps ownership of what "resolve" means: it hands us a {@link Runnable} that spends
 * the cost and applies the effect. That runs only on confirm, so a cancelled aim costs nothing.
 */
public final class AreaTargeting {

    private AreaTargeting() {}

    private static final long PERIOD_TICKS = 4L;   // preview refresh cadence
    private static final int TIMEOUT_TICKS = 20 * 30; // auto-cancel after 30s of indecision

    private static final class Pending {
        final UUID casterId;
        final CombatSession session;
        final String shape;
        final double sizeFeet;
        final String label;
        final Runnable onConfirm;
        final Set<UUID> glowing = new HashSet<>(); // entities we've toggled glow on, to restore later
        int ticksLeft = TIMEOUT_TICKS;
        Pending(UUID casterId, CombatSession session, String shape, double sizeFeet, String label, Runnable onConfirm) {
            this.casterId = casterId;
            this.session = session;
            this.shape = shape;
            this.sizeFeet = sizeFeet;
            this.label = label;
            this.onConfirm = onConfirm;
        }
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static BukkitTask task;

    /**
     * Begin aiming an area effect. Replaces any prior pending aim for this player. Nothing is spent
     * until {@link #confirm(Player)} runs {@code onConfirm}.
     */
    public static void begin(Player player, CombatSession session, Combatant caster, String label,
                             String shape, double sizeFeet, String targets, Runnable onConfirm) {
        if (player == null || onConfirm == null) return;
        cancel(player, false); // clear any earlier aim first
        pending.put(player.getUniqueId(), new Pending(caster.getId(), session, shape, sizeFeet, label, onConfirm));
        player.sendMessage(Component.text("🎯 Aim your " + label + " — ", NamedTextColor.GOLD)
                .append(Component.text("right-click to fire", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(", sneak to cancel. Caught creatures glow.", NamedTextColor.GRAY)));
        startTaskIfNeeded();
    }

    public static boolean isAiming(UUID playerId) { return pending.containsKey(playerId); }

    /** Confirm the pending aim: stop the preview and run the caller's resolution. */
    public static boolean confirm(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) return false;
        clearGlow(p);
        try {
            p.onConfirm.run();
        } catch (Exception e) {
            JkVttPlugin.logger().warning("Area effect confirm failed: " + e.getMessage());
        }
        return true;
    }

    /** Cancel the pending aim with nothing spent. */
    public static boolean cancel(Player player, boolean notify) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) return false;
        clearGlow(p);
        if (notify) player.sendMessage(Component.text("Aim cancelled — nothing spent.", NamedTextColor.YELLOW));
        return true;
    }

    private static void startTaskIfNeeded() {
        if (task != null) return;
        task = JkVttPlugin.getInstance().getServer().getScheduler().runTaskTimer(
                JkVttPlugin.getInstance(), AreaTargeting::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    private static void tick() {
        if (pending.isEmpty()) return;
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            Player player = JkVttPlugin.getInstance().getServer().getPlayer(entry.getKey());
            Pending p = entry.getValue();
            if (player == null || !player.isOnline()) { clearGlow(p); expired.add(entry.getKey()); continue; }
            p.ticksLeft -= PERIOD_TICKS;
            if (p.ticksLeft <= 0) {
                clearGlow(p);
                player.sendMessage(Component.text("Aim timed out — nothing spent.", NamedTextColor.YELLOW));
                expired.add(entry.getKey());
                continue;
            }
            drawPreview(player, p);
        }
        for (UUID id : expired) pending.remove(id);
    }

    private static void drawPreview(Player player, Pending p) {
        Combatant caster = combatantById(p.session, p.casterId);
        if (caster == null) return;
        Location origin = caster.getLocation();
        if (origin == null || origin.getWorld() == null) return;

        // Outline the area from the player's live aim so they can see where it lands.
        Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();
        double lengthBlocks = p.sizeFeet / 5.0;
        Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.1f);
        boolean cone = "cone".equalsIgnoreCase(p.shape);
        boolean line = "line".equalsIgnoreCase(p.shape);
        Location eye = origin.clone().add(0, 1.0, 0);
        for (double d = 0.5; d <= lengthBlocks; d += 0.5) {
            Location center = eye.clone().add(dir.clone().multiply(d));
            double halfWidth = cone ? d / 2.0 : (line ? 0.5 : lengthBlocks);
            if (cone || line) {
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(halfWidth);
                origin.getWorld().spawnParticle(Particle.DUST, center.clone().add(perp), 1, 0, 0, 0, 0, edge);
                origin.getWorld().spawnParticle(Particle.DUST, center.clone().subtract(perp), 1, 0, 0, 0, edge);
            } else { // burst/sphere: ring around the origin
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    Location pt = center.clone().add(Math.cos(rad) * lengthBlocks, 0, Math.sin(rad) * lengthBlocks);
                    origin.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, edge);
                    break; // one ring pass is enough per tick for burst/sphere
                }
            }
        }

        // Glow whoever is caught right now; un-glow anyone who has stepped out.
        List<Combatant> caught = SpellCastHandler.combatantsInArea(caster, player, p.shape, p.sizeFeet);
        Set<UUID> nowCaught = new HashSet<>();
        for (Combatant c : caught) {
            if (c.getId().equals(caster.getId()) || c.isDead()) continue;
            Entity body = bodyOf(c);
            if (body == null) continue;
            nowCaught.add(c.getId());
            if (!body.isGlowing()) body.setGlowing(true);
            p.glowing.add(c.getId());
        }
        // Remove glow from those no longer caught.
        p.glowing.removeIf(id -> {
            if (nowCaught.contains(id)) return false;
            Combatant c = combatantById(p.session, id);
            Entity body = c != null ? bodyOf(c) : null;
            if (body != null) body.setGlowing(false);
            return true;
        });

        player.sendActionBar(Component.text("🎯 " + p.label + " — " + nowCaught.size()
                + " caught. Right-click to fire, sneak to cancel.", NamedTextColor.GOLD));
    }

    private static void clearGlow(Pending p) {
        for (UUID id : p.glowing) {
            Combatant c = combatantById(p.session, id);
            Entity body = c != null ? bodyOf(c) : null;
            if (body != null) body.setGlowing(false);
        }
        p.glowing.clear();
    }

    private static Entity bodyOf(Combatant c) {
        if (c.isPlayer()) return c.getPlayer();
        return c.getEntityInstance() != null ? c.getEntityInstance().getArmorStand() : null;
    }

    private static Combatant combatantById(CombatSession session, UUID id) {
        for (Combatant c : session.getCombatants()) if (c.getId().equals(id)) return c;
        return null;
    }
}
