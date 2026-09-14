package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;

/**
 * Turns a player's right-click on a DM-annotated block (#185) into a DM-first interaction: the player
 * sees the object (and that it's locked), and the DM is notified with a [call a check] button. Hidden
 * objects don't respond to players until the DM reveals them.
 */
public class InteractiveObjectListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // main hand only (avoid double-fire)
        Block block = event.getClickedBlock();
        if (block == null) return;

        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
        if (o == null) return;

        Player player = event.getPlayer();
        // DMs annotate/inspect via /dm object; let their clicks fall through to normal behavior.
        if (DMManager.isDM(player)) return;

        boolean armedTrap = o.trapped && !o.disarmed;
        // Hidden objects are inert to players — EXCEPT a live trap, which a blundering player springs.
        if (o.hidden && !armedTrap) return;

        String prettyBlock = ObjectCommand.pretty(block.getType().name());

        if (armedTrap) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You reach toward the " + prettyBlock + "…", NamedTextColor.GRAY));
            notifyTrap(player, prettyBlock, block.getLocation(), o);
            return;
        }

        if (o.locked) {
            event.setCancelled(true); // no vanilla open — it's locked
            player.sendMessage(Component.text("🔒 You see a " + prettyBlock
                    + (o.description.isEmpty() ? "" : " — " + o.description) + ". It's locked.", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Tell the DM how you'd like to open it.", NamedTextColor.GRAY));
            notifyDms(player, prettyBlock, block.getLocation());
        } else if (!o.description.isEmpty()) {
            // Not locked — just flavor; let it open normally, but share what they notice.
            player.sendMessage(Component.text("You see: " + o.description, NamedTextColor.GRAY));
        }
    }

    /** Tell every online DM that a player is at a locked object, with a button to call a check. */
    private void notifyDms(Player player, String prettyBlock, Location loc) {
        String callCmd = "/dm check " + player.getName() + " skill ";
        Component msg = Component.text("🔒 " + player.getName() + " is trying to open a locked "
                        + prettyBlock + " ", NamedTextColor.GOLD)
                .append(clickableCoords(loc))
                .append(Component.text(" — ", NamedTextColor.GOLD))
                .append(Component.text("[call a check]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(callCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills /dm check " + player.getName()
                                + " skill … — pick the skill (e.g. sleight_of_hand, athletics) and a dc."))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (DMManager.isDM(p)) p.sendMessage(msg);
        }
    }

    /** A clickable "(x, y, z)" that teleports the DM there. */
    static Component clickableCoords(Location loc) {
        String w = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return Component.text("(" + x + ", " + y + ", " + z + ")", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/dm tp " + w + " " + x + " " + y + " " + z))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport here")));
    }

    /** Tell every online DM that a player sprang a live trap, with spot / disarm / trigger buttons. */
    private void notifyTrap(Player player, String prettyBlock, Location loc, InteractiveObjectManager.Obj o) {
        String dc = o.trapDc > 0 ? " dc " + o.trapDc : " ";
        String save = o.trapSave.isEmpty() ? "dexterity" : o.trapSave;
        Component header = Component.text("🪤 " + player.getName() + " is at a trapped " + prettyBlock + " ", NamedTextColor.GOLD)
                .append(clickableCoords(loc))
                .append(Component.text(" — fires " + o.trapDamage + " on a failed " + save + " save"
                        + (o.trapDc > 0 ? " (DC " + o.trapDc + ")" : "") + ".", NamedTextColor.GOLD));
        Component buttons = Component.text("  ", NamedTextColor.GRAY)
                .append(trapButton("[Perception]", "/dm check " + player.getName() + " skill perception" + dc, "Did they notice the trap?"))
                .append(Component.text(" "))
                .append(trapButton("[Disarm]", "/dm check " + player.getName() + " skill sleight_of_hand" + dc, "Try to disarm it (then /dm object disarm on a success)"))
                .append(Component.text(" "))
                .append(trapButton("[Trigger]", "/dm check " + player.getName() + " save " + save + dc, "It goes off — call the save, then apply " + o.trapDamage + " on a fail"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (DMManager.isDM(p)) { p.sendMessage(header); p.sendMessage(buttons); }
        }
    }

    private static Component trapButton(String label, String cmd, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }
}
