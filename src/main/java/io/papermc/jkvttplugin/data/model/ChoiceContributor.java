package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.Util;

import java.util.List;
import java.util.function.Function;

/**
 * Turns parsed {@code player_choices} into the session's {@link PendingChoice}s — the one place
 * that does it for races, subraces, classes, subclasses and backgrounds alike.
 * <p>
 * Each of those used to carry its own copy of this switch, and they drifted: backgrounds dropped
 * {@code type: tool} choices on the floor, and race/subrace/subclass cast every choice to a string
 * list, so an equipment choice there would have thrown. Add a new choice type here, once.
 */
public final class ChoiceContributor {

    private ChoiceContributor() {}

    /** Adds a PendingChoice for every usable entry, tagged with {@code source}: who offers it ("Monk", "High Elf"). */
    public static void contribute(List<ChoiceEntry> entries, String source, List<PendingChoice<?>> out) {
        if (entries == null) return;
        for (ChoiceEntry e : entries) {
            if (!ChoiceUtil.usable(e.pc())) continue;
            PendingChoice<?> pc = toPending(e, source);
            if (pc != null) out.add(pc);
        }
    }

    @SuppressWarnings("unchecked")
    private static PendingChoice<?> toPending(ChoiceEntry e, String source) {
        return switch (e.type()) {
            case SKILL, CUSTOM -> PendingChoice.ofStrings(e.id(), e.title(), (PlayersChoice<String>) e.pc(), source);
            case TOOL -> strings(e, source, ToolRegistry::displayName);
            case LANGUAGE -> strings(e, source, LanguageRegistry::displayName);
            case EXPERTISE -> strings(e, source, ChoiceContributor::skillOrToolLabel);
            // A bonus-cantrip pick (high elf, Nature Domain); applied by CharacterSheetManager at finish.
            case SPELL -> strings(e, source, ChoiceContributor::spellLabel);
            case EQUIPMENT -> equipment(e, source);
            // Feat / ability-score picks aren't parsed from player_choices (feats don't exist yet;
            // racial ability choices have their own creation step).
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static PendingChoice<String> strings(ChoiceEntry e, String source, Function<String, String> label) {
        return PendingChoice.ofGeneric(e.id(), e.title(), (PlayersChoice<String>) e.pc(), source,
                s -> s, s -> s, label);
    }

    @SuppressWarnings("unchecked")
    private static PendingChoice<EquipmentOption> equipment(ChoiceEntry e, String source) {
        PlayersChoice<EquipmentOption> pc = (PlayersChoice<EquipmentOption>) e.pc();
        return PendingChoice.ofGeneric(e.id(), e.title(), pc, source,
                ChoiceContributor::equipmentKey, key -> equipmentFromKey(key, pc),
                EquipmentOption::prettyLabel);
    }

    /** An expertise option is a skill id or a tool id; name it either way. */
    private static String skillOrToolLabel(String key) {
        return io.papermc.jkvttplugin.data.model.enums.Skill.fromString(key) != null
                ? Util.prettify(key) : ToolRegistry.displayName(key);
    }

    private static String spellLabel(String id) {
        DndSpell spell = SpellLoader.getSpell(id);
        return spell != null ? spell.getName() : Util.prettify(id);
    }

    /** {@code item:rope@2}, {@code tag:martial_weapon}, {@code bundle:item:a+item:b@20}. */
    static String equipmentKey(EquipmentOption eo) {
        return switch (eo.getKind()) {
            case ITEM -> "item:" + eo.getIdOrTag() + (eo.getQuantity() > 1 ? "@" + eo.getQuantity() : "");
            case TAG -> "tag:" + eo.getIdOrTag();
            case BUNDLE -> "bundle:" + eo.getParts().stream()
                    .map(p -> p.getKind() == EquipmentOption.Kind.BUNDLE ? "bundle:..." : equipmentKey(p))
                    .reduce((a, b) -> a + "+" + b).orElse("empty");
        };
    }

    private static EquipmentOption equipmentFromKey(String key, PlayersChoice<EquipmentOption> pc) {
        if (key == null) return null;
        if (key.startsWith("item:")) {
            String rest = key.substring(5);
            int at = rest.indexOf('@');
            String id = (at >= 0) ? rest.substring(0, at) : rest;
            int qty = (at >= 0) ? ParseUtil.asInt(rest.substring(at + 1), 1) : 1;
            return EquipmentOption.item(id, qty);
        }
        if (key.startsWith("tag:")) return EquipmentOption.tag(key.substring(4));
        if (key.startsWith("bundle:")) {
            return pc.getOptions().stream()
                    .filter(o -> equipmentKey(o).equals(key))
                    .findFirst().orElse(null);
        }
        return null;
    }
}
