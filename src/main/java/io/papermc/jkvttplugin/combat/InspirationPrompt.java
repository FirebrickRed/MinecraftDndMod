package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bardic Inspiration after the roll (#40, PHB p.54): "the creature can wait until after it rolls the
 * d20 before deciding to use the Bardic Inspiration die". After every attack, save or check a holder
 * rolls, they're asked: <b>[Roll it] [I rolled…] [Don't use it]</b>, answered by
 * {@code /character inspiration}. Using it spends the die and announces the new total.
 *
 * <p>What the new total does: an attack that <b>missed</b> is checked again against the target's AC,
 * and a hit now goes on to damage ({@code onAdded}). Anything else (a save, a check) is the DM's call,
 * so the table and the DM see the new number.
 *
 * <p>For now it asks after every roll, not only the ones it could change; a config switch for "only
 * when it would matter" is a possible follow-up. A newer roll's question replaces an older one.
 */
public final class InspirationPrompt {

    private record Pending(UUID sheetId, ActiveEffect die, String what, int total, IntConsumer onAdded,
                           Runnable onDeclined, Consumer<Component> tell) {}

    private static final Map<UUID, Pending> pending = new HashMap<>();

    private InspirationPrompt() {}

    /**
     * A holder's d20 roll just resolved: ask whether to add the die. Does nothing if they hold none.
     *
     * @param what    what was rolled, for the message: "attack roll", "DEX save", "Stealth check"
     * @param total   the roll's total so far
     * @param onAdded what a higher total does (an attack's miss re-checked), or null for "the DM rules on it"
     * @param tell    who hears the answer: the table in a fight, or just the roller for a private roll
     */
    public static void offer(CharacterSheet sheet, String what, int total, IntConsumer onAdded, Consumer<Component> tell) {
        offer(sheet, what, total, onAdded, null, tell);
    }

    /**
     * As above, for a roll whose failure waits on the answer (a concentration save, a death save):
     * {@code onDeclined} applies the failure when they say no, or when a newer roll's question replaces this one.
     * Returns false (running nothing) if they hold no die, so the caller applies the failure itself.
     */
    public static boolean offer(CharacterSheet sheet, String what, int total, IntConsumer onAdded, Runnable onDeclined,
                                Consumer<Component> tell) {
        if (sheet == null || Bukkit.getServer() == null) return false;
        ActiveEffect die = heldDie(sheet);
        if (die == null) return false;
        Player p = sheet.getPlayerId() != null ? Bukkit.getPlayer(sheet.getPlayerId()) : null;
        if (p == null) return false;
        Pending older = pending.put(p.getUniqueId(), new Pending(sheet.getCharacterId(), die, what, total, onAdded, onDeclined, tell));
        if (older != null && older.onDeclined() != null) older.onDeclined().run(); // that roll's chance has passed
        p.sendMessage(question(die, what, total));
        return true;
    }

    /** {@code /character inspiration <autoRoll | manualRoll <n> | no>}: answer the question. */
    public static void answer(Player player, String[] words) {
        Pending q = pending.get(player.getUniqueId());
        if (q == null) {
            player.sendMessage(Component.text("There's no roll waiting for your Bardic Inspiration. You'll be asked after your next attack, save or check.", NamedTextColor.GRAY));
            return;
        }
        if (words.length > 0 && (words[0].equalsIgnoreCase("no") || words[0].equalsIgnoreCase("keep"))) {
            pending.remove(player.getUniqueId());
            player.sendMessage(Component.text("🎵 You keep your " + q.die().getSourceName() + ".", NamedTextColor.GRAY));
            if (q.onDeclined() != null) q.onDeclined().run();
            return;
        }
        CharacterSheet sheet = CharacterSheetManager.getCharacterById(q.sheetId());
        if (sheet == null || !sheet.getActiveEffects().contains(q.die())) {
            pending.remove(player.getUniqueId());
            player.sendMessage(Component.text("You don't have that Bardic Inspiration any more.", NamedTextColor.GRAY));
            if (q.onDeclined() != null) q.onDeclined().run();
            return;
        }
        String dice = q.die().getRollBonusDice();
        int sides = sidesOf(dice);
        RollService.RollInput in = RollService.parseInput(words, player);
        int rolled;
        String work;
        if (in.providedRoll() != null) {
            if (in.providedRoll() < 1 || in.providedRoll() > sides) {
                player.sendMessage(Component.text("⚠ " + in.providedRoll() + " isn't a " + dice + " roll (1 to " + sides + ").", NamedTextColor.RED));
                player.sendMessage(question(q.die(), q.what(), q.total()));
                return;
            }
            rolled = in.providedRoll();
            work = RollPrompt.youRolled(rolled, null, rolled);
        } else if (in.forceAuto() || PluginConfig.isAutoRoll()) {
            DiceRoller.Rolled r = DiceRoller.rollOrFlat(dice);
            rolled = r != null ? r.total() : 0;
            work = RollPrompt.gameRolled(dice, r != null ? r.shown() : "0", null, rolled);
        } else {
            player.sendMessage(question(q.die(), q.what(), q.total()));
            return;
        }
        pending.remove(player.getUniqueId());
        ActiveEffect die = q.die();
        sheet.removeEffects(e -> e == die); // spent
        int newTotal = q.total() + rolled;
        q.tell().accept(Component.text("🎵 " + sheet.getCharacterName() + " adds " + die.getSourceName() + " to the " + q.what()
                + ": " + work + " → " + newTotal + ".", NamedTextColor.LIGHT_PURPLE));
        if (q.onAdded() != null) q.onAdded().accept(newTotal);
        else q.tell().accept(Component.text("   The DM decides whether " + newTotal + " changes the result.", NamedTextColor.GRAY));
    }

    // ==================== HELPERS ====================

    /** The Bardic Inspiration-style die this character holds (a held roll bonus), or null. */
    static ActiveEffect heldDie(CharacterSheet sheet) {
        for (ActiveEffect e : sheet.getActiveEffects()) {
            if (e.isHeld() && e.getRollBonusDice() != null) return e;
        }
        return null;
    }

    private static Component question(ActiveEffect die, String what, int total) {
        String dice = die.getRollBonusDice();
        return Component.text("🎵 Add your " + die.getSourceName() + " (" + dice + ") to that " + what + " (" + total + ")? ",
                        NamedTextColor.LIGHT_PURPLE)
                .append(RollPrompt.buttons("/character inspiration ", dice, null))
                .append(Component.text(" "))
                .append(Component.text("[Don't use it]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/character inspiration no"))
                        .hoverEvent(HoverEvent.showText(Component.text("Keep it for a later roll.\nFills chat: press Enter."))));
    }

    private static int sidesOf(String dice) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("d(\\d+)").matcher(dice == null ? "" : dice);
        return m.find() ? Integer.parseInt(m.group(1)) : 6;
    }

    /** Who hears the answer in a fight: the whole table. */
    public static Consumer<Component> table(CombatSession session) {
        return session::broadcast;
    }

    /** Out of a fight: the roller and the DMs. */
    public static Consumer<Component> rollerAndDms(Player roller) {
        return msg -> {
            if (roller != null) roller.sendMessage(msg);
            for (Player dm : io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs()) if (roller == null || !dm.equals(roller)) dm.sendMessage(msg);
        };
    }
}
