package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.loader.SoundLoader;
import io.papermc.jkvttplugin.data.model.SoundCue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dm sound <board key | sound name> [everyone | here | <player>]} and {@code /dm sound stop}
 * (#16): the DM plays a sound live. A wolf howling in the distance, a door slamming, thunder. The same
 * as the Sound Board tool in DM mode.
 *
 * <ul>
 *   <li>{@code everyone} (the default): each player hears it where they stand.</li>
 *   <li>{@code here}: it comes from where the DM is standing, carrying its {@code range}, so players
 *       hear which way it came from, and anyone too far away doesn't hear it at all.</li>
 *   <li>{@code <player>}: only them (a whisper only one character hears).</li>
 * </ul>
 * These play on the "Ambient/Environment" slider.
 */
public class SoundCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player dm)) {
            sender.sendMessage(Component.text("Only a player can play sounds.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            dm.sendMessage(Component.text("Usage: /dm sound <" + String.join(" | ", SoundLoader.board().keySet())
                    + " | a sound name> [everyone | here | <player>]  ·  /dm sound stop", NamedTextColor.RED));
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            stopAll();
            dm.sendActionBar(Component.text("🔇 Sounds stopped.", NamedTextColor.GRAY));
            return true;
        }
        SoundCue cue = cueFor(args[0]);
        if (cue == null) {
            dm.sendMessage(Component.text("'" + args[0] + "' isn't on the Sound Board (Sounds.yml board:) or a sound name.", NamedTextColor.RED));
            return true;
        }
        String to = args.length > 1 ? args[1] : "everyone";
        if (to.equalsIgnoreCase("here")) {
            fromHere(dm, cue);
        } else if (to.equalsIgnoreCase("everyone") || to.equalsIgnoreCase("all")) {
            toEveryone(dm, cue);
        } else {
            Player p = Bukkit.getPlayerExact(to);
            if (p == null) { dm.sendMessage(Component.text("No player online called " + to + ".", NamedTextColor.RED)); return true; }
            p.playSound(p, cue.sound(), SoundCategory.AMBIENT, cue.volume(), cue.pitch());
            dm.sendActionBar(Component.text("♪ " + cue.name() + " → " + p.getName(), NamedTextColor.GRAY));
        }
        return true;
    }

    /** A board entry by key, or any valid sound name played as-is. */
    public static SoundCue cueFor(String keyOrSound) {
        SoundCue board = SoundLoader.board().get(keyOrSound.toLowerCase());
        if (board != null) return board;
        return SoundCue.isValidName(keyOrSound.toLowerCase()) && (keyOrSound.contains(":") || keyOrSound.contains("."))
                ? new SoundCue(keyOrSound.toLowerCase(), 1f, 1f, keyOrSound, 0, 0) : null;
    }

    /** Every online player hears it where they stand (and the DM, so they know it played). */
    public static void toEveryone(Player dm, SoundCue cue) {
        if (!PluginConfig.isSoundsEnabled()) { dm.sendActionBar(Component.text("Sounds are off (sounds.enabled in config.yml).", NamedTextColor.GRAY)); return; }
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p, cue.sound(), SoundCategory.AMBIENT, cue.volume(), cue.pitch());
        dm.sendActionBar(Component.text("♪ " + cue.name() + " → everyone", NamedTextColor.GRAY));
    }

    /** It comes from where the DM stands, as far as its range carries. */
    public static void fromHere(Player dm, SoundCue cue) {
        if (!PluginConfig.isSoundsEnabled()) { dm.sendActionBar(Component.text("Sounds are off (sounds.enabled in config.yml).", NamedTextColor.GRAY)); return; }
        dm.getWorld().playSound(dm.getLocation(), cue.sound(), SoundCategory.AMBIENT, cue.effectiveVolume(), cue.pitch());
        int range = Math.max(16, (int) (cue.effectiveVolume() * 16));
        dm.sendActionBar(Component.text("♪ " + cue.name() + " from here (carries about " + range + " blocks)", NamedTextColor.GRAY));
    }

    /** Stop every Sound Board sound for everyone (Ambient/Environment). Combat music keeps playing. */
    public static void stopAll() {
        for (Player p : Bukkit.getOnlinePlayers()) p.stopSound(SoundCategory.AMBIENT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SoundLoader.board().keySet());
            out.add("stop");
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("stop")) {
            out.add("everyone");
            out.add("here");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(typed));
        return out;
    }
}
