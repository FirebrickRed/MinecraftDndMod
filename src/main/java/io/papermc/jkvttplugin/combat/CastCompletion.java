package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.UUID;

/**
 * "The cast went through" (#269): the part that's the same wherever a spell is cast. In this order,
 * once each: the readied spell is cleared, a DM's "close enough" is used, the cost is spent, the new
 * concentration replaces the old, and a mark spell (Hex, Hunter's Mark) marks its target.
 *
 * <p>It changes the sheet and hands back {@link Done} for the caller to announce to its own audience.
 * What differs stays with the callers: a fight's Action, bonus action or reaction and its turn-scoped AC
 * bonus ({@code CombatCommand.afterCast}); the out-of-combat "Spent …" line and the DM's approval before
 * it ({@code OutOfCombatAttack.commit}). Call it only when the cast has resolved: a prompt, a refusal, a
 * denied or still-unapproved cast, an unconfirmed area, complete nothing.
 *
 * <p>There were three copies. The out-of-combat ones didn't clear the readied spell, a ritual replaced
 * concentration without a word, and a mark cast out of a fight spent its slot and marked nobody.
 */
public final class CastCompletion {

    private CastCompletion() {}

    /** Whom a mark spell marks, the rider damage on the caster's hits, and (Hex) the ability the target is worse at. */
    public record Mark(UUID targetId, String damage, String damageType, String disadvantageAbility) {
        public static Mark of(DndSpell spell, UUID targetId, Ability choice) {
            return new Mark(targetId, spell.getMarkDamage(), spell.getDamageType(), choice != null ? choice.name().toLowerCase() : null);
        }
    }

    /**
     * What completing the cast did. {@code spent} is "level 1 slot (1 left)", "an innate use", or empty for
     * a cantrip or a ritual. {@code concentrationEnded} is the spell they were concentrating on before, when
     * this one replaced it. {@code concentrating} is whether they're now concentrating on this spell.
     */
    public record Done(String spent, DndSpell concentrationEnded, boolean concentrating) {}

    /**
     * @param playerId the caster's player, whose readied spell and reach allowance this cast uses; null in tests of a sheet alone
     * @param cost     what it costs; null when it costs nothing by rule (a ritual)
     * @param mark     for a mark spell, what it marks; null otherwise
     */
    public static Done finish(UUID playerId, CharacterSheet sheet, DndSpell spell, SpellCost cost, Mark mark) {
        if (playerId != null) {
            SpellTargeting.clear(playerId); // a readied spell is cast
            Reach.spend(playerId);          // a DM's "close enough" covered this one cast
        }
        String spent = "";
        if (cost != null) {
            cost.spend(sheet, spell);
            spent = cost.spentLabel(sheet);
        }
        DndSpell ended = null;
        boolean concentrating = false;
        if (sheet != null && spell.isConcentration()) {
            // A second concentration spell replaces the first (PHB p.203). Replacing also ends its effects
            // on others and a mark it held (CharacterSheet.setConcentratingOn), so the mark below comes after.
            if (sheet.isConcentrating() && sheet.getConcentratingOn() != spell) ended = sheet.getConcentratingOn();
            sheet.setConcentratingOn(spell);
            concentrating = true;
        }
        if (sheet != null && mark != null) {
            sheet.setSpellMark(mark.targetId(), mark.damage(), mark.damageType(), mark.disadvantageAbility());
        }
        return new Done(spent, ended, concentrating);
    }
}
