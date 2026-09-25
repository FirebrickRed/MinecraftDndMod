package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * <b>The</b> way a player asks the DM for something ("let my Fire Bolt happen", "let it reach").
 * Players spam commands and click everything, so each player has at most one open request: the
 * same one again isn't re-sent to the DM, and a different one replaces it (the old buttons then
 * say so instead of acting).
 *
 * <p>Build the DM's buttons with {@link #button}, then {@link #send} the message.
 */
public final class DmRequests {

    private DmRequests() {}

    private record Request(String label, int id, long at) {}

    private static final Map<UUID, Request> open = new HashMap<>();
    private static int nextId = 1;
    private static final long REPEAT_MS = Duration.ofMinutes(2).toMillis();
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    /** False (after telling them) when no DM is online to ask. */
    public static boolean anyDmOnline(Player from) {
        if (!DMManager.getOnlineDMs().isEmpty()) return true;
        from.sendMessage(Component.text("No DM is online to ask.", NamedTextColor.RED));
        return false;
    }

    /**
     * Send a request to every online DM, unless this player already has the same one open.
     *
     * @param label    what's being asked, short ("Cure Wounds out of reach"); a repeat is matched on it
     * @param toPlayer what the player is told ("Asked the DM whether it reaches.")
     * @param toDms    the DM's message, with buttons built by {@link #button} just before this call
     */
    public static void send(Player from, String label, String toPlayer, Component toDms) {
        Request mine = open.get(from.getUniqueId());
        if (mine != null && mine.label().equals(label) && System.currentTimeMillis() - mine.at() < REPEAT_MS) {
            from.sendMessage(Component.text("Still waiting on the DM for " + label + ".", NamedTextColor.GRAY));
            return;
        }
        if (mine != null) {
            from.sendMessage(Component.text("(That replaces your earlier request: " + mine.label() + ".)", NamedTextColor.DARK_GRAY));
        }
        open.put(from.getUniqueId(), new Request(label, nextId++, System.currentTimeMillis()));
        from.sendMessage(Component.text(toPlayer, NamedTextColor.GRAY));
        for (Player dm : DMManager.getOnlineDMs()) dm.sendMessage(toDms);
    }

    /** A DM button on a player's request: acts only while that request is still the open one. */
    public static Component button(UUID playerId, String text, NamedTextColor color, String hover, Consumer<Player> onClick) {
        // The id this button belongs to is the one about to be issued (buttons are built just before send).
        final int mine = nextId;
        return Component.text(text, color, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.callback(a -> {
                    if (!(a instanceof Player dm)) return;
                    Request current = open.get(playerId);
                    if (current == null || current.id() != mine) {
                        dm.sendMessage(Component.text("That request was already answered or replaced by a newer one.", NamedTextColor.GRAY));
                        return;
                    }
                    open.remove(playerId);
                    onClick.accept(dm);
                }, ONCE));
    }
}
