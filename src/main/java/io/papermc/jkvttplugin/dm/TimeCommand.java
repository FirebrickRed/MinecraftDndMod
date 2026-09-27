package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /dm time [add <duration> | stop | start]}: the in-game clock. The DM-mode Time tool does
 * the same from the hotbar, and {@code /dm rest … <duration>} moves it when a rest ends. The time
 * is only shown to the DM.
 */
public class TimeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        World world = WorldTime.worldOf(sender);
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "" -> show(sender, world);
            case "add" -> {
                Integer minutes = args.length >= 2 ? WorldTime.parseMinutes(args[1]) : null;
                if (minutes == null) {
                    sender.sendMessage(Component.text("Usage: /dm time add <duration>, e.g. 10m, 1h, 1h30m, or -1h to go back.",
                            NamedTextColor.RED));
                    return true;
                }
                shift(sender, world, minutes, false);
            }
            case "stop", "start" -> {
                WorldTime.setRunning(world, sub.equals("start"));
                sender.sendMessage(Component.text(sub.equals("start")
                        ? "▶ The clock is running again (a Minecraft day is 20 real minutes)."
                        : "⏸ The clock is stopped: time only moves when you move it.", NamedTextColor.GOLD));
            }
            default -> sender.sendMessage(Component.text("Usage: /dm time [add <duration> | stop | start]", NamedTextColor.RED));
        }
        return true;
    }

    /**
     * Move the clock and tell the DM where it landed. {@code quiet} puts it in the action bar (the
     * hotbar tool, clicked repeatedly) rather than chat.
     */
    public static void shift(CommandSender sender, World world, int minutes, boolean quiet) {
        if (minutes == 0) return;
        String now = WorldTime.advance(world, minutes);
        Component msg = Component.text((minutes > 0 ? "⏩ +" : "⏪ −") + WorldTime.describe(minutes) + " → ", NamedTextColor.GOLD)
                .append(Component.text(now, NamedTextColor.YELLOW));
        if (quiet && sender instanceof Player p) p.sendActionBar(msg);
        else sender.sendMessage(msg);
    }

    private static void show(CommandSender sender, World world) {
        boolean running = WorldTime.isRunning(world);
        sender.sendMessage(Component.text("🕰 " + WorldTime.now(world), NamedTextColor.GOLD)
                .append(Component.text(running ? "  (clock running)" : "  (clock stopped)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  ")
                .append(button("[+10 min]", "/dm time add 10m", "Move time forward 10 minutes"))
                .append(Component.text(" "))
                .append(button("[+1 hour]", "/dm time add 1h", "Move time forward an hour"))
                .append(Component.text(" "))
                .append(button(running ? "[Stop the clock]" : "[Start the clock]", "/dm time " + (running ? "stop" : "start"),
                        running ? "Time only moves when you move it" : "Let the day run on its own again")));
    }

    private static Component button(String label, String cmd, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return filter(Stream.of("add", "stop", "start"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) return filter(Stream.of("10m", "30m", "1h", "8h", "-1h"), args[1]);
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String typed) {
        String t = typed.toLowerCase();
        return options.filter(o -> o.startsWith(t)).collect(Collectors.toList());
    }
}
