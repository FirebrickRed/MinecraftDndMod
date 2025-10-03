package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.CustomNPCs.NpcManager;
import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.util.DndSpell;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;

public class ReloadYamlCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage("Reloading YAML files...");

        File weaponsFile = new File(JkVttPlugin.getInstance().getDataFolder(), "weapons.yml");
        if (weaponsFile.exists()) {
            sender.sendMessage("Deleting old weapons.yml...");
            weaponsFile.delete();
        }

        JkVttPlugin.getInstance().saveResource("weapons.yml", true);
        sender.sendMessage("Saved latest weapons.yml!");

//        DndWeapon.loadWeapons();
        sender.sendMessage("Weapons reloaded!");

        File spellsFile = new File(JkVttPlugin.getInstance().getDataFolder(), "spells.yml");
        if (spellsFile.exists()) {
            sender.sendMessage("Deleting old spells.yml...");
            spellsFile.delete();
        }

        JkVttPlugin.getInstance().saveResource("spells.yml", true);
        sender.sendMessage("Saved latest spells.yml!");

        DndSpell.loadSpells();
        sender.sendMessage("Spells reloaded!");


        // Broken for now
        File npcFile = new File(JkVttPlugin.getInstance().getDataFolder(), "npcs.yml");
        if (npcFile.exists()) {
            sender.sendMessage("Deleting old npcs.yml...");
            npcFile.delete();
        }

        JkVttPlugin.getInstance().saveResource("npcs.yml", true);
        sender.sendMessage("Saved latest npcs.yml!");

        NpcManager.loadNpcs();
        sender.sendMessage("NPCs reloaded!");

        sender.sendMessage("YAML files successfully reloaded!");

        return true;
    }
}
