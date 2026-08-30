package io.papermc.jkvttplugin.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed access to config.yml (Issue #142). For now it only carries the roll mode, but this is the
 * home for the plugin's other configurables as they land (#104, #112, #92, ...).
 */
public final class PluginConfig {

    /** How a d20 resolves when the player supplies neither a roll nor a total. */
    public enum RollMode { PHYSICAL, AUTO }

    private static RollMode rollMode = RollMode.PHYSICAL;

    private PluginConfig() {}

    public static void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration cfg = plugin.getConfig();
        String mode = cfg.getString("rolls.mode", "physical");
        rollMode = "auto".equalsIgnoreCase(mode) ? RollMode.AUTO : RollMode.PHYSICAL;
    }

    public static RollMode getRollMode() { return rollMode; }

    /** True when the game should roll for the player if they don't provide their own result. */
    public static boolean isAutoRoll() { return rollMode == RollMode.AUTO; }
}
