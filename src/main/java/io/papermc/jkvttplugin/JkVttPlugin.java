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
        io.papermc.jkvttplugin.config.PluginConfig.load(this);

        // Load Data
        DataManager dataManager = new DataManager(this);
        dataManager.loadAllData();

        CharacterSheetManager.initialize(this);
        DMPersistenceLoader.initialize(this);
        ShopPersistenceLoader.initialize(this);
        io.papermc.jkvttplugin.dm.DmModeManager.initialize(this);

        // Listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new CharacterSheetItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlanetListener(), this);
        Bukkit.getPluginManager().registerEvents(new WeaponListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.dm.DmModeListener(), this);
        Bukkit.getPluginManager().registerEvents(new MenuClickListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.listeners.CreationNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.listeners.AnvilNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.listeners.EntityChunkListener(), this);
        Bukkit.getPluginManager().registerEvents(new CharacterNameListener(), this);
        Bukkit.getPluginManager().registerEvents(new SpellFocusListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ArmorEquipListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SpellCastingMenuListener(), this);
        // EntityInteractionListener removed - use /dmentity info command instead
        Bukkit.getPluginManager().registerEvents(new StatBlockMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.combat.CombatListener(), this);
        Bukkit.getPluginManager().registerEvents(new io.papermc.jkvttplugin.loot.LootListener(), this);

        // Commands — five consolidated roots (Issue #122). The legacy per-action command
        // classes still exist and are delegated to from CharacterCommand / DmCommand; they
        // are simply no longer registered as their own top-level commands.
        CharacterCommand characterCommand = new CharacterCommand();
        this.getCommand("character").setExecutor(characterCommand);
        this.getCommand("character").setTabCompleter(characterCommand);

        this.getCommand("roll").setExecutor(new RollDiceCommand());

        CombatCommand combatCommand = new CombatCommand();
        this.getCommand("combat").setExecutor(combatCommand);
        this.getCommand("combat").setTabCompleter(combatCommand);

        DmEntityCommand dmEntityCommand = new DmEntityCommand();
        this.getCommand("dmentity").setExecutor(dmEntityCommand);
        this.getCommand("dmentity").setTabCompleter(dmEntityCommand);

        DmCommand dmCommand = new DmCommand();
        this.getCommand("dm").setExecutor(dmCommand);
        this.getCommand("dm").setTabCompleter(dmCommand);

        // Restore saved D&D entities in already-loaded chunks, next tick so worlds are ready.
        // Entities in other chunks re-hydrate via EntityChunkListener as they load (Issue #89).
        Bukkit.getScheduler().runTask(this, () -> {
            int restored = io.papermc.jkvttplugin.commands.DmEntityCommand.restoreAll();
            if (restored > 0) getLogger().info("Restored " + restored + " saved D&D entities.");
        });
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
                        .append(Component.text("/character create", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(
                Component.text("  • View Your Sheet: ", NamedTextColor.YELLOW)
                        .append(Component.text("/character view (or right-click the Character Sheet)", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(
                Component.text("  • Rest: ", NamedTextColor.YELLOW)
                        .append(Component.text("/character rest short|long", NamedTextColor.WHITE))
        );
        event.getPlayer().sendMessage(
                Component.text("  • Roll Dice: ", NamedTextColor.YELLOW)
                        .append(Component.text("/roll 2d6+3", NamedTextColor.WHITE))
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
        // Persist spawned entities' current state onto their armor stands so they survive a
        // restart with the right HP (Issue #89).
        for (io.papermc.jkvttplugin.data.model.DndEntityInstance inst
                : io.papermc.jkvttplugin.data.model.DndEntityInstance.getAll()) {
            try { inst.persist(); } catch (Exception e) {
                getLogger().warning("Failed to persist an entity on shutdown: " + e.getMessage());
            }
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