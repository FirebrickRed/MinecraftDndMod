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

    // A single right-click on an entity fires both PlayerInteractEvent and PlayerInteractAtEntityEvent,
    // which ran the tool twice — the Add tool toggled add-then-remove ("Added X" then "not found"),
    // and Possess re-possessed. Ignore a second tool action within this window.
    private final java.util.Map<UUID, Long> lastToolAction = new java.util.HashMap<>();
    private static final long TOOL_DEBOUNCE_MS = 200;

    private boolean toolDebounced(Player player) {
        long now = System.currentTimeMillis();
        Long prev = lastToolAction.put(player.getUniqueId(), now);
        return prev != null && now - prev < TOOL_DEBOUNCE_MS;
    }

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
        } else if (DmModeManager.TOOL_PAGE_COMBAT.equals(tool)) {
            DmModeManager.giveCombatPage(player);
        } else if (DmModeManager.TOOL_PAGE_EXPLORE.equals(tool)) {
            DmModeManager.giveExplorePage(player);
        } else if (DmModeManager.TOOL_BACK.equals(tool)) {
            DmModeManager.giveTools(player);
        } else if (DmModeManager.TOOL_VIEW.equals(tool)) {
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit != null && hit.getHitEntity() != null) {
                view(player, hit.getHitEntity());
            } else {
                player.sendActionBar(Component.text("Look at a player or entity to view them.", NamedTextColor.GRAY));
            }
        } else if (DmModeManager.TOOL_POSSESS.equals(tool)) {
            if (toolDebounced(player)) return; // this click already handled via the entity event
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit != null && hit.getHitEntity() instanceof ArmorStand stand) {
                PossessionManager.possess(player, stand);
            } else {
                player.sendActionBar(Component.text("Look at an entity to possess it.", NamedTextColor.GRAY));
            }
        } else if (DmModeManager.TOOL_START.equals(tool)) {
            toggleEncounter(player);
        } else if (DmModeManager.TOOL_INITIATIVE.equals(tool)) {
            player.performCommand("combat rollforinitiative");
        } else if (DmModeManager.TOOL_ADD.equals(tool)) {
            if (toolDebounced(player)) return; // this click already handled via the entity event
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit != null && hit.getHitEntity() != null) {
                toggleCombatant(player, hit.getHitEntity());
            } else {
                player.sendActionBar(Component.text("Look at a player or entity to add/remove them.", NamedTextColor.GRAY));
            }
        } else if (DmModeManager.TOOL_MOVE.equals(tool)) {
            // Right-click the ground → send the selection there (aim at distant ground works too).
            org.bukkit.block.Block dest = event.getClickedBlock();
            if (dest == null) dest = player.getTargetBlockExact(24);
            if (dest == null) {
                player.sendActionBar(Component.text("Aim at the ground where they should go.", NamedTextColor.GRAY));
            } else {
                MoveToolManager.moveSelectionTo(player, dest.getLocation().add(0.5, 1, 0.5));
            }
        } else if (DmModeManager.TOOL_OBJECT.equals(tool)) {
            org.bukkit.block.Block b = event.getClickedBlock();
            if (b == null) b = player.getTargetBlockExact(6);
            if (b == null) player.sendActionBar(Component.text("Right-click a block to annotate it.", NamedTextColor.GRAY));
            else showObjectMenu(player, b);
        }
    }

    /** A clickable annotation menu for the block the DM clicked with the Annotate Object tool (#185/#187). */
    private void showObjectMenu(Player player, org.bukkit.block.Block block) {
        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
        String name = ObjectCommand.pretty(block.getType().name());
        boolean locked = o != null && o.locked;
        boolean hidden = o != null && o.hidden;
        boolean trapped = o != null && o.trapped;
        String trapStr = trapped ? "trap[" + o.trapDamage + (o.disarmed ? ", disarmed" : ", armed") + "] " : "";
        String status = (o == null) ? "unannotated"
                : ((locked ? "locked " : "") + (hidden ? "hidden " : "") + trapStr
                   + (o.description.isEmpty() ? "" : "\"" + o.description + "\"")).trim();
        player.sendMessage(Component.text("🔧 " + name + " — " + (status.isEmpty() ? "annotated" : status), NamedTextColor.GOLD));

        Component opts = Component.text("  ", NamedTextColor.GRAY)
                .append(button(locked ? "[Unlock]" : "[Lock]", "/dm object " + (locked ? "unlock" : "lock"),
                        locked ? "Remove the lock" : "Mark it locked"))
                .append(Component.text(" "))
                .append(button(hidden ? "[Reveal]" : "[Hide]", "/dm object " + (hidden ? "reveal" : "hide"),
                        hidden ? "Let players interact with it" : "Hide it from players until revealed"));
        if (trapped) {
            opts = opts.append(Component.text(" "))
                    .append(button(o.disarmed ? "[Arm]" : "[Disarm]", "/dm object " + (o.disarmed ? "arm" : "disarm"),
                            o.disarmed ? "Re-arm the trap" : "Disarm the trap"));
        }
        opts = opts.append(Component.text(" "))
                .append(button("[Clear]", "/dm object clear", "Remove the annotation"))
                .append(Component.text(" "))
                .append(button("[Info]", "/dm object info", "Show its annotation"));
        player.sendMessage(opts);
        player.sendMessage(Component.text("  (describe: /dm object desc <text>  ·  trap: /dm object trap <dmg> [save] [dc]  — while looking at it)", NamedTextColor.DARK_GRAY));
    }

    private static Component button(String label, String cmd, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(cmd))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hover)));
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        String tool = DmModeManager.getToolType(player.getInventory().getItemInMainHand());
        if (DmModeManager.TOOL_VIEW.equals(tool)) {
            event.setCancelled(true);
            view(player, event.getRightClicked());
        } else if (DmModeManager.TOOL_POSSESS.equals(tool) && event.getRightClicked() instanceof ArmorStand stand) {
            event.setCancelled(true);
            if (toolDebounced(player)) return;
            PossessionManager.possess(player, stand);
        } else if (DmModeManager.TOOL_ADD.equals(tool)) {
            event.setCancelled(true);
            if (toolDebounced(player)) return;
            toggleCombatant(player, event.getRightClicked());
        } else if (DmModeManager.TOOL_START.equals(tool)) {
            event.setCancelled(true);
            toggleEncounter(player);
        } else if (DmModeManager.TOOL_INITIATIVE.equals(tool)) {
            event.setCancelled(true);
            player.performCommand("combat rollforinitiative");
        } else if (DmModeManager.TOOL_MOVE.equals(tool) && event.getRightClicked() instanceof ArmorStand stand) {
            event.setCancelled(true);
            MoveToolManager.toggleSelect(player, stand);
        }
    }

    /**
     * The Start Combat tool: begin an encounter, or cancel one that's still being set up. It will
     * NOT end a fight that's already underway (too easy to wipe an active combat by mis-click) — use
     * /combat finished deliberately for that.
     */
    private void toggleEncounter(Player dm) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatCommand.getDMSession(dm.getUniqueId());
        if (session == null || !session.isActive()) {
            dm.performCommand("combat start");
        } else if (session.isSetupPhase()) {
            dm.performCommand("combat finished"); // cancel the not-yet-started encounter
        } else {
            dm.sendActionBar(Component.text("Combat is underway — use /combat finished to end it.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Add the clicked player/entity to the DM's combat, or remove it if already in — so the same
     * tool toggles membership. Routes through /combat add|remove to reuse all the command logic.
     */
    private void toggleCombatant(Player dm, Entity target) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatCommand.getDMSession(dm.getUniqueId());
        if (session == null || !session.isActive()) {
            dm.sendActionBar(Component.text("Start combat first (use the Start Combat tool).", NamedTextColor.RED));
            return;
        }

        UUID id;
        String name;
        if (target instanceof Player p) {
            id = p.getUniqueId();
            name = p.getName();
        } else if (target instanceof ArmorStand stand) {
            DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
            if (inst == null) {
                dm.sendActionBar(Component.text("That isn't a combatant entity.", NamedTextColor.GRAY));
                return;
            }
            id = inst.getInstanceId();
            name = inst.getDisplayName();
        } else {
            dm.sendActionBar(Component.text("Right-click a player or a D&D entity.", NamedTextColor.GRAY));
            return;
        }

        boolean member = session.getCombatants().stream().anyMatch(c -> c.getId().equals(id));
        dm.performCommand("combat " + (member ? "remove " : "add ") + name);
    }

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (event.isSneaking() && PossessionManager.isPossessing(event.getPlayer().getUniqueId())) {
            PossessionManager.unpossess(event.getPlayer());
        }
    }

    /** While possessing, F (swap hands) toggles whether the DM can see their own model (F5 to view). */
    @EventHandler
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (PossessionManager.isPossessing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            PossessionManager.toggleSelfModel(event.getPlayer());
        }
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
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        // Disconnecting mid-possession would otherwise leave the DM invisible/shrunk on rejoin.
        PossessionManager.endPossession(event.getPlayer(), true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        // Don't let DM tools be dropped into the world.
        if (DmModeManager.getToolType(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }
}
