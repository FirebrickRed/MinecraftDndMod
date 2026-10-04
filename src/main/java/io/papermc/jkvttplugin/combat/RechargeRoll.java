package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.CreatureUses;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A creature's "Recharge X-Y" d6 (#256), rolled by the DM like every other die: the start of its turn
 * hands the DM the usual buttons, and {@code /combat recharge} takes the answer.
 */
public final class RechargeRoll {

    private RechargeRoll() {}

    /** "🎲 Giant Spider · Web (Recharge 5-6), roll its recharge: [Roll it] [I rolled…]". */
    public static Component prompt(DndEntityInstance creature, DndAttack attack) {
        String name = creature.getDisplayName();
        String base = "/combat recharge " + (name.contains(" ") ? "\"" + name + "\"" : name) + " "
                + CreatureUses.key(attack.getName()) + " ";
        return RollPrompt.line("🎲 " + name + " · " + attack.getName() + " (" + attack.limitLabel() + "), roll its recharge:",
                NamedTextColor.GOLD, base, "d6", null);
    }

    /** A d6 shows 1 to 6: anything else isn't a recharge roll. */
    public static boolean isD6(int n) {
        return n >= 1 && n <= 6;
    }
}
