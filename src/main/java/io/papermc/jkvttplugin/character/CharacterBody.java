package io.papermc.jkvttplugin.character;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The player's body matches their character: a Small halfling stands about half as tall as a Medium
 * human (Size.scale). Applied when a character becomes active and on join; a player with no active
 * character is left as they are.
 */
public final class CharacterBody implements Listener {

    public static void apply(Player player) {
        if (player == null) return;
        // While possessing, the DM wears the creature's size; letting go puts back what they had.
        if (io.papermc.jkvttplugin.dm.PossessionManager.isPossessing(player.getUniqueId())) return;
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) return;
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(sheet.getSize().scale());
    }

    // Two ticks: DM mode's join recovery (one tick) puts back a scale a crash left mid-possession first.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    // A respawned player is a fresh entity, back at the default scale.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    private static void later(Player player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(io.papermc.jkvttplugin.JkVttPlugin.getInstance(), () -> {
            if (player.isOnline()) apply(player);
        }, 2L);
    }
}
