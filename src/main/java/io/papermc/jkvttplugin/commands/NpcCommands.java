package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.CustomNPCs.Hadozee;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NpcCommands  implements CommandExecutor {
    private final JavaPlugin plugin;

    public NpcCommands(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // if (!(sender instanceof Player)) {
        //     sender.sendMessage("This command can only be used by players.");
        //     return true;
        // }

        Player player = (Player) sender;
        Location location = player.getLocation();
        new Hadozee(location);

        player.sendMessage("Hadozee NPC spawned at your location");
        return true;
    }
}
