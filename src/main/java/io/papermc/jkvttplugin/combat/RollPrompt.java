package io.papermc.jkvttplugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>The</b> roll prompt and <b>the</b> roll result wording (#216). Every "roll this" in the game,
 * whether a d20 or damage dice, a player's or the DM's for a creature, is built here, so the buttons,
 * their order, colors and hover text never drift apart again.
 *
 * <p>Three ways to answer, always in this order:
 * <ul>
 *   <li><b>[I rolled…]</b>: fills {@code <base>manualRoll }; you type the dice, the game adds the bonus.</li>
 *   <li><b>[Roll it]</b>: runs {@code <base>autoRoll}; the game rolls and adds the bonus.</li>
 *   <li><b>[My total…]</b>: fills {@code <base>total }; you type the final number, bonuses included.
 *       Only offered when there is a bonus, since otherwise it's the same as [I rolled…].</li>
 * </ul>
 * And the result reads the same way for each: {@link #gameRolled}, {@link #youRolled}, {@link #yourTotal}.
 * {@code RollServiceTest}-style tests guard the wording; a grep for the labels should only find this file.
 */
public final class RollPrompt {

    private RollPrompt() {}

    public static final String I_ROLLED = "[I rolled…]";
    public static final String ROLL_IT = "[Roll it]";
    public static final String MY_TOTAL = "[My total…]";

    /**
     * The three buttons.
     *
     * @param base  the command up to the roll words, ending in a space ({@code "/combat save "})
     * @param dice  what's rolled, for the hover: {@code "d20"}, {@code "2d6"}
     * @param bonus what the game adds, spelled out ({@code "+5 (+3 DEX, +2 proficiency)"}); null or
     *              blank when nothing is added
     */
    public static Component buttons(String base, String dice, String bonus) {
        boolean hasBonus = bonus != null && !bonus.isBlank();
        String adds = hasBonus ? "; the game adds " + bonus : "";
        Component out = button(I_ROLLED, NamedTextColor.GREEN, ClickEvent.suggestCommand(base + "manualRoll "),
                "Type what your " + dice + " came to" + adds + ".")
                .append(Component.text(" "))
                .append(button(ROLL_IT, NamedTextColor.AQUA, ClickEvent.runCommand(base + "autoRoll"),
                        "The game rolls " + dice + (hasBonus ? " and adds " + bonus : "") + "."));
        if (hasBonus) {
            out = out.append(Component.text(" "))
                    .append(button(MY_TOTAL, NamedTextColor.YELLOW, ClickEvent.suggestCommand(base + "total "),
                            "Type your final number, with your bonuses already added."));
        }
        return out;
    }

    /** A lead-in line followed by {@link #buttons}: {@code "🛡 Roll a DEX save: [I rolled…] [Roll it] [My total…]"}. */
    public static Component line(String lead, NamedTextColor color, String base, String dice, String bonus) {
        return Component.text(lead + " ", color).append(buttons(base, dice, bonus));
    }

    // ==================== "YOU DIDN'T SAY HOW YOU'RE ROLLING" ====================
    // A roll command typed without autoRoll / manualRoll / total (physical-dice mode) is answered deep
    // in a call chain that doesn't know what was typed. The root commands remember the line, so the
    // re-ask is the same three buttons on that exact command instead of "type 'manualRoll <n>'".

    private static final java.util.Map<java.util.UUID, String> typed = new java.util.HashMap<>();

    /** Called by a root command ({@code /combat}, {@code /character}) before it dispatches. */
    public static void rememberCommand(org.bukkit.command.CommandSender sender, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player p)) return;
        StringBuilder line = new StringBuilder("/" + label);
        for (String a : args) {
            // "showModifiers" only prints the info panel; the roll it offers is the real attack.
            if (a.isBlank() || a.equalsIgnoreCase("showModifiers") || a.equalsIgnoreCase("showMods")) continue;
            line.append(' ').append(a);
        }
        typed.put(p.getUniqueId(), line.append(' ').toString());
    }

    /** The buttons again, on the command this player just typed. Falls back to a plain hint if unknown. */
    public static Component again(org.bukkit.command.CommandSender to, String lead, String dice, String bonus) {
        String base = to instanceof org.bukkit.entity.Player p ? typed.get(p.getUniqueId()) : null;
        if (base == null) return Component.text(lead + " Add autoRoll, manualRoll <n> or total <n>.", NamedTextColor.YELLOW);
        return line(lead, NamedTextColor.YELLOW, base, dice, bonus);
    }

    // ==================== A FORMULA'S OWN FLAT BONUS ====================

    /**
     * A dice formula split into what you roll and its flat part, labelled by where it comes from:
     * a Healing Potion's "2d4+2" is {@code 2d4} and {@code +2[Healing Potion]}, Magic Missile's
     * "1d4+1" is {@code 1d4} and {@code +1[Magic Missile]}. Labelled with the thing itself, never
     * [Prof] or an ability, so it can't be mistaken for a character bonus. {@code label} is null
     * when there's no flat part.
     */
    public record Formula(String dice, int flat, String label) {}

    public static Formula split(String formula, String source) {
        String f = formula == null ? "" : formula.trim();
        java.util.regex.Matcher m = FLAT.matcher(f);
        if (!m.find() || !f.toLowerCase().contains("d")) return new Formula(f, 0, null);
        int flat = Integer.parseInt(m.group(1).replaceAll("\\s", ""));
        if (flat == 0) return new Formula(f, 0, null);
        String dice = f.substring(0, m.start()).trim();
        return new Formula(dice, flat, (flat > 0 ? "+" : "") + flat + "[" + source + "]");
    }

    private static final java.util.regex.Pattern FLAT = java.util.regex.Pattern.compile("([+-]\\s*\\d+)\\s*$");

    // ==================== RESULTS ====================
    // One wording per way of answering, so a roll always says where its number came from.

    /** The game rolled: {@code "🎲 2d6 [4, 3] +3[CHA] = 10"}. {@code shown} is the dice as rolled. */
    public static String gameRolled(String dice, String shown, String bonusLabel, int total) {
        return "🎲 " + dice + " " + shown + label(bonusLabel) + " = " + total;
    }

    /** You rolled it: {@code "🎲 you rolled 7 +3[CHA] = 10"}. */
    public static String youRolled(int rolled, String bonusLabel, int total) {
        return "🎲 you rolled " + rolled + label(bonusLabel) + " = " + total;
    }

    /** You gave the final number: {@code "🎲 your total: 10"}. */
    public static String yourTotal(int total) {
        return "🎲 your total: " + total;
    }

    private static String label(String bonusLabel) {
        return bonusLabel == null || bonusLabel.isBlank() ? "" : " " + bonusLabel.trim();
    }

    private static Component button(String text, NamedTextColor color, ClickEvent click, String hover) {
        return Component.text(text, color, TextDecoration.UNDERLINED)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }
}
