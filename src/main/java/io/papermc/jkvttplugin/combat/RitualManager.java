package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


/**
 * Ritual casting during combat (Issue #156) — a house-ruled multi-turn channel.
 *
 * <p>By RAW a ritual adds 10 minutes (~100 rounds), so it can never finish in a fight. Here a ritual
 * completes after a configurable number of the caster's turns ({@code rituals.combat_rounds}, or a
 * spell's own {@code ritual_rounds}). Each of those turns the caster's action is committed to the
 * channel; taking damage can break it (per {@code rituals.interrupt}), as can a condition that stops
 * the caster acting (Stunned, Paralyzed, …).
 */
public final class RitualManager {


    private RitualManager() {}

    /** Begin channelling {@code spell} as a ritual on the caster's turn. Spends this turn's action. */
    public static boolean begin(CombatSession session, Player controller, Combatant caster, DndSpell spell) {
        if (!spell.isRitual()) {
            controller.sendMessage(Component.text(spell.getName() + " can't be cast as a ritual.", NamedTextColor.RED));
            return false;
        }
        if (caster.isChanneling()) {
            controller.sendMessage(Component.text("You're already channelling " + caster.getRitualSpellName() + ".", NamedTextColor.RED));
            return false;
        }
        TurnState state = caster.getTurnState();
        if (state != null && state.isActionUsed()) {
            controller.sendMessage(Component.text("You've already used your action this turn.", NamedTextColor.RED));
            return false;
        }
        int rounds = spell.getRitualRounds() > 0 ? spell.getRitualRounds() : PluginConfig.getRitualCombatRounds();
        caster.beginRitual(spell.getId(), spell.getName(), rounds);
        if (state != null) state.useAction();

        session.broadcast(Component.text("✦ " + caster.getDisplayName() + " begins channelling ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(spell.getName(), NamedTextColor.AQUA))
                .append(Component.text(" as a ritual (" + rounds + " rounds).", NamedTextColor.LIGHT_PURPLE)));
        controller.sendMessage(Component.text("You begin the ritual — keep safe; taking damage may break it. "
                + "Use /combat cast cancel to stop.", NamedTextColor.GRAY));
        session.sendActionBar(caster);
        session.updateScoreboard();
        return true;
    }

    /** Called at the start of a combatant's turn: advance (or break) any ritual it's channelling. */
    public static void onTurnStart(CombatSession session, Combatant c) {
        if (c == null || !c.isChanneling()) return;

        // Being downed (0 HP / dead) breaks the channel — tracked as a flag, not a condition.
        if (c.isDead() || c.isUnconscious()) {
            cancel(session, c, "the caster was knocked down");
            return;
        }
        // A condition that stops the caster acting (Stunned, Paralyzed, …) breaks it.
        if (c.cannotAct()) {
            cancel(session, c, (c.actionBlockingCondition() != null ? c.actionBlockingCondition() : "a condition") + " interrupts it");
            return;
        }
        String name = c.getRitualSpellName();
        TurnState state = c.getTurnState();
        if (state != null) state.useAction(); // the channel occupies this turn's action

        if (c.tickRitual()) {
            session.broadcast(Component.text("✦ " + c.getDisplayName() + " completes the ritual: ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .append(Component.text(name + "!", NamedTextColor.AQUA)));
        } else {
            Player controller = c.isPlayer() ? c.getPlayer() : Bukkit.getPlayer(session.getDmId());
            if (controller != null) {
                controller.sendMessage(Component.text("Channelling " + name + " — " + c.getRitualRoundsLeft()
                        + " round" + (c.getRitualRoundsLeft() == 1 ? "" : "s") + " left (your action is committed).", NamedTextColor.LIGHT_PURPLE));
            }
        }
        session.updateScoreboard();
    }

    /** Break an in-progress ritual and announce why. No-op if the combatant isn't channelling. */
    public static void cancel(CombatSession session, Combatant c, String reason) {
        if (c == null || !c.isChanneling()) return;
        String name = c.getRitualSpellName();
        c.cancelRitual();
        session.broadcast(Component.text("✦ " + c.getDisplayName() + "'s ritual (" + name + ") is broken — " + reason + ".",
                NamedTextColor.RED));
        session.updateScoreboard();
    }

    /** Compact scoreboard tag for an active channel, e.g. "§d✦2". */
    public static String scoreboardTag(Combatant c) {
        if (c == null || !c.isChanneling()) return "";
        return " §d✦" + c.getRitualRoundsLeft();
    }
}
