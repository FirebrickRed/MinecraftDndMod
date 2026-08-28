package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.commands.DmEntityCommand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Re-hydrates saved D&D entities as their chunks load (Issue #89).
 *
 * Entity state rides on the armor stand's persistent data, which Minecraft saves with the world,
 * so restoring is just: when a chunk loads, re-register any of our armor stands that aren't
 * already tracked. This covers entities outside the spawn chunks that only load when approached.
 */
public class EntityChunkListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ArmorStand stand) {
                DmEntityCommand.rehydrate(stand);
            }
        }
    }
}
