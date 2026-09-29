package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.loader.DamageTypeLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * What a spell looks like when it goes off (#230). Purely cosmetic: rolls and damage don't change.
 *
 * <p><b>Shape</b> (a spell's {@code visual: shape:}, else from what it does):
 * <ul>
 *   <li>{@code bolt}: flies from the caster to the target (an attack roll, Magic Missile)</li>
 *   <li>{@code burst}: bursts on the target (a save spell: Sacred Flame, Acid Splash)</li>
 *   <li>{@code glow}: rises around the target (healing, and buffs like Bless)</li>
 *   <li>none: an area spell (its aim preview already shows it) or a utility spell</li>
 * </ul>
 * <b>Look</b>: the spell's own {@code visual: particle:}, else its damage type's in
 * {@code DMContent/DamageTypes.yml}, else {@code healing} / {@code buff} / {@code arcane} from there.
 */
public final class SpellVisuals {

    private SpellVisuals() {}

    /** The shape this spell plays as, or null for none. */
    public static String shapeFor(DndSpell spell) {
        String own = spell.getVisualShape();
        if (own != null) return own.equals("none") ? null : own;
        if (spell.isAoe()) return null;
        if (spell.isHealing() || spell.grantsTempHp() || spell.hasEffect() && !spell.isSaveSpell()) return "glow";
        if (spell.isAttackRoll() || spell.isAutoHit()) return "bolt";
        if (spell.isSaveSpell()) return "burst";
        return null;
    }

    /** The particle it plays with, or null if nothing's defined for it. */
    public static DamageTypeLoader.Look lookFor(DndSpell spell) {
        if (spell.getVisualLook() != null) return spell.getVisualLook();
        DamageTypeLoader.Look byType = DamageTypeLoader.lookFor(spell.getDamageType());
        if (byType != null) return byType;
        if (spell.isHealing() || spell.grantsTempHp()) return DamageTypeLoader.lookFor("healing");
        if (spell.hasEffect()) return DamageTypeLoader.lookFor("buff");
        return DamageTypeLoader.lookFor("arcane");
    }

    /**
     * Play it from the caster to the target (each a creature's feet, as {@code getLocation} gives them).
     * Does nothing without a server, a shape or a look.
     */
    public static void play(DndSpell spell, Location caster, Location target) {
        if (Bukkit.getServer() == null || spell == null || target == null || target.getWorld() == null) return;
        String shape = shapeFor(spell);
        DamageTypeLoader.Look look = lookFor(spell);
        if (shape == null || look == null) return;
        Location at = target.clone().add(0, 1, 0);
        switch (shape) {
            case "bolt" -> {
                if (caster == null || caster.getWorld() == null || !caster.getWorld().equals(target.getWorld())) {
                    burst(look, at);
                } else {
                    bolt(look, caster.clone().add(0, 1.4, 0), at);
                }
            }
            case "burst" -> burst(look, at);
            case "glow" -> glow(look, target.clone());
            default -> { }
        }
    }

    /** A line from one point to the other, drawn over a few ticks so it reads as flying, then a pop. */
    private static void bolt(DamageTypeLoader.Look look, Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1) { burst(look, to); return; }
        int points = (int) Math.min(120, Math.ceil(length / 0.25));
        int ticks = (int) Math.max(3, Math.min(10, Math.ceil(length / 3)));
        Vector each = step.clone().multiply(1.0 / points);
        new BukkitRunnable() {
            int drawn = 0;
            @Override
            public void run() {
                int until = Math.min(points, drawn + (int) Math.ceil(points / (double) ticks));
                for (; drawn < until; drawn++) {
                    DamageTypeLoader.spawn(look, from.clone().add(each.clone().multiply(drawn)), 2, 0.03, 0.03, 0.03, 0);
                }
                if (drawn >= points) { burst(look, to); cancel(); }
            }
        }.runTaskTimer(JkVttPlugin.getInstance(), 0L, 1L);
    }

    /** A pop on the target. */
    private static void burst(DamageTypeLoader.Look look, Location at) {
        DamageTypeLoader.spawn(look, at, 30, 0.35, 0.45, 0.35, look.isDust() ? 0 : 0.05);
    }

    /** A ring rising around the target over half a second: healing, a blessing. */
    private static void glow(DamageTypeLoader.Look look, Location feet) {
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                double y = tick * 0.2;
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2 * i / 8) + tick * 0.4;
                    DamageTypeLoader.spawn(look, feet.clone().add(Math.cos(angle) * 0.6, y, Math.sin(angle) * 0.6), 1, 0, 0, 0, 0);
                }
                if (++tick > 10) cancel();
            }
        }.runTaskTimer(JkVttPlugin.getInstance(), 0L, 1L);
    }
}
