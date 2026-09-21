package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * The one place a d20 action (attack, check, save, death save, loot) turns inputs into a result
 * (Issue #142). Three modes, spelled as bare keywords since #183:
 *   - {@code total <n>}       → used as-is, nothing added.
 *   - {@code manualRoll <n>}  → that die + the modifier.
 *   - {@code autoRoll}        → the game rolls the die (advantage applied automatically).
 *   - none of the above       → PHYSICAL config: the caller must prompt for a die (returns
 *                               {@code null}); AUTO config: the game rolls the die itself.
 *
 * <p>The old {@code --roll} / {@code --total} / {@code --type} flags are gone. They used to be
 * ignored silently, which let three prompts ship a command whose roll argument was quietly
 * discarded — see {@link #parseInput(String[], CommandSender)}, which now rejects them loudly.
 */
public final class RollService {

    private RollService() {}

    /** The outcome of a resolved d20 action. {@code d20} is -1 in provided-total mode. */
    public record RollResult(int d20, int total, boolean providedTotal, boolean nat20, boolean nat1, String breakdown) {}

    /** Does this to-hit roll beat the target's AC? Nat 20 always hits, nat 1 always misses (#154). */
    public static boolean hits(RollResult r, int targetAC) {
        if (r.providedTotal()) return r.total() >= targetAC;
        return r.nat20() || (!r.nat1() && r.total() >= targetAC);
    }

    /**
     * How a d20 action gets its die, parsed from the command (#183):
     *   - {@code autoRoll}            → the game rolls it (applying any advantage).
     *   - {@code manualRoll <n>}      → you rolled n; the game adds your modifiers.
     *   - {@code total <n>}           → a final total; nothing is added.
     * {@code providedRoll}/{@code providedTotal} are null unless supplied; {@code forceAuto} means the
     * player explicitly asked the game to roll (so it rolls even in physical-dice mode).
     */
    public record RollInput(Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        public boolean isEmpty() { return providedRoll == null && providedTotal == null && !forceAuto; }
    }

    /** The bare words that supply a roll, so positional parsing knows to stop at them. */
    public static boolean isRollKeyword(String token) {
        if (token == null) return false;
        String t = token.toLowerCase();
        return t.equals("autoroll") || t.equals("manualroll") || t.equals("total");
    }

    /**
     * Flags still parsed somewhere in {@code /combat}. Anything else starting with {@code --} is
     * dead syntax — either a stale prompt or a player's muscle memory from before #183.
     */
    private static final java.util.Set<String> LIVE_FLAGS = java.util.Set.of("--force", "--hidden", "--radius");

    /** Parse without diagnostics. Prefer the {@code CommandSender} overload so stale syntax is caught. */
    public static RollInput parseInput(String[] args) {
        return parseInput(args, null);
    }

    /**
     * As {@link #parseInput(String[])}, but tells {@code who} when the input carries removed
     * syntax instead of dropping it on the floor.
     *
     * <p>Only unknown {@code --flags} are reported. Supplying no roll keyword at all is a
     * perfectly normal path — the caller prompts for a roll mode — so that isn't an error, and
     * {@code /combat damage <target> <amount>} legitimately takes a bare number.
     */
    public static RollInput parseInput(String[] args, CommandSender who) {
        Integer providedRoll = null, providedTotal = null;
        boolean forceAuto = false;
        java.util.List<String> stale = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.startsWith("--") && !LIVE_FLAGS.contains(a)) stale.add(args[i]);
            String next = (i + 1 < args.length) ? args[i + 1] : null;
            switch (a) {
                case "autoroll" -> forceAuto = true; // optional trailing dice (e.g. 2d20) is ignored — advantage is auto-detected
                case "manualroll" -> {
                    if (next != null) {
                        if (next.toLowerCase().contains("d")) forceAuto = true; // a dice expression → let the game roll
                        else { try { providedRoll = Integer.parseInt(next.trim()); } catch (NumberFormatException ignored) {} }
                    }
                }
                case "total" -> {
                    if (next != null) { try { providedTotal = Integer.parseInt(next.trim()); } catch (NumberFormatException ignored) {} }
                }
                default -> {}
            }
        }
        if (who != null && !stale.isEmpty()) warnStaleSyntax(who, stale);
        return new RollInput(providedRoll, providedTotal, forceAuto);
    }

    /**
     * Say plainly that the input used syntax that no longer exists, rather than silently ignoring
     * it and resolving the roll some other way. If this fires from a prompt the player *clicked*,
     * the prompt is the bug — so the message says so and asks them to report it.
     */
    private static void warnStaleSyntax(CommandSender who, java.util.List<String> stale) {
        who.sendMessage(Component.text("⚠ Removed syntax: " + String.join(", ", stale), NamedTextColor.RED));
        who.sendMessage(Component.text("Rolls now use bare keywords — ", NamedTextColor.YELLOW)
                .append(Component.text("manualRoll <n>", NamedTextColor.WHITE))
                .append(Component.text(" (you rolled it) · ", NamedTextColor.YELLOW))
                .append(Component.text("autoRoll", NamedTextColor.WHITE))
                .append(Component.text(" (game rolls) · ", NamedTextColor.YELLOW))
                .append(Component.text("total <n>", NamedTextColor.WHITE))
                .append(Component.text(" (final number).", NamedTextColor.YELLOW)));
        who.sendMessage(Component.text("Your roll was NOT applied — run the command again. "
                + "If you got this from clicking a prompt, that prompt is out of date: please report it.",
                NamedTextColor.GRAY));
    }

    /**
     * @param providedRoll  the player's physically-rolled d20, or null
     * @param providedTotal a final total the player computed themselves, or null
     * @param modifier      the bonus to add to a rolled die
     * @param modLabel      how the modifier reads in the breakdown, e.g. "+5[ToHit]" or "+3[STR]"
     * @return the result, or {@code null} when the config is PHYSICAL and no roll/total was given —
     *         the caller should then prompt the player to roll rather than resolving.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, false);
    }

    /**
     * As {@link #resolve(Integer, Integer, int, String)}, but if {@code rerollNat1} is set and the d20
     * comes up a natural 1, it's rerolled once and the new die stands (Halfling Lucky). The reroll is
     * shown in the breakdown so it's transparent. A provided total is never rerolled (no die to see).
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, rerollNat1, Advantage.NONE);
    }

    /**
     * As above, applying advantage/disadvantage. When the game auto-rolls, this rolls TWO d20 and
     * keeps the higher (advantage) or lower (disadvantage), showing both dice. A single provided roll
     * is used as-is (the player already accounted for adv/dis when they physically rolled), and a
     * provided total is likewise trusted — the caller is expected to have reminded the player.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1, Advantage advantage) {
        return resolve(providedRoll, providedTotal, modifier, modLabel, rerollNat1, advantage, false);
    }

    /** Resolve a roll straight from parsed {@link RollInput} (carries the forceAuto intent). */
    public static RollResult resolve(RollInput input, int modifier, String modLabel, boolean rerollNat1, Advantage advantage) {
        return resolve(input.providedRoll(), input.providedTotal(), modifier, modLabel, rerollNat1, advantage, input.forceAuto());
    }

    /**
     * As above, plus {@code forceAuto}: when the player explicitly asked the game to roll
     * ({@code autoRoll}), it rolls even in physical-dice mode instead of returning null to prompt.
     */
    public static RollResult resolve(Integer providedRoll, Integer providedTotal, int modifier, String modLabel,
                                     boolean rerollNat1, Advantage advantage, boolean forceAuto) {
        if (providedTotal != null) {
            return new RollResult(-1, providedTotal, true, false, false, providedTotal + " (provided total)");
        }
        if (advantage == null) advantage = Advantage.NONE;

        Integer d20 = providedRoll;
        String advNote = "";
        if (d20 == null) {
            if (!forceAuto && !PluginConfig.isAutoRoll()) return null; // physical mode: caller prompts for a die
            if (!advantage.affectsRoll()) { // NONE or CANCELLED: one die
                d20 = DiceRoller.rollDice(1, 20);
            } else {
                int a = DiceRoller.rollDice(1, 20);
                int b = DiceRoller.rollDice(1, 20);
                d20 = advantage.isAdvantage() ? Math.max(a, b) : Math.min(a, b);
                advNote = " [" + advantage.label() + ": " + a + "/" + b + "]";
            }
        }
        String luck = "";
        if (rerollNat1 && d20 == 1) {
            int first = d20;
            d20 = DiceRoller.rollDice(1, 20);
            luck = " [Lucky: reroll of " + first + "]";
        }
        int total = d20 + modifier;
        return new RollResult(d20, total, false, d20 == 20, d20 == 1,
                "d20(" + d20 + ") " + modLabel + " = " + total + advNote + luck);
    }
}
