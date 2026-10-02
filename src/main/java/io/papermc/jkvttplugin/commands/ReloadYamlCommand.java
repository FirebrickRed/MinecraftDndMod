package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /dm reload}: reload DMContent, and say honestly how it went (#243). It used to print
 * "✓ … successfully reloaded" for every category whatever happened.
 *
 * <p>A YAML file with a syntax error loads nothing, so reloading with one would make everything it
 * defines vanish. Those are checked first, and the reload is refused, with the old content kept.
 * Anything else the loaders or the content check notice is a warning: the reload goes ahead and lists them.
 */
public class ReloadYamlCommand implements CommandExecutor {

    /** How many warnings fit in chat; the rest are in the console. */
    private static final int SHOWN = 12;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        DataManager dataManager = new DataManager(JkVttPlugin.getInstance());

        List<String> broken = dataManager.syntaxErrors();
        if (!broken.isEmpty()) {
            sender.sendMessage(Component.text("✗ Reload refused: " + broken.size() + " file(s) won't parse, and each would load nothing. "
                    + "Nothing changed; what's loaded stays loaded.", NamedTextColor.RED));
            for (String b : broken) sender.sendMessage(Component.text("   " + b, NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Fix them and /dm reload again.", NamedTextColor.GRAY));
            return true;
        }

        List<String> warnings;
        try {
            warnings = dataManager.loadAllData();
        } catch (Exception e) {
            sender.sendMessage(Component.text("✗ Reload failed part way: " + e + ". Some content may be missing: fix it and "
                    + "/dm reload again, or restart.", NamedTextColor.RED));
            JkVttPlugin.getInstance().getLogger().severe("Reload failed: " + e);
            e.printStackTrace();
            return true;
        }

        if (warnings.isEmpty()) {
            sender.sendMessage(Component.text("✓ DMContent reloaded: a clean load.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("⚠ DMContent reloaded with " + warnings.size() + " warning(s):", NamedTextColor.GOLD));
        for (String w : warnings.subList(0, Math.min(SHOWN, warnings.size()))) {
            sender.sendMessage(Component.text("   " + w, NamedTextColor.YELLOW));
        }
        if (warnings.size() > SHOWN) {
            sender.sendMessage(Component.text("   …and " + (warnings.size() - SHOWN) + " more in the server console.", NamedTextColor.GRAY));
        }
        return true;
    }
}
