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
    private static final java.util.Set<String> LIVE_FLAGS = java.util.Set.of("--hidden", "--radius");

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
        java.util.List<Integer> bonusDice = new java.util.ArrayList<>();
        java.util.List<String> stale = new java.util.ArrayList<>();
        java.util.List<String> notNumbers = new java.util.ArrayList<>(), missing = new java.util.ArrayList<>(),
                belowOne = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.startsWith("--") && !LIVE_FLAGS.contains(a)) stale.add(args[i]);
            String next = (i + 1 < args.length) ? args[i + 1] : null;
            switch (a) {
                case "autoroll" -> forceAuto = true; // optional trailing dice (e.g. 2d20) is ignored — advantage is auto-detected
                case "manualroll" -> {
                    if (next == null || isRollKeyword(next)) missing.add(args[i]);
                    else if (isDice(next)) forceAuto = true; // a dice expression → let the game roll
                    else {
                        try {
                            int n = Integer.parseInt(next.trim());
                            if (n < 1) belowOne.add(next); else providedRoll = n; // no die shows 0
                            // Numbers after the d20 are the bonus dice you rolled too, in order (#225):
                            // "manualRoll 14 3" = a 14, and 3 on Bless's d4.
                            for (int j = i + 2; j < args.length && args[j].trim().matches("\\d+"); j++) {
                                bonusDice.add(Integer.parseInt(args[j].trim()));
                            }
                        } catch (NumberFormatException e) { notNumbers.add(next); }
                    }
                }
                case "total" -> {
                    if (next == null || isRollKeyword(next)) missing.add(args[i]);
                    else { try { providedTotal = Integer.parseInt(next.trim()); } catch (NumberFormatException e) { notNumbers.add(next); } }
                }
                default -> {}
            }
        }
        if (who != null && !stale.isEmpty()) warnStaleSyntax(who, stale);
        // A bad number leaves the input empty, so the caller re-offers the buttons; say why first.
        if (who != null) {
            for (String n : notNumbers) who.sendMessage(Component.text("'" + n + "' isn't a number. manualRoll takes the d20 you rolled "
                    + "(manualRoll 14), total takes your final number (total 19).", NamedTextColor.RED));
            for (String m : missing) who.sendMessage(Component.text(m + " needs a number after it (" + m + " 14).", NamedTextColor.RED));
            for (String b : belowOne) who.sendMessage(Component.text("manualRoll " + b + "? No die shows less than 1.", NamedTextColor.RED));
        }
        providedBonusDice = new ProvidedDice(new java.util.ArrayDeque<>(bonusDice), currentTick());
        return new RollInput(providedRoll, providedTotal, forceAuto);
    }

    private static final java.util.regex.Pattern DICE = java.util.regex.Pattern.compile("(?i)\\d*d\\d+([+-]\\d+)?");

    /** "d20", "2d20", "1d8+3": dice, as opposed to a number or a typo. */
    public static boolean isDice(String token) {
        return token != null && DICE.matcher(token.trim()).matches();
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
     * @param modLabel      how the modifier reads in the breakdown, e.g. "+3[STR] +2[Prof]" or a creature's "+4[Scimitar]"
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
            return new RollResult(-1, providedTotal, true, false, false, RollPrompt.yourTotal(providedTotal));
        }
        if (advantage == null) advantage = Advantage.NONE;
        // A d20 shows 1 to 20. Anything else isn't a roll (manualRoll 0 used to count), so the caller
        // asks again. (parseInput already told them about 0 or less.)
        if (providedRoll != null && (providedRoll < 1 || providedRoll > 20)) return null;

        Integer d20 = providedRoll;
        String shown = null; // the dice as the game rolled them: "[14]", or "[9, 15] advantage"
        if (d20 == null) {
            if (!forceAuto && !PluginConfig.isAutoRoll()) return null; // physical mode: caller prompts for a die
            if (!advantage.affectsRoll()) { // NONE or CANCELLED: one die
                d20 = DiceRoller.rollDice(1, 20);
                shown = "[" + d20 + "]";
            } else {
                int a = DiceRoller.rollDice(1, 20);
                int b = DiceRoller.rollDice(1, 20);
                d20 = advantage.isAdvantage() ? Math.max(a, b) : Math.min(a, b);
                shown = "[" + a + ", " + b + "] " + advantage.label();
            }
        }
        String luck = "";
        if (rerollNat1 && d20 == 1) {
            int first = d20;
            d20 = DiceRoller.rollDice(1, 20);
            luck = " [Lucky: the 1 was rerolled, " + d20 + " stands]";
        }
        // Dice in the bonus (Bless's "+1d4[Bless]", #225): the game rolls them with an autoRoll; with a
        // manualRoll they're yours to type too ("manualRoll 14 3"), and a missing or impossible one is
        // refused, so the caller re-offers the buttons (RollPrompt shows why). A total is taken as final.
        LabelDice extra = rollLabelDice(modLabel, providedRoll != null);
        if (extra == null) return null;
        int total = d20 + modifier + extra.sum();
        String work = providedRoll != null
                ? RollPrompt.youRolled(d20, extra.label(), total)
                : RollPrompt.gameRolled("d20", shown, extra.label(), total);
        return new RollResult(d20, total, false, d20 == 20, d20 == 1, work + luck + natCallout(d20));
    }

    /** What the dice in a bonus came to: their sum, and the bonus with each die replaced by its roll. */
    public record LabelDice(int sum, String label) {}

    private static final java.util.regex.Pattern LABEL_DICE =
            java.util.regex.Pattern.compile("([+-])(\\d*)d(\\d+)\\[([^\\]]+)\\]");

    /**
     * Roll the dice written into a bonus: {@code "+3[STR] +1d4[Bless] -1d4[Bane]"} becomes, say,
     * sum 1 and {@code "+3[STR] +3[Bless 1d4] -2[Bane 1d4]"}. Flat parts are left alone (the caller
     * already added them as the modifier). A bonus with no dice comes back as it was, sum 0.
     */
    /**
     * The bonus dice typed after a manualRoll ("manualRoll 14 3"), for the roll this command makes.
     * parseInput sets them and the next rollLabelDice takes them. A command parses before it resolves,
     * on the same tick, so they're only good for that tick: a prompt that never resolved can't leave a
     * 3 behind for someone else's roll later.
     */
    private record ProvidedDice(java.util.ArrayDeque<Integer> dice, long tick) {}
    private static ProvidedDice providedBonusDice;

    private static long currentTick() {
        return org.bukkit.Bukkit.getServer() != null ? org.bukkit.Bukkit.getCurrentTick() : -1;
    }

    private static java.util.Deque<Integer> takeProvidedBonusDice() {
        ProvidedDice p = providedBonusDice;
        providedBonusDice = null;
        return p != null && p.tick() == currentTick() ? p.dice() : new java.util.ArrayDeque<>();
    }

    /** Why the last manualRoll's bonus dice were refused, this tick; RollPrompt shows it above the buttons. */
    private record BonusDiceError(String message, long tick) {}
    private static BonusDiceError bonusDiceError;

    /** The refusal from this tick's manualRoll, once, or null. */
    public static String takeBonusDiceError() {
        BonusDiceError e = bonusDiceError;
        bonusDiceError = null;
        return e != null && e.tick() == currentTick() ? e.message() : null;
    }

    /** True if a bonus has dice for the game to roll ("+1d4[Bless]"), so a prompt can say you may roll them too. */
    public static boolean hasLabelDice(String label) {
        return label != null && LABEL_DICE.matcher(label).find();
    }

    /** The game rolls every die in the bonus (an autoRoll). */
    public static LabelDice rollLabelDice(String label) {
        return rollLabelDice(label, false);
    }

    /**
     * Roll the dice in a bonus. With {@code typedByPlayer} (a manualRoll) each must come from what they
     * typed after the d20, and nothing is rolled for them: a missing or impossible one gives null, with
     * the reason kept for the re-prompt ({@link #takeBonusDiceError}).
     */
    public static LabelDice rollLabelDice(String label, boolean typedByPlayer) {
        if (label == null || label.indexOf('d') < 0) { takeProvidedBonusDice(); return new LabelDice(0, label); }
        java.util.regex.Matcher m = LABEL_DICE.matcher(label);
        StringBuilder out = new StringBuilder();
        int sum = 0;
        java.util.Deque<Integer> given = takeProvidedBonusDice();
        while (m.find()) {
            int count = m.group(2).isEmpty() ? 1 : Integer.parseInt(m.group(2));
            int sides = Integer.parseInt(m.group(3));
            String die = count + "d" + sides;
            int rolled;
            if (typedByPlayer) {
                Integer mine = given.poll();
                if (mine == null) {
                    bonusDiceError = new BonusDiceError(m.group(4) + " adds " + die + ": type yours after the d20, e.g. manualRoll 14 3"
                            + " (or use autoRoll and the game rolls both).", currentTick());
                    return null;
                }
                if (mine < count || mine > count * sides) {
                    bonusDiceError = new BonusDiceError(mine + " isn't a " + die + " roll for " + m.group(4) + " (" + count + " to "
                            + count * sides + ").", currentTick());
                    return null;
                }
                rolled = mine;
            } else {
                rolled = io.papermc.jkvttplugin.util.DiceRoller.rollDice(count, sides);
            }
            boolean minus = m.group(1).equals("-");
            sum += minus ? -rolled : rolled;
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    (minus ? "-" : "+") + rolled + "[" + m.group(4) + " " + count + "d" + m.group(3) + "]"));
        }
        m.appendTail(out);
        return new LabelDice(sum, out.toString());
    }

    /**
     * " — NATURAL 20!" / " — NATURAL 1" for the kept die, so every roll that shows its work calls it
     * out. Only a callout: on a check or save a nat 20 isn't an automatic success (PHB p.7), so the
     * DM still grades it against the DC.
     */
    public static String natCallout(int keptD20) {
        return keptD20 == 20 ? " — NATURAL 20!" : keptD20 == 1 ? " — NATURAL 1" : "";
    }
}
