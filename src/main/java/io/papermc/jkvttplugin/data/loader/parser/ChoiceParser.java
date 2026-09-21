package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Parses generic {@code player_choices} blocks (skill/tool/language/spell/custom/equipment)
 * and standalone skill choices. Split out of {@code LoaderUtils} (issue #12).
 */
public final class ChoiceParser {

    private ChoiceParser() {}

    public static PlayersChoice<String> parseSkillChoice(Object node) {
        if (!(node instanceof List<?> blocks)) return null;

        for (Object block : blocks) {
            if (!(block instanceof Map<?, ?> m)) continue;
            Object pcNode = m.get("players_choice");
            if (pcNode instanceof Map<?, ?> pm) {
                int choose = ParseUtil.asInt(pm.get("choose"), 0);
                List<String> options = ParseUtil.normalizeStringList(pm.get("options"));
                if (choose > 0 && !options.isEmpty()) {
                    return new PlayersChoice<>(choose, options, PlayersChoice.ChoiceType.SKILL);
                }
            }
        }

        return null;
    }

    public static List<ChoiceEntry> parsePlayerChoices(Object node) {
        if (!(node instanceof List<?> arr)) {
            return List.of();
        }
        List<ChoiceEntry> out = new ArrayList<>();

        for (Object raw : arr) {
            if (!(raw instanceof Map<?, ?> m)) continue;

            String id = ParseUtil.asString(m.get("id"), "");
            String title = ParseUtil.asString(m.get("title"), id);
            String typeString = ParseUtil.asString(m.get("type"), "").toUpperCase();
            int choose = ParseUtil.asInt(m.get("choose"), 0);

            PlayersChoice<?> pc = null;
            PlayersChoice.ChoiceType type;

            switch(typeString) {
                case "SKILL" -> {
                    type = PlayersChoice.ChoiceType.SKILL;
                    var opts = ParseUtil.normalizeStringList(m.get("options"));

                    // Empty options means "choose from all skills"
                    if (opts.isEmpty()) {
                        opts = new ArrayList<>();
                        for (Skill skill : Skill.values()) {
                            opts.add(skill.name().toLowerCase());
                        }
                    }

                    pc = new PlayersChoice<>(choose, opts, type);
                }
                case "TOOL" -> {
                    type = PlayersChoice.ChoiceType.TOOL;
                    var rawOpts = ParseUtil.normalizeStringList(m.get("options"));

                    // Options are canonical tool ids (= item ids). Empty means "any tool"; a
                    // category tag (artisan_tool, musical_instrument, gaming_set, vehicle, tool)
                    // expands to every registered tool in it, homebrew items included.
                    LinkedHashSet<String> toolIds = new LinkedHashSet<>();
                    if (rawOpts.isEmpty()) {
                        toolIds.addAll(ToolRegistry.getAllTools());
                    } else {
                        for (String opt : rawOpts) {
                            List<String> expanded = ToolRegistry.expandTag(opt);
                            if (expanded != null) toolIds.addAll(expanded);
                            else toolIds.add(ToolRegistry.idOf(opt));
                        }
                    }

                    pc = new PlayersChoice<>(choose, new ArrayList<>(toolIds), type);
                }
                case "LANGUAGE" -> {
                    type = PlayersChoice.ChoiceType.LANGUAGE;
                    // Options are canonical language ids. Empty means "any language".
                    var opts = ParseUtil.normalizeStringList(m.get("options"));
                    List<String> langIds = opts.isEmpty()
                            ? LanguageRegistry.getAllLanguages()
                            : new ArrayList<>(new LinkedHashSet<>(opts.stream().map(LanguageRegistry::idOf).toList()));
                    pc = new PlayersChoice<>(choose, langIds, type);
                }
                case "SPELL" -> {
                    type = PlayersChoice.ChoiceType.SPELL;

                    // First check if explicit options are provided in YAML (e.g., Astral Fire: [Dancing Lights, Light, Sacred Flame])
                    List<String> spellOpts = ParseUtil.normalizeStringList(m.get("options"));

                    // If no explicit options, populate from spell_list (e.g., spell_list: wizard, spell_level: 0)
                    if (spellOpts.isEmpty()) {
                        String spellList = ParseUtil.asString(m.get("spell_list"), "");
                        int spellLevel = ParseUtil.asInt(m.get("spell_level"), -1);

                        if (!spellList.isEmpty()) {
                            if (spellLevel == 0) {
                                // Cantrips only - use spell IDs, not display names
                                spellOpts = SpellLoader.getCantripsForClass(spellList)
                                    .stream()
                                    .map(DndSpell::getId)
                                    .toList();
                            } else if (spellLevel > 0) {
                                // Specific spell level - use spell IDs, not display names
                                spellOpts = SpellLoader.getSpellsByLevel(spellList, spellLevel)
                                    .stream()
                                    .map(DndSpell::getId)
                                    .toList();
                            } else {
                                // All spells for that class - use spell IDs, not display names
                                spellOpts = SpellLoader.getSpellsForClass(spellList)
                                    .stream()
                                    .map(DndSpell::getId)
                                    .toList();
                            }
                        }
                    }

                    if (spellOpts.isEmpty()) {
                        io.papermc.jkvttplugin.JkVttPlugin.logger().warning("[ChoiceParser] SPELL choice '" + id + "' has empty spell options!");
                    }

                    pc = new PlayersChoice<>(choose, spellOpts, type);
                }
                case "CUSTOM" -> {
                    type = PlayersChoice.ChoiceType.CUSTOM;
                    var opts = ParseUtil.normalizeStringList(m.get("options"));
                    pc = new PlayersChoice<>(choose, opts, type);
                }
                case "EQUIPMENT" -> {
                    type = PlayersChoice.ChoiceType.EQUIPMENT;
                    var opts = EquipmentParser.parseEquipmentOptions(m.get("options"));
//                    opts = expandTagsForChoices(opts);
                    pc = new PlayersChoice<>(choose, opts, type);
                }
                default -> { continue; }
            }

            if (choose > 0 && pc.getOptions() != null && !pc.getOptions().isEmpty()) {
                out.add(new ChoiceEntry(id, title, pc.getType(), pc));
            }
        }
        return out;
    }
}
