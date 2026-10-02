package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * An effect's {@code aura:} (#247): a ring of coloured dust around whoever has it, every half second,
 * seen by everyone nearby. Rage is red. A vanilla potion effect can't do this job: Strength shows only
 * in the inventory and as faint swirls, with no tint (playtest: "I don't see the red tint").
 */
public final class EffectAuras {

    private EffectAuras() {}

    private static final Map<String, Color> NAMED = Map.of(
            "red", Color.fromRGB(0xD0, 0x20, 0x20), "orange", Color.fromRGB(0xF0, 0x80, 0x20),
            "yellow", Color.fromRGB(0xF0, 0xD0, 0x30), "green", Color.fromRGB(0x40, 0xC0, 0x40),
            "blue", Color.fromRGB(0x40, 0x70, 0xE0), "purple", Color.fromRGB(0xA0, 0x40, 0xD0),
            "white", Color.WHITE, "gold", Color.fromRGB(0xFF, 0xC0, 0x30));

    /** "red", "#ff3300" → a colour; null if it's neither. */
    public static Color color(String aura) {
        if (aura == null) return null;
        String a = aura.trim().toLowerCase(Locale.ROOT);
        if (NAMED.containsKey(a)) return NAMED.get(a);
        if (a.matches("#[0-9a-f]{6}")) return Color.fromRGB(Integer.parseInt(a.substring(1), 16));
        return null;
    }

    public static void start() {
        Bukkit.getScheduler().runTaskTimer(JkVttPlugin.getInstance(), EffectAuras::tick, 20L, 10L);
    }

    private static void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(p);
            if (sheet == null) continue;
            for (ActiveEffect e : sheet.getActiveEffects()) {
                Color c = color(e.getAura());
                if (c != null) ring(p, c);
            }
        }
    }

    private static void ring(Player p, Color c) {
        Location base = p.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(c, 1.1f);
        double height = p.getHeight();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + (System.currentTimeMillis() / 400.0);
            Location at = base.clone().add(Math.cos(angle) * 0.6, height * (0.3 + 0.4 * ((i % 2))), Math.sin(angle) * 0.6);
            p.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
        }
    }
}
