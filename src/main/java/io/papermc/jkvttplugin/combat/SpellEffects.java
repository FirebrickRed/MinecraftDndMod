package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.effect.ActiveEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A spell's timed effect on the creatures it targets (#225): Bless's +1d4, Bane's -1d4, Guidance's
 * +1d4 on one check, Shield of Faith's +2 AC. The effect itself is an ordinary {@link ActiveEffect}
 * (the spell's {@code effect:} block), held by a character's sheet or a creature.
 *
 * <p>How long it lasts:
 * <ul>
 *   <li><b>In a fight</b>, rounds: it ticks at the holder's turn start, like Rage.</li>
 *   <li><b>Out of a fight</b>, the DM's clock: moving time forward ({@code /dm time add}, the Time tool,
 *       a rest) runs it down, 10 rounds a minute. A running daylight cycle doesn't: a Minecraft minute is
 *       under a real second, and Bless shouldn't be gone before anyone rolls.</li>
 *   <li><b>Concentration</b>: when the caster's ends, so does the spell on everyone.</li>
 *   <li><b>Once</b> (Guidance, Resistance): the roll that uses it ends it.</li>
 * </ul>
 */
public final class SpellEffects {

    private SpellEffects() {}

    // ==================== APPLY ====================

    /** Put {@code spell}'s effect on {@code target}, as part of cast {@code castId} by {@code casterId} (a character id). */
    public static void apply(UUID casterId, Combatant target, DndSpell spell, long castId) {
        give(target, castEffect(casterId, spell, castId));
    }

    private static final java.util.concurrent.atomic.AtomicLong CASTS = new java.util.concurrent.atomic.AtomicLong();

    /**
     * The identity of one actual cast (#269): taken once when a cast begins, and carried by everything
     * that cast does: the effects it puts on its targets ({@link #castEffect}), the saves it leaves pending
     * ({@code SpellSave.Facts.castId}), and the caster's concentration once it completes
     * ({@code CharacterSheet.getConcentrationCastId}). The spell's id can't tell two casts of the same spell
     * apart; this does. Never 0, which means "no known cast" (an effect or concentration loaded from disk).
     */
    public static long newCast() { return CASTS.incrementAndGet(); }

    /**
     * {@code spell}'s effect, ready to put on one target of cast {@code castId} by {@code casterId}: a copy
     * that knows its caster and its cast. Every spell effect goes on through this.
     */
    public static ActiveEffect castEffect(UUID casterId, DndSpell spell, long castId) {
        ActiveEffect e = spell.getEffect().copy();
        e.setCasterId(casterId);
        e.setCastId(castId);
        return e;
    }

    /**
     * Whether cast {@code castId} of {@code spell} is still the one {@code casterCharacterId} is
     * concentrating on. A concentration spell's effect that lands late (a save answered after the cast)
     * may only go on while this holds: the same spell cast again is another cast, and so is concentration
     * that ended and came back. A caster the game can't find can't be shown to still hold it.
     */
    public static boolean ownsConcentration(UUID casterCharacterId, DndSpell spell, long castId) {
        if (casterCharacterId == null || spell == null || castId == 0) return false;
        CharacterSheet caster = CharacterSheetManager.getCharacterById(casterCharacterId);
        return caster != null && caster.isConcentrating()
                && caster.getConcentratingOn().getId().equalsIgnoreCase(spell.getId())
                && caster.getConcentrationCastId() == castId;
    }
    /**
     * Put an effect on {@code target}: a spell's, or a feature's given to someone else (Bardic
     * Inspiration, #40). A second from the same source replaces the first: the same spell doesn't
     * stack (PHB p.205), and a creature holds one Bardic Inspiration die. A held effect on a creature
     * is armed at once, since the DM rolls for it anyway; a character is asked after each roll (InspirationPrompt).
     */
    public static void give(Combatant target, ActiveEffect e) {
        CharacterSheet sheet = target.getCharacterSheet();
        if (sheet != null) {
            sheet.addEffect(e);
            if (e.isHeld()) tellHeld(sheet, e);
        } else if (target.getEntityInstance() != null) {
            if (e.isHeld()) e.setArmed(true);
            List<ActiveEffect> list = target.getEntityInstance().getEffects();
            list.removeIf(x -> x.getSourceId().equalsIgnoreCase(e.getSourceId()));
            list.add(e);
        }
    }

