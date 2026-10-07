package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a spell's saving throw does once it's been graded (#267), the same in a fight and out of one:
 * the spell's structured effect (Bane), its condition (with the immunity check, and concentration ending
 * when the condition incapacitates), and how much of its damage is owed.
 *
 * <p>Two things stay with the caller, because they differ: <b>rolling the damage</b> (the caster's
 * {@code /combat damage} step in a fight, {@code /character damage} out of one) and <b>who hears it</b>
 * (the table, or the caster and whoever is near). So {@link #apply} changes the target and hands back
 * an {@link Outcome}: the damage owed and the lines to say. It never rolls and never sends a message.
 *
 * <p>Grading ({@link #saved}) is separate from the consequences ({@link #apply}) on purpose: a caller can
 * hold a failed save between the two (#268: an Inspiration die may still turn it into a success).
 *
 * <p>Before this, only a fight did any of it. Out of a fight a failed save offered the damage and told
 * the DM to apply the condition by hand; Bane did nothing at all.
 */
public final class SpellSave {

    private SpellSave() {}

    /**
     * A save a spell called for. {@code casterId} is the caster's combatant (who rolls the damage);
     * {@code effectCasterId} is the caster's character, whose concentration the effect hangs on.
     */
    public record Facts(String spellName, UUID casterId, UUID effectCasterId, int dc, Ability ability,
                        String damage, String damageType, String saveEffect, String conditionOnFail,
                        Set<String> saveTags, String effectSpellId) {

        /** The facts of {@code spell}'s save, as cast by this caster at this DC. */
        public static Facts of(DndSpell spell, UUID casterId, UUID effectCasterId, int dc, Ability ability, Set<String> saveTags) {
            return new Facts(spell.getName(), casterId, effectCasterId, dc, ability, spell.getDamage(), spell.getDamageType(),
                    spell.getSaveEffect(), spell.getConditionOnFail(), saveTags, spell.hasEffect() ? spell.getId() : null);
        }

        boolean hasDamage() { return damage != null && !damage.isBlank(); }
    }

    /**
     * What a save against this spell is "against", for conditional advantages (a dwarf against poison, a
     * gnome against magic, an elf against being charmed): always magic, plus its damage type and the
     * condition it sets on a fail. The same tags in a fight and out of one (#266).
     */
    public static Set<String> tagsFor(DndSpell spell) {
        Set<String> tags = new java.util.LinkedHashSet<>();
        tags.add("magic"); // every spell save is against magic
        if (spell.getDamageType() != null && !spell.getDamageType().isBlank()) tags.add(spell.getDamageType().toLowerCase());
        if (spell.getConditionOnFail() != null && !spell.getConditionOnFail().isBlank()) tags.add(spell.getConditionOnFail().toLowerCase());
        return java.util.Collections.unmodifiableSet(tags);
    }

    /** How much of the spell's damage the caller now has rolled: all of it, half, or none. */
    public enum Owed { FULL, HALF, NONE }

    /**
     * What happened. {@code effectLines} come before the damage step and {@code conditionLines} after it,
     * the order a fight has always shown them in. All of them are for the caller's own audience.
     */
    public record Outcome(boolean saved, Owed damage, List<Component> effectLines, List<Component> conditionLines,
                          boolean conditionApplied) {}

    /**
     * Whoever made the save, as far as its consequences are concerned. A live {@link Combatant} in play
     * ({@link #subject}); a sheet or a stat block in the tests, which have no player or armor stand.
     */
    public interface Subject {
        /** As the caller shows them: "Zek", or "???" for a creature the table hasn't identified. */
        String name();
        boolean isImmuneTo(String conditionId);
        /** False when they already had it. */
        boolean addCondition(String conditionId);
        /** True when a condition they now have stops them acting (Incapacitated, Stunned, Paralyzed…). */
        boolean cannotAct();
        void give(ActiveEffect effect);
        /** The condition's look on them, where there's a player to show it to. */
        void showCondition(DndCondition condition);
        /** They can't act any more: concentration ends outright, no save (PHB p.203). */
        void endConcentration(String why);
    }

    /** {@code target} as a {@link Subject}. {@code session} is their fight, or null out of one. */
    public static Subject subject(Combatant target, CombatSession session, String shownName) {
        return new Subject() {
            @Override public String name() { return shownName; }
            @Override public boolean isImmuneTo(String conditionId) { return target.isImmuneToCondition(conditionId); }
            @Override public boolean addCondition(String conditionId) { return target.addCondition(conditionId); }
            @Override public boolean cannotAct() { return target.cannotAct(); }
            @Override public void give(ActiveEffect effect) { SpellEffects.give(target, effect); }
            // The potion effect is the player's own, so it needs no fight (it's what /dm adjust uses too).
            @Override public void showCondition(DndCondition condition) { CombatSession.setConditionEffect(target, condition, true); }
            // The one place this class's "never sends" has an indirect exception: ending concentration announces
            // itself (to the table in a fight, to the target's player out of one), during apply. Tracked in #271.
            @Override public void endConcentration(String why) { ConcentrationManager.onIncapacitated(session, target, why); }
        };
    }

    /** Grading: meeting the DC saves. Kept apart from {@link #apply} so a caller can pause between them. */
    public static boolean saved(int total, int dc) {
        return total >= dc;
    }

    /**
     * Carry out a graded save on {@code target}: on a fail, the spell's effect and condition; either way,
     * the damage owed. Changes the target; says and rolls nothing.
     */
    public static Outcome apply(Facts facts, Subject target, boolean saved) {
        if (saved) {
            boolean half = facts.hasDamage() && "half".equalsIgnoreCase(facts.saveEffect());
            return new Outcome(true, half ? Owed.HALF : Owed.NONE, List.of(), List.of(), false);
        }
        List<Component> effectLines = new ArrayList<>(), conditionLines = new ArrayList<>();

        // The spell's structured effect lands on a failed save (Bane's -1d4, #225).
        DndSpell effectSpell = facts.effectSpellId() != null ? SpellLoader.getSpell(facts.effectSpellId()) : null;
        if (effectSpell != null && effectSpell.hasEffect()) {
            ActiveEffect effect = effectSpell.getEffect().copy();
            effect.setCasterId(facts.effectCasterId());
            target.give(effect);
            effectLines.add(Component.text(target.name() + " is under " + effectSpell.getName() + ": "
                    + SpellEffects.describe(effectSpell) + ".", NamedTextColor.YELLOW));
        }

        boolean applied = false;
        DndCondition cond = facts.conditionOnFail() != null && !facts.conditionOnFail().isBlank()
                ? ConditionLoader.get(facts.conditionOnFail()) : null;
        if (cond != null && target.isImmuneTo(cond.getId())) {
            conditionLines.add(Component.text(target.name() + " is immune to being " + cond.getName() + ".", NamedTextColor.GRAY)); // #252
        } else if (cond != null && target.addCondition(cond.getId())) {
            applied = true;
            // Incapacitated ends concentration outright, no save (PHB p.203).
            if (target.cannotAct()) target.endConcentration("they were " + cond.getName().toLowerCase());
            target.showCondition(cond);
            conditionLines.add(Component.text(target.name() + " is now " + cond.getName() + "!", NamedTextColor.YELLOW));
        }
        return new Outcome(false, facts.hasDamage() ? Owed.FULL : Owed.NONE, effectLines, conditionLines, applied);
    }
}
