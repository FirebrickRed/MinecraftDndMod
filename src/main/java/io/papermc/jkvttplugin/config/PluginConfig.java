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

    /** What breaks a ritual channelled in combat when its caster takes damage (#156). */
    public enum RitualInterrupt { CONCENTRATION_CHECK, BREAK_ON_DAMAGE, NONE }

    private static RollMode rollMode = RollMode.PHYSICAL;
    private static int ritualCombatRounds = 10;
    private static RitualInterrupt ritualInterrupt = RitualInterrupt.CONCENTRATION_CHECK;
    private static int ritualInterruptDc = 0; // 0 = dynamic: max(10, half the damage taken)

    private PluginConfig() {}

    public static void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration cfg = plugin.getConfig();
        String mode = cfg.getString("rolls.mode", "physical");
        rollMode = "auto".equalsIgnoreCase(mode) ? RollMode.AUTO : RollMode.PHYSICAL;

        ritualCombatRounds = Math.max(1, cfg.getInt("rituals.combat_rounds", 10));
        ritualInterrupt = switch (cfg.getString("rituals.interrupt", "concentration_check").toLowerCase()) {
            case "break_on_damage" -> RitualInterrupt.BREAK_ON_DAMAGE;
            case "none" -> RitualInterrupt.NONE;
            default -> RitualInterrupt.CONCENTRATION_CHECK;
        };
        ritualInterruptDc = Math.max(0, cfg.getInt("rituals.interrupt_dc", 0));
    }

    public static RollMode getRollMode() { return rollMode; }

    /** True when the game should roll for the player if they don't provide their own result. */
    public static boolean isAutoRoll() { return rollMode == RollMode.AUTO; }

    /** Default number of the caster's turns a ritual takes to complete when channelled in combat (#156). */
    public static int getRitualCombatRounds() { return ritualCombatRounds; }
    public static RitualInterrupt getRitualInterrupt() { return ritualInterrupt; }
    /** Fixed concentration DC for ritual interruption, or 0 to use the dynamic max(10, half damage). */
    public static int getRitualInterruptDc() { return ritualInterruptDc; }
}