    /** Tell a character what they were given, and that they'll be asked after their rolls (#40). */
    private static void tellHeld(CharacterSheet sheet, ActiveEffect e) {
        if (Bukkit.getServer() == null) return;
        Player p = sheet.getPlayerId() != null ? Bukkit.getPlayer(sheet.getPlayerId()) : null;
        if (p == null) return;
        p.sendMessage(Component.text("🎵 You have " + e.getSourceName() + " (" + e.getRollBonusDice() + ", " + e.durationLabel()
                + "). After you roll an attack, save or check, you'll be asked whether to add it.", NamedTextColor.LIGHT_PURPLE));
    }


    /** What the effect does and for how long, for the cast line: "+1d4 to attacks and saves, 10 rounds". */
    public static String describe(DndSpell spell) {
        ActiveEffect e = spell.getEffect();
        String what = String.join(", ", e.describe());
        return what + (e.getRoundsRemaining() > 0 ? " for " + (e.getRoundsRemaining() >= 10 && e.getRoundsRemaining() % 10 == 0
                ? (e.getRoundsRemaining() / 10) + (e.getRoundsRemaining() == 10 ? " minute" : " minutes")
                : e.durationLabel()) : "");
    }

    // ==================== END ====================

    /** The caster's concentration on {@code spell} ended: the spell ends on everyone it's on. */
    public static void endConcentration(UUID casterId, DndSpell spell) {
        end(casterId, spell, e -> true, "concentration ended");
    }

    /**
     * The caster cast {@code spell} again while still concentrating on it (#269): their earlier casts of it
     * end on everyone they were on, and cast {@code castId} stays, whether its effects are already on
     * their targets (a fight applies them before the cast completes) or still to come. Someone in both
     * casts had their old effect replaced by the new one, so they keep it. Another caster's effects of the
     * same spell aren't touched.
     */
    public static void endOtherCasts(UUID casterId, DndSpell spell, long castId) {
        end(casterId, spell, e -> e.getCastId() != castId, "cast again");
    }
    private static void end(UUID casterId, DndSpell spell, java.util.function.Predicate<ActiveEffect> which, String why) {
        if (casterId == null || spell == null || !spell.hasEffect()) return;
        String source = spell.getEffect().getSourceId();
        java.util.function.Predicate<ActiveEffect> theirs =
                e -> source.equalsIgnoreCase(e.getSourceId()) && casterId.equals(e.getCasterId()) && which.test(e);
        List<String> from = new ArrayList<>();
        for (CharacterSheet s : CharacterSheetManager.getAllCharacters()) {
            if (!s.removeEffects(theirs).isEmpty()) {
                from.add(s.getCharacterName());
                tellHolder(s, spell.getName() + " on you has ended.");
            }
        }
        for (DndEntityInstance i : DndEntityInstance.getAll()) {
            if (i.getEffects().removeIf(theirs)) {
                from.add(i.getDisplayName());
            }
        }
        if (!from.isEmpty()) toDms(spell.getName() + " ends on " + String.join(", ", from) + " (" + why + ").");
    }

    /**
     * A roll of {@code kind} just used this holder's once-only bonus (Guidance on a check, Resistance on
     * a save): that effect ends. Call it once the roll has resolved. The spell ends with it, so a caster
     * concentrating on it stops.
     */
    public static void useUp(Combatant holder, String kind) {
        if (holder == null) return;
        CharacterSheet sheet = holder.getCharacterSheet();
        List<ActiveEffect> used = new ArrayList<>();
        if (sheet != null) {
            used.addAll(sheet.removeEffects(e -> e.isUntilUsed() && e.rollBonusFor(kind) != null));
        } else {
            useUp(holder.getEntityInstance(), kind);
            return;
        }
        useUp(used);
    }

    /** {@link #useUp(Combatant, String)} for a creature (the DM rolled its check or save). */
    public static void useUp(DndEntityInstance creature, String kind) {
        if (creature == null) return;
        List<ActiveEffect> used = new ArrayList<>();
        creature.getEffects().removeIf(e -> {
            if (e.isUntilUsed() && e.rollBonusFor(kind) != null) { used.add(e); return true; }
            return false;
        });
        useUp(used);
    }

    /** {@link #useUp(Combatant, String)} for a character rolled from their sheet, in or out of a fight. */
    public static void useUp(CharacterSheet sheet, String kind) {
        if (sheet == null) return;
        useUp(sheet.removeEffects(e -> e.isUntilUsed() && e.rollBonusFor(kind) != null));
    }

