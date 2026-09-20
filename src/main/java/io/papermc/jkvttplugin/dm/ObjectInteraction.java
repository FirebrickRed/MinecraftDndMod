package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * What a player gets for right-clicking a container (#185): one prompt, always the same two choices,
 * then the outcome of whichever they pick.
 *
 * <p>The prompt is deliberately generic — <b>[Open it]</b> and <b>[Ask for a check]</b>. An earlier
 * sketch offered "[Check for traps] / [Pick the lock] / [Force it]", but naming the options tells the
 * party what the DM prepared: a chest offering "Pick the lock" is a chest with a lock. It also can't
 * anticipate every intent (do I have a key? is it even locked? can I tip it over?). "Ask for a check"
 * pings the DM and lets the player say aloud what they're actually doing, which is how a table works
 * and is what {@code /dm check} was built for (#186).
 *
 * <p>For the prompt to hide anything it has to appear on <em>every</em> container, annotated or not —
 * see {@link PluginConfig.InteractionPrompt}. Buttons run server-side callbacks rather than commands,
 * so there's nothing for a player to type, replay from chat history, or aim at a chest across the map.
 */
public final class ObjectInteraction {

    private ObjectInteraction() {}

    /** A button is good for one click, and expires well inside a session. */
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(30))
            .build();

    /** True when this block should offer the prompt instead of resolving on the click itself. */
    public static boolean shouldPrompt(Block block, InteractiveObjectManager.Obj o) {
        return switch (PluginConfig.getInteractionPrompt()) {
            case OFF -> false;
            case ANNOTATED_ONLY -> o != null;
            // Annotated non-containers (a false wall, a statue) prompt too, or they'd stand out by
            // being the only blocks that don't.
            case ALL_CONTAINERS -> o != null || isContainer(block);
        };
    }

    public static boolean isContainer(Block block) {
        return block.getState() instanceof org.bukkit.block.Container;
    }

    /** The one prompt every container gives, whatever is or isn't hiding in it. */
    public static void prompt(Player player, Block block) {
        Location loc = block.getLocation();
        String name = ObjectCommand.pretty(block.getType().name());

        Component buttons = Component.text("  ")
                .append(button("[Open it]", "Just open it and find out",
                        audience -> open(player, loc)))
                .append(Component.text("  "))
                .append(button("[Ask for a check]", "Tell the DM you want to try something first — "
                                + "search it, check the lock, use a key, anything",
                        audience -> askForCheck(player, loc)));

        player.sendMessage(Component.text("The " + name + " is closed.", NamedTextColor.GRAY));
        player.sendMessage(buttons);
    }

    /** Player chose to just open it. This is where a trap gets its chance. */
    private static void open(Player player, Location loc) {
        Block block = loc.getBlock();
        String name = ObjectCommand.pretty(block.getType().name());
        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(loc);

        if (o != null && o.hasArmedTrap()) {
            springTrap(player, block, o);
            return; // the trap is the whole interaction; opening it is a second, separate try
        }
        if (o != null && !o.description.isEmpty()) {
            player.sendMessage(Component.text("You see: " + o.description, NamedTextColor.GRAY));
        }
        if (o != null) {
            switch (o.opening) {
                case LOCKED -> {
                    player.sendMessage(Component.text("🔒 The " + name + " is locked.", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Tell the DM how you'd like to get it open.", NamedTextColor.GRAY));
                    notifyLocked(player, name, loc);
                    return;
                }
                case SEALED -> {
                    if (o.description.isEmpty()) {
                        player.sendMessage(Component.text("You see a " + name + ". There's nothing here for you.", NamedTextColor.GRAY));
                    }
                    return;
                }
                case OPENS -> { /* fall through to the open below */ }
            }
        }

        if (isContainer(block)) {
            // The right-click was cancelled to show the prompt, so open it ourselves.
            player.openInventory(((org.bukkit.block.Container) block.getState()).getInventory());
        } else if (o != null && !o.loot.isEmpty()) {
            player.sendMessage(Component.text("You find something. The DM will hand it over.", NamedTextColor.GRAY));
            notifyLoot(player, name, loc);
        } else if (o == null || o.description.isEmpty()) {
            player.sendMessage(Component.text("Nothing happens.", NamedTextColor.GRAY));
        }
    }

    /**
     * Player wants to try something first. The game doesn't guess what — the player says it at the
     * table and the DM calls whatever check fits, which covers traps, locks, keys and the things
     * nobody thought of.
     */
    private static void askForCheck(Player player, Location loc) {
        String name = ObjectCommand.pretty(loc.getBlock().getType().name());
        player.sendMessage(Component.text("You hold off, and tell the DM what you want to try.", NamedTextColor.GRAY));

        Component msg = Component.text("🎲 " + player.getName() + " wants to try something with the "
                        + name + " ", NamedTextColor.AQUA)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(" — ", NamedTextColor.AQUA))
                .append(Component.text("[call a check]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/dm check " + player.getName() + " skill "))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Ask them what they're doing, then finish the command — e.g. "
                                + "perception (searching), investigation (how it's put together), "
                                + "sleight_of_hand (the lock), athletics (force it)."))));
        toDms(msg);
    }

    /**
     * The trap goes off. It auto-disarms, so a party of four don't each eat it on their own click —
     * {@code /dm object arm} puts it back if the DM wants a reset.
     *
     * <p>The save and the damage are both offered to the DM as filled-in commands rather than rolled
     * here. That's deliberate for now: the game not taking players' d20s is a standing rule in this
     * codebase, and non-damage traps (a cage drops, an alarm sounds) have no automatic form at all.
     * Auto-applying damage on a failed save is tracked separately.
     */
    private static void springTrap(Player player, Block block, InteractiveObjectManager.Obj o) {
        o.disarmed = true;
        InteractiveObjectManager.save();

        Location loc = block.getLocation();
        String name = ObjectCommand.pretty(block.getType().name());
        String save = o.trapSave.isEmpty() ? "dexterity" : o.trapSave;
        String dc = o.trapDc > 0 ? " dc " + o.trapDc : " ";

        player.sendMessage(Component.text("You touch the " + name + " — something clicks.", NamedTextColor.RED));

        Component header = Component.text("🪤 " + player.getName() + " opened the trapped " + name + " ", NamedTextColor.RED)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(" — it fires " + o.trapDamage + ", " + save + " save"
                        + (o.trapDc > 0 ? " (DC " + o.trapDc + ")" : "") + ". Now disarmed.", NamedTextColor.RED));
        Component buttons = Component.text("  ")
                .append(dmButton("[Call the save]", "/dm check " + player.getName() + " save " + save + dc,
                        "They roll it; you get the result privately"))
                .append(Component.text(" "))
                .append(dmButton("[Apply damage]", "/dm hp " + player.getName() + " damage " + o.trapDamage + " type ",
                        "Fills the damage command — add a type (piercing, fire, …) and Enter"));
        toDms(header);
        toDms(buttons);
    }

    private static void notifyLocked(Player player, String name, Location loc) {
        toDms(Component.text("🔒 " + player.getName() + " is trying to open a locked " + name + " ", NamedTextColor.GOLD)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(" — ", NamedTextColor.GOLD))
                .append(dmButton("[call a check]", "/dm check " + player.getName() + " skill ",
                        "Pick the skill they're using and a dc")));
    }

    private static void notifyLoot(Player player, String name, Location loc) {
        toDms(Component.text("📦 " + player.getName() + " opened the " + name + " ", NamedTextColor.GOLD)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(" — ", NamedTextColor.GOLD))
                .append(dmButton("[Give the loot]", "/dm object give " + player.getName(),
                        "Look at the block first — this hands over everything on it")));
    }

    private static Component button(String label, String hover, ClickCallback<net.kyori.adventure.audience.Audience> action) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.callback(action, ONCE))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static Component dmButton(String label, String cmd, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static void toDms(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (DMManager.isDM(p)) p.sendMessage(msg);
        }
    }
}
