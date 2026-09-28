package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Prepared spells (PHB p.58, #218). A prepared caster chooses, after each long rest, which spells
 * they have ready: a number worked out by the class's {@code spells_prepared_formula}.
 *
 * <ul>
 *   <li><b>From the class list</b> (cleric, druid, paladin, artificer): any spell on their list up to
 *       their highest slot. Their prepared spells <em>are</em> their known spells.</li>
 *   <li><b>From a spellbook</b> (wizard): the spellbook ({@code spells_known_by_level}) holds more than
 *       they can prepare; {@link CharacterSheet#getPreparedSpellIds()} is the day's pick.</li>
 * </ul>
 * Cantrips, racial spells and a subclass's always-prepared spells (domain spells) don't count.
 *
 * <p><b>Rituals</b> (PHB p.201): a wizard can cast a ritual spell from the spellbook without preparing
 * it; a cleric or druid can ritual-cast only what they have prepared.
 *
 * <p>{@link #castRefusal} is the one question every cast path asks.
 */
public final class PreparedSpells {

    private PreparedSpells() {}

    public enum Kind { NONE, CLASS_LIST, SPELLBOOK }

    public static Kind kind(CharacterSheet s) {
        SpellcastingInfo info = info(s);
        if (info == null || !"prepared".equalsIgnoreCase(info.getPreparationType())) return Kind.NONE;
        boolean book = info.getSpellsKnownByLevel() != null && info.getSpellsKnownByLevel().stream().anyMatch(n -> n != null && n > 0);
        return book ? Kind.SPELLBOOK : Kind.CLASS_LIST;
    }

    /** How many they may prepare (not counting always-prepared spells). */
    public static int max(CharacterSheet s) {
        SpellcastingInfo info = info(s);
        if (info == null) return 0;
        if (info.getSpellsPreparedFormula() != null) {
            EnumMap<Ability, Integer> scores = new EnumMap<>(Ability.class);
            for (Ability a : Ability.values()) scores.put(a, s.getAbility(a));
            return info.getSpellsPreparedFormula().calculate(scores, s.getTotalLevel());
        }
        Ability ability = Ability.fromString(info.getCastingAbility());
        return Math.max(1, (ability != null ? s.getModifier(ability) : 0) + s.getTotalLevel());
    }

    /** The highest spell level they have slots for (what a cleric can prepare up to). */
    public static int highestSlotLevel(CharacterSheet s) {
        int top = 0;
        for (int lvl = 1; lvl <= 9; lvl++) if (s.getMaxSpellSlots(lvl) > 0) top = lvl;
        return top;
    }

    /** What they choose from: their class list up to their highest slot, or their spellbook. */
    public static List<DndSpell> candidates(CharacterSheet s) {
        Set<String> always = s.alwaysPreparedSpellIds();
        List<DndSpell> out = new ArrayList<>();
        switch (kind(s)) {
            case CLASS_LIST -> {
                int top = highestSlotLevel(s);
                String list = info(s).getSpellList() != null ? info(s).getSpellList() : s.getMainClass().getName();
                for (DndSpell sp : SpellLoader.getSpellsForClass(list)) {
                    if (sp.getLevel() >= 1 && sp.getLevel() <= top && !always.contains(sp.getId().toLowerCase())) out.add(sp);
                }
            }
            case SPELLBOOK -> {
                for (DndSpell sp : s.getKnownSpells()) {
                    if (sp.getLevel() >= 1 && !always.contains(sp.getId().toLowerCase())) out.add(sp);
                }
            }
            case NONE -> { }
        }
        out.sort(Comparator.comparingInt(DndSpell::getLevel).thenComparing(DndSpell::getName));
        return out;
    }

    /** Their prepared spells today, not counting always-prepared ones. */
    public static List<DndSpell> prepared(CharacterSheet s) {
        List<DndSpell> out = new ArrayList<>();
        for (DndSpell sp : candidates(s)) if (isPrepared(s, sp)) out.add(sp);
        return out;
    }

    /** Whether this spell is ready to cast normally. Cantrips, racial and always-prepared spells always are. */
    public static boolean isPrepared(CharacterSheet s, DndSpell spell) {
        if (spell == null) return false;
        if (spell.getLevel() == 0 || s.alwaysPreparedSpellIds().contains(spell.getId().toLowerCase())) return true;
        if (s.findAvailableInnateSpell(spell) != null) return true;
        return switch (kind(s)) {
            case NONE -> s.knowsSpell(spell);
            case CLASS_LIST -> knowsById(s, spell);
            case SPELLBOOK -> s.getPreparedSpellIds() != null && s.getPreparedSpellIds().contains(spell.getId().toLowerCase());
        };
    }

    /** A spellbook spell (wizard): in the book, prepared or not. */
    public static boolean inSpellbook(CharacterSheet s, DndSpell spell) {
        return kind(s) == Kind.SPELLBOOK && knowsById(s, spell);
    }

    /** Whether prepared spells can be changed right now: after a long rest, or a new wizard's first time. */
    public static boolean canChangeNow(CharacterSheet s) {
        Kind k = kind(s);
        if (k == Kind.NONE) return false;
        return s.isLongRestOpen() || (k == Kind.SPELLBOOK && s.getPreparedSpellIds() == null);
    }

    /** Why it can't change now, for the menu. */
    public static String whenYouCanChange() {
        return "You can change your prepared spells after a long rest (until you join a fight or rest again).";
    }

    /**
     * Prepare or unprepare one spell. Null when it worked, else why not. Doesn't check
     * {@link #canChangeNow}; the menu does, so it can show the list either way.
     */
    public static String toggle(CharacterSheet s, DndSpell spell) {
        if (spell == null || !candidates(s).contains(spell)) return "That isn't a spell you can prepare.";
        boolean now = isPrepared(s, spell);
        if (!now && prepared(s).size() >= max(s)) return "You can prepare " + max(s) + ": unprepare one first.";
        switch (kind(s)) {
            case CLASS_LIST -> {
                if (now) s.unprepareClassSpell(spell); else s.prepareClassSpell(spell);
            }
            case SPELLBOOK -> {
                Set<String> ids = s.getPreparedSpellIds() != null ? new LinkedHashSet<>(s.getPreparedSpellIds()) : new LinkedHashSet<>();
                if (now) ids.remove(spell.getId().toLowerCase()); else ids.add(spell.getId().toLowerCase());
                s.setPreparedSpellIds(ids);
            }
            case NONE -> { return "You don't prepare spells."; }
        }
        return null;
    }

    /**
     * The one question every cast path asks: null if they can cast it, else what to tell them.
     * {@code asRitual}: cast as a ritual (+10 minutes, no slot).
     */
    public static String castRefusal(CharacterSheet s, DndSpell spell, boolean asRitual) {
        if (spell == null) return "Unknown spell.";
        Kind k = kind(s);
        if (!s.knowsSpell(spell)) {
            // A cleric's unprepared spell: they know the whole list, it just isn't ready today.
            if (k == Kind.CLASS_LIST && candidates(s).contains(spell)) {
                return spell.getName() + " isn't prepared. " + whenYouCanChange()
                        + (asRitual || spell.isRitual() ? " (A cleric or druid can only cast a ritual they have prepared.)" : "");
            }
            return s.getCharacterName() + " doesn't know " + spell.getName() + ".";
        }
        if (asRitual) {
            if (!spell.isRitual()) return spell.getName() + " can't be cast as a ritual.";
            SpellcastingInfo info = info(s);
            boolean innate = s.findAvailableInnateSpell(spell) != null;
            if (!innate && (info == null || !info.isRitualCasting())) return s.getCharacterName() + "'s class can't cast rituals.";
            if (inSpellbook(s, spell)) return null; // a wizard: from the book, prepared or not
            return isPrepared(s, spell) ? null : spell.getName() + " isn't prepared. " + whenYouCanChange();
        }
        if (isPrepared(s, spell)) return null;
        if (k == Kind.SPELLBOOK && s.getPreparedSpellIds() == null) {
            return "You haven't prepared any spells yet: open your spellbook and use Prepare Spells.";
        }
        return spell.getName() + " is in your spellbook but not prepared. " + whenYouCanChange()
                + (spell.isRitual() ? " It's a ritual, so you can still cast it as one (10 extra minutes, no slot)." : "");
    }

    private static boolean knowsById(CharacterSheet s, DndSpell spell) {
        for (DndSpell k : s.getKnownSpells()) if (k.getId().equalsIgnoreCase(spell.getId())) return true;
        return false;
    }

    private static SpellcastingInfo info(CharacterSheet s) {
        return s == null || s.getMainClass() == null ? null : s.getMainClass().getSpellcastingInfo();
    }
}
