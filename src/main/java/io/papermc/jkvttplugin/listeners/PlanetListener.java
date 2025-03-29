package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.player.PlayerManager;
import io.papermc.jkvttplugin.player.Races.DndRace;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlanetListener implements Listener {
    private static final String CUSTOM_DIMENSION_KEY = "world_jkvttdatapack_xegrurn";
    private static final Material SAFE_BLOCK = Material.IRON_BLOCK;
    private final Map<UUID, Integer> playerBreath = new HashMap<>();

    public PlanetListener() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().getName().equals(CUSTOM_DIMENSION_KEY)) {
                        handleAirDepletion(player);
                        updatePlayerAir(player);
                    }
                }
            }
        }.runTaskTimer(JkVttPlugin.getInstance(), 0L, 1L);
    }

    private void updatePlayerAir(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerBreath.containsKey(playerId)) {
            player.setRemainingAir(0);
            return;
        }

        int remainingAir = playerBreath.get(playerId);
        CharacterSheet sheet = PlayerManager.getCharacterSheet(player);
        if (sheet == null) return;
        DndRace race = sheet.getRaceForPlayer();
        if (race == null) return;

        int maxBreath = race.getBreathDuration();
        int visualAir = (int) (((double) remainingAir / maxBreath) * player.getMaximumAir());

        player.setMaximumAir(player.getMaximumAir());
        player.setRemainingAir(visualAir);
    }

    @EventHandler
    public void onPlayerLeaveDimension(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(CUSTOM_DIMENSION_KEY)) {
            playerBreath.remove(player.getUniqueId());
        }
    }

    private void handleAirDepletion(Player player) {
        UUID playerId = player.getUniqueId();
        CharacterSheet sheet = PlayerManager.getCharacterSheet(player);
        if (sheet == null) return;
        DndRace race = sheet.getRaceForPlayer();
        if (race == null) return;

        if (!playerBreath.containsKey(playerId)) {
            playerBreath.put(playerId, race.getBreathDuration());
        }

        int remainingAir = playerBreath.get(playerId);
        boolean isSafe = isUnderSafeBlock(player.getLocation());

        if (isSafe) {
            playerBreath.put(playerId, race.getBreathDuration());
            // player.setRemainingAir(race.getBreathDuration());
            player.removePotionEffect(PotionEffectType.POISON);
        } else {
            remainingAir--;
            if (remainingAir <= 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, true, true));
                remainingAir = 0;
            }
            playerBreath.put(playerId, remainingAir);
        }

        // player.setRemainingAir(playerBreath.get(playerId));
    }

    private boolean isUnderSafeBlock(Location location) {
        for (int y = location.getBlockY() + 1; y <= location.getBlockY() + 5; y++) {
            Material blockType = location.getWorld().getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType();
            if (blockType == SAFE_BLOCK) {
                return true;
            }
        }
        return false;
    }
}
