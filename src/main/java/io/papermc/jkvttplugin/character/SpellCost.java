package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.InnateSpell;

/**
 * What casting a spell costs a character right now: an innate use, a spell slot, or nothing
 * (a cantrip, or a caster with no sheet — an entity's "spells" are stat-block attacks).
 *
 * <p>Deliberately split into <em>check</em> and <em>spend</em>. A cast can bail out halfway —
 * waiting on a d20, an out-of-range target, a cancelled aim — and a slot that's already been
 * deducted is very hard to give back convincingly at the table. So: {@link #available()} before
 * the spell resolves, {@link #spend} only once it has.
 *
 * <p>This used to live nowhere: {@code /combat cast} never deducted a slot at all (the spellbook
 * menu did it, and routing to the combat command skipped that), so a 1st-level spell cast in a
 * fight was free. Both paths go through here now.
 */
public record SpellCost(Kind kind, int level) {

    public enum Kind {
        /** A cantrip, or nothing to spend. */
        FREE,
        /** A racial/innate casting with its own limited uses. */
        INNATE,
        /** An ordinary spell slot of {@link #level()}. */
        SLOT,
        /** Known innately, but out of uses. */
        NONE_LEFT_INNATE,
        /** No slot of that level remains. */
        NONE_LEFT_SLOT
    }

    /** Work out what {@code spell} costs {@code sheet} right now, from its own level. */
    public static SpellCost of(CharacterSheet sheet, DndSpell spell) {
        return of(sheet, spell, null);
    }

    /**
     * As above, but cast from a higher slot when {@code castLevel} says so — the spellbook offers
     * this ("⬆ Casting at 2nd level") and the slot spent has to match what the player was shown.
     * A null or too-low {@code castLevel} falls back to the spell's own level.
     *
     * <p>Upcasting an innate casting is meaningless — it has uses, not slots — so the level is
     * ignored in that case.
     */
    public static SpellCost of(CharacterSheet sheet, DndSpell spell, Integer castLevel) {
        if (sheet == null || spell == null || spell.getLevel() <= 0) return new SpellCost(Kind.FREE, 0);
        InnateSpell innate = null;
        for (InnateSpell i : sheet.getAvailableInnateSpells()) {
            if (i.getSpellId().equalsIgnoreCase(spell.getId())) { innate = i; break; }
        }
        if (innate != null) {
            return innate.canCast()
                    ? new SpellCost(Kind.INNATE, spell.getLevel())
                    : new SpellCost(Kind.NONE_LEFT_INNATE, spell.getLevel());
        }
        int level = (castLevel != null && castLevel > spell.getLevel()) ? castLevel : spell.getLevel();
        return sheet.hasSpellSlot(level)
                ? new SpellCost(Kind.SLOT, level)
                : new SpellCost(Kind.NONE_LEFT_SLOT, level);
    }

    public boolean available() {
        return kind == Kind.FREE || kind == Kind.INNATE || kind == Kind.SLOT;
    }

    /** Why the cast can't happen, phrased for the caster. Only meaningful when {@link #available()} is false. */
    public String unavailableReason(DndSpell spell) {
        return kind == Kind.NONE_LEFT_INNATE
                ? "No uses of " + spell.getName() + " left — it comes back on a rest."
                : "No level " + level + " spell slots left.";
    }

    /** Deduct the cost. Call only after the spell has actually resolved. */
    public void spend(CharacterSheet sheet, DndSpell spell) {
        if (sheet == null) return;
        if (kind == Kind.SLOT) {
            sheet.consumeSpellSlot(level);
        } else if (kind == Kind.INNATE) {
            for (InnateSpell innate : sheet.getAvailableInnateSpells()) {
                if (innate.getSpellId().equalsIgnoreCase(spell.getId())) { innate.use(); return; }
            }
        }
    }

    /** A short "what it cost" line for the cast announcement, or empty for a cantrip. */
    public String spentLabel(CharacterSheet sheet) {
        if (kind == Kind.SLOT) {
            return "level " + level + " slot (" + (sheet == null ? "?" : sheet.getSpellSlotsRemaining(level)) + " left)";
        }
        if (kind == Kind.INNATE) return "an innate use";
        return "";
    }
}
