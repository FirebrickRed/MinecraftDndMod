package io.papermc.jkvttplugin.config;

import io.papermc.jkvttplugin.character.AbilityRollMethod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed access to config.yml (Issue #142). For now it only carries the roll mode, but this is the
 * home for the plugin's other configurables as they land (#104, #112, #92, ...).
 */
public final class PluginConfig {

    /** How a d20 resolves when the player supplies neither a roll nor a total. */
    public enum RollMode { PHYSICAL, AUTO }

    /** What breaks a ritual channelled in combat when its caster takes damage (#156). */
    public enum RitualInterrupt { CONCENTRATION_CHECK, BREAK_ON_DAMAGE, NONE }

    /**
     * Which blocks give a player the [Open it] / [Ask for a check] prompt (#185).
     *
     * <p>{@link #ALL_CONTAINERS} is the default because it's the only value that hides anything: if
     * only annotated blocks prompted, the prompt would itself announce "the DM set this one up".
     */
    public enum InteractionPrompt { ALL_CONTAINERS, ANNOTATED_ONLY, OFF }
    /** When a DM-graded thieves' tools check uses up the set (#210). */
    public enum ThievesToolsBreak { NEVER, ON_FAIL, ALWAYS }

    private static RollMode rollMode = RollMode.PHYSICAL;
    private static int ritualCombatRounds = 10;
    private static RitualInterrupt ritualInterrupt = RitualInterrupt.CONCENTRATION_CHECK;
    private static int ritualInterruptDc = 0; // 0 = dynamic: max(10, half the damage taken)
    private static List<AbilityRollMethod> abilityRollMethods = List.of(AbilityRollMethod.values());
    private static boolean abilityScoreCap20 = false; // false = house rule (racial bonuses may exceed 20)
    private static boolean trackAmmunition = true;    // bows consume arrows (#128)
    private static boolean trackThrownWeapons = true; // a thrown weapon leaves your hand (#192)
    private static boolean damageNeedsDmApproval = true; // a player's /combat damage waits for [Apply] (#175)
    private static InteractionPrompt interactionPrompt = InteractionPrompt.ALL_CONTAINERS; // #185
    private static ThievesToolsBreak thievesToolsBreak = ThievesToolsBreak.ON_FAIL; // #210, BG3-style default
    private static boolean annotationGlow = true;     // outline annotated blocks for the annotating DM
    private static int annotationGlowRadius = 24;

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

        // Ability roll-reference methods (#59). Absent key → offer all methods; present but empty →
        // the DM has turned the roll helper off. Unknown/duplicate entries are ignored.
        if (!cfg.contains("abilities.roll_methods")) {
            abilityRollMethods = List.of(AbilityRollMethod.values());
        } else {
            List<AbilityRollMethod> methods = new ArrayList<>();
            for (String key : cfg.getStringList("abilities.roll_methods")) {
                AbilityRollMethod m = AbilityRollMethod.fromKey(key);
                if (m != null && !methods.contains(m)) methods.add(m);
            }
            abilityRollMethods = methods; // may be empty = helper disabled
        }

        // Ability-score cap (#112). Default false = house rule: racial bonuses may push above 20.
        abilityScoreCap20 = cfg.getBoolean("abilities.cap_scores_at_20", false);

        // Ammunition tracking (#128). Default true — running dry is a real tactical beat; a DM who
        // does not want the bookkeeping turns it off.
        trackAmmunition = cfg.getBoolean("combat.track_ammunition", true);
        trackThrownWeapons = cfg.getBoolean("combat.track_thrown_weapons", true);
        // A player's /combat damage waits for the DM's [Apply] (#175). Default on: a playtest saw damage go
        // wrong around reactions, and a DM who trusts the flow turns it off.
        damageNeedsDmApproval = cfg.getBoolean("combat.damage_needs_dm_approval", true);

        // Interaction prompt (#185). Unknown values fall back to all_containers rather than
        // silently disabling the prompt, since "off" leaks more than a misspelling should cost.
        interactionPrompt = switch (cfg.getString("objects.interaction_prompt", "all_containers").toLowerCase()) {
            case "annotated_only" -> InteractionPrompt.ANNOTATED_ONLY;
            case "off" -> InteractionPrompt.OFF;
            default -> InteractionPrompt.ALL_CONTAINERS;
        };
        thievesToolsBreak = switch (cfg.getString("objects.thieves_tools_break", "on_fail").toLowerCase()) {
            case "never" -> ThievesToolsBreak.NEVER;
            case "always" -> ThievesToolsBreak.ALWAYS;
            default -> ThievesToolsBreak.ON_FAIL;
        };
        annotationGlow = cfg.getBoolean("objects.annotation_glow", true);
        annotationGlowRadius = Math.max(4, Math.min(64, cfg.getInt("objects.annotation_glow_radius", 24)));
    }

    /** Which blocks give players the [Open it] / [Ask for a check] prompt (#185). */
    public static InteractionPrompt getInteractionPrompt() { return interactionPrompt; }

    /** When a graded thieves' tools check breaks the set (#210). */
    public static ThievesToolsBreak getThievesToolsBreak() { return thievesToolsBreak; }

    /** True when annotated blocks are outlined for a DM holding the Annotate Object tool (#185). */
    public static boolean isAnnotationGlow() { return annotationGlow; }

    /** How far that outline reaches, in blocks. */
    public static int getAnnotationGlowRadius() { return annotationGlowRadius; }

    /** True when ranged weapons must spend ammunition to fire (#128). */
    public static boolean isTrackAmmunition() { return trackAmmunition; }

    /** True when a thrown weapon leaves the thrower's hand and lands in the world (#192). */
    public static boolean isTrackThrownWeapons() { return trackThrownWeapons; }

    /** Whether a player's /combat damage waits for the DM to approve it (#175). */
    public static boolean isDamageNeedsDmApproval() { return damageNeedsDmApproval; }

    public static RollMode getRollMode() { return rollMode; }

    /** True when the game should roll for the player if they don't provide their own result. */
    public static boolean isAutoRoll() { return rollMode == RollMode.AUTO; }

    /** Default number of the caster's turns a ritual takes to complete when channelled in combat (#156). */
    public static int getRitualCombatRounds() { return ritualCombatRounds; }
    public static RitualInterrupt getRitualInterrupt() { return ritualInterrupt; }
    /** Fixed concentration DC for ritual interruption, or 0 to use the dynamic max(10, half damage). */
    public static int getRitualInterruptDc() { return ritualInterruptDc; }

    /** Ability roll-reference methods to offer in creation (#59); empty means the helper is off. */
    public static List<AbilityRollMethod> getAbilityRollMethods() { return abilityRollMethods; }

    /** True to clamp final ability scores to 20 (RAW); false (default) lets racial bonuses exceed 20 (#112). */
    public static boolean isAbilityScoreCap20() { return abilityScoreCap20; }
}
