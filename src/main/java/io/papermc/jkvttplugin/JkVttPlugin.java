package io.papermc.jkvttplugin;

import io.papermc.jkvttplugin.character.CharacterSheetItemListener;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.combat.CombatCommand;
import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.commands.*;
import io.papermc.jkvttplugin.data.DataManager;
import io.papermc.jkvttplugin.dm.DmCommand;
import io.papermc.jkvttplugin.dm.DMPersistenceLoader;
import io.papermc.jkvttplugin.listeners.*;
import io.papermc.jkvttplugin.shop.ShopListener;
import io.papermc.jkvttplugin.shop.ShopPersistenceLoader;
import io.papermc.jkvttplugin.ui.listener.MenuClickListener;
import io.papermc.jkvttplugin.ui.listener.SpellCastingMenuListener;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

        CharacterSheetManager.initialize(this);
        DMPersistenceLoader.initialize(this);
        ShopPersistenceLoader.initialize(this);

        // Listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new CharacterSheetItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlanetListener(), this);
        Bukkit.getPluginManager().registerEvents(new WeaponListener(), this);
        Bukkit.getPluginManager().registerEvents(new MenuClickListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.listeners.CreationNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.listeners.AnvilNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new CharacterNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new SpellFocusListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ArmorEquipListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SpellCastingMenuListener(), this);
        // EntityInteractionListener removed - use /dmentity info command instead
        Bukkit.getPluginManager().registerEvents(new StatBlockMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.combat.CombatListener(), this);

        // Commands
        this.getCommand("reloadyaml").setExecutor(new ReloadYamlCommand());
        this.getCommand("rolldice").setExecutor(new RollDiceCommand());
        this.getCommand("createcharacter").setExecutor(new CreateCharacterCommand());
        this.getCommand("closesheet").setExecutor(new CloseSheetCommand());

        // Rest Commands
        this.getCommand("shortrest").setExecutor(new ShortRestCommand());
        this.getCommand("longrest").setExecutor(new LongRestCommand());

        // DM Commands
        RestCommand restCommand = new RestCommand();
        this.getCommand("rest").setExecutor(restCommand);
        this.getCommand("rest").setTabCompleter(restCommand);

        RestoreResourceCommand restoreResourceCommand = new RestoreResourceCommand();
        this.getCommand("restoreresource").setExecutor(restoreResourceCommand);
        this.getCommand("restoreresource").setTabCompleter(restoreResourceCommand);

        ConsumeResourceCommand consumeResourceCommand = new ConsumeResourceCommand();
        this.getCommand("consumeresource").setExecutor(consumeResourceCommand);
        this.getCommand("consumeresource").setTabCompleter(consumeResourceCommand);

        DmEntityCommand dmEntityCommand = new DmEntityCommand();
        this.getCommand("dmentity").setExecutor(dmEntityCommand);
        this.getCommand("dmentity").setTabCompleter(dmEntityCommand);

        DmGiveCommand dmGiveCommand = new DmGiveCommand();
        this.getCommand("dmgive").setExecutor(dmGiveCommand);
        this.getCommand("dmgive").setTabCompleter(dmGiveCommand);

        DmCommand dmCommand = new DmCommand();
        this.getCommand("dm").setExecutor(dmCommand);
        this.getCommand("dm").setTabCompleter(dmCommand);

        // Combat Commands (Issue #97)
        CombatCommand combatCommand = new CombatCommand();
        this.getCommand("combat").setExecutor(combatCommand);
        this.getCommand("combat").setTabCompleter(combatCommand);

        // Consolidated character command (Issue #122): /character (alias /char).
        // The legacy top-level commands below stay registered as deprecated aliases
        // during the transition and will be retired once command consolidation lands.
        CharacterCommand characterCommand = new CharacterCommand();
        this.getCommand("character").setExecutor(characterCommand);
        this.getCommand("character").setTabCompleter(characterCommand);

        // Character sheet commands (Issue #47)
        ViewSheetCommand viewSheetCommand = new ViewSheetCommand();
        this.getCommand("viewsheet").setExecutor(viewSheetCommand);
        this.getCommand("viewsheet").setTabCompleter(viewSheetCommand);

        GiveSheetCommand giveSheetCommand = new GiveSheetCommand();
        this.getCommand("givesheet").setExecutor(giveSheetCommand);
        this.getCommand("givesheet").setTabCompleter(giveSheetCommand);

        // DM-prompted checks with advantage/disadvantage (Issue #61)
        CheckCommand checkCommand = new CheckCommand();
        this.getCommand("check").setExecutor(checkCommand);
        this.getCommand("check").setTabCompleter(checkCommand);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Alpha testing welcome message
        event.getPlayer().sendMessage(Component.empty());
        event.getPlayer().sendMessage(
                Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
        );
        event.getPlayer().sendMessage(
                Component.text("Welcome to D&D VTT Alpha Testing!", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        event.getPlayer().sendMessage(Component.empty());
        event.getPlayer().sendMessage(
                Component.text("Getting Started:", NamedTextColor.AQUA, TextDecoration.BOLD)
        );
        event.getPlayer().sendMessage(
                Component.text("  • Create a Character: ", NamedTextColor.YELLOW)
                        .append(Component.text("/createcharacter", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(
                Component.text("  • View Your Sheet: ", NamedTextColor.YELLOW)
                        .append(Component.text("Right-click Character Sheet item", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(
                Component.text("  • Rest Commands: ", NamedTextColor.YELLOW)
                        .append(Component.text("/shortrest or /longrest", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(Component.empty());
        event.getPlayer().sendMessage(
                Component.text("⚠ Known Issues:", NamedTextColor.RED, TextDecoration.BOLD)
        );
        event.getPlayer().sendMessage(
                Component.text("  • Some items may show as \"Item Not Found\"", NamedTextColor.GRAY)
        );
        event.getPlayer().sendMessage(
                Component.text("  • Icons and visuals are not finalized", NamedTextColor.GRAY)
        );
        event.getPlayer().sendMessage(
                Component.text("  • Characters may be wiped in future updates", NamedTextColor.GRAY)
        );
        event.getPlayer().sendMessage(Component.empty());
        event.getPlayer().sendMessage(
                Component.text("Found a bug or have feedback?", NamedTextColor.YELLOW, TextDecoration.BOLD)
        );
        event.getPlayer().sendMessage(
                Component.text("  → Message on Discord!", NamedTextColor.GREEN)
        );
        event.getPlayer().sendMessage(
                Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
        );
        event.getPlayer().sendMessage(Component.empty());
    }

    @Override
    public void onDisable() {
        // Tidy shutdown: persist characters (so mid-combat HP isn't lost) and cleanly
        // end any active combat (clears glow, scoreboards, prone). This is a safety net,
        // not full crash recovery — see Issue #105.
        try {
            CharacterPersistenceLoader.saveAllCharacters();
        } catch (Exception e) {
            getLogger().warning("Failed to save characters on shutdown: " + e.getMessage());
        }
        for (CombatSession session : new java.util.ArrayList<>(CombatSession.getAllSessions())) {
            try {
                session.endCombat();
            } catch (Exception e) {
                getLogger().warning("Failed to end a combat session cleanly on shutdown: " + e.getMessage());
            }
        }
        // Despawn tracked entities so they don't become orphaned armor stands the plugin
        // can no longer manage after restart (stopgap until entity persistence, Issue #89).
        try {
            DmEntityCommand.despawnAllOnShutdown();
        } catch (Exception e) {
            getLogger().warning("Failed to despawn entities on shutdown: " + e.getMessage());
        }
        getLogger().info("D&D Plugin has been disabled!");
    }

    public static JkVttPlugin getInstance() {
        return instance;
    }
}