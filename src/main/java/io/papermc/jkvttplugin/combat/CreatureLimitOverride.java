package io.papermc.jkvttplugin.combat;

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

/**
 * The DM's "use it anyway" for a creature's spent ability or empty quiver (#256, #257). The game
 * counts; the DM rules. One override covers one attack: it's still there while the roll prompt is
 * answered, and gone once the attack goes through.
 */
public final class CreatureLimitOverride {

    private CreatureLimitOverride() {}

    private static final Map<UUID, String> allowed = new HashMap<>(); // creature → attack name (lower case)
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    public static boolean has(UUID creature, String attackName) {
        return attackName != null && attackName.equalsIgnoreCase(allowed.get(creature));
    }

    /** The attack went through: the override is used up. */
    public static void spend(UUID creature) {
        allowed.remove(creature);
    }

    /** [Use it anyway]: allows this one attack and hands the command back to fill in chat. */
    public static Component button(UUID creature, String attackName, String retry) {
        return Component.text("[Use it anyway]", NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("DM: allow it this once")))
                .clickEvent(ClickEvent.callback(a -> {
                    allowed.put(creature, attackName.toLowerCase());
                    if (!(a instanceof Player dm)) return;
                    Component line = Component.text("Allowed this once. ", NamedTextColor.GREEN);
                    if (retry != null && !retry.isBlank()) {
                        line = line.append(Component.text("[go again]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand(retry + " "))
                                .hoverEvent(HoverEvent.showText(Component.text("Fills: " + retry))));
                    }
                    dm.sendMessage(line);
                }, ONCE));
    }
}
