package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
            if (toolDebounced(player)) return; // this click already handled via the entity event
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit != null && hit.getHitEntity() != null) {
                view(player, hit.getHitEntity());
            } else {
                player.sendActionBar(Component.text("Look at a player or entity to view them.", NamedTextColor.GRAY));
            }
        } else if (DmModeManager.TOOL_SURPRISE.equals(tool)) {
            if (toolDebounced(player)) return; // this click already handled via the entity event
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit == null || hit.getHitEntity() == null) {
                player.sendActionBar(Component.text("Look at someone in the fight to mark them Surprised.", NamedTextColor.GRAY));
            } else {
                surprise(player, hit.getHitEntity());
            }
        } else if (DmModeManager.TOOL_ADJUST.equals(tool)) {
            if (toolDebounced(player)) return; // this click already handled via the entity event
            RayTraceResult hit = player.rayTraceEntities(10);
            if (hit == null || hit.getHitEntity() == null || !adjust(player, hit.getHitEntity())) {
                player.sendActionBar(Component.text("Look at a player or creature to adjust them.", NamedTextColor.GRAY));
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
        } else if (DmModeManager.TOOL_SPAWN.equals(tool)) {
            showSpawnMenu(player);
        }
    }

    /** A clickable chat list of every loaded entity; clicking fills /dm entity spawn for that id (#20). */
    private void showSpawnMenu(Player player) {
        java.util.List<io.papermc.jkvttplugin.data.model.DndEntity> entities =
                new java.util.ArrayList<>(io.papermc.jkvttplugin.data.loader.EntityLoader.getAllEntities());
        if (entities.isEmpty()) {
            player.sendActionBar(Component.text("No entities are loaded (check DMContent/Entities/).", NamedTextColor.RED));
            return;
        }
        entities.sort(java.util.Comparator.comparing(e -> e.getName() == null ? e.getId() : e.getName()));

        player.sendMessage(Component.text("🥚 Spawn which entity? ", NamedTextColor.GOLD)
                .append(Component.text("(appears where you stand — Move to reposition)", NamedTextColor.GRAY)));
        Component row = Component.text("   ", NamedTextColor.GRAY);
        for (io.papermc.jkvttplugin.data.model.DndEntity e : entities) {
            String label = e.getName() != null ? e.getName() : e.getId();
            // Fill the command (trailing space) so the DM can add a custom name before Enter, and
            // spawn happens on Enter rather than firing the instant they browse the list.
            String cmd = "/dm entity spawn " + e.getId() + " ";
            row = row.append(Component.text("[" + label + "] ", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand(cmd))
                    .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + "\nAdd a name, or press Enter to spawn "
                            + label + " where you stand."))));
        }
        player.sendMessage(row);
    }

    /** A clickable annotation menu for the block the DM clicked with the Annotate Object tool (#185/#187). */
    private void showObjectMenu(Player player, org.bukkit.block.Block block) {
        block = InteractiveObjectManager.annotationBlock(block); // either half of a double chest
        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
        String name = ObjectCommand.pretty(block.getType().name());
        InteractiveObjectManager.Obj.Opening opening = o != null ? o.opening : InteractiveObjectManager.Obj.Opening.OPENS;
        boolean hidden = o != null && o.hidden;
        boolean trapped = o != null && o.trapped;
        String trapStr = trapped ? "trap[" + o.trapDamage + (o.disarmed ? ", disarmed" : ", armed") + "] " : "";
        String status = (o == null) ? "unannotated"
                : (ObjectCommand.openingLabel(opening) + (hidden ? "hidden " : "") + trapStr
                   + (o.description.isEmpty() ? "" : "\"" + o.description + "\"")).trim();
        player.sendMessage(Component.text("🔧 " + name + " — " + (status.isEmpty() ? "annotated" : status), NamedTextColor.GOLD));

        // Opening is pick-one, so it's a row of three with the current one marked — not a toggle.
        Component opts = Component.text("  ", NamedTextColor.GRAY)
                .append(openingButton("Opens", "unlock", opening, InteractiveObjectManager.Obj.Opening.OPENS,
                        "It opens normally"))
                .append(Component.text(" "))
                .append(openingButton("Locked", "lock", opening, InteractiveObjectManager.Obj.Opening.LOCKED,
                        "It won't open, and you get pinged to call a check"))
                .append(Component.text(" "))
                .append(openingButton("Sealed", "seal", opening, InteractiveObjectManager.Obj.Opening.SEALED,
                        "Scenery — it never opens and you aren't pinged"))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
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
        // The ones that need typing: click to get the command filled in, then finish it and Enter
        // (keep looking at the block). A description already there is filled in to edit.
        String desc = o != null && !o.description.isEmpty() ? o.description : "";
        player.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                .append(fill("[Describe…]", "/dm object desc " + desc, "What players see when they look closer"))
                .append(Component.text(" "))
                .append(fill("[Trap…]", "/dm object trap ", "Damage, then an optional save and DC: 2d6 dex 13"))
                .append(Component.text(" "))
                .append(fill("[Key…]", "/dm object key ", "The item that opens it: iron_key, brass_key, … (add single-use to use it up)"))
                .append(Component.text("  (keep looking at it)", NamedTextColor.DARK_GRAY)));
    }

    /** A button that fills a command in chat to finish typing (for the ones that need a value). */
    private static Component fill(String label, String cmd, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hover + "\nFills: " + cmd)));
    }

    /** One choice in the pick-one opening row: the active one is marked and inert, the rest are clickable. */
    private static Component openingButton(String label, String sub,
                                           InteractiveObjectManager.Obj.Opening current,
                                           InteractiveObjectManager.Obj.Opening mine,
                                           String hover) {
        if (current == mine) {
            return Component.text("[✔ " + label + "]", NamedTextColor.GREEN)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hover)));
        }
        return button("[" + label + "]", "/dm object " + sub, hover);
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
            if (toolDebounced(player)) return;
            view(player, event.getRightClicked());
        } else if (DmModeManager.TOOL_ADJUST.equals(tool)) {
            event.setCancelled(true);
            if (toolDebounced(player)) return;
            adjust(player, event.getRightClicked());
        } else if (DmModeManager.TOOL_SURPRISE.equals(tool)) {
            event.setCancelled(true);
            if (toolDebounced(player)) return;
            surprise(player, event.getRightClicked());
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

    /** The Surprise tool: toggle Surprised on someone already in the DM's fight, via /combat surprise. */
    private void surprise(Player dm, Entity target) {
        if (!DMManager.isDM(dm)) return;
        io.papermc.jkvttplugin.combat.CombatSession session = null;
        for (var s : io.papermc.jkvttplugin.combat.CombatSession.getAllSessions()) {
            if (dm.getUniqueId().equals(s.getDmId())) session = s;
        }
        if (session == null) {
            dm.sendActionBar(Component.text("Start a fight first (Start Combat), then mark who's surprised.", NamedTextColor.GRAY));
            return;
        }
        UUID id = target instanceof Player p ? p.getUniqueId()
                : target instanceof ArmorStand stand && DndEntityInstance.getByArmorStand(stand) != null
                        ? DndEntityInstance.getByArmorStand(stand).getInstanceId() : null;
        io.papermc.jkvttplugin.combat.Combatant c = id != null ? session.getCombatantById(id) : null;
        if (c == null) {
            dm.sendActionBar(Component.text("They're not in the fight. Add them first (Add / Remove tool).", NamedTextColor.GRAY));
            return;
        }
        dm.performCommand("combat surprise " + c.getDisplayName());
    }

    /** Open the Adjust menu (#175) on a player's character or a creature. False if it's neither. */
    private boolean adjust(Player dm, Entity target) {
        if (!DMManager.isDM(dm)) return false;
        UUID id = null;
        if (target instanceof Player p && ActiveCharacterTracker.getActiveCharacter(p) != null) id = p.getUniqueId();
        else if (target instanceof ArmorStand stand && DndEntityInstance.getByArmorStand(stand) != null) {
            id = DndEntityInstance.getByArmorStand(stand).getInstanceId();
        }
        if (id == null) return false;
        io.papermc.jkvttplugin.ui.menu.AdjustMenu.open(dm, id);
        return true;
    }

    /**
     * The View tool (#175): right-click → the quick look in chat (HP, AC and its changes, conditions,
     * notes); sneak + right-click → the full view (inventory, notes, sheet / stat block).
     */
    private void view(Player dm, Entity target) {
        if (!DMManager.isDM(dm)) return;
        io.papermc.jkvttplugin.combat.CombatTargets.Target t = null;
        if (target instanceof Player p && ActiveCharacterTracker.getActiveCharacter(p) != null) {
            t = io.papermc.jkvttplugin.combat.CombatTargets.forPlayer(p);
        } else if (target instanceof Player p) {
            dm.sendMessage(Component.text(p.getName() + " has no character.", NamedTextColor.GRAY));
            return;
        } else if (target instanceof ArmorStand stand && DndEntityInstance.getByArmorStand(stand) != null) {
            t = io.papermc.jkvttplugin.combat.CombatTargets.forEntity(DndEntityInstance.getByArmorStand(stand));
        }
        if (t == null) return;
        if (dm.isSneaking()) io.papermc.jkvttplugin.ui.menu.DmViewMenu.open(dm, t.combatant().getId());
        else ViewCommand.quick(dm, t);
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
