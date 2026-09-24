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
