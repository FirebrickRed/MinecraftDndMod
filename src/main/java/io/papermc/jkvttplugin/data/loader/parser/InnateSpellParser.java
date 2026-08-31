package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.InnateSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses racial innate-spell definitions from YAML (issue #51).
 * Split out of {@code LoaderUtils} (issue #12).
 *
 * Expected YAML structure:
 * innate_spells:
 *   - spell_id: thaumaturgy
 *     level_requirement: 1
 *     is_cantrip: true
 *     spell_level: 0
 *     uses: 0
 *     recovery: long_rest
 *     casting_ability: Charisma
 *     description: "Optional description"
 */
public final class InnateSpellParser {

    private InnateSpellParser() {}

    public static List<InnateSpell> parseInnateSpells(Object input) {
        if (!(input instanceof List<?> inputList)) return List.of();

        List<InnateSpell> result = new ArrayList<>();
        for (Object obj : inputList) {
            if (obj instanceof Map<?, ?> spellMap) {
                try {
                    InnateSpell spell = new InnateSpell();

                    // Required fields
                    spell.setSpellId(ParseUtil.asString(spellMap.get("spell_id"), null));
                    spell.setLevelRequirement(ParseUtil.asInt(spellMap.get("level_requirement"), 1));

                    // Cantrip vs leveled spell
                    Object isCantripObj = spellMap.get("is_cantrip");
                    boolean isCantrip = (isCantripObj instanceof Boolean) ? (Boolean) isCantripObj : false;
                    spell.setCantrip(isCantrip);

                    spell.setSpellLevel(ParseUtil.asInt(spellMap.get("spell_level"), 0));

                    // Parse uses - can be integer or "proficiency_bonus" string
                    Object usesObj = spellMap.get("uses");
                    if (usesObj instanceof String usesStr && "proficiency_bonus".equalsIgnoreCase(usesStr)) {
                        spell.setScalesWithProficiency(true);
                        spell.setUses(-1); // Marker value, will be calculated based on character's proficiency
                    } else {
                        spell.setUses(ParseUtil.asInt(usesObj, 0));
                        spell.setScalesWithProficiency(false);
                    }

                    spell.setRecovery(ParseUtil.asString(spellMap.get("recovery"), "long_rest"));

                    // Casting ability
                    String abilityStr = ParseUtil.asString(spellMap.get("casting_ability"), null);
                    if (abilityStr != null) {
                        try {
                            spell.setCastingAbility(Ability.fromString(abilityStr));
                        } catch (IllegalArgumentException e) {
                            JkVttPlugin.logger().warning("[InnateSpellParser] Invalid casting ability '" + abilityStr + "' for spell " + spell.getSpellId());
                        }
                    }

                    // Optional description
                    spell.setDescription(ParseUtil.asString(spellMap.get("description"), null));

                    // Only add if we have at least a spell ID
                    if (spell.getSpellId() != null && !spell.getSpellId().isBlank()) {
                        result.add(spell);
                    } else {
                        JkVttPlugin.logger().warning("[InnateSpellParser] Skipped innate spell with missing/blank spell_id");
                    }
                } catch (Exception e) {
                    JkVttPlugin.logger().warning("[InnateSpellParser] ERROR parsing innate spell: " + e.getMessage());
                }
            }
        }

        return result;
    }
}
