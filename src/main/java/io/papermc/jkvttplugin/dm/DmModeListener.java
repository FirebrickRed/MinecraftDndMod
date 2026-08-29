package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Drives the DM-mode tools (Issue #85 redesign): the View tool, the Exit tool, and crash recovery. */
public class DmModeListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        String tool = DmModeManager.getToolType(player.getInventory().getItemInMainHand());
        if (tool == null) return;
        event.setCancelled(true);

        if (DmModeManager.TOOL_EXIT.equals(tool)) {
            DmModeManager.exit(player);
        } else if (DmModeManager.TOOL_VIEW.equals(tool)) {
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit != null && hit.getHitEntity() != null) {
                view(player, hit.getHitEntity());
            } else {
                player.sendActionBar(Component.text("Look at a player or entity to view them.", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!DmModeManager.TOOL_VIEW.equals(DmModeManager.getToolType(player.getInventory().getItemInMainHand()))) return;
        event.setCancelled(true);
        view(player, event.getRightClicked());
    }

    private void view(Player dm, Entity target) {
        if (!DMManager.isDM(dm)) return;

        if (target instanceof Player targetPlayer) {
            UUID charId = ActiveCharacterTracker.getActiveCharacterId(targetPlayer);
            if (charId == null) {
                List<CharacterSheet> chars = CharacterSheetManager.getPlayerCharacters(targetPlayer.getUniqueId());
                if (chars != null && !chars.isEmpty()) charId = chars.get(0).getCharacterId();
            }
            if (charId != null) {
                ViewCharacterSheetMenu.open(dm, charId);
            } else {
                dm.sendMessage(Component.text(targetPlayer.getName() + " has no character.", NamedTextColor.GRAY));
            }
        } else if (target instanceof ArmorStand stand) {
            DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
            if (inst != null) statBlock(dm, inst);
        }
    }

    private void statBlock(Player dm, DndEntityInstance inst) {
        DndEntity t = inst.getTemplate();
        dm.sendMessage(Component.empty());
        dm.sendMessage(Component.text("━━━ " + inst.getDisplayName() + " (" + t.getName() + ") ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        dm.sendMessage(Component.text("AC " + t.getArmorClass()
                + "  |  HP " + inst.getCurrentHp() + "/" + inst.getMaxHp()
                + "  |  Speed " + t.getSpeed() + " ft", NamedTextColor.WHITE));
        List<DndAttack> attacks = t.getAttacks();
        if (attacks != null && !attacks.isEmpty()) {
            dm.sendMessage(Component.text("Attacks: "
                    + attacks.stream().map(DndAttack::getName).collect(Collectors.joining(", ")), NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay a tick so the player's saved inventory is fully loaded before we restore over it.
        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> DmModeManager.recoverOnJoin(player));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        // Don't let DM tools be dropped into the world.
        if (DmModeManager.getToolType(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }
}
