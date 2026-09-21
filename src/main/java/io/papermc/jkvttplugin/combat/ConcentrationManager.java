package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concentration: holding a spell together after you've been hit.
 *
 * <p>RAW (PHB 203): damage forces a Constitution saving throw, DC 10 or half the damage taken,
 * whichever is higher. Fail and the spell ends. Being knocked unconscious or killed ends it with no
 * save at all, and so does casting a second concentration spell.
 *
 * <p><b>The game never rolls this for you.</b> A hit prompts the concentrating player with the same
 * {@code autoRoll} / {@code manualRoll <n>} / {@code total <n>} choice as every other d20 in the
 * system, and the prompt spells out what gets added, so someone rolling a physical die knows what to
 * add before they pick it up. The DM rolls for a creature, as with any other save.
 *
 * <p>A channelled ritual (#156) rides the same prompt. It used to roll itself, silently, which was
 * the only d20 in combat the game took out of the players' hands; it now asks like everything else.
 * Its {@code rituals.interrupt} config still decides <em>whether</em> a check happens at all.
 */
public final class ConcentrationManager {

    /** What a pending check is holding together. A single roll settles both. */
    private record Pending(UUID combatantId, int dc, boolean spell, boolean ritual, String what) {}

    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** Concentration saves are against being interrupted; the tag lets a trait key off it later. */
    private static final Set<String> SAVE_TAGS = Set.of("concentration");

    private ConcentrationManager() {}

    // ==================== TRIGGERS ====================

    /**
     * Called after a combatant takes damage. Ends concentration outright if they went down, and
     * otherwise opens the save prompt. Replaces the old {@code RitualManager.onDamage} call so a
     * caster who is both channelling and concentrating is asked once, not twice.
     */
    public static void onDamage(CombatSession session, Combatant target, int finalDamage) {
        if (session == null || target == null || finalDamage <= 0) return;

        boolean channelling = target.isChanneling();
        boolean concentrating = concentratingSpell(target) != null;
        if (!channelling && !concentrating) return;

        // Dropped or killed: no save, both end. (PHB: you lose concentration when incapacitated.)
        if (target.isDead() || target.getCurrentHp() <= 0) {
            breakAll(session, target, "they were knocked down");
            return;
        }

        // The ritual config can say a ritual simply isn't interrupted, or always breaks. A
        // concentration spell always gets its RAW save regardless of that setting.
        boolean ritualNeedsCheck = false;
        if (channelling) {
            switch (PluginConfig.getRitualInterrupt()) {
                case NONE -> { /* the ritual rides out the hit */ }
                case BREAK_ON_DAMAGE -> RitualManager.cancel(session, target, "the hit shattered its focus");
                case CONCENTRATION_CHECK -> ritualNeedsCheck = true;
            }
        }
        if (!ritualNeedsCheck && !concentrating) return;

        int dc = Math.max(10, finalDamage / 2);
        // A fixed ritual DC from config still applies to the ritual half; take the harder of the two
        // so one roll can honestly settle both.
        if (ritualNeedsCheck && PluginConfig.getRitualInterruptDc() > 0) {
            dc = Math.max(dc, PluginConfig.getRitualInterruptDc());
        }

        DndSpell spell = concentratingSpell(target);
        String what = ritualNeedsCheck && spell != null
                ? spell.getName() + " and the " + target.getRitualSpellName() + " ritual"
                : (spell != null ? spell.getName() : target.getRitualSpellName());

        pending.put(target.getId(), new Pending(target.getId(), dc, spell != null, ritualNeedsCheck, what));
        prompt(session, target, dc, what);
    }

    /** Incapacitated, unconscious or dead: concentration ends with no save. */
    public static void onIncapacitated(CombatSession session, Combatant target, String why) {
        if (target == null) return;
        if (!target.isChanneling() && concentratingSpell(target) == null) return;
        breakAll(session, target, why);
    }

    // ==================== THE PROMPT ====================

    /**
     * Ask for the save, spelling out the modifier. Someone with a physical d20 needs to know what
     * to add before they roll it, so the breakdown is part of the question, not the answer.
     */
    private static void prompt(CombatSession session, Combatant target, int dc, String what) {
        int bonus = saveBonus(target);
        String sign = bonus >= 0 ? "+" : "";
        String breakdown = bonusBreakdown(target);

        session.broadcast(Component.text("◈ Concentration — ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(target.getDisplayName(true) + " must hold " + what
                        + " together: DC " + dc + " CON save.", NamedTextColor.YELLOW)));

        Component ask = Component.text("◈ Roll a CON save vs DC " + dc + " — you add " + sign + bonus
                        + " (" + breakdown + "): ", NamedTextColor.GOLD)
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/combat concentration manualRoll "))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Fills: /combat concentration manualRoll <your d20>\nThe game adds " + sign + bonus + "."))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[or let the game roll]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/combat concentration autoRoll"))
                        .hoverEvent(HoverEvent.showText(Component.text("The game rolls the d20 and adds " + sign + bonus + "."))));

        if (target.isPlayer() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ask);
        } else {
            String quoted = target.getDisplayName().contains(" ")
                    ? "\"" + target.getDisplayName() + "\"" : target.getDisplayName();
            session.sendToDM(Component.text("◈ Roll " + target.getDisplayName(true) + "'s CON save (DC " + dc
                            + ", they add " + sign + bonus + ") — ", NamedTextColor.GOLD)
                    .append(Component.text("[click, then type the d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/combat concentration " + quoted + " manualRoll "))
                            .hoverEvent(HoverEvent.showText(Component.text("Rolls the concentration save for the creature.")))));
        }
    }

    // ==================== RESOLUTION ====================

    /** True while this combatant owes a concentration save. */
    public static boolean isPending(Combatant c) {
        return c != null && pending.containsKey(c.getId());
    }

    /** Remind whoever tried to carry on that the save is still owed. */
    public static void explainPending(Player to, Combatant c) {
        Pending p = c == null ? null : pending.get(c.getId());
        if (p == null || to == null) return;
        to.sendMessage(Component.text("◈ Hold — " + c.getDisplayName() + " still owes a DC " + p.dc()
                + " CON save to keep " + p.what() + ".", NamedTextColor.GOLD));
        to.sendMessage(Component.text("   /combat concentration autoRoll — or manualRoll <your d20>.", NamedTextColor.GRAY));
    }

    /** Resolve the pending save. Players roll their own; the DM rolls for a creature. */
    public static void resolve(Player roller, CombatSession session, Combatant target,
                               Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        Pending p = pending.get(target.getId());
        if (p == null) {
            roller.sendMessage(Component.text(target.getDisplayName() + " has no concentration save to make.", NamedTextColor.YELLOW));
            return;
        }
        int bonus = saveBonus(target);
        Advantage advantage = target.saveAdvantage(Ability.CONSTITUTION, SAVE_TAGS);
        if (advantage != Advantage.NONE) {
            roller.sendMessage(Component.text("↯ " + target.getDisplayName() + " rolls this save with "
                    + advantage.label() + ".", advantage.isAdvantage() ? NamedTextColor.GREEN : advantage.isDisadvantage() ? NamedTextColor.RED : NamedTextColor.GRAY));
        }
        RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, bonus,
                (bonus >= 0 ? "+" : "") + bonus + "[CON]", target.rerollsNat1(), advantage, forceAuto);
        if (r == null) {
            roller.sendMessage(Component.text("Add your roll: 'manualRoll <n>', or 'autoRoll'.", NamedTextColor.YELLOW));
            return;
        }
        pending.remove(target.getId());

        boolean held = r.total() >= p.dc();
        session.broadcast(Component.text(target.getDisplayName(true) + " concentration: " + r.breakdown()
                + " vs DC " + p.dc() + " → " + (held ? "HELD" : "BROKEN"),
                held ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (held) {
            session.broadcast(Component.text(target.getDisplayName(true) + " keeps " + p.what() + " going.", NamedTextColor.GRAY));
            return;
        }
        if (p.spell()) breakSpell(session, target, "the save failed");
        if (p.ritual()) RitualManager.cancel(session, target, "the concentration check failed");
        session.updateScoreboard();
    }

    // ==================== BREAKING ====================

    /** End both a concentration spell and a channelled ritual, with no save. */
    private static void breakAll(CombatSession session, Combatant target, String why) {
        pending.remove(target.getId());
        if (concentratingSpell(target) != null) breakSpell(session, target, why);
        if (target.isChanneling()) RitualManager.cancel(session, target, why);
    }

    private static void breakSpell(CombatSession session, Combatant target, String why) {
        CharacterSheet sheet = target.getCharacterSheet();
        if (sheet == null || !sheet.isConcentrating()) return;
        String name = sheet.getConcentratingOn().getName();
        sheet.breakConcentration(); // also drops a Hex / Hunter's Mark rider
        if (session != null) {
            session.broadcast(Component.text("◈ " + target.getDisplayName(true) + "'s concentration on "
                    + name + " ends — " + why + ".", NamedTextColor.RED));
        } else if (target.getPlayer() != null) {
            target.getPlayer().sendMessage(Component.text("◈ Your concentration on " + name
                    + " ends — " + why + ".", NamedTextColor.RED));
        }
    }

    /** Drop every pending save (combat ended). */
    public static void clearAll() {
        pending.clear();
    }

    // ==================== HELPERS ====================

    /** The spell this combatant is concentrating on, or null. Only characters hold concentration. */
    private static DndSpell concentratingSpell(Combatant c) {
        if (c == null || c.getCharacterSheet() == null) return null;
        return c.getCharacterSheet().isConcentrating() ? c.getCharacterSheet().getConcentratingOn() : null;
    }

    /** The CON saving-throw bonus, for a character or a creature. */
    private static int saveBonus(Combatant c) {
        if (c.isPlayer() && c.getCharacterSheet() != null) {
            return c.getCharacterSheet().getSavingThrowBonus(Ability.CONSTITUTION);
        }
        return c.getConstitutionModifier();
    }

    /** Spelled-out modifier, so a player with a physical die knows what they're adding and why. */
    private static String bonusBreakdown(Combatant c) {
        if (c.isPlayer() && c.getCharacterSheet() != null) {
            CharacterSheet sheet = c.getCharacterSheet();
            int mod = sheet.getModifier(Ability.CONSTITUTION);
            String s = (mod >= 0 ? "+" : "") + mod + " CON";
            if (sheet.isProficientInSave(Ability.CONSTITUTION)) {
                s += ", +" + sheet.getProficiencyBonus() + " proficiency";
            }
            return s;
        }
        int mod = c.getConstitutionModifier();
        return (mod >= 0 ? "+" : "") + mod + " CON";
    }
}