    private static void useUp(List<ActiveEffect> used) {
        for (ActiveEffect e : used) {
            if (e.getCasterId() == null) continue;
            CharacterSheet caster = CharacterSheetManager.getCharacterById(e.getCasterId());
            if (caster != null && caster.isConcentrating()
                    && DndSpell.effectSourceId(caster.getConcentratingOn().getId()).equalsIgnoreCase(e.getSourceId())) {
                caster.breakConcentration();
                tellHolder(caster, e.getSourceName() + " was used, so your concentration on it ends.");
            }
        }
    }

    // ==================== TIME ====================

    /**
     * The DM moved the clock forward {@code minutes} (the Time tool, {@code /dm time add}, a rest's
     * time): every effect with a round timer runs down by 10 rounds a minute, in or out of a fight.
     */
    public static void passTime(int minutes) {
        if (minutes <= 0) return;
        int rounds = minutes * 10;
        for (CharacterSheet s : CharacterSheetManager.getAllCharacters()) {
            Set<ActiveEffect> ran = new HashSet<>();
            for (ActiveEffect e : s.getActiveEffects()) if (e.passRounds(rounds)) ran.add(e);
            if (ran.isEmpty()) { if (!s.getActiveEffects().isEmpty()) s.saveNow(); continue; } // fewer rounds left, still saved
            s.removeEffects(ran::contains);
            List<String> names = ran.stream().map(ActiveEffect::getSourceName).toList();
            tellHolder(s, String.join(", ", names) + " on you " + (names.size() == 1 ? "has" : "have") + " worn off.");
            toDms(String.join(", ", names) + " on " + s.getCharacterName() + " wore off.");
            endConcentrationOn(ran);
        }
        for (DndEntityInstance i : DndEntityInstance.getAll()) {
            List<ActiveEffect> ran = new ArrayList<>();
            i.getEffects().removeIf(e -> { if (e.passRounds(rounds)) { ran.add(e); return true; } return false; });
            if (ran.isEmpty()) continue;
            toDms(String.join(", ", ran.stream().map(ActiveEffect::getSourceName).toList()) + " on " + i.getDisplayName() + " wore off.");
            endConcentrationOn(ran);
        }
    }

    /** A creature's effects tick at its turn start in a fight (a character's tick on the sheet). Returns the ones that ended. */
    public static List<ActiveEffect> tickTurnStart(DndEntityInstance creature) {
        List<ActiveEffect> ended = new ArrayList<>();
        if (creature == null) return ended;
        creature.getEffects().removeIf(e -> { if (e.tickTurnStartAndCheckExpiry()) { ended.add(e); return true; } return false; });
        endConcentrationOn(ended);
        return ended;
    }

    /** A concentration spell that ran its full time ends for its caster too (so the action bar's ◈ goes). */
    public static void endConcentrationOn(java.util.Collection<ActiveEffect> ended) {
        for (ActiveEffect e : ended) {
            if (e.getCasterId() == null) continue;
            CharacterSheet caster = CharacterSheetManager.getCharacterById(e.getCasterId());
            if (caster == null || !caster.isConcentrating()) continue;
            if (!DndSpell.effectSourceId(caster.getConcentratingOn().getId()).equalsIgnoreCase(e.getSourceId())) continue;
            if (stillOnAnyone(e.getSourceId(), e.getCasterId())) continue;
            caster.breakConcentration();
        }
    }

    private static boolean stillOnAnyone(String source, UUID casterId) {
        for (CharacterSheet s : CharacterSheetManager.getAllCharacters()) {
            for (ActiveEffect e : s.getActiveEffects()) {
                if (source.equalsIgnoreCase(e.getSourceId()) && casterId.equals(e.getCasterId())) return true;
            }
        }
        for (DndEntityInstance i : DndEntityInstance.getAll()) {
            for (ActiveEffect e : i.getEffects()) {
                if (source.equalsIgnoreCase(e.getSourceId()) && casterId.equals(e.getCasterId())) return true;
            }
        }
        return false;
    }

    // ==================== MESSAGES ====================

    private static void tellHolder(CharacterSheet sheet, String text) {
        if (Bukkit.getServer() == null) return; // tests: no one to tell
        Player p = sheet.getPlayerId() != null ? Bukkit.getPlayer(sheet.getPlayerId()) : null;
        if (p != null) p.sendMessage(Component.text("✨ " + text, NamedTextColor.GRAY));
    }

    private static void toDms(String text) {
        if (Bukkit.getServer() == null) return;
        for (Player dm : DMManager.getOnlineDMs()) dm.sendMessage(Component.text("✨ " + text, NamedTextColor.GRAY));
    }
}
