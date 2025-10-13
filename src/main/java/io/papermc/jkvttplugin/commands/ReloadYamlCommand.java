package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.CustomNPCs.NpcManager;
import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.DataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadYamlCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage("Reloading YAML data from DMContent/...");

        try {
            // Reload all D&D content from DMContent/ folder
            DataManager dataManager = new DataManager(JkVttPlugin.getInstance());
            dataManager.loadAllData();

            sender.sendMessage("✓ Races reloaded");
            sender.sendMessage("✓ Classes reloaded");
            sender.sendMessage("✓ Backgrounds reloaded");
            sender.sendMessage("✓ Spells reloaded");
            sender.sendMessage("✓ Weapons reloaded");
            sender.sendMessage("✓ Armor reloaded");
            sender.sendMessage("✓ Items reloaded");

            // Reload NPCs (separate system)
            NpcManager.loadNpcs();
            sender.sendMessage("✓ NPCs reloaded");

            sender.sendMessage("All YAML files successfully reloaded!");
        } catch (Exception e) {
            sender.sendMessage("Error reloading YAML files: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
