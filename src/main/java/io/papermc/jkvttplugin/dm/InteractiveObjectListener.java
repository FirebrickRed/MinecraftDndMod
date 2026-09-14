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
        // Hidden: players don't perceive the interaction until the DM reveals it.
        if (o.hidden) return;

        String prettyBlock = ObjectCommand.pretty(block.getType().name());

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
        String where = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        String callCmd = "/dm check " + player.getName() + " skill ";
        Component msg = Component.text("🔒 " + player.getName() + " is trying to open a locked "
                        + prettyBlock + " (" + where + ") — ", NamedTextColor.GOLD)
                .append(Component.text("[call a check]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(callCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills /dm check " + player.getName()
                                + " skill … — pick the skill (e.g. sleight_of_hand, athletics) and a dc."))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (DMManager.isDM(p)) p.sendMessage(msg);
        }
    }
}
