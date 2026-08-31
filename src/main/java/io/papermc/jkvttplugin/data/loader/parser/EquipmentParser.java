package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.jkvttplugin.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses starting-equipment lists and equipment player-choices from YAML.
 * Split out of {@code LoaderUtils} (issue #12).
 */
public final class EquipmentParser {

    private EquipmentParser() {}

    public static List<String> parseEquipment(List<?> equipmentList) {
        List<String> result = new ArrayList<>();
        if (equipmentList == null) return result;

        for (Object entry : equipmentList) {
            if (entry instanceof String) {
                result.add((String) entry);
            } else if (entry instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) entry;
                Object itemObj = map.get("item");
                if (itemObj instanceof String) {
                    result.add((String) itemObj);
                } else {
                    io.papermc.jkvttplugin.JkVttPlugin.logger().warning("[EquipmentParser] Equipment entry object missing valid 'item' key: " + entry);
                }
            } else {
                io.papermc.jkvttplugin.JkVttPlugin.logger().warning("[EquipmentParser] Unexpected equipment entry type: " + entry);
            }
        }
        return result;
    }

    public static List<PlayersChoice<String>> parseEquipmentChoicesList(Object obj) {
        if (obj instanceof List<?> list) {
            List<PlayersChoice<String>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(PlayersChoice.fromMap(map, String.class, PlayersChoice.ChoiceType.EQUIPMENT));
                }
            }
            return result;
        }
        return List.of();
    }

    public static List<EquipmentOption> parseEquipmentOptions(Object node) {
        List<EquipmentOption> out = new ArrayList<>();
        if (!(node instanceof List<?> raw)) return out;

        for (Object opt : raw) {
            if (opt instanceof List<?> bundle) {
                List<EquipmentOption> parts = new ArrayList<>();
                for (Object part : bundle) {
                    EquipmentOption p = parseEquipmentElement(part);
                    if (p != null) {
                        parts.add(p);
                    }
                }
                if (!parts.isEmpty()) {
                    out.add(EquipmentOption.bundle(parts));
                }
                continue;
            }
            EquipmentOption single = parseEquipmentElement(opt);
            if (single != null) out.add(single);
        }
        return out;
    }

    private static EquipmentOption parseEquipmentElement(Object node) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey("item")) {
                String id = Util.normalize(ParseUtil.asString(m.get("item"), ""));
                int qty = ParseUtil.asInt(m.get("quantity"), 1);
                if (!id.isBlank()) {
                    return EquipmentOption.item(id, qty);
                }
            }
            if (m.containsKey("tag")) {
                String tag = Util.normalize(ParseUtil.asString(m.get("tag"), ""));
                if (!tag.isBlank()) {
                    return EquipmentOption.tag(tag);
                }
            }
        } else if (node instanceof String s) {
            String id = Util.normalize(s);
            if (!id.isBlank()) {
                return EquipmentOption.item(id, 1);
            }
        }
        return null;
    }

    @SuppressWarnings("unused") // kept for a planned choice-time tag expansion (see ChoiceParser EQUIPMENT case)
    private static List<EquipmentOption> expandTagsForChoices(List<EquipmentOption> options) {
        List<EquipmentOption> out = new ArrayList<>();
        for (var opt : options) {
            if (opt.getKind() == EquipmentOption.Kind.TAG) {
                for (String id : TagRegistry.itemsFor(opt.getIdOrTag())) {
                    out.add(EquipmentOption.item(id));
                }
            } else {
                out.add(opt);
            }
        }
        return out;
    }
}
