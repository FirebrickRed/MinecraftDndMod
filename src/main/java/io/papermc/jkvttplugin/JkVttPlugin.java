package io.papermc.jkvttplugin;

import io.papermc.jkvttplugin.character.CharacterSheetItemListener;
import io.papermc.jkvttplugin.commands.*;
import io.papermc.jkvttplugin.data.DataManager;
import io.papermc.jkvttplugin.listeners.NpcListener;
import io.papermc.jkvttplugin.listeners.PlanetListener;
import io.papermc.jkvttplugin.listeners.WeaponListener;
import io.papermc.jkvttplugin.ui.listener.MenuClickListener;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class JkVttPlugin extends JavaPlugin implements Listener {
    private static JkVttPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("D&D Plugin has been enabled!");

        ItemUtil.initialize(this);

        // Load Data
        DataManager dataManager = new DataManager(this);
        dataManager.loadAllData();

        // Listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new CharacterSheetItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlanetListener(), this);
        Bukkit.getPluginManager().registerEvents(new WeaponListener(), this);
        Bukkit.getPluginManager().registerEvents(new MenuClickListener(), this);

        // Commands
        EquipmentCommand equipmentCommand = new EquipmentCommand();
        this.getCommand("reloadyaml").setExecutor(new ReloadYamlCommand());
        this.getCommand("spawnhadozee").setExecutor(new NpcCommands(this));
        this.getCommand("rolldice").setExecutor(new RollDiceCommand());
        this.getCommand("takeequipment").setExecutor(equipmentCommand);
        this.getCommand("choosegear").setExecutor(equipmentCommand);
        this.getCommand("createcharacter").setExecutor(new CreateCharacterCommand());

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }

    @Override
    public void onDisable() {
        getLogger().info("D&D Plugin has been disabled!");
    }

    public static JkVttPlugin getInstance() {
        return instance;
    }
}